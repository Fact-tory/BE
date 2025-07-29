package com.commonground.be.domain.social.kakao.service;

import static com.commonground.be.global.security.JwtProvider.AUTHORIZATION_HEADER;
import static jakarta.servlet.http.HttpServletResponse.SC_OK;

import com.commonground.be.domain.session.dto.SessionCreateRequest;
import com.commonground.be.domain.session.dto.SessionResponse;
import com.commonground.be.domain.session.service.SessionService;
import com.commonground.be.domain.social.SocialAuthService;
import com.commonground.be.domain.social.dto.SocialUserInfo;
import com.commonground.be.domain.social.service.SocialUserService;
import com.commonground.be.domain.user.dto.KakaoUserInfoDto;
import com.commonground.be.domain.user.entity.User;
import com.commonground.be.global.concurrency.RedisLock;
import com.commonground.be.global.exception.SocialExceptions;
import com.commonground.be.global.security.JwtProvider;
import com.commonground.be.global.security.TokenManager;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
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

@Slf4j(topic = "KAKAO Login")
@Service
@RequiredArgsConstructor
public class KakaoService implements SocialAuthService {

	private final RestTemplate restTemplate;
	private final JwtProvider jwtProvider;
	private final SessionService sessionService;
	private final TokenManager tokenManager;
	private final ObjectMapper objectMapper;
	private final SocialUserService socialUserService;


	@Value("${kakao.auth.url}")
	private String kakaoUrl;
	@Value("${kakao.auth.client-id}")
	private String kakaoClientId;
	@Value("${kakao.auth.redirect-uri}")
	private String kakaoRedirectUri;
	@Value("${kakao.api.url}")
	private String kakaoApiUrl;


	@Override
	@RedisLock(key = "'social_login:' + #code", waitTime = 3, leaseTime = 10)
	public String socialLogin(String code, HttpServletResponse res) throws JsonProcessingException {
		log.info("카카오 로그인 시작 - code: {}", code);

		// 1. "인가 코드"로 "액세스 토큰" 요청
		String tokens = getToken(code);
		log.info("카카오 토큰 발급 완료");

		String KakaoAccessToken = tokens.split(" ")[0];

		// 2. 토큰으로 카카오 API 호출 : "액세스 토큰"으로 "카카오 사용자 정보" 가져오기
		KakaoUserInfoDto kakaoUserInfo = getKakaoUserInfo(KakaoAccessToken);
		log.info("카카오 사용자 정보 조회 완료 - 사용자 ID: {}", kakaoUserInfo.getId());

		// 3. 필요 시 회원가입
		SocialUserInfo socialUserInfo = new SocialUserInfo(
				kakaoUserInfo.getId().toString(),
				kakaoUserInfo.getUsername(),
				kakaoUserInfo.getEmail(),
				"kakao"
		);
		User socialUser = socialUserService.registerSocialUserIfNeeded(socialUserInfo);
		log.info("카카오 사용자 등록/조회 완료 - username: {}", socialUser.getUsername());

		// 4. 세션 생성 및 관리
		SessionCreateRequest sessionRequest = SessionCreateRequest.builder()
				.userId(socialUser.getUsername())
				.userAgent("Kakao-Social-Login")
				.build();

		SessionResponse session = sessionService.createSession(sessionRequest);
		log.info("세션 생성 완료 - sessionId: {}", session.getSessionId());

		// 5. JWT 토큰 생성 (세션 ID 포함)
		String accessToken = jwtProvider.createAccessTokenWithSession(socialUser.getUsername(),
				socialUser.getUserRole(), session.getSessionId());
		String refreshToken = jwtProvider.createRefreshTokenWithSession(socialUser.getUsername(),
				socialUser.getUserRole(), session.getSessionId());
		log.info("JWT 토큰 생성 완료 - username: {}", socialUser.getUsername());

		// Access Token: 헤더로 전송
		res.setHeader(AUTHORIZATION_HEADER, accessToken);

		// Refresh Token: HttpOnly 쿠키로 설정
		jwtProvider.setRefreshTokenCookie(res, refreshToken);

		res.setStatus(SC_OK);
		res.setCharacterEncoding("UTF-8");
		res.setContentType("application/json");

		log.info("카카오 로그인 완료 - username: {}", socialUser.getUsername());
		return accessToken;
	}

