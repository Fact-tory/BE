package com.commonground.be.domain.auth.facade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.commonground.be.domain.auth.service.AuthService;
import com.commonground.be.domain.session.dto.SessionCreateRequest;
import com.commonground.be.domain.session.dto.SessionResponse;
import com.commonground.be.domain.session.service.SessionService;
import com.commonground.be.domain.user.entity.User;
import com.commonground.be.domain.user.repository.UserRepository;
import com.commonground.be.domain.user.utils.UserRole;
import com.commonground.be.global.infrastructure.security.jwt.JwtProvider;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.test.context.ActiveProfiles;

/**
 * AuthFacade OAuth2 Client 통합 테스트
 * 
 * OAuth2 로그인 성공 파이프라인과 세션 관리 통합 테스트
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AuthFacade OAuth2 Client 통합 테스트")
@ActiveProfiles("test")
class AuthFacadeOAuth2Test {

	@InjectMocks
	private AuthFacade authFacade;

	@Mock
	private AuthService authService;

	@Mock
	private UserRepository userRepository;

	@Mock
	private JwtProvider jwtProvider;

	@Mock
	private SessionService sessionService;

	@Mock
	private OAuth2User oauth2User;

	private MockHttpServletRequest mockRequest;
	private MockHttpServletResponse mockResponse;
	private User testUser;

	// 테스트 데이터
	private static final String TEST_EMAIL = "test@example.com";
	private static final String TEST_NAME = "테스트사용자";
	private static final String TEST_USERNAME = "test";
	private static final String TEST_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36";
	private static final String TEST_ACCESS_TOKEN = "test.access.token";
	private static final String TEST_REFRESH_TOKEN = "test.refresh.token";
	private static final String TEST_SESSION_ID = "session123";

	@BeforeEach
	void setUp() {
		// Mock HTTP Request/Response 설정
		mockRequest = new MockHttpServletRequest();
		mockRequest.addHeader("User-Agent", TEST_USER_AGENT);
		mockRequest.setRemoteAddr("127.0.0.1");

		mockResponse = new MockHttpServletResponse();

		// 테스트용 사용자 (생성자 사용)
		testUser = new User(TEST_USERNAME, TEST_NAME, TEST_EMAIL, UserRole.USER);
		// ID는 JPA에서 자동 생성되므로 리플렉션으로 설정
		org.springframework.test.util.ReflectionTestUtils.setField(testUser, "id", 1L);
	}

	@Nested
	@DisplayName("Google OAuth2 로그인 성공 테스트")
	class GoogleOAuth2LoginTest {

		@Test
		@DisplayName("Google OAuth2 로그인 성공 - 새 사용자 생성")
		void oauth2LoginSuccessFlow_Google_NewUser_ShouldCreateUserAndSession() {
			// Given: Google OAuth2 사용자 정보
			Map<String, Object> attributes = new HashMap<>();
			attributes.put("email", TEST_EMAIL);
			attributes.put("name", TEST_NAME);
			attributes.put("picture", "https://example.com/avatar.jpg");

			when(oauth2User.getAttribute("email")).thenReturn(TEST_EMAIL);
			when(oauth2User.getAttribute("name")).thenReturn(TEST_NAME);
			when(oauth2User.getAttribute("picture")).thenReturn("https://example.com/avatar.jpg");

			// 새 사용자 생성 시나리오
			when(userRepository.findByEmail(TEST_EMAIL)).thenReturn(Optional.empty());
			when(userRepository.save(any(User.class))).thenReturn(testUser);

			// JWT 토큰 생성
			when(jwtProvider.createAccessToken(TEST_EMAIL, UserRole.USER)).thenReturn(TEST_ACCESS_TOKEN);
			when(jwtProvider.createRefreshToken(TEST_EMAIL)).thenReturn(TEST_REFRESH_TOKEN);

			// 세션 생성
			SessionResponse sessionResponse = SessionResponse.builder()
					.sessionId(TEST_SESSION_ID)
					.userId(String.valueOf(testUser.getId()))
					.userAgent(TEST_USER_AGENT)
					.createdAt(LocalDateTime.now())
					.active(true)
					.build();
			when(sessionService.createSession(any(SessionCreateRequest.class))).thenReturn(sessionResponse);

			// 쿠키 설정 Mock
			doAnswer(invocation -> {
				HttpServletResponse response = invocation.getArgument(0);
				String token = invocation.getArgument(1);
				Cookie cookie = new Cookie("accessToken", token);
				response.addCookie(cookie);
				return null;
			}).when(jwtProvider).setAccessTokenCookie(any(), anyString());

			doAnswer(invocation -> {
				HttpServletResponse response = invocation.getArgument(0);
				String token = invocation.getArgument(1);
				Cookie cookie = new Cookie("refreshToken", token);
				response.addCookie(cookie);
				return null;
			}).when(jwtProvider).setRefreshTokenCookie(any(), anyString());

			// When: OAuth2 로그인 성공 처리
			AuthFacade.AuthFlowResult result = authFacade.oauth2LoginSuccessFlow(
					oauth2User, "google", mockRequest, mockResponse);

			// Then: 성공 결과 검증
			assertThat(result.isSuccess()).isTrue();
			assertThat(result.getMessage()).isEqualTo("OAuth2 로그인 성공");

			// 결과 데이터 검증
			@SuppressWarnings("unchecked")
			Map<String, Object> resultData = (Map<String, Object>) result.getData();
			assertThat(resultData).containsKeys("userId", "email", "name", "provider", "loginTime");
			assertThat(resultData.get("email")).isEqualTo(TEST_EMAIL);
			assertThat(resultData.get("provider")).isEqualTo("google");

			// Mock 호출 검증
			verify(userRepository).findByEmail(TEST_EMAIL);
			verify(userRepository).save(any(User.class));
			verify(jwtProvider).createAccessToken(TEST_EMAIL, UserRole.USER);
			verify(jwtProvider).createRefreshToken(TEST_EMAIL);
			verify(sessionService).createSession(any(SessionCreateRequest.class));
			verify(jwtProvider).setAccessTokenCookie(mockResponse, TEST_ACCESS_TOKEN);
			verify(jwtProvider).setRefreshTokenCookie(mockResponse, TEST_REFRESH_TOKEN);
		}

		@Test
		@DisplayName("Google OAuth2 로그인 성공 - 기존 사용자")
		void oauth2LoginSuccessFlow_Google_ExistingUser_ShouldUpdateSession() {
			// Given: Google OAuth2 사용자 정보 (기존 사용자)
			when(oauth2User.getAttribute("email")).thenReturn(TEST_EMAIL);
			when(oauth2User.getAttribute("name")).thenReturn(TEST_NAME);
			when(oauth2User.getAttribute("picture")).thenReturn("https://example.com/avatar.jpg");

			// 기존 사용자 조회
			when(userRepository.findByEmail(TEST_EMAIL)).thenReturn(Optional.of(testUser));

			// JWT 토큰 생성
			when(jwtProvider.createAccessToken(TEST_EMAIL, UserRole.USER)).thenReturn(TEST_ACCESS_TOKEN);
			when(jwtProvider.createRefreshToken(TEST_EMAIL)).thenReturn(TEST_REFRESH_TOKEN);

			// 세션 생성
			SessionResponse sessionResponse = SessionResponse.builder()
					.sessionId(TEST_SESSION_ID)
					.userId(String.valueOf(testUser.getId()))
					.userAgent(TEST_USER_AGENT)
					.createdAt(LocalDateTime.now())
					.active(true)
					.build();
			when(sessionService.createSession(any(SessionCreateRequest.class))).thenReturn(sessionResponse);

			// When: OAuth2 로그인 성공 처리
			AuthFacade.AuthFlowResult result = authFacade.oauth2LoginSuccessFlow(
					oauth2User, "google", mockRequest, mockResponse);

			// Then: 성공 결과 검증
			assertThat(result.isSuccess()).isTrue();

			// 기존 사용자이므로 새 사용자 생성하지 않음
			verify(userRepository).findByEmail(TEST_EMAIL);
			verify(userRepository, org.mockito.Mockito.never()).save(any(User.class));
			verify(sessionService).createSession(any(SessionCreateRequest.class));
		}
	}

	@Nested
	@DisplayName("Kakao OAuth2 로그인 성공 테스트")
	class KakaoOAuth2LoginTest {

		@Test
		@DisplayName("Kakao OAuth2 로그인 성공 - 새 사용자 생성")
		void oauth2LoginSuccessFlow_Kakao_NewUser_ShouldCreateUserAndSession() {
			// Given: Kakao OAuth2 사용자 정보
			Map<String, Object> kakaoAccount = new HashMap<>();
			kakaoAccount.put("email", TEST_EMAIL);

			Map<String, Object> profile = new HashMap<>();
			profile.put("nickname", TEST_NAME);
			profile.put("profile_image_url", "https://kakao.com/avatar.jpg");
			kakaoAccount.put("profile", profile);

			when(oauth2User.getAttribute("kakao_account")).thenReturn(kakaoAccount);

			// 새 사용자 생성 시나리오
			when(userRepository.findByEmail(TEST_EMAIL)).thenReturn(Optional.empty());
			when(userRepository.save(any(User.class))).thenReturn(testUser);

			// JWT 토큰 생성
			when(jwtProvider.createAccessToken(TEST_EMAIL, UserRole.USER)).thenReturn(TEST_ACCESS_TOKEN);
			when(jwtProvider.createRefreshToken(TEST_EMAIL)).thenReturn(TEST_REFRESH_TOKEN);

			// 세션 생성
			SessionResponse sessionResponse = SessionResponse.builder()
					.sessionId(TEST_SESSION_ID)
					.userId(String.valueOf(testUser.getId()))
					.userAgent(TEST_USER_AGENT)
					.createdAt(LocalDateTime.now())
					.active(true)
					.build();
			when(sessionService.createSession(any(SessionCreateRequest.class))).thenReturn(sessionResponse);

			// When: OAuth2 로그인 성공 처리
			AuthFacade.AuthFlowResult result = authFacade.oauth2LoginSuccessFlow(
					oauth2User, "kakao", mockRequest, mockResponse);

			// Then: 성공 결과 검증
			assertThat(result.isSuccess()).isTrue();
			assertThat(result.getMessage()).isEqualTo("OAuth2 로그인 성공");

			// 결과 데이터 검증
			@SuppressWarnings("unchecked")
			Map<String, Object> resultData = (Map<String, Object>) result.getData();
			assertThat(resultData.get("provider")).isEqualTo("kakao");

			// Mock 호출 검증
			verify(userRepository).findByEmail(TEST_EMAIL);
			verify(userRepository).save(any(User.class));
			verify(sessionService).createSession(any(SessionCreateRequest.class));
		}
	}

	@Nested
	@DisplayName("OAuth2 로그인 실패 테스트")
	class OAuth2LoginFailureTest {

		@Test
		@DisplayName("Google - 필수 정보 누락 시 실패")
		void oauth2LoginSuccessFlow_Google_MissingRequiredInfo_ShouldFail() {
			// Given: 이메일 정보가 누락된 Google OAuth2 사용자
			when(oauth2User.getAttribute("email")).thenReturn(null);
			when(oauth2User.getAttribute("name")).thenReturn(TEST_NAME);

			// When: OAuth2 로그인 성공 처리
			AuthFacade.AuthFlowResult result = authFacade.oauth2LoginSuccessFlow(
					oauth2User, "google", mockRequest, mockResponse);

			// Then: 실패 결과 검증
			assertThat(result.isSuccess()).isFalse();
			assertThat(result.getMessage()).contains("OAuth2 로그인 처리 실패");
			assertThat(result.getError()).isNotNull();
		}

		@Test
		@DisplayName("Kakao - 필수 정보 누락 시 실패")
		void oauth2LoginSuccessFlow_Kakao_MissingRequiredInfo_ShouldFail() {
			// Given: 이메일 정보가 누락된 Kakao OAuth2 사용자
			Map<String, Object> kakaoAccount = new HashMap<>();
			kakaoAccount.put("email", null); // 이메일 누락

			Map<String, Object> profile = new HashMap<>();
			profile.put("nickname", TEST_NAME);
			kakaoAccount.put("profile", profile);

			when(oauth2User.getAttribute("kakao_account")).thenReturn(kakaoAccount);

			// When: OAuth2 로그인 성공 처리
			AuthFacade.AuthFlowResult result = authFacade.oauth2LoginSuccessFlow(
					oauth2User, "kakao", mockRequest, mockResponse);

			// Then: 실패 결과 검증
			assertThat(result.isSuccess()).isFalse();
			assertThat(result.getMessage()).contains("OAuth2 로그인 처리 실패");
		}

		@Test
		@DisplayName("지원하지 않는 Provider 시 실패")
		void oauth2LoginSuccessFlow_UnsupportedProvider_ShouldFail() {
			// When: 지원하지 않는 Provider로 OAuth2 로그인 처리
			AuthFacade.AuthFlowResult result = authFacade.oauth2LoginSuccessFlow(
					oauth2User, "naver", mockRequest, mockResponse);

			// Then: 실패 결과 검증
			assertThat(result.isSuccess()).isFalse();
			assertThat(result.getMessage()).contains("OAuth2 로그인 처리 실패");
			assertThat(result.getError()).isInstanceOf(IllegalArgumentException.class);
		}
	}

	@Nested
	@DisplayName("세션 생성 테스트")
	class SessionCreationTest {

		@Test
		@DisplayName("UserAgent 정보 추출 및 세션 생성")
		void oauth2LoginSuccessFlow_ShouldExtractUserAgentAndCreateSession() {
			// Given: Google OAuth2 사용자 정보
			when(oauth2User.getAttribute("email")).thenReturn(TEST_EMAIL);
			when(oauth2User.getAttribute("name")).thenReturn(TEST_NAME);
			when(oauth2User.getAttribute("picture")).thenReturn("https://example.com/avatar.jpg");

			when(userRepository.findByEmail(TEST_EMAIL)).thenReturn(Optional.of(testUser));
			when(jwtProvider.createAccessToken(TEST_EMAIL, UserRole.USER)).thenReturn(TEST_ACCESS_TOKEN);
			when(jwtProvider.createRefreshToken(TEST_EMAIL)).thenReturn(TEST_REFRESH_TOKEN);

			// 세션 생성 Mock - SessionCreateRequest 검증
			doAnswer(invocation -> {
				SessionCreateRequest request = invocation.getArgument(0);
				assertThat(request.getUserId()).isEqualTo(String.valueOf(testUser.getId()));
				assertThat(request.getUserAgent()).isEqualTo(TEST_USER_AGENT);

				return SessionResponse.builder()
						.sessionId(TEST_SESSION_ID)
						.userId(request.getUserId())
						.userAgent(request.getUserAgent())
						.createdAt(LocalDateTime.now())
						.active(true)
						.build();
			}).when(sessionService).createSession(any(SessionCreateRequest.class));

			// When: OAuth2 로그인 성공 처리
			AuthFacade.AuthFlowResult result = authFacade.oauth2LoginSuccessFlow(
					oauth2User, "google", mockRequest, mockResponse);

			// Then: 세션 생성 검증
			assertThat(result.isSuccess()).isTrue();
			verify(sessionService).createSession(any(SessionCreateRequest.class));
		}

		@Test
		@DisplayName("UserAgent가 긴 경우 100자로 제한")
		void oauth2LoginSuccessFlow_LongUserAgent_ShouldTruncateTo100Chars() {
			// Given: 긴 UserAgent
			String longUserAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36 with very long additional information that exceeds 100 characters";
			mockRequest.removeHeader("User-Agent");
			mockRequest.addHeader("User-Agent", longUserAgent);

			when(oauth2User.getAttribute("email")).thenReturn(TEST_EMAIL);
			when(oauth2User.getAttribute("name")).thenReturn(TEST_NAME);
			when(oauth2User.getAttribute("picture")).thenReturn("https://example.com/avatar.jpg");

			when(userRepository.findByEmail(TEST_EMAIL)).thenReturn(Optional.of(testUser));
			when(jwtProvider.createAccessToken(TEST_EMAIL, UserRole.USER)).thenReturn(TEST_ACCESS_TOKEN);
			when(jwtProvider.createRefreshToken(TEST_EMAIL)).thenReturn(TEST_REFRESH_TOKEN);

			// 세션 생성 Mock - UserAgent 길이 검증
			doAnswer(invocation -> {
				SessionCreateRequest request = invocation.getArgument(0);
				assertThat(request.getUserAgent()).hasSize(100); // 100자로 제한
				assertThat(request.getUserAgent()).isEqualTo(longUserAgent.substring(0, 100));

				return SessionResponse.builder()
						.sessionId(TEST_SESSION_ID)
						.userId(request.getUserId())
						.userAgent(request.getUserAgent())
						.createdAt(LocalDateTime.now())
						.active(true)
						.build();
			}).when(sessionService).createSession(any(SessionCreateRequest.class));

			// When: OAuth2 로그인 성공 처리
			AuthFacade.AuthFlowResult result = authFacade.oauth2LoginSuccessFlow(
					oauth2User, "google", mockRequest, mockResponse);

			// Then: UserAgent 길이 제한 검증
			assertThat(result.isSuccess()).isTrue();
			verify(sessionService).createSession(any(SessionCreateRequest.class));
		}

		@Test
		@DisplayName("UserAgent가 없는 경우 'unknown'으로 설정")
		void oauth2LoginSuccessFlow_NoUserAgent_ShouldUseUnknown() {
			// Given: UserAgent 헤더가 없는 요청
			mockRequest.removeHeader("User-Agent");

			when(oauth2User.getAttribute("email")).thenReturn(TEST_EMAIL);
			when(oauth2User.getAttribute("name")).thenReturn(TEST_NAME);
			when(oauth2User.getAttribute("picture")).thenReturn("https://example.com/avatar.jpg");

			when(userRepository.findByEmail(TEST_EMAIL)).thenReturn(Optional.of(testUser));
			when(jwtProvider.createAccessToken(TEST_EMAIL, UserRole.USER)).thenReturn(TEST_ACCESS_TOKEN);
			when(jwtProvider.createRefreshToken(TEST_EMAIL)).thenReturn(TEST_REFRESH_TOKEN);

			// 세션 생성 Mock - UserAgent 'unknown' 검증
			doAnswer(invocation -> {
				SessionCreateRequest request = invocation.getArgument(0);
				assertThat(request.getUserAgent()).isEqualTo("unknown");

				return SessionResponse.builder()
						.sessionId(TEST_SESSION_ID)
						.userId(request.getUserId())
						.userAgent(request.getUserAgent())
						.createdAt(LocalDateTime.now())
						.active(true)
						.build();
			}).when(sessionService).createSession(any(SessionCreateRequest.class));

			// When: OAuth2 로그인 성공 처리
			AuthFacade.AuthFlowResult result = authFacade.oauth2LoginSuccessFlow(
					oauth2User, "google", mockRequest, mockResponse);

			// Then: 'unknown' UserAgent 검증
			assertThat(result.isSuccess()).isTrue();
			verify(sessionService).createSession(any(SessionCreateRequest.class));
		}
	}
}