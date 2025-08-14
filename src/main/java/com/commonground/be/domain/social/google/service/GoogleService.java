package com.commonground.be.domain.social.google.service;

import com.commonground.be.domain.session.service.SessionService;
import com.commonground.be.domain.social.dto.SocialUserInfo;
import com.commonground.be.domain.social.service.AbstractSocialAuthService;
import com.commonground.be.domain.social.service.SocialUserService;
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
 * Google OAuth2 소셜 로그인 서비스 AbstractSocialAuthService를 상속받아 Google 특화 로직만 구현
 */
@Slf4j(topic = "GOOGLE Login")
@Service
public class GoogleService extends AbstractSocialAuthService {

	private final RestTemplate restTemplate;
	private final ObjectMapper objectMapper;

	@Value("${google.auth.url}")
	private String googleAuthUrl;
	@Value("${google.auth.client-id}")
	private String googleClientId;
	@Value("${google.auth.client-secret}")
	private String googleClientSecret;
	@Value("${google.auth.redirect-uri}")
	private String googleRedirectUri;
	@Value("${google.api.url}")
	private String googleApiUrl;

	public GoogleService(JwtProvider jwtProvider, SessionService sessionService,
			SocialUserService socialUserService, RestTemplate restTemplate,
			ObjectMapper objectMapper) {
		super(jwtProvider, sessionService, socialUserService);
		this.restTemplate = restTemplate;
		this.objectMapper = objectMapper;
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
		return "구글";
	}

	@Override
	protected String getAccessToken(String code) throws JsonProcessingException {
		// HTTP Header 생성
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

		// HTTP Body 생성
		MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
		body.add("grant_type", "authorization_code");
		body.add("client_id", googleClientId);
		body.add("client_secret", googleClientSecret);
		body.add("redirect_uri", googleRedirectUri);
		body.add("code", code);

		RequestEntity<MultiValueMap<String, String>> requestEntity = RequestEntity
				.post(URI.create(googleAuthUrl + "/oauth2/v4/token"))
				.headers(headers)
				.body(body);

		// HTTP 요청 보내기
		ResponseEntity<String> response = restTemplate.exchange(requestEntity, String.class);

		// HTTP 응답 (JSON) -> 액세스 토큰 파싱
		JsonNode jsonNode = objectMapper.readTree(response.getBody());
		return jsonNode.get("access_token").asText();
	}

	@Override
	protected SocialUserInfo getUserInfo(String accessToken) throws JsonProcessingException {
		// HTTP Header 생성
		HttpHeaders headers = new HttpHeaders();
		headers.add("Authorization", "Bearer " + accessToken);
		headers.setContentType(MediaType.APPLICATION_JSON);

		RequestEntity<Void> requestEntity = RequestEntity
				.get(URI.create(googleApiUrl + "/oauth2/v2/userinfo"))
				.headers(headers)
				.build();

		// HTTP 요청 보내기
		ResponseEntity<String> response = restTemplate.exchange(requestEntity, String.class);

		JsonNode jsonNode = objectMapper.readTree(response.getBody());
		JsonNode idNode = jsonNode.get("id");
		JsonNode nameNode = jsonNode.get("name");
		JsonNode emailNode = jsonNode.get("email");

		if (idNode == null || nameNode == null || emailNode == null) {
			log.error("구글 사용자 정보 응답이 올바르지 않습니다: {}", response.getBody());
			throw SocialExceptions.invalidUserInfoResponse();
		}

		String id = idNode.asText();
		String username = nameNode.asText();
		String email = emailNode.asText();

		return new SocialUserInfo(id, username, email, "google");
	}
}