package com.commonground.be.domain.auth.facade;

import com.commonground.be.domain.auth.dto.request.LogoutRequest;
import com.commonground.be.domain.auth.dto.response.AuthResponse;
import com.commonground.be.domain.auth.dto.response.UserInfoResponse;
import com.commonground.be.domain.auth.service.AuthService;
import com.commonground.be.domain.user.entity.User;
import com.commonground.be.domain.user.service.SocialUserServiceInterface;
import com.commonground.be.domain.user.utils.UserRole;
import com.commonground.be.global.infrastructure.security.jwt.JwtProvider;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Component;

/**
 * Auth Facade - 인증 관련 데이터 흐름을 명시적으로 관리 디버깅과 모니터링이 용이한 중간 계층
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuthFacade {

	private final AuthService authService;
	private final SocialUserServiceInterface userService;
	private final JwtProvider jwtProvider;
	private final RedisTemplate<String, String> redisTemplate;

	/**
	 * 토큰 재발급 파이프라인 Request → Cookie 검증 → Service → Response
	 */
	public AuthFlowResult reissueTokenFlow(HttpServletRequest request,
			HttpServletResponse response) {
		long startTime = System.currentTimeMillis();
		String clientIp = getClientIp(request);
		log.info("🔄 [FACADE] 토큰 재발급 파이프라인 시작 - clientIp: {}", clientIp);

		try {
			// 1. 보안 검증 (Rate Limiting + IP 검증)
			log.debug("🔍 [STEP 1/4] 보안 검증 중...");
			validateTokenReissueRequest(request);
			checkRateLimit(clientIp, "token_reissue");
			log.debug("✅ [STEP 1/4] 보안 검증 완료 - clientIp: {}", clientIp);

			// 2. Refresh Token 추출 및 사전 검증
			log.debug("🔍 [STEP 2/4] Refresh Token 추출 및 사전 검증 중...");
			String refreshToken = extractRefreshTokenFromRequest(request);
			if (refreshToken == null || refreshToken.trim().isEmpty()) {
				log.warn("❌ [STEP 2/4] Refresh Token이 요청에 포함되지 않음 - clientIp: {}", clientIp);
				throw new IllegalArgumentException("Refresh Token이 필요합니다. 쿠키 또는 Authorization 헤더에 토큰을 포함해주세요.");
			}
			log.debug("✅ [STEP 2/4] Refresh Token 추출 성공 - 길이: {}자", refreshToken.length());

			// 3. 토큰 재발급 서비스 실행
			log.debug("🔍 [STEP 3/4] 토큰 재발급 서비스 실행 중...");
			Map<String, Object> result = authService.reissueAccessToken(request, response);
			log.debug("✅ [STEP 3/4] 토큰 재발급 서비스 완료 - 새 accessToken 생성됨");

			// 4. 성공 결과 구성 및 보안 로그
			log.debug("🔍 [STEP 4/4] 성공 결과 구성 중...");
			AuthFlowResult flowResult = AuthFlowResult.success("토큰 재발급 성공", result);
			
			long executionTime = System.currentTimeMillis() - startTime;
			log.info("✅ [FACADE] 토큰 재발급 파이프라인 완료 - clientIp: {}, 실행시간: {}ms", clientIp, executionTime);
			
			// 성공 시 Redis에 재발급 기록 (보안 감사)
			recordTokenReissueSuccess(clientIp);

			return flowResult;

		} catch (Exception e) {
			long executionTime = System.currentTimeMillis() - startTime;
			log.error("❌ [FACADE] 토큰 재발급 파이프라인 실패 - clientIp: {}, 실행시간: {}ms, 오류: {}", 
					clientIp, executionTime, e.getMessage(), e);
			
			// 실패 시 Redis에 실패 기록 (보안 감사)
			recordTokenReissueFailure(clientIp);
			
			return AuthFlowResult.failure("토큰 재발급 실패: " + e.getMessage(), e);
		}
	}

	/**
	 * 사용자 정보 조회 파이프라인 UserDetails → Validation → Service → Response
	 */
	public AuthFlowResult getCurrentUserFlow(UserDetails userDetails) {
		log.debug("👤 현재 사용자 정보 조회 파이프라인 시작");

		try {
			// 1. 인증 상태 검증
			validateAuthentication(userDetails);
			log.debug("✅ 사용자 인증 상태 검증 완료: {}", userDetails.getUsername());

			// 2. 사용자 정보 조회
			UserInfoResponse userInfo = authService.getCurrentUser(userDetails);
			log.debug("🔄 사용자 정보 조회 완료: {}", userInfo.getUsername());

			// 3. 성공 결과 반환
			AuthFlowResult result = AuthFlowResult.success("사용자 정보 조회 성공", userInfo);
			log.debug("📤 사용자 정보 조회 파이프라인 완료");

			return result;

		} catch (Exception e) {
			log.error("❌ 사용자 정보 조회 파이프라인 실패: {}", e.getMessage());
			return AuthFlowResult.failure("사용자 정보 조회 실패: " + e.getMessage(), e);
		}
	}

	/**
	 * 통합 로그아웃 파이프라인 Request → Validation → Service (일반 + 카카오) → Response
	 */
	public AuthFlowResult logoutFlow(UserDetails userDetails, LogoutRequest logoutRequest,
			HttpServletRequest httpRequest, HttpServletResponse response) {
		log.debug("🚪 통합 로그아웃 파이프라인 시작 - everywhere: {}, 카카오토큰: {}",
				logoutRequest != null ? logoutRequest.getEverywhere() : false,
				logoutRequest != null && logoutRequest.getKakaoAccessToken() != null ? "있음" : "없음");

		try {
			// 1. 인증 상태 검증
			validateAuthentication(userDetails);
			log.debug("✅ 로그아웃 요청 사용자 검증 완료: {}", userDetails.getUsername());

			// 2. 로그아웃 요청 검증
			validateLogoutRequest(logoutRequest);
			log.debug("✅ 로그아웃 요청 파라미터 검증 완료");

			// 3. 통합 로그아웃 서비스 실행
			AuthResponse authResponse = authService.logout(userDetails, logoutRequest, httpRequest,
					response);
			log.debug("🔄 통합 로그아웃 서비스 완료 - 성공: {}", authResponse.isSuccess());

			// 4. 결과 반환
			AuthFlowResult result = AuthFlowResult.fromAuthResponse(authResponse);
			log.debug("📤 통합 로그아웃 파이프라인 완료 - 최종 결과: {}", result.isSuccess());

			return result;

		} catch (Exception e) {
			log.error("❌ 통합 로그아웃 파이프라인 실패: {}", e.getMessage());
			return AuthFlowResult.failure("로그아웃 실패: " + e.getMessage(), e);
		}
	}

	// 회원탈퇴 기능 제거 - 소셜 로그인 전용 서비스

	/**
	 * OAuth2 로그인 성공 처리 파이프라인 OAuth2User → 사용자 정보 추출 → 사용자 생성/조회 → JWT 발급 → 쿠키 설정
	 */
	public AuthFlowResult oauth2LoginSuccessFlow(OAuth2User oauth2User, String registrationId,
			HttpServletRequest request, HttpServletResponse response) {
		log.debug("🔐 OAuth2 로그인 성공 파이프라인 시작 - provider: {}", registrationId);

		try {
			// 1. OAuth2 사용자 정보 추출 및 검증
			OAuth2UserInfo userInfo = extractOAuth2UserInfo(oauth2User, registrationId);
			log.debug("📝 OAuth2 사용자 정보 추출 완료 - email: {}", userInfo.email());

			// 2. 사용자 생성 또는 업데이트
			User user = createOrUpdateUserFlow(userInfo, registrationId);
			log.debug("👤 사용자 처리 완료 - userId: {}, email: {}", user.getId(), user.getEmail());

			// 3. JWT 토큰 발급 (이메일을 subject로 사용)
			String accessToken = jwtProvider.createAccessToken(
				user.getEmail(), user.getUserRole());
			String refreshToken = jwtProvider.createRefreshToken(
				user.getEmail(), user.getUserRole());
			log.debug("🎫 JWT 토큰 발급 완료");

			String redisKey = "RT:user:" + user.getId();
			
			// Redis에 저장할 때는 토큰이 이미 Bearer 접두사를 포함하고 있으므로 그대로 저장
			String tokenToStore = refreshToken.startsWith(JwtProvider.BEARER_PREFIX) 
				? refreshToken 
				: JwtProvider.BEARER_PREFIX + refreshToken;
			
			redisTemplate.opsForValue().set(
					redisKey,
					tokenToStore,
					jwtProvider.getRefreshTokenExpiration(), // JwtProvider에서 만료 시간 가져오기
					TimeUnit.MILLISECONDS
			);
			log.debug("💾 Redis에 Refresh Token 저장 완료 - Key: {} (userId 기반)", redisKey);

			// 4. 쿠키 설정 (RefreshToken만 HttpOnly 쿠키로 설정)
			jwtProvider.setRefreshTokenCookie(response, refreshToken);
			log.debug("🍪 Refresh Token 쿠키 설정 완료");

			// 5. 성공 결과 반환 (AccessToken 포함)
			String cleanAccessToken = jwtProvider.prepareAccessTokenForResponse(accessToken);
			Map<String, Object> resultData = Map.of(
					"userId", user.getId(),
					"email", user.getEmail(),
					"name", user.getName(),
					"provider", registrationId,
					"accessToken", cleanAccessToken, // AccessToken 응답 데이터에 포함
					"loginTime", java.time.LocalDateTime.now()
			);

			AuthFlowResult result = AuthFlowResult.success("OAuth2 로그인 성공", resultData);
			log.debug("📤 OAuth2 로그인 성공 파이프라인 완료");

			return result;

		} catch (Exception e) {
			log.error("❌ OAuth2 로그인 성공 파이프라인 실패: {}", e.getMessage());
			return AuthFlowResult.failure("OAuth2 로그인 처리 실패: " + e.getMessage(), e);
		}
	}

	// === Private OAuth2 Methods ===

	/**
	 * OAuth2 사용자 정보 추출 (플랫폼별 처리)
	 */
	private OAuth2UserInfo extractOAuth2UserInfo(OAuth2User oauth2User, String registrationId) {
		return switch (registrationId.toLowerCase()) {
			case "google" -> extractGoogleUserInfo(oauth2User);
			case "kakao" -> extractKakaoUserInfo(oauth2User);
			default -> throw new IllegalArgumentException(
					"지원하지 않는 OAuth2 Provider: " + registrationId);
		};
	}

	/**
	 * Google 사용자 정보 추출
	 */
	private OAuth2UserInfo extractGoogleUserInfo(OAuth2User oauth2User) {
		String email = oauth2User.getAttribute("email");
		String name = oauth2User.getAttribute("name");
		String picture = oauth2User.getAttribute("picture");

		if (email == null || name == null) {
			throw new IllegalStateException("Google에서 필수 사용자 정보를 가져올 수 없습니다");
		}

		return new OAuth2UserInfo(email, name, email.split("@")[0], picture);
	}

	/**
	 * Kakao 사용자 정보 추출
	 */
	private OAuth2UserInfo extractKakaoUserInfo(OAuth2User oauth2User) {
		Map<String, Object> kakaoAccount = oauth2User.getAttribute("kakao_account");
		Object profileObj = kakaoAccount.get("profile");
		if (!(profileObj instanceof Map)) {
			throw new IllegalStateException("Invalid profile data from Kakao");
		}
		@SuppressWarnings("unchecked")
		Map<String, Object> profile = (Map<String, Object>) profileObj;

		String email = (String) kakaoAccount.get("email");
		String nickname = (String) profile.get("nickname");
		String profileImage = (String) profile.get("profile_image_url");

		if (email == null || nickname == null) {
			throw new IllegalStateException("Kakao에서 필수 사용자 정보를 가져올 수 없습니다");
		}

		return new OAuth2UserInfo(email, nickname, nickname, profileImage);
	}

	/**
	 * 사용자 생성 또는 업데이트 (Repository + Adapter 패턴 준수)
	 */
	private User createOrUpdateUserFlow(OAuth2UserInfo userInfo, String provider) {
		try {
			User existingUser = userService.findByEmail(userInfo.email());
			log.debug("📝 기존 사용자 정보 확인 - email: {}", userInfo.email());
			// 필요시 사용자 정보 업데이트 로직 추가
			return existingUser;
		} catch (Exception e) {
			log.debug("🆕 새 사용자 생성 - email: {}, provider: {}", userInfo.email(), provider);
			User newUser = User.builder()
					.username(userInfo.email()) // 이메일을 username으로 설정
					.name(userInfo.name())
					.email(userInfo.email())
					.role(UserRole.USER)
					.build();

			return userService.save(newUser);
		}
	}


	/**
	 * OAuth2 사용자 정보 레코드
	 */
	private record OAuth2UserInfo(String email, String name, String username, String profileImage) {

	}

	// === Private Validation Methods ===

	private void validateTokenReissueRequest(HttpServletRequest request) {
		if (request == null) {
			throw new IllegalArgumentException("토큰 재발급 요청이 필요합니다.");
		}
	}

	private void validateAuthentication(UserDetails userDetails) {
		if (userDetails == null) {
			throw new IllegalArgumentException("인증이 필요합니다.");
		}
	}

	private void validateLogoutRequest(LogoutRequest request) {
		// 로그아웃 요청은 null이어도 허용 (기본값 사용)
		if (request != null && request.getEverywhere() == null) {
			request.setEverywhere(false); // 기본값 설정
		}
	}

	// validateWithdrawRequest 제거 - 회원탈퇴 기능 제거

	/**
	 * Auth 도메인 데이터 흐름 결과 컨테이너
	 */
	public static class AuthFlowResult {

		private final boolean success;
		private final String message;
		private final Object data;
		private final Exception error;

		private AuthFlowResult(boolean success, String message, Object data, Exception error) {
			this.success = success;
			this.message = message;
			this.data = data;
			this.error = error;
		}

		public static AuthFlowResult success(String message, Object data) {
			return new AuthFlowResult(true, message, data, null);
		}

		public static AuthFlowResult failure(String message, Exception error) {
			return new AuthFlowResult(false, message, null, error);
		}

		public static AuthFlowResult fromAuthResponse(AuthResponse authResponse) {
			return new AuthFlowResult(
					authResponse.isSuccess(),
					authResponse.getMessage(),
					authResponse.getData(),
					null
			);
		}

		// Getters
		public boolean isSuccess() {
			return success;
		}

		public String getMessage() {
			return message;
		}

		public Object getData() {
			return data;
		}

		public Exception getError() {
			return error;
		}

		/**
		 * AccessToken 추출 (OAuth2 로그인 성공 시 사용)
		 */
		public String getAccessToken() {
			if (data instanceof Map<?, ?> dataMap) {
				return (String) dataMap.get("accessToken");
			}
			return null;
		}
	}
	
	// === 보안 알고리즘 유틸리티 메서드들 ===
	
	/**
	 * Rate Limiting 알고리즘 (Redis 기반 Sliding Window)
	 * 시간당 요청 제한으로 DDoS 및 무차별 대입 공격 방지
	 */
	private void checkRateLimit(String clientIp, String operation) {
		String key = "rate_limit:" + operation + ":" + clientIp;
		String currentCount = redisTemplate.opsForValue().get(key);
		
		int maxAttempts = getMaxAttemptsForOperation(operation);
		int currentAttempts = currentCount != null ? Integer.parseInt(currentCount) : 0;
		
		if (currentAttempts >= maxAttempts) {
			log.warn("🚫 Rate limit exceeded - clientIp: {}, operation: {}, attempts: {}", 
					clientIp, operation, currentAttempts);
			throw new SecurityException("요청 횟수 제한을 초과했습니다. 잠시 후 다시 시도해주세요.");
		}
		
		// Sliding window 방식으로 카운터 증가
		redisTemplate.opsForValue().increment(key);
		redisTemplate.expire(key, 1, TimeUnit.HOURS);
	}
	
	/**
	 * 작업별 최대 시도 횟수 정의
	 */
	private int getMaxAttemptsForOperation(String operation) {
		switch (operation) {
			case "token_reissue": return 10; // 시간당 토큰 재발급 10회
			case "login": return 5; // 시간당 로그인 시도 5회
			case "logout": return 20; // 시간당 로그아웃 20회
			default: return 3;
		}
	}
	
	/**
	 * 클라이언트 IP 추출 (프록시 헤더 고려)
	 */
	private String getClientIp(HttpServletRequest request) {
		String xForwardedFor = request.getHeader("X-Forwarded-For");
		if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
			return xForwardedFor.split(",")[0].trim();
		}
		
		String xRealIp = request.getHeader("X-Real-IP");
		if (xRealIp != null && !xRealIp.isEmpty()) {
			return xRealIp;
		}
		
		return request.getRemoteAddr();
	}
	
	/**
	 * Refresh Token 추출 (Cookie 또는 Header에서)
	 * 쿠키를 우선적으로 확인하고, 없으면 Authorization 헤더에서 추출
	 */
	private String extractRefreshTokenFromRequest(HttpServletRequest request) {
		// 1. 쿠키에서 Refresh Token 확인
		String refreshTokenFromCookie = jwtProvider.getRefreshTokenFromCookie(request);
		if (refreshTokenFromCookie != null && !refreshTokenFromCookie.trim().isEmpty()) {
			log.debug("🔍 쿠키에서 Refresh Token 발견");
			return refreshTokenFromCookie;
		}
		
		// 2. Authorization 헤더에서 확인
		String authHeader = request.getHeader("Authorization");
		if (authHeader != null && authHeader.startsWith("Bearer ")) {
			String token = authHeader.substring(7);
			log.debug("🔍 Authorization 헤더에서 Refresh Token 발견");
			return token;
		}
		
		log.debug("🔍 Refresh Token을 찾을 수 없음 (쿠키와 헤더 모두 확인함)");
		return null;
	}
	
	/**
	 * 보안 감사 로그: 토큰 재발급 성공
	 */
	private void recordTokenReissueSuccess(String clientIp) {
		String key = "security_audit:token_reissue_success:" + clientIp;
		redisTemplate.opsForValue().set(key, String.valueOf(System.currentTimeMillis()), 24, TimeUnit.HOURS);
	}
	
	/**
	 * 보안 감사 로그: 토큰 재발급 실패
	 */
	private void recordTokenReissueFailure(String clientIp) {
		String key = "security_audit:token_reissue_failure:" + clientIp;
		String currentCount = redisTemplate.opsForValue().get(key);
		int failures = currentCount != null ? Integer.parseInt(currentCount) : 0;
		
		redisTemplate.opsForValue().set(key, String.valueOf(failures + 1), 24, TimeUnit.HOURS);
		
		// 실패 횟수가 많으면 추가 보안 조치
		if (failures >= 3) {
			log.warn("🚨 Multiple token reissue failures detected - clientIp: {}, failures: {}", clientIp, failures + 1);
		}
	}
}