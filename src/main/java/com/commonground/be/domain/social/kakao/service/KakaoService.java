package com.commonground.be.domain.social.kakao.service;

import com.commonground.be.domain.session.service.SessionService;
import com.commonground.be.domain.social.dto.SocialUserInfo;
import com.commonground.be.domain.social.service.AbstractSocialAuthService;
import com.commonground.be.domain.social.service.SocialUserService;
import com.commonground.be.domain.user.dto.KakaoUserInfoDto;
import com.commonground.be.global.application.exception.SocialExceptions;
import com.commonground.be.global.infrastructure.security.jwt.JwtProvider;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
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

/**
 * Kakao OAuth2 소셜 로그인 서비스 AbstractSocialAuthService를 상속받아 Kakao 특화 로직만 구현
 */
@Slf4j(topic = "KAKAO Login")
@Service
public class KakaoService extends AbstractSocialAuthService {

	private final RestTemplate restTemplate;
	private final ObjectMapper objectMapper;

	@Value("${kakao.auth.url}")
	private String kakaoUrl;
	@Value("${kakao.auth.client-id}")
	private String kakaoClientId;
	@Value("${kakao.auth.redirect-uri}")
	private String kakaoRedirectUri;
	@Value("${kakao.api.url}")
	private String kakaoApiUrl;

	public KakaoService(JwtProvider jwtProvider, SessionService sessionService,
			SocialUserService socialUserService, RestTemplate restTemplate,
			ObjectMapper objectMapper) {
		super(jwtProvider, sessionService, socialUserService);
		this.restTemplate = restTemplate;
		this.objectMapper = objectMapper;
	}

	@Override
	public String getAuthUrl(String redirectUri) {
		return UriComponentsBuilder
				.fromUriString(kakaoUrl)
				.path("/oauth/authorize")
				.queryParam("response_type", "code")
				.queryParam("client_id", kakaoClientId)
				.queryParam("redirect_uri", redirectUri)
				.encode()
				.toUriString();
	}

	@Override
	public String getProviderName() {
		return "카카오";
	}

	@Override
	protected String getAccessToken(String code) throws JsonProcessingException {
		// 요청 URL 만들기
		URI uri = buildKakaoUri(kakaoUrl, "/oauth/token");

		// HTTP Header 생성
		HttpHeaders headers = new HttpHeaders();
		headers.add("Authorization", "Bearer " + kakaoClientId);
		headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

		// HTTP Body 생성
		MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
		body.add("grant_type", "authorization_code");
		body.add("client_id", kakaoClientId);
		body.add("redirect_uri", kakaoRedirectUri);
		body.add("code", code);

		RequestEntity<MultiValueMap<String, String>> requestEntity = RequestEntity
				.post(uri)
				.headers(headers)
				.body(body);

		// HTTP 요청 보내기
		ResponseEntity<String> response = restTemplate.exchange(requestEntity, String.class);

		// HTTP 응답 (JSON) -> 액세스 토큰 파싱
		JsonNode jsonNode = objectMapper.readTree(response.getBody());
		JsonNode accessTokenNode = jsonNode.get("access_token");
		JsonNode refreshTokenNode = jsonNode.get("refresh_token");

		if (accessTokenNode == null || refreshTokenNode == null) {
			throw SocialExceptions.invalidTokenResponse();
		}

		// 카카오는 access_token만 반환 (refresh_token은 사용하지 않음)
		return accessTokenNode.asText();
	}

	@Override
	protected SocialUserInfo getUserInfo(String accessToken) throws JsonProcessingException {
		// 카카오 API로 사용자 정보 조회
		KakaoUserInfoDto kakaoUserInfo = getKakaoUserInfo(accessToken);

		// 표준 SocialUserInfo로 변환
		return new SocialUserInfo(
				kakaoUserInfo.getId().toString(),
				kakaoUserInfo.getUsername(),
				kakaoUserInfo.getEmail(),
				"kakao"
		);
	}

	/**
	 * 카카오 API로 사용자 정보 조회
	 */
	private KakaoUserInfoDto getKakaoUserInfo(String accessToken) throws JsonProcessingException {
		// 요청 URL 만들기
		URI uri = buildKakaoUri(kakaoApiUrl, "/v2/user/me");

		// HTTP Header 생성
		HttpHeaders headers = new HttpHeaders();
		headers.setBearerAuth(accessToken);
		headers.setContentType(MediaType.APPLICATION_JSON);

		RequestEntity<MultiValueMap<String, String>> requestEntity = RequestEntity
				.post(uri)
				.headers(headers)
				.body(new LinkedMultiValueMap<>());

		// HTTP 요청 보내기
		ResponseEntity<String> response = restTemplate.exchange(requestEntity, String.class);

		JsonNode jsonNode = objectMapper.readTree(response.getBody());
		JsonNode idNode = jsonNode.get("id");
		JsonNode propertiesNode = jsonNode.get("properties");
		JsonNode kakaoAccountNode = jsonNode.get("kakao_account");

		if (idNode == null || propertiesNode == null || kakaoAccountNode == null) {
			log.error("카카오 사용자 정보 응답이 올바르지 않습니다: {}", response.getBody());
			throw SocialExceptions.invalidUserInfoResponse();
		}

		Long id = idNode.asLong();
		String username = propertiesNode.get("nickname").asText();
		String email = kakaoAccountNode.get("email").asText();

		return new KakaoUserInfoDto(id, username, email);
	}

	/**
	 * 카카오 API URI 빌더
	 */
	private URI buildKakaoUri(String baseUrl, String path) {
		return UriComponentsBuilder
				.fromUriString(baseUrl)
				.path(path)
				.encode()
				.build()
				.toUri();
	}
}