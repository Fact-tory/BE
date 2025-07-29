package com.commonground.be.global.security.filters;


import com.commonground.be.domain.session.service.SessionService;
import com.commonground.be.global.response.HttpResponseDto;
import com.commonground.be.global.security.details.CustomUserDetailsService;
import com.commonground.be.global.security.JwtProvider;
import com.commonground.be.global.security.TokenManager;
import com.commonground.be.global.security.admin.AdminTokenValidator;
import com.commonground.be.global.security.admin.AdminUserDetails;
import com.fasterxml.jackson.databind.ObjectMapper;
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
	private final SessionService sessionService;
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

		String accessToken = jwtProvider.getAccessTokenFromHeader(req);

		if (StringUtils.hasText(accessToken)) {
			// 1. 먼저 관리자 토큰인지 확인
			if (adminTokenValidator.isValidAdminToken(accessToken)) {
				setAdminAuthentication();
				log.info("관리자 토큰으로 인증 성공 - URI: {}", req.getRequestURI());
			}
			// 2. 일반 JWT 토큰 검증
			else if (validateTokenAndSession(accessToken, res)) {
				setAuthentication(accessToken);
				log.debug("OAuth2 JWT 토큰으로 인증 성공");
			} else {
				jwtExceptionHandler(res, HttpStatus.UNAUTHORIZED, "유효하지 않은 토큰입니다.");
				return;
			}
		}
		filterChain.doFilter(req, res);
	}

	/**
	 * JWT 토큰으로 UserDetails 기반 인증 설정
	 */
	private void setAuthentication(String token) {
		try {
			String username = jwtProvider.getUsernameFromToken(token);
			String sessionId = jwtProvider.getClaimFromToken(token, "sessionId");

			// UserDetailsService를 통해 UserDetails 생성
			UserDetails userDetails;
			if (sessionId != null) {
				userDetails = userDetailsService.loadUserByUsernameWithSession(username, sessionId);
			} else {
				userDetails = userDetailsService.loadUserByUsername(username);
			}

			// Spring Security Authentication 생성
			Authentication authentication = new UsernamePasswordAuthenticationToken(
					userDetails, null, userDetails.getAuthorities());

			SecurityContext context = SecurityContextHolder.createEmptyContext();
			context.setAuthentication(authentication);
			SecurityContextHolder.setContext(context);

			log.debug("JWT 토큰 UserDetails 인증 설정 완료: username={}", username);

		} catch (Exception e) {
			log.error("JWT 토큰에서 UserDetails 생성 실패: {}", e.getMessage());
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
	 * 토큰 및 세션 유효성 검증
	 */
	private boolean validateTokenAndSession(String token, HttpServletResponse res) {
		try {
			// 1. 기본 토큰 유효성 검증
			if (!jwtProvider.validateAccessToken(token)) {
				return false;
			}

			// 2. 토큰 버전 검증 (TokenManager 활용)
			String username = jwtProvider.getUsernameFromToken(token);
			String tokenVersionStr = jwtProvider.getClaimFromToken(token, "tokenVersion");
			if (tokenVersionStr != null) {
				Long tokenVersion = Long.parseLong(tokenVersionStr);
				if (!tokenManager.isTokenVersionValid(username, tokenVersion)) {
					log.warn("토큰 버전 무효: user={}, tokenVersion={}", username, tokenVersion);
					return false;
				}
			}

			// 3. 세션 ID 추출 및 세션 유효성 검증
			String sessionId = extractSessionId(token);
			if (sessionId != null) {
				if (sessionService.validateSession(sessionId)) {
					// 세션 마지막 접근 시간 업데이트
					sessionService.updateSessionAccess(sessionId);
					return true;
				}
				return false;
			}

			// 4. 레거시 토큰 (세션 ID 없음) - 기존 로직 유지
			return true;

		} catch (Exception e) {
			log.error("토큰 검증 중 오류: {}", e.getMessage());
			return false;
		}
	}

	/**
	 * JWT에서 세션 ID 추출
	 */
	private String extractSessionId(String token) {
		try {
			return jwtProvider.getClaimFromToken(token, "sessionId");
		} catch (Exception e) {
			// 세션 ID가 없는 레거시 토큰
			return null;
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

		// 토큰 재발급 경로
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
