package com.commonground.be.domain.social.kakao.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.commonground.be.domain.session.service.SessionService;
import com.commonground.be.domain.social.dto.SocialUserInfo;
import com.commonground.be.domain.social.service.SocialUserService;
import com.commonground.be.global.infrastructure.security.jwt.JwtProvider;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

/**
 * KakaoService 단위 테스트 클래스
 * <p>
 * 이 테스트는 KakaoService의 Kakao OAuth2 특화 로직을 검증합니다. URL 생성, 프로바이더 이름 반환, URI 빌더 등의 순수 비즈니스 로직과 외부 API
 * 호출이 포함된 메서드들을 테스트합니다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("KakaoService 단위 테스트")
class KakaoServiceTest {

	private KakaoService kakaoService;

	@Mock
	private RestTemplate restTemplate;

	@Mock
	private ObjectMapper objectMapper;

	@Mock
	private JwtProvider jwtProvider;

	@Mock
	private SessionService sessionService;

	@Mock
	private SocialUserService socialUserService;

	// 테스트용 Kakao OAuth 설정값들
	private static final String TEST_KAKAO_URL = "https://kauth.kakao.com";
	private static final String TEST_CLIENT_ID = "test_client_id";
	private static final String TEST_REDIRECT_URI = "http://localhost:8080/auth/kakao/callback";
	private static final String TEST_KAKAO_API_URL = "https://kapi.kakao.com";

	/**
	 * 각 테스트 실행 전 KakaoService 인스턴스를 초기화하고 필요한 설정값들을 주입합니다.
	 */
	@BeforeEach
	void setUp() {
		// KakaoService 인스턴스 생성
		kakaoService = new KakaoService(
				jwtProvider,
				sessionService,
				socialUserService,
				restTemplate,
				objectMapper
		);

		// @Value로 주입되는 설정값들을 리플렉션을 통해 설정
		ReflectionTestUtils.setField(kakaoService, "kakaoUrl", TEST_KAKAO_URL);
		ReflectionTestUtils.setField(kakaoService, "kakaoClientId", TEST_CLIENT_ID);
		ReflectionTestUtils.setField(kakaoService, "kakaoRedirectUri", TEST_REDIRECT_URI);
		ReflectionTestUtils.setField(kakaoService, "kakaoApiUrl", TEST_KAKAO_API_URL);
	}

	@Nested
	@DisplayName("Kakao OAuth URL 생성 테스트")
	class GetAuthUrlTest {

		@Test
		@DisplayName("올바른 Kakao OAuth URL이 생성되어야 함")
		void getAuthUrl_WithValidRedirectUri_ShouldReturnValidKakaoAuthUrl() {
			// Given: 유효한 리다이렉트 URI가 주어졌을 때
			String redirectUri = "http://localhost:8080/callback";

			// When: Kakao OAuth URL을 생성하면
			String authUrl = kakaoService.getAuthUrl(redirectUri);

			// Then: 올바른 Kakao OAuth URL이 생성되어야 함
			assertThat(authUrl).isNotNull();
			assertThat(authUrl).startsWith(TEST_KAKAO_URL);
			assertThat(authUrl).contains("/oauth/authorize");
			assertThat(authUrl).contains("client_id=" + TEST_CLIENT_ID);
			assertThat(authUrl).contains("redirect_uri=" + redirectUri);
			assertThat(authUrl).contains("response_type=code");
		}

		@Test
		@DisplayName("리다이렉트 URI가 포함된 완전한 URL이 생성되어야 함")
		void getAuthUrl_ShouldIncludeAllRequiredParameters() {
			// Given: 테스트용 리다이렉트 URI
			String redirectUri = "http://localhost:8080/oauth/callback/kakao";

			// When: OAuth URL을 생성하면
			String authUrl = kakaoService.getAuthUrl(redirectUri);

			// Then: 모든 필수 파라미터가 포함되어야 함
			assertThat(authUrl)
					.contains("response_type=code")
					.contains("client_id=")
					.contains("redirect_uri=");
		}

		@Test
		@DisplayName("특수 문자가 포함된 리다이렉트 URI도 올바르게 인코딩되어야 함")
		void getAuthUrl_WithSpecialCharacters_ShouldEncodeUri() {
			// Given: 특수 문자가 포함된 리다이렉트 URI
			String redirectUri = "http://localhost:8080/auth/callback?state=test&code=123";

			// When: OAuth URL을 생성하면
			String authUrl = kakaoService.getAuthUrl(redirectUri);

			// Then: URI가 올바르게 인코딩되어야 함
			assertThat(authUrl).isNotNull();
			assertThat(authUrl).contains("redirect_uri=");
			// URL 인코딩으로 인해 특수문자들이 변환되었는지 확인
			assertThat(authUrl).doesNotContain("redirect_uri=" + redirectUri);
		}

		@Test
		@DisplayName("Kakao OAuth 엔드포인트 경로가 올바르게 포함되어야 함")
		void getAuthUrl_ShouldIncludeCorrectKakaoOAuthEndpoint() {
			// Given: 테스트용 리다이렉트 URI
			String redirectUri = "http://example.com/callback";

			// When: OAuth URL을 생성하면
			String authUrl = kakaoService.getAuthUrl(redirectUri);

			// Then: Kakao OAuth 엔드포인트 경로가 포함되어야 함
			assertThat(authUrl).contains("/oauth/authorize");
		}
	}

	@Nested
	@DisplayName("프로바이더 이름 반환 테스트")
	class GetProviderNameTest {

		@Test
		@DisplayName("카카오 프로바이더 이름이 올바르게 반환되어야 함")
		void getProviderName_ShouldReturnKakaoInKorean() {
			// When: 프로바이더 이름을 조회하면
			String providerName = kakaoService.getProviderName();

			// Then: 한글로 "카카오"가 반환되어야 함
			assertThat(providerName).isEqualTo("카카오");
		}

		@Test
		@DisplayName("프로바이더 이름은 null이 아니어야 함")
		void getProviderName_ShouldNotBeNull() {
			// When: 프로바이더 이름을 조회하면
			String providerName = kakaoService.getProviderName();

			// Then: null이 아니어야 함
			assertThat(providerName).isNotNull();
			assertThat(providerName).isNotEmpty();
		}
	}

	@Nested
	@DisplayName("액세스 토큰 획득 테스트")
	class GetAccessTokenTest {

		@Test
		@DisplayName("유효한 인증 코드로 액세스 토큰 획득 성공")
		void getAccessToken_WithValidCode_ShouldReturnAccessToken() throws JsonProcessingException {
			// Given: 유효한 인증 코드와 Mock 응답이 준비되었을 때
			String authCode = "valid_auth_code";
			String mockResponse = """
					{
					    "access_token": "test_kakao_access_token",
					    "refresh_token": "test_kakao_refresh_token",
					    "token_type": "Bearer",
					    "expires_in": 21599
					}
					""";

			// Mock 설정: RestTemplate과 ObjectMapper의 동작 정의
			when(restTemplate.exchange(any(), any(Class.class)))
					.thenReturn(new ResponseEntity<>(mockResponse, HttpStatus.OK));
			when(objectMapper.readTree(anyString()))
					.thenReturn(new ObjectMapper().readTree(mockResponse));

			// When: 액세스 토큰을 요청하면
			String accessToken = kakaoService.getAccessToken(authCode);

			// Then: 올바른 액세스 토큰이 반환되어야 함
			assertThat(accessToken).isEqualTo("test_kakao_access_token");
		}

		@Test
		@DisplayName("액세스 토큰이 없는 응답 시 예외 발생")
		void getAccessToken_WithMissingAccessToken_ShouldThrowException()
				throws JsonProcessingException {
			// Given: 액세스 토큰이 없는 응답
			String authCode = "valid_auth_code";
			String incompleteResponse = """
					{
					    "refresh_token": "test_kakao_refresh_token",
					    "token_type": "Bearer"
					}
					""";

			when(restTemplate.exchange(any(), any(Class.class)))
					.thenReturn(new ResponseEntity<>(incompleteResponse, HttpStatus.OK));
			when(objectMapper.readTree(anyString()))
					.thenReturn(new ObjectMapper().readTree(incompleteResponse));

			// When & Then: SocialExceptions.invalidTokenResponse() 예외가 발생해야 함
			assertThatThrownBy(() -> kakaoService.getAccessToken(authCode))
					.isInstanceOf(RuntimeException.class);
		}

		@Test
		@DisplayName("리프레시 토큰이 없는 응답 시 예외 발생")
		void getAccessToken_WithMissingRefreshToken_ShouldThrowException()
				throws JsonProcessingException {
			// Given: 리프레시 토큰이 없는 응답
			String authCode = "valid_auth_code";
			String incompleteResponse = """
					{
					    "access_token": "test_kakao_access_token",
					    "token_type": "Bearer"
					}
					""";

			when(restTemplate.exchange(any(), any(Class.class)))
					.thenReturn(new ResponseEntity<>(incompleteResponse, HttpStatus.OK));
			when(objectMapper.readTree(anyString()))
					.thenReturn(new ObjectMapper().readTree(incompleteResponse));

			// When & Then: SocialExceptions.invalidTokenResponse() 예외가 발생해야 함
			assertThatThrownBy(() -> kakaoService.getAccessToken(authCode))
					.isInstanceOf(RuntimeException.class);
		}

		@Test
		@DisplayName("JSON 파싱 오류 시 예외 발생")
		void getAccessToken_WithJsonParsingError_ShouldThrowException()
				throws JsonProcessingException {
			// Given: JSON 파싱 오류가 발생하는 상황
			String authCode = "valid_auth_code";
			String invalidJson = "invalid_json_response";

			when(restTemplate.exchange(any(), any(Class.class)))
					.thenReturn(new ResponseEntity<>(invalidJson, HttpStatus.OK));
			when(objectMapper.readTree(anyString()))
					.thenThrow(new JsonProcessingException("Invalid JSON") {
					});

			// When & Then: JsonProcessingException이 발생해야 함
			assertThatThrownBy(() -> kakaoService.getAccessToken(authCode))
					.isInstanceOf(JsonProcessingException.class);
		}
	}

	@Nested
	@DisplayName("사용자 정보 획득 테스트")
	class GetUserInfoTest {

		@Test
		@DisplayName("유효한 액세스 토큰으로 사용자 정보 획득 성공")
		void getUserInfo_WithValidAccessToken_ShouldReturnSocialUserInfo()
				throws JsonProcessingException {
			// Given: 유효한 액세스 토큰과 Mock 사용자 정보 응답
			String accessToken = "valid_access_token";
			String mockUserInfoResponse = """
					{
					    "id": 12345,
					    "properties": {
					        "nickname": "카카오사용자"
					    },
					    "kakao_account": {
					        "email": "kakao@example.com",
					        "email_verified": true
					    }
					}
					""";

			// Mock 설정
			when(restTemplate.exchange(any(), any(Class.class)))
					.thenReturn(new ResponseEntity<>(mockUserInfoResponse, HttpStatus.OK));
			when(objectMapper.readTree(anyString()))
					.thenReturn(new ObjectMapper().readTree(mockUserInfoResponse));

			// When: 사용자 정보를 요청하면
			SocialUserInfo userInfo = kakaoService.getUserInfo(accessToken);

			// Then: 올바른 사용자 정보가 반환되어야 함
			assertThat(userInfo).isNotNull();
			assertThat(userInfo.getSocialId()).isEqualTo("12345");
			assertThat(userInfo.getName()).isEqualTo("카카오사용자");
			assertThat(userInfo.getEmail()).isEqualTo("kakao@example.com");
			assertThat(userInfo.getProvider()).isEqualTo("kakao");
		}

		@Test
		@DisplayName("필수 필드가 누락된 응답 시 예외 발생")
		void getUserInfo_WithMissingRequiredFields_ShouldThrowException()
				throws JsonProcessingException {
			// Given: 필수 필드가 누락된 응답 (id 필드 없음)
			String accessToken = "valid_access_token";
			String incompleteResponse = """
					{
					    "properties": {
					        "nickname": "카카오사용자"
					    },
					    "kakao_account": {
					        "email": "kakao@example.com"
					    }
					}
					""";

			when(restTemplate.exchange(any(), any(Class.class)))
					.thenReturn(new ResponseEntity<>(incompleteResponse, HttpStatus.OK));
			when(objectMapper.readTree(anyString()))
					.thenReturn(new ObjectMapper().readTree(incompleteResponse));

			// When & Then: SocialExceptions.invalidUserInfoResponse() 예외가 발생해야 함
			assertThatThrownBy(() -> kakaoService.getUserInfo(accessToken))
					.isInstanceOf(RuntimeException.class);
		}

		@Test
		@DisplayName("properties 필드가 누락된 응답 시 예외 발생")
		void getUserInfo_WithMissingProperties_ShouldThrowException()
				throws JsonProcessingException {
			// Given: properties 필드가 누락된 응답
			String accessToken = "valid_access_token";
			String incompleteResponse = """
					{
					    "id": 12345,
					    "kakao_account": {
					        "email": "kakao@example.com"
					    }
					}
					""";

			when(restTemplate.exchange(any(), any(Class.class)))
					.thenReturn(new ResponseEntity<>(incompleteResponse, HttpStatus.OK));
			when(objectMapper.readTree(anyString()))
					.thenReturn(new ObjectMapper().readTree(incompleteResponse));

			// When & Then: 예외가 발생해야 함
			assertThatThrownBy(() -> kakaoService.getUserInfo(accessToken))
					.isInstanceOf(RuntimeException.class);
		}

		@Test
		@DisplayName("kakao_account 필드가 누락된 응답 시 예외 발생")
		void getUserInfo_WithMissingKakaoAccount_ShouldThrowException()
				throws JsonProcessingException {
			// Given: kakao_account 필드가 누락된 응답
			String accessToken = "valid_access_token";
			String incompleteResponse = """
					{
					    "id": 12345,
					    "properties": {
					        "nickname": "카카오사용자"
					    }
					}
					""";

			when(restTemplate.exchange(any(), any(Class.class)))
					.thenReturn(new ResponseEntity<>(incompleteResponse, HttpStatus.OK));
			when(objectMapper.readTree(anyString()))
					.thenReturn(new ObjectMapper().readTree(incompleteResponse));

			// When & Then: 예외가 발생해야 함
			assertThatThrownBy(() -> kakaoService.getUserInfo(accessToken))
					.isInstanceOf(RuntimeException.class);
		}
	}

	@Nested
	@DisplayName("URI 빌드 기능 테스트")
	class UriBuilderTest {

		@Test
		@DisplayName("생성된 URL이 HTTPS 프로토콜을 사용해야 함")
		void getAuthUrl_ShouldUseHttpsProtocol() {
			// Given: 임의의 리다이렉트 URI
			String redirectUri = "http://localhost:8080/callback";

			// When: OAuth URL을 생성하면
			String authUrl = kakaoService.getAuthUrl(redirectUri);

			// Then: HTTPS 프로토콜을 사용해야 함
			assertThat(authUrl).startsWith("https://");
		}

		@Test
		@DisplayName("OAuth 엔드포인트 경로가 올바르게 포함되어야 함")
		void getAuthUrl_ShouldIncludeCorrectOAuthEndpoint() {
			// Given: 테스트용 리다이렉트 URI
			String redirectUri = "http://example.com/callback";

			// When: OAuth URL을 생성하면
			String authUrl = kakaoService.getAuthUrl(redirectUri);

			// Then: Kakao OAuth 엔드포인트 경로가 포함되어야 함
			assertThat(authUrl).contains("/oauth/authorize");
		}

		@Test
		@DisplayName("필수 OAuth 파라미터들이 모두 포함되어야 함")
		void getAuthUrl_ShouldIncludeAllRequiredOAuthParameters() {
			// Given: 테스트용 리다이렉트 URI
			String redirectUri = "http://example.com/callback";

			// When: OAuth URL을 생성하면
			String authUrl = kakaoService.getAuthUrl(redirectUri);

			// Then: 모든 필수 OAuth 파라미터가 포함되어야 함
			assertThat(authUrl)
					.contains("response_type=code")
					.contains("client_id=")
					.contains("redirect_uri=");
		}
	}

	@Nested
	@DisplayName("URL 구성 요소 검증 테스트")
	class UrlComponentValidationTest {

		@Test
		@DisplayName("클라이언트 ID가 URL에 올바르게 포함되어야 함")
		void getAuthUrl_ShouldIncludeClientId() {
			// Given: 테스트용 리다이렉트 URI
			String redirectUri = "http://example.com/callback";

			// When: OAuth URL을 생성하면
			String authUrl = kakaoService.getAuthUrl(redirectUri);

			// Then: 클라이언트 ID가 포함되어야 함
			assertThat(authUrl).contains("client_id=" + TEST_CLIENT_ID);
		}

		@Test
		@DisplayName("리다이렉트 URI가 URL에 올바르게 포함되어야 함")
		void getAuthUrl_ShouldIncludeRedirectUri() {
			// Given: 테스트용 리다이렉트 URI
			String redirectUri = "http://example.com/callback";

			// When: OAuth URL을 생성하면
			String authUrl = kakaoService.getAuthUrl(redirectUri);

			// Then: 리다이렉트 URI가 포함되어야 함
			assertThat(authUrl).contains("redirect_uri=");
		}

		@Test
		@DisplayName("응답 타입이 code로 설정되어야 함")
		void getAuthUrl_ShouldIncludeResponseTypeCode() {
			// Given: 테스트용 리다이렉트 URI
			String redirectUri = "http://example.com/callback";

			// When: OAuth URL을 생성하면
			String authUrl = kakaoService.getAuthUrl(redirectUri);

			// Then: 응답 타입이 code로 설정되어야 함
			assertThat(authUrl).contains("response_type=code");
		}
	}
}