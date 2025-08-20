package com.commonground.be.domain.auth.service;

import com.commonground.be.domain.auth.dto.request.LogoutRequest;
import com.commonground.be.domain.auth.dto.request.WithdrawRequest;
import com.commonground.be.domain.auth.dto.response.AuthResponse;
import com.commonground.be.domain.auth.dto.response.UserInfoResponse;

import com.commonground.be.domain.user.entity.User;
import com.commonground.be.domain.user.service.SocialUserServiceInterface;
import com.commonground.be.domain.user.utils.UserRole;
import com.commonground.be.global.application.exception.AuthExceptions;
import com.commonground.be.global.infrastructure.security.jwt.JwtProvider;
import com.commonground.be.global.infrastructure.security.jwt.TokenManager;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class AuthServiceImpl implements AuthService {

	private final JwtProvider jwtProvider;
	private final TokenManager tokenManager;
	private final SocialUserServiceInterface userService;
	private final RedisTemplate<String, String> redisTemplate;

	@Override
	public Map<String, Object> reissueAccessToken(HttpServletRequest request,
			HttpServletResponse response) {
		log.info("토큰 재발급 요청");

		// 1. 다중 소스에서 Refresh Token 추출 시도
		String refreshToken = null;
		String sourceMethod = null;
		
		// 1-1. 쿠키에서 Refresh Token 추출 시도
		refreshToken = jwtProvider.getRefreshTokenFromCookie(request);
		if (refreshToken != null) {
			sourceMethod = "cookie";
			log.info("🔍 쿠키에서 Refresh Token 추출 성공");
		} else {
			log.info("🔍 쿠키에서 Refresh Token 없음, Access Token 기반 조회 시도");
			
			// 1-2. Access Token을 통해 사용자 정보 확인 후 Redis에서 Refresh Token 조회
			String accessToken = jwtProvider.getAccessTokenFromHeader(request);
			if (accessToken != null && jwtProvider.validateAccessToken(accessToken)) {
				String email = jwtProvider.getUsernameFromToken(accessToken);
				Long userId = getUserIdFromEmail(email);
				
				if (userId != null) {
					String redisKey = "RT:user:" + userId;
					refreshToken = redisTemplate.opsForValue().get(redisKey);
					
					if (refreshToken != null) {
						sourceMethod = "redis_via_access_token";
						log.info("🔍 Redis에서 Refresh Token 추출 성공 - userId: {}", userId);
					}
				}
			}
		}
		
		if (refreshToken == null) {
			log.warn("❌ Refresh Token을 찾을 수 없음 (쿠키 및 Redis 모두 확인함)");
			throw AuthExceptions.invalidRefreshToken();
		}
		
		log.info("✅ Refresh Token 추출 완료 - 소스: {}", sourceMethod);

		// 2. Bearer 접두사 제거 (JWT 파싱을 위해)
		String cleanRefreshToken = refreshToken.startsWith(JwtProvider.BEARER_PREFIX) 
			? refreshToken.substring(JwtProvider.BEARER_PREFIX.length())
			: refreshToken;
		log.info("🔍 Refresh Token 정리 완료 - Bearer 접두사 제거: {}", cleanRefreshToken.substring(0, Math.min(20, cleanRefreshToken.length())));

		// 3. Refresh Token 검증
		if (!jwtProvider.validateAccessToken(cleanRefreshToken)) {
			log.warn("❌ 유효하지 않은 Refresh Token");
			throw AuthExceptions.invalidRefreshToken();
		}
		log.info("✅ Refresh Token 유효성 검증 성공");

		// 4. 토큰에서 사용자 정보 추출 (subject는 이메일)
		String email = jwtProvider.getUsernameFromToken(cleanRefreshToken); // subject가 이메일임
		String roleStr = jwtProvider.getClaimFromToken(cleanRefreshToken, "auth");
		String tokenId = jwtProvider.getClaimFromToken(cleanRefreshToken, "tokenId");

		if (email == null) {
			log.warn("❌ Refresh Token에서 이메일 정보 추출 실패");
			throw AuthExceptions.invalidRefreshToken();
		}
		log.info("🔍 Refresh Token에서 추출된 이메일: {}", email);

		// 이메일로 사용자 조회 (이메일은 고유하므로 동명이인 문제 없음)
		User user = userService.findByEmail(email);
		String redisKey = "RT:user:" + user.getId();
		log.info("🔍 Redis 키 조회 시작 - Key: {}, Email: {}, UserId: {}", redisKey, email, user.getId());
		
		String storedRefreshToken = redisTemplate.opsForValue().get(redisKey);
		log.info("🔍 Redis에서 조회된 Refresh Token: {}", storedRefreshToken != null ? "존재함" : "null");
		
		if (storedRefreshToken == null) {
			log.warn("❌ 서버에 Refresh Token이 존재하지 않음 (로그아웃된 사용자) - email: {}, redisKey: {}", email, redisKey);
			throw AuthExceptions.invalidRefreshToken();
		}

		// refreshToken이 이미 Bearer 접두사를 포함할 수 있으므로 중복 방지
		String expectedToken = refreshToken.startsWith(JwtProvider.BEARER_PREFIX) 
			? refreshToken 
			: JwtProvider.BEARER_PREFIX + refreshToken;
		log.info("🔍 토큰 비교 - 저장된 토큰 길이: {}, 요청 토큰(Bearer 포함) 길이: {}", 
			storedRefreshToken.length(), expectedToken.length());
		log.info("🔍 저장된 토큰 시작 부분: {}", storedRefreshToken.substring(0, Math.min(20, storedRefreshToken.length())));
		log.info("🔍 요청 토큰 시작 부분: {}", expectedToken.substring(0, Math.min(20, expectedToken.length())));
		
		if (!storedRefreshToken.equals(expectedToken)) {
			log.warn("❌ 쿠키의 Refresh Token이 서버의 토큰과 일치하지 않음 - email: {}", email);
			log.warn("❌ 저장된 토큰: {}", storedRefreshToken);
			log.warn("❌ 요청 토큰: {}", expectedToken);
			// 중요: 불일치 시 보안 위협(탈취 시도)일 수 있으므로 서버의 토큰을 삭제하여 강제 로그아웃 처리
			redisTemplate.delete(redisKey);
			throw AuthExceptions.invalidRefreshToken();
		}
		
		log.info("✅ Refresh Token 검증 성공 - email: {}", email);

		// 4. 새로운 토큰 쌍 생성
		UserRole userRole = UserRole.valueOf(roleStr);
		
		// 이메일을 subject로 사용하여 토큰 생성
		String newAccessToken = jwtProvider.createAccessToken(email, userRole);
		String newRefreshToken = jwtProvider.createRefreshToken(email, userRole);

		// 8. 새로운 Refresh Token을 쿠키에 설정
		jwtProvider.setRefreshTokenCookie(response, newRefreshToken);
		
		// Redis에 저장할 때는 토큰이 이미 Bearer 접두사를 포함하고 있으므로 그대로 저장
		String tokenToStore = newRefreshToken.startsWith(JwtProvider.BEARER_PREFIX) 
			? newRefreshToken 
			: JwtProvider.BEARER_PREFIX + newRefreshToken;
		
		redisTemplate.opsForValue().set(
				redisKey,
				tokenToStore,
				jwtProvider.getRefreshTokenExpiration(),
				TimeUnit.MILLISECONDS
		);
		log.debug("🔄 Redis의 Refresh Token 갱신 완료 - Key: {}", redisKey);

		// 9. 응답 바디로 Access Token 반환 (Bearer 접두사 제거)
		String cleanAccessToken = jwtProvider.prepareAccessTokenForResponse(newAccessToken);

		log.info("토큰 재발급 완료 - email: {}", email);
		return Map.of("accessToken", cleanAccessToken);
	}

	@Override
	@Transactional(readOnly = true)
	public UserInfoResponse getCurrentUser(UserDetails userDetails) {
		if (userDetails == null) {
			log.warn("인증되지 않은 사용자의 /me 요청");
			throw AuthExceptions.authenticationRequired();
		}

		String email = userDetails.getUsername(); // UserDetailsImpl에서 이메일을 반환하도록 수정됨
		log.info("현재 사용자 정보 조회 - email: {}, userDetails.class: {}", email, userDetails.getClass().getSimpleName());

		User user = userService.findByEmail(email);
		return UserInfoResponse.from(user);
	}

	@Override
	public AuthResponse logout(UserDetails userDetails, LogoutRequest request,
			HttpServletRequest httpRequest, HttpServletResponse response) {
		if (userDetails == null) {
			log.warn("인증되지 않은 사용자의 logout 요청");
			throw AuthExceptions.authenticationRequired();
		}

		String email = userDetails.getUsername(); // username이 실제로는 email임
		boolean everywhere = request != null && Boolean.TRUE.equals(request.getEverywhere());

		log.info("통합 로그아웃 요청 - email: {}, everywhere: {}", email, everywhere);

		try {
			// 1. 로그아웃 처리 (OAuth2 Client 방식에서는 소셜 플랫폼 로그아웃이 자동 처리됨)

			// 이메일로 사용자 조회 (이메일은 고유하므로 동명이인 문제 없음)
			User user = userService.findByEmail(email);
			String redisKey = "RT:user:" + user.getId();
			redisTemplate.delete(redisKey);
			log.info("Redis에서 Refresh Token 삭제 완료 - email: {}, userId: {}", email, user.getId());

			if (everywhere) {
				// 모든 기기에서 로그아웃
				tokenManager.invalidateAllUserTokens(email);
				log.info("전체 기기 로그아웃 완료 - email: {}", email);
			} else {
				// 현재 토큰만 로그아웃
				String accessToken = jwtProvider.getAccessTokenFromHeader(httpRequest);
				if (accessToken != null) {
					String tokenId = jwtProvider.getClaimFromToken(accessToken, "tokenId");
					if (tokenId != null) {
						tokenManager.invalidateToken(tokenId);
					}
				}
				log.info("현재 토큰 로그아웃 완료 - email: {}", email);
			}

			// 2. 쿠키에서 토큰 제거
			jwtProvider.clearCookie(response, JwtProvider.ACCESS_TOKEN_COOKIE_NAME);
			jwtProvider.clearCookie(response, JwtProvider.REFRESH_TOKEN_COOKIE_NAME);

			// 3. 결과 메시지 생성
			String message = everywhere ? "모든 기기에서 로그아웃되었습니다." : "로그아웃되었습니다.";
			return AuthResponse.success(message);

		} catch (Exception e) {
			log.error("로그아웃 처리 중 오류 발생 - email: {}", email, e);

			// 오류 발생 시에도 최소한 서비스 로그아웃은 수행
			try {
				if (everywhere) {
					tokenManager.invalidateAllUserTokens(email);
				} else {
					String accessToken = jwtProvider.getAccessTokenFromHeader(httpRequest);
					if (accessToken != null) {
						String tokenId = jwtProvider.getClaimFromToken(accessToken, "tokenId");
						if (tokenId != null) {
							tokenManager.invalidateToken(tokenId);
						}
					}
				}
				jwtProvider.clearCookie(response, JwtProvider.ACCESS_TOKEN_COOKIE_NAME);
				jwtProvider.clearCookie(response, JwtProvider.REFRESH_TOKEN_COOKIE_NAME);
				log.info("대체 로그아웃 처리 완료 - email: {}", email);
			} catch (Exception fallbackError) {
				log.error("대체 로그아웃 처리 실패 - email: {}", email, fallbackError);
			}

			return AuthResponse.failure("로그아웃 처리 중 오류가 발생했습니다.");
		}
	}

	// 회원탈퇴 기능 제거 - 소셜 로그인 전용 서비스로 운영
	// 필요 시 약관 만료 공지를 통한 데이터 정리 예정
	
	/**
	 * 이메일로 사용자 ID 조회 (토큰 재발급용)
	 */
	private Long getUserIdFromEmail(String email) {
		try {
			User user = userService.findByEmail(email);
			return user.getId();
		} catch (Exception e) {
			log.warn("이메일로 사용자 ID 조회 실패: {} - {}", email, e.getMessage());
			return null;
		}
	}
}