	@Override
	public String getAuthUrl(String redirectUri) {
		return UriComponentsBuilder
				.fromUriString(kakaoUrl)
				.path("/oauth/authorize")
				.queryParam("client_id", kakaoClientId)
				.queryParam("redirect_uri", redirectUri)
				.queryParam("response_type", "code")
				.encode()
				.toUriString();
	}

	@Override
	public String getProviderName() {
		return "kakao";
	}

	// 인가 코드 받는 메서드
	public String getToken(String code) throws JsonProcessingException {
		// 요청 URL 만들기
		URI uri = buildKakaoUri(kakaoUrl, "/oauth/token");

		// HTTP Header 생성
		HttpHeaders headers = new HttpHeaders();
		headers.add("Authorization", "Bearer " + code); // 토큰의 식별자
		headers.add("Content-type", "application/x-www-form-urlencoded;charset=utf-8");

		// HTTP Body 생성
		MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
		body.add("grant_type", "authorization_code");
		body.add("client_id", kakaoClientId);
		body.add("redirect_uri", kakaoRedirectUri);
		body.add("code", code); // 우리가 이전에 받아온 인가코드

		RequestEntity<MultiValueMap<String, String>> requestEntity = RequestEntity
				.post(uri)
				.headers(headers)
				.body(body);

		// HTTP 요청 보내기
		ResponseEntity<String> response = restTemplate.exchange(
				requestEntity,
				String.class
		);

		// HTTP 응답 (JSON) -> 액세스 토큰 파싱
		JsonNode jsonNode = objectMapper.readTree(response.getBody());
		// access_token 이라는 이름으로 되어 있는 토큰의 텍스트를 가져올 수 있습니다.
		JsonNode accessTokenNode = jsonNode.get("access_token");
		JsonNode refreshTokenNode = jsonNode.get("refresh_token");

		if (accessTokenNode == null || refreshTokenNode == null) {
			throw SocialExceptions.invalidTokenResponse();
		}

		String accessToken = accessTokenNode.asText();
		String refreshToken = refreshTokenNode.asText();
		return accessToken + " " + refreshToken;
	}


	private KakaoUserInfoDto getKakaoUserInfo(String accessToken) throws JsonProcessingException {
		// 요청 URL 만들기
		URI uri = buildKakaoUri(kakaoApiUrl, "/v2/user/me");

		// HTTP Header 생성
		HttpHeaders headers = new HttpHeaders();
		headers.setBearerAuth(accessToken); // 토큰의 식별자
		headers.setContentType(MediaType.APPLICATION_JSON);

		RequestEntity<MultiValueMap<String, String>> requestEntity = RequestEntity
				.post(uri)
				.headers(headers)
				.body(new LinkedMultiValueMap<>());

		// HTTP 요청 보내기
		ResponseEntity<String> response = restTemplate.exchange(
				requestEntity,
				String.class
		);

		// 추후에는 토큰 내역을 보고 가져 올 수 있다.
		JsonNode jsonNode = objectMapper.readTree(response.getBody());

		JsonNode idNode = jsonNode.get("id");
		JsonNode propertiesNode = jsonNode.get("properties");
		JsonNode kakaoAccountNode = jsonNode.get("kakao_account");

		if (idNode == null || propertiesNode == null || kakaoAccountNode == null) {
			throw SocialExceptions.invalidUserInfoResponse();
		}

		Long id = idNode.asLong();
		JsonNode nicknameNode = propertiesNode.get("nickname");
		JsonNode emailNode = kakaoAccountNode.get("email");

		if (nicknameNode == null || emailNode == null) {
			throw SocialExceptions.invalidUserInfoResponse();
		}

		String username = nicknameNode.asText();
		String email = emailNode.asText();

		log.info("카카오 사용자 정보: {}, {}, {}", id, username, email);
		return new KakaoUserInfoDto(id, username, email);
	}


	// kakao server에 요청을 하기 위한 uri 생성 메서드
	private URI buildKakaoUri(String baseUrl, String path) {
		return UriComponentsBuilder
				.fromUriString(baseUrl)
				.path(path)
				.encode()
				.build()
				.toUri();
	}
}
