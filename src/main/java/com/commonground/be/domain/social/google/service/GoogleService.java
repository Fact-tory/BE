package com.commonground.be.domain.social.google.service;

import static com.commonground.be.global.security.JwtProvider.AUTHORIZATION_HEADER;
import static jakarta.servlet.http.HttpServletResponse.SC_OK;

import com.commonground.be.domain.session.service.SessionService;
import com.commonground.be.domain.session.dto.SessionCreateRequest;
import com.commonground.be.domain.session.dto.SessionResponse;
import com.commonground.be.global.concurrency.RedisLock;
import com.commonground.be.domain.social.SocialAuthService;
import com.commonground.be.domain.social.dto.SocialUserInfo;
import com.commonground.be.domain.social.service.SocialUserService;
import com.commonground.be.domain.user.entity.User;
import com.commonground.be.global.exception.SocialExceptions;
import com.commonground.be.global.security.JwtProvider;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Slf4j(topic = "GOOGLE Login")
@Service
@RequiredArgsConstructor
public class GoogleService implements SocialAuthService {

	private final RestTemplate restTemplate;
	private final JwtProvider jwtProvider;
	private final SessionService sessionService;
	private final ObjectMapper objectMapper;
	private final SocialUserService socialUserService;

	@Value("${google.auth.url}")
	private String googleAuthUrl;
	@Value("${google.auth.client-id}")
	private String googleClientId;
	@Value("${google.auth.client-secret}")
	private String googleClientSecret;
	@Value("${google.api.url}")
	private String googleApiUrl;

	@Override
	@RedisLock(key = "'social_login:' + #code", waitTime = 3, leaseTime = 10)
	public String socialLogin(String code, HttpServletResponse res) throws JsonProcessingException {
		log.info("구글 로그인 시작 - code: {}", code);

		// 1. 인가 코드로 액세스 토큰 요청
		String accessToken = getToken(code);
		log.info("구글 토큰 발급 완료");

		// 2. 토큰으로 구글 사용자 정보 가져오기
		SocialUserInfo googleUserInfo = getGoogleUserInfo(accessToken);
		log.info("구글 사용자 정보 조회 완료 - 사용자 ID: {}", googleUserInfo.getId());

		// 3. 필요 시 회원가입
		User socialUser = socialUserService.registerSocialUserIfNeeded(googleUserInfo);
		log.info("구글 사용자 등록/조회 완료 - username: {}", socialUser.getUsername());

		// 4. 세션 생성 및 관리
		SessionCreateRequest sessionRequest = SessionCreateRequest.builder()
				.userId(socialUser.getUsername())
				.userAgent("Google-Social-Login")
				.build();
		
		SessionResponse session = sessionService.createSession(sessionRequest);
		log.info("세션 생성 완료 - sessionId: {}", session.getSessionId());

		// 5. JWT 토큰 생성 (세션 ID 포함)
		String jwtAccessToken = jwtProvider.createAccessTokenWithSession(socialUser.getUsername(),
				socialUser.getUserRole(), session.getSessionId());
		String refreshToken = jwtProvider.createRefreshTokenWithSession(socialUser.getUsername(),
				socialUser.getUserRole(), session.getSessionId());
		log.info("JWT 토큰 생성 완료 - username: {}", socialUser.getUsername());

		// Access Token: 헤더로 전송
		res.setHeader(AUTHORIZATION_HEADER, jwtAccessToken);
		
		// Refresh Token: HttpOnly 쿠키로 설정
		jwtProvider.setRefreshTokenCookie(res, refreshToken);

		res.setStatus(SC_OK);
		res.setCharacterEncoding("UTF-8");
		res.setContentType("application/json");

		log.info("구글 로그인 완료 - username: {}", socialUser.getUsername());
		return jwtAccessToken;
	}

	@Override
	public String getAuthUrl(String redirectUri) {
		return UriComponentsBuilder
				.fromUriString(googleAuthUrl)
				.path("/o/oauth2/v2/auth")
				.queryParam("client_id", googleClientId)
				.queryParam("redirect_uri", redirectUri)
				.queryParam("response_type", "code")
				.queryParam("scope", "openid email profile")
				.encode()
				.toUriString();
	}

	@Override
	public String getProviderName() {
		return "google";
	}

	private String getToken(String code) throws JsonProcessingException {
		URI uri = UriComponentsBuilder
				.fromUriString(googleAuthUrl)
				.path("/oauth2/v4/token")
				.encode()
				.build()
				.toUri();

		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

		MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
		body.add("grant_type", "authorization_code");
		body.add("client_id", googleClientId);
		body.add("client_secret", googleClientSecret);
		body.add("code", code);

		RequestEntity<MultiValueMap<String, String>> requestEntity = RequestEntity
				.post(uri)
				.headers(headers)
				.body(body);

		ResponseEntity<String> response = restTemplate.exchange(requestEntity, String.class);

		JsonNode jsonNode = objectMapper.readTree(response.getBody());
		JsonNode accessTokenNode = jsonNode.get("access_token");

		if (accessTokenNode == null) {
			throw SocialExceptions.invalidTokenResponse();
		}

		return accessTokenNode.asText();
	}

	private SocialUserInfo getGoogleUserInfo(String accessToken) throws JsonProcessingException {
		URI uri = UriComponentsBuilder
				.fromUriString(googleApiUrl)
				.path("/oauth2/v2/userinfo")
				.encode()
				.build()
				.toUri();

		HttpHeaders headers = new HttpHeaders();
		headers.setBearerAuth(accessToken);

		RequestEntity<Void> requestEntity = RequestEntity
				.get(uri)
				.headers(headers)
				.build();

		ResponseEntity<String> response = restTemplate.exchange(requestEntity, String.class);

		JsonNode jsonNode = objectMapper.readTree(response.getBody());

		JsonNode idNode = jsonNode.get("id");
		JsonNode nameNode = jsonNode.get("name");
		JsonNode emailNode = jsonNode.get("email");

		if (idNode == null || nameNode == null || emailNode == null) {
			throw SocialExceptions.invalidUserInfoResponse();
		}

		String id = idNode.asText();
		String username = nameNode.asText();
		String email = emailNode.asText();

		log.info("구글 사용자 정보: {}, {}, {}", id, username, email);
		return new SocialUserInfo(id, username, email, "google");
	}


}