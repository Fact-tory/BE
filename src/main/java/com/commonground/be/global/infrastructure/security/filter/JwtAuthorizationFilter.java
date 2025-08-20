package com.commonground.be.global.infrastructure.security.filter;


import com.commonground.be.global.application.response.HttpResponseDto;
import com.commonground.be.global.domain.security.AdminUserDetails;
import com.commonground.be.global.infrastructure.security.admin.AdminTokenValidator;
import com.commonground.be.global.infrastructure.security.jwt.JwtProvider;
import com.commonground.be.global.infrastructure.security.jwt.TokenManager;
import com.commonground.be.global.infrastructure.security.service.CustomUserDetailsService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

@Slf4j
@RequiredArgsConstructor
public class JwtAuthorizationFilter extends OncePerRequestFilter {

	private final JwtProvider jwtProvider;
	private final TokenManager tokenManager;
	private final AdminTokenValidator adminTokenValidator;
	private final CustomUserDetailsService userDetailsService;

	@Override
	protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res,
			FilterChain filterChain)
			throws ServletException, IOException {

		// OAuth2 소셜 로그인 관련 경로는 인증 없이 통과
		if (isPublicPath(req.getRequestURI())) {
			filterChain.doFilter(req, res);
			return;
		}

		// 1. X-Admin-Token을 먼저 확인 (관리자 토큰 우선 처리)
		String token = req.getHeader(AdminTokenValidator.ADMIN_TOKEN_HEADER);

		// 2. 없다면 Authorization 헤더 또는 쿠키에서 확인 (일반 JWT 토큰)
		if (!StringUtils.hasText(token)) {
			log.info("🔍 Authorization 헤더에서 토큰 추출 시도");
			token = jwtProvider.getAccessTokenFromHeader(req);
			if (StringUtils.hasText(token)) {
				log.info("✅ Authorization 헤더에서 토큰 발견");
			} else {
				log.info("🔍 Authorization 헤더에 토큰 없음, 쿠키에서 찾기 시도");
			}
			
			// 헤더에 없으면 쿠키에서 찾기
			if (!StringUtils.hasText(token)) {
				if (req.getCookies() != null) {
					log.info("🔍 쿠키 개수: {}", req.getCookies().length);
					for (jakarta.servlet.http.Cookie cookie : req.getCookies()) {
						log.debug("🔍 쿠키 확인: {} = {}", cookie.getName(), 
							cookie.getValue().length() > 20 ? cookie.getValue().substring(0, 20) + "..." : cookie.getValue());
						if ("AccessToken".equals(cookie.getName())) {
							token = cookie.getValue();
							log.info("✅ AccessToken 쿠키에서 토큰 발견");
							break;
						}
					}
					if (!StringUtils.hasText(token)) {
						log.info("❌ AccessToken 쿠키에 토큰 없음");
					}
				} else {
					log.info("❌ 요청에 쿠키가 없음");
				}
			}
		} else {
			log.info("✅ X-Admin-Token 헤더에서 토큰 발견");
		}

		// 토큰이 헤더에 존재하는 경우
		if (StringUtils.hasText(token)) {
			log.info("🔍 토큰 발견 - URI: {}, 토큰 시작: {}", req.getRequestURI(), token.substring(0, Math.min(token.length(), 20)) + "...");
			
			// X-Admin-Token 헤더로 온 경우는 관리자 토큰으로 바로 처리
			boolean isAdminTokenHeader = StringUtils.hasText(req.getHeader(AdminTokenValidator.ADMIN_TOKEN_HEADER));
			log.info("🔍 토큰 타입 검사 - isAdminTokenHeader: {}", isAdminTokenHeader);
			
			if (isAdminTokenHeader) {
				// 관리자 토큰 검증
				log.info("🔍 관리자 토큰 검증 시작");
				if (adminTokenValidator.isValidAdminToken(token)) {
					setAdminAuthentication();
					log.info("✅ 관리자 토큰으로 인증 성공 - URI: {}", req.getRequestURI());
				} else {
					log.warn("❌ 관리자 토큰 검증 실패 - URI: {}", req.getRequestURI());
					jwtExceptionHandler(res, HttpStatus.UNAUTHORIZED, "유효하지 않은 관리자 토큰입니다.");
					return;
				}
			} else {
				// JWT 토큰을 먼저 검증 (일반적인 경우)
				log.info("🔍 JWT 토큰 검증 시작");
				if (validateTokenAndSession(token, res)) {
					setAuthentication(token);
					log.info("✅ OAuth2 JWT 토큰으로 인증 성공 - URI: {}", req.getRequestURI());
				}
				// JWT 토큰이 아닌 경우 관리자 토큰으로 재시도
				else {
					log.info("🔍 JWT 토큰 검증 실패, 관리자 토큰으로 재시도");
					if (adminTokenValidator.isValidAdminToken(token)) {
						setAdminAuthentication();
						log.info("✅ 관리자 토큰으로 인증 성공 - URI: {}", req.getRequestURI());
					}
					// 어떤 토큰으로도 유효하지 않은 경우
					else {
						log.warn("❌ 토큰 검증 실패 (JWT와 관리자 토큰 모두 실패) - URI: {}", req.getRequestURI());
						jwtExceptionHandler(res, HttpStatus.UNAUTHORIZED, "유효하지 않은 토큰입니다.");
						return; // 필터 체인 종료
					}
				}
			}
		} else {
			log.info("🔍 토큰 없음 - URI: {} (공개 접근 또는 인증 불필요)", req.getRequestURI());
		}
		// 토큰이 아예 없는 요청은 그냥 통과시킨다 (이후 Spring Security의 .hasRole() 등에서 차단)

		filterChain.doFilter(req, res);
	}

	/**
	 * JWT 토큰으로 UserDetails 기반 인증 설정
	 */
	private void setAuthentication(String token) {
		try {
			String username = jwtProvider.getUsernameFromToken(token);
			log.debug("JWT 토큰에서 추출한 username: {}", username);
			
			// JWT 토큰에서 이메일과 이름 추출 (세션 ID 제거)
			String email = jwtProvider.getClaimFromToken(token, "email");
			String name = jwtProvider.getClaimFromToken(token, "name");

			// UserDetailsService를 통해 UserDetails 생성 (단순화)
			UserDetails userDetails;
			
			// 이메일과 이름이 모두 있는 경우 더 정확한 매칭 사용
			if (email != null && name != null) {
				log.debug("이메일과 이름으로 UserDetails 로드: email={}, name={}", email, name);
				userDetails = userDetailsService.loadUserByEmailAndName(email, name);
			} else {
				log.debug("기본 UserDetails 로드: username={}", username);
				userDetails = userDetailsService.loadUserByUsername(username);
			}

			// Spring Security Authentication 생성
			Authentication authentication = new UsernamePasswordAuthenticationToken(
					userDetails, null, userDetails.getAuthorities());

			SecurityContext context = SecurityContextHolder.createEmptyContext();
			context.setAuthentication(authentication);
			SecurityContextHolder.setContext(context);

			log.info("✅ JWT 토큰 UserDetails 인증 설정 완료: username={}, authorities={}", 
				username, userDetails.getAuthorities());

		} catch (Exception e) {
			log.error("❌ JWT 토큰에서 UserDetails 생성 실패: {}", e.getMessage());
			// 사용자를 찾을 수 없는 경우 인증 실패로 처리하지만 예외를 던지지 않음
			// Spring Security가 이후 인증 체크에서 처리하도록 함
		}
	}

	/**
	 * 관리자 토큰을 위한 인증 설정
	 */
	private void setAdminAuthentication() {
		SecurityContext context = SecurityContextHolder.createEmptyContext();
		AdminUserDetails adminUserDetails = new AdminUserDetails();
		Authentication authentication = new UsernamePasswordAuthenticationToken(
				adminUserDetails, null, adminUserDetails.getAuthorities());
		context.setAuthentication(authentication);
		SecurityContextHolder.setContext(context);
	}

	private void jwtExceptionHandler(HttpServletResponse res, HttpStatus status, String msg) {
		int statusCode = status.value();
		res.setStatus(statusCode);
		res.setContentType("application/json");
		try {
			String json = new ObjectMapper().writeValueAsString(
					new HttpResponseDto(statusCode, msg));
			res.getWriter().write(json);
		} catch (Exception e) {
			log.error(e.getMessage());
		}
	}

	/**
	 * 간소화된 JWT 토큰 유효성 검증
	 */
	private boolean validateTokenAndSession(String token, HttpServletResponse res) {
		try {
			log.info("🔍 JWT 토큰 유효성 검증 시작");
			
			// JWT 기본 유효성 검증만 수행 (세션 검증 제거)
			if (!jwtProvider.validateAccessToken(token)) {
				log.warn("❌ JWT 토큰 유효성 검증 실패");
				return false;
			}
			
			// 사용자명 추출 가능성 확인
			String username = jwtProvider.getUsernameFromToken(token);
			log.info("🔍 토큰에서 추출된 username: {}", username);
			
			if (username == null || username.trim().isEmpty()) {
				log.warn("❌ 토큰에서 사용자명 추출 실패");
				return false;
			}
			
			log.info("✅ JWT 토큰 검증 성공 - user: {}", username);
			return true;

		} catch (Exception e) {
			log.error("❌ 토큰 검증 중 오류: {}", e.getMessage(), e);
			return false;
		}
	}


	/**
	 * 인증이 필요하지 않은 공개 경로인지 확인 OAuth2 소셜 로그인 및 공개 API 경로들을 포함
	 */
	private boolean isPublicPath(String requestURI) {
		// OAuth2 소셜 로그인 관련 경로
		if (requestURI.startsWith("/api/v1/auth/social/")) {
			return true;
		}

		// OAuth2 API 로그인 경로 (validate는 인증 필요하므로 제외)
		if (requestURI.equals("/api/v1/auth/oauth2/login")) {
			return true;
		}

		// 토큰 재발급 경로 (쿠키에서 refresh token 읽어야 하므로 공개)
		if (requestURI.equals("/api/v1/auth/reissue")) {
			return true;
		}

		// 헬스체크 및 기타 공개 경로
		if (requestURI.equals("/health") ||
				requestURI.equals("/") ||
				requestURI.startsWith("/static/") ||
				requestURI.startsWith("/public/")) {
			return true;
		}

		// 레거시 경로 지원 (하위 호환성)
		if (requestURI.equals("/v1/users/login") ||
				requestURI.startsWith("/v1/users/oauth/")) {
			log.warn("레거시 OAuth 경로 사용됨: {} - /api/v1/auth/social/ 사용 권장", requestURI);
			return true;
		}

		return false;
	}
}
