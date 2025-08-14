package com.commonground.be.domain.social.service;

import static com.commonground.be.global.infrastructure.security.jwt.JwtProvider.AUTHORIZATION_HEADER;
import static jakarta.servlet.http.HttpServletResponse.SC_OK;

import com.commonground.be.domain.session.dto.SessionCreateRequest;
import com.commonground.be.domain.session.dto.SessionResponse;
import com.commonground.be.domain.session.service.SessionService;
import com.commonground.be.domain.social.SocialAuthService;
import com.commonground.be.domain.social.dto.SocialUserInfo;
import com.commonground.be.domain.user.entity.User;
import com.commonground.be.global.infrastructure.concurrency.RedisLock;
import com.commonground.be.global.infrastructure.security.jwt.JwtProvider;
import com.fasterxml.jackson.core.JsonProcessingException;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 소셜 로그인 공통 로직을 담당하는 추상 클래스 Template Method 패턴을 사용하여 공통 흐름을 정의하고 각 소셜 플랫폼별 세부 구현은 하위 클래스에서 담당
 */
@Slf4j
@RequiredArgsConstructor
public abstract class AbstractSocialAuthService implements SocialAuthService {

	protected final JwtProvider jwtProvider;
	protected final SessionService sessionService;
	protected final SocialUserService socialUserService;

	/**
	 * Template Method: 소셜 로그인 공통 흐름 정의 1. 액세스 토큰 발급 2. 사용자 정보 조회 3. 사용자 등록/조회 4. 세션 생성 5. JWT 토큰 생성
	 * 및 설정
	 */
	@Override
	@RedisLock(key = "'social_login:' + #code", waitTime = 3, leaseTime = 10)
	public String socialLogin(String code, HttpServletResponse res) throws JsonProcessingException {
		String providerName = getProviderName();
		log.info("{} 로그인 시작 - code: {}", providerName, code);

		// 1. 인가 코드로 액세스 토큰 발급 (각 플랫폼별 구현)
		String accessToken = getAccessToken(code);
		log.info("{} 토큰 발급 완료", providerName);

		// 2. 토큰으로 사용자 정보 조회 (각 플랫폼별 구현)
		SocialUserInfo userInfo = getUserInfo(accessToken);
		log.info("{} 사용자 정보 조회 완료 - 사용자 ID: {}", providerName, userInfo.getId());

		// 3. 필요 시 회원가입 (공통 로직)
		User socialUser = socialUserService.registerSocialUserIfNeeded(userInfo);
		log.info("{} 사용자 등록/조회 완료 - username: {}", providerName, socialUser.getUsername());

		// 4. 세션 생성 및 관리 (공통 로직)
		SessionResponse session = createSession(socialUser);
		log.info("세션 생성 완료 - sessionId: {}", session.getSessionId());

		// 5. JWT 토큰 생성 및 응답 설정 (공통 로직)
		String jwtAccessToken = generateAndSetTokens(socialUser, session, res);

		log.info("{} 로그인 완료 - username: {}", providerName, socialUser.getUsername());
		return jwtAccessToken;
	}

	/**
	 * 세션 생성 공통 로직
	 */
	protected SessionResponse createSession(User user) {
		SessionCreateRequest sessionRequest = SessionCreateRequest.builder()
				.userId(user.getUsername())
				.userAgent(getProviderName() + "-Social-Login")
				.build();

		return sessionService.createSession(sessionRequest);
	}

	/**
	 * JWT 토큰 생성 및 응답 설정 공통 로직
	 */
	protected String generateAndSetTokens(User user, SessionResponse session,
			HttpServletResponse res) {
		// JWT 토큰 생성 (세션 ID 포함)
		String jwtAccessToken = jwtProvider.createAccessTokenWithSession(
				user.getUsername(), user.getUserRole(), session.getSessionId());
		String refreshToken = jwtProvider.createRefreshTokenWithSession(
				user.getUsername(), user.getUserRole(), session.getSessionId());

		log.info("JWT 토큰 생성 완료 - username: {}", user.getUsername());

		// Access Token: 헤더로 전송
		res.setHeader(AUTHORIZATION_HEADER, jwtAccessToken);

		// Refresh Token: HttpOnly 쿠키로 설정
		jwtProvider.setRefreshTokenCookie(res, refreshToken);

		// 응답 설정
		res.setStatus(SC_OK);
		res.setCharacterEncoding("UTF-8");
		res.setContentType("application/json");

		return jwtAccessToken;
	}

	// ========== Abstract Methods (각 플랫폼별 구현 필요) ==========

	/**
	 * 인가 코드로 액세스 토큰 발급
	 *
	 * @param code OAuth 인가 코드
	 * @return 액세스 토큰
	 */
	protected abstract String getAccessToken(String code) throws JsonProcessingException;

	/**
	 * 액세스 토큰으로 사용자 정보 조회
	 *
	 * @param accessToken 소셜 플랫폼 액세스 토큰
	 * @return 표준화된 사용자 정보
	 */
	protected abstract SocialUserInfo getUserInfo(String accessToken)
			throws JsonProcessingException;

	/**
	 * 소셜 플랫폼 이름 반환
	 *
	 * @return 플랫폼 이름 (예: "구글", "카카오")
	 */
	public abstract String getProviderName();
}