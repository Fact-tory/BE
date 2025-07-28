package com.commonground.be.domain.social.kakao.service;

import static com.commonground.be.global.security.JwtProvider.AUTHORIZATION_HEADER;
import static jakarta.servlet.http.HttpServletResponse.SC_OK;

import com.commonground.be.domain.redis.RedisService;
import com.commonground.be.domain.user.dto.KakaoUserInfoDto;
import com.commonground.be.domain.user.entity.User;
import com.commonground.be.domain.user.repository.UserRepository;
import com.commonground.be.domain.user.utils.UserRole;
import com.commonground.be.global.security.JwtProvider;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import java.net.URI;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Slf4j(topic = "KAKAO Login")
@Service
@RequiredArgsConstructor
public class KakaoService {

	private final PasswordEncoder passwordEncoder;
	private final UserRepository userRepository;
	// restTemplate를 수동으로 등록해야합니다.
	private final RestTemplate restTemplate;
	private final JwtProvider jwtProvider;
	private final RedisService redisService;
	// requestBody 생성을 위해 중복 인스턴스 생성을 방지하기 위한 필드화
	private final ObjectMapper objectMapper;


	@Value("${kakao.auth.url}")
	private String kakaoUrl;
	@Value("${kakao.auth.client-id}")
	private String kakaoClientId;
	@Value("${kakao.auth.redirect-uri}")
	private String kakaoRedirectUri;
	@Value("${kakao.api.url}")
	private String kakaoApiUrl;


	public String kakaoLogin(String code, HttpServletResponse res) throws JsonProcessingException {
		log.info("카카오 로그인 시작 - code: {}", code);

		// 1. "인가 코드"로 "액세스 토큰" 요청
		String tokens = getToken(code);
		log.info("카카오 토큰 발급 완료");

		String KakaoAccessToken = tokens.split(" ")[0];

		// 2. 토큰으로 카카오 API 호출 : "액세스 토큰"으로 "카카오 사용자 정보" 가져오기
		KakaoUserInfoDto kakaoUserInfo = getKakaoUserInfo(KakaoAccessToken);
		log.info("카카오 사용자 정보 조회 완료 - 사용자 ID: {}", kakaoUserInfo.getId());

		// 3. 필요 시 회원가입
		User kakaoUser = registerKakaoUserIfNeeded(kakaoUserInfo);
		log.info("카카오 사용자 등록/조회 완료 - username: {}", kakaoUser.getUsername());

		// 4. return jwt
		String accessToken = jwtProvider.createAccessToken(kakaoUser.getUsername(),
				kakaoUser.getUserRole());
		String refreshToken = jwtProvider.createRefreshToken(kakaoUser.getUsername(),
				kakaoUser.getUserRole());
		log.info("JWT 토큰 생성 완료 - username: {}", kakaoUser.getUsername());

		res.setHeader(AUTHORIZATION_HEADER, accessToken);
		redisService.saveRefreshToken(kakaoUser.getUsername(), refreshToken);

		res.setStatus(SC_OK);
		res.setCharacterEncoding("UTF-8");
		res.setContentType("application/json");

		log.info("카카오 로그인 완료 - username: {}", kakaoUser.getUsername());
		return accessToken;
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
			throw new IllegalArgumentException("카카오 토큰 응답이 올바르지 않습니다.");
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
			throw new IllegalArgumentException("카카오 사용자 정보 응답이 올바르지 않습니다.");
		}

		Long id = idNode.asLong();
		JsonNode nicknameNode = propertiesNode.get("nickname");
		JsonNode emailNode = kakaoAccountNode.get("email");

		if (nicknameNode == null || emailNode == null) {
			throw new IllegalArgumentException("카카오 사용자 닉네임 또는 이메일 정보가 없습니다.");
		}

		String username = nicknameNode.asText();
		String email = emailNode.asText();

		log.info("카카오 사용자 정보: {}, {}, {}", id, username, email);
		return new KakaoUserInfoDto(id, username, email);
	}

	private User registerKakaoUserIfNeeded(KakaoUserInfoDto kakaoUserInfo) {
		// DB 에 중복된 Kakao Id 가 있는지 확인
		Long kakaoId = kakaoUserInfo.getId();
		User kakaoUser = userRepository.findByKakaoId(kakaoId).orElse(null);

		if (kakaoUser == null) {
			log.info("신규 카카오 사용자 처리 - 카카오 ID: {}", kakaoId);

			// 카카오 사용자 email 동일한 email 가진 회원이 있는지 확인
			String kakaoUsername = kakaoUserInfo.getUsername();
			User sameUsernameUser = userRepository.findByUsername(kakaoUsername).orElse(null);
			if (sameUsernameUser != null) {
				log.info("기존 회원과 카카오 계정 통합 - username: {}", kakaoUsername);
				// 통합 과정
				kakaoUser = sameUsernameUser;
				// 기존 회원정보에 카카오 Id 추가
				kakaoUser.setKakaoId(kakaoId);
			} else {
				log.info("신규 카카오 회원 가입 - username: {}", kakaoUsername);
				// 신규 회원가입
				// password: random UUID
				String password = UUID.randomUUID().toString();
				String encodedPassword = passwordEncoder.encode(password);

				// email: kakao email
				String email = kakaoUserInfo.getEmail();

				kakaoUser = User.builder()
						.username(kakaoUsername)
						.name(kakaoUsername)
						.nickname(kakaoUsername)
						.kakaoId(kakaoId)
						.role(UserRole.USER)
						.encodedPassword(encodedPassword)
						.email(email)
						.address("")
						.build();
			}

			userRepository.save(kakaoUser);
			log.info("카카오 사용자 DB 저장 완료 - username: {}", kakaoUser.getUsername());
		} else {
			log.info("기존 카카오 사용자 로그인 - username: {}", kakaoUser.getUsername());
		}
		return kakaoUser;
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
