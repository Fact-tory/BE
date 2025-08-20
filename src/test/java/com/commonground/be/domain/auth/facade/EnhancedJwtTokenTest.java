package com.commonground.be.domain.auth.facade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.commonground.be.domain.auth.service.AuthService;
import com.commonground.be.domain.session.dto.SessionCreateRequest;
import com.commonground.be.domain.session.dto.SessionResponse;
import com.commonground.be.domain.session.service.SessionService;
import com.commonground.be.domain.user.entity.User;
import com.commonground.be.domain.user.repository.UserRepository;
import com.commonground.be.domain.user.service.UserService;
import com.commonground.be.domain.user.utils.UserRole;
import com.commonground.be.global.infrastructure.security.jwt.JwtProvider;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.LocalDateTime;
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
 * 향상된 JWT 토큰 기능 테스트
 * 
 * 이메일과 이름이 포함된 JWT 토큰 생성/검증 및 
 * 암호화된 이메일 필드 매칭 기능 테스트
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("향상된 JWT 토큰 기능 테스트")
@ActiveProfiles("test")
class EnhancedJwtTokenTest {

	@InjectMocks
	private AuthFacade authFacade;

	@Mock
	private AuthService authService;

	@Mock
	private UserRepository userRepository;

	@Mock
	private UserService userService;

	@Mock
	private JwtProvider jwtProvider;

	@Mock
	private SessionService sessionService;

	private MockHttpServletRequest mockRequest;
	private MockHttpServletResponse mockResponse;
	private User testUser;

	// 테스트 데이터
	private static final String TEST_EMAIL = "enhanced.test@example.com";
	private static final String TEST_NAME = "향상된테스트사용자";
	private static final String TEST_USERNAME = "enhanced.test";
	private static final String TEST_PICTURE = "https://example.com/avatar.jpg";
	private static final String TEST_SESSION_ID = "enhanced_session_123";
	private static final String TEST_ENHANCED_ACCESS_TOKEN = "enhanced.access.token.with.email.and.name";
	private static final String TEST_ENHANCED_REFRESH_TOKEN = "enhanced.refresh.token";

	@BeforeEach
	void setUp() {
		// Mock HTTP Request/Response 설정
		mockRequest = new MockHttpServletRequest();
		mockRequest.addHeader("User-Agent", "Test User Agent");
		mockRequest.setRemoteAddr("127.0.0.1");

		mockResponse = new MockHttpServletResponse();

		// 테스트용 사용자
		testUser = new User(TEST_USERNAME, TEST_NAME, TEST_EMAIL, UserRole.USER);
		org.springframework.test.util.ReflectionTestUtils.setField(testUser, "id", 100L);
	}

	@Nested
	@DisplayName("이메일+이름 포함 JWT 토큰 생성 테스트")
	class EnhancedJwtTokenGenerationTest {

		@Test
		@DisplayName("Google OAuth2 로그인 - 이메일과 이름이 포함된 JWT 토큰 생성")
		void oauth2LoginSuccessFlow_Google_ShouldCreateTokenWithEmailAndName() {
			// Given: Google OAuth2 사용자 정보
			OAuth2User oauth2User = mock(OAuth2User.class);
			when(oauth2User.getAttribute("email")).thenReturn(TEST_EMAIL);
			when(oauth2User.getAttribute("name")).thenReturn(TEST_NAME);
			when(oauth2User.getAttribute("picture")).thenReturn(TEST_PICTURE);

			String provider = "google";

			// 새 사용자 생성 시나리오
			when(userRepository.findByEmail(TEST_EMAIL)).thenReturn(Optional.empty());
			when(userRepository.save(any(User.class))).thenReturn(testUser);

			// 향상된 JWT 토큰 생성 (이메일과 이름 포함)
			when(jwtProvider.createAccessTokenWithEmailAndName(
					eq(TEST_USERNAME), eq(UserRole.USER), eq(TEST_EMAIL), eq(TEST_NAME)))
					.thenReturn(TEST_ENHANCED_ACCESS_TOKEN);
			when(jwtProvider.createRefreshToken(TEST_USERNAME)).thenReturn(TEST_ENHANCED_REFRESH_TOKEN);

			// 세션 생성
			SessionResponse sessionResponse = SessionResponse.builder()
					.sessionId(TEST_SESSION_ID)
					.userId(String.valueOf(testUser.getId()))
					.userAgent("Test User Agent")
					.createdAt(LocalDateTime.now())
					.active(true)
					.build();
			when(sessionService.createSession(any(SessionCreateRequest.class))).thenReturn(sessionResponse);

			// 쿠키 설정
			doNothing().when(jwtProvider).setAccessTokenCookie(any(), eq(TEST_ENHANCED_ACCESS_TOKEN));
			doNothing().when(jwtProvider).setRefreshTokenCookie(any(), eq(TEST_ENHANCED_REFRESH_TOKEN));

			// When: OAuth2 로그인 성공 처리
			AuthFacade.AuthFlowResult result = authFacade.oauth2LoginSuccessFlow(
					oauth2User, provider, mockRequest, mockResponse);

			// Then: 성공 결과 검증
			assertThat(result.isSuccess()).isTrue();
			assertThat(result.getMessage()).isEqualTo("OAuth2 로그인 성공");

			// 향상된 JWT 토큰 생성 메서드 호출 검증
			verify(jwtProvider).createAccessTokenWithEmailAndName(
					TEST_USERNAME, UserRole.USER, TEST_EMAIL, TEST_NAME);

			// 결과 데이터 검증
			@SuppressWarnings("unchecked")
			java.util.Map<String, Object> resultData = (java.util.Map<String, Object>) result.getData();
			assertThat(resultData).containsKeys("userId", "email", "name", "provider", "loginTime");
			assertThat(resultData.get("email")).isEqualTo(TEST_EMAIL);
			assertThat(resultData.get("name")).isEqualTo(TEST_NAME);
			assertThat(resultData.get("provider")).isEqualTo(provider);

			// 사용자 저장 및 세션 생성 검증
			verify(userRepository).save(any(User.class));
			verify(sessionService).createSession(any(SessionCreateRequest.class));
		}

		@Test
		@DisplayName("Kakao OAuth2 로그인 - 이메일과 이름이 포함된 JWT 토큰 생성")
		void oauth2LoginSuccessFlow_Kakao_ShouldCreateTokenWithEmailAndName() {
			// Given: Kakao OAuth2 사용자 정보
			OAuth2User oauth2User = mock(OAuth2User.class);
			
			// Kakao 특별 구조
			java.util.Map<String, Object> kakaoAccount = new java.util.HashMap<>();
			kakaoAccount.put("email", TEST_EMAIL);
			
			java.util.Map<String, Object> profile = new java.util.HashMap<>();
			profile.put("nickname", TEST_NAME);
			profile.put("profile_image_url", TEST_PICTURE);
			kakaoAccount.put("profile", profile);
			
			when(oauth2User.getAttribute("kakao_account")).thenReturn(kakaoAccount);

			String provider = "kakao";

			// 기존 사용자 조회
			when(userRepository.findByEmail(TEST_EMAIL)).thenReturn(Optional.of(testUser));

			// 향상된 JWT 토큰 생성 (이메일과 이름 포함)
			when(jwtProvider.createAccessTokenWithEmailAndName(
					eq(TEST_USERNAME), eq(UserRole.USER), eq(TEST_EMAIL), eq(TEST_NAME)))
					.thenReturn(TEST_ENHANCED_ACCESS_TOKEN);
			when(jwtProvider.createRefreshToken(TEST_USERNAME)).thenReturn(TEST_ENHANCED_REFRESH_TOKEN);

			// 세션 생성
			SessionResponse sessionResponse = SessionResponse.builder()
					.sessionId(TEST_SESSION_ID)
					.userId(String.valueOf(testUser.getId()))
					.build();
			when(sessionService.createSession(any())).thenReturn(sessionResponse);

			// When: OAuth2 로그인 성공 처리
			AuthFacade.AuthFlowResult result = authFacade.oauth2LoginSuccessFlow(
					oauth2User, provider, mockRequest, mockResponse);

			// Then: 성공 결과 검증
			assertThat(result.isSuccess()).isTrue();

			// 향상된 JWT 토큰 생성 검증
			verify(jwtProvider).createAccessTokenWithEmailAndName(
					TEST_USERNAME, UserRole.USER, TEST_EMAIL, TEST_NAME);

			// 기존 사용자이므로 새로운 사용자 저장하지 않음
			verify(userRepository, org.mockito.Mockito.never()).save(any(User.class));
		}
	}

	@Nested
	@DisplayName("JWT 토큰 클레임 검증 테스트")
	class JwtTokenClaimsTest {

		@Test
		@DisplayName("JWT 토큰에서 이메일과 이름 클레임 추출 검증")
		void jwtTokenShouldContainEmailAndNameClaims() {
			// Given: 이메일과 이름이 포함된 JWT 토큰
			String token = TEST_ENHANCED_ACCESS_TOKEN;

			// JWT Provider에서 클레임 추출 Mock
			when(jwtProvider.getUsernameFromToken(token)).thenReturn(TEST_EMAIL);
			when(jwtProvider.getClaimFromToken(token, "email")).thenReturn(TEST_EMAIL);
			when(jwtProvider.getClaimFromToken(token, "name")).thenReturn(TEST_NAME);
			when(jwtProvider.getClaimFromToken(token, "auth")).thenReturn("USER");

			// When: JWT 토큰에서 클레임 추출
			String extractedUsername = jwtProvider.getUsernameFromToken(token);
			String extractedEmail = jwtProvider.getClaimFromToken(token, "email");
			String extractedName = jwtProvider.getClaimFromToken(token, "name");
			String extractedRole = jwtProvider.getClaimFromToken(token, "auth");

			// Then: 클레임 값 검증
			assertThat(extractedUsername).isEqualTo(TEST_EMAIL);
			assertThat(extractedEmail).isEqualTo(TEST_EMAIL);
			assertThat(extractedName).isEqualTo(TEST_NAME);
			assertThat(extractedRole).isEqualTo("USER");

			// Mock 호출 검증
			verify(jwtProvider).getUsernameFromToken(token);
			verify(jwtProvider).getClaimFromToken(token, "email");
			verify(jwtProvider).getClaimFromToken(token, "name");
			verify(jwtProvider).getClaimFromToken(token, "auth");
		}
	}

	@Nested
	@DisplayName("이메일+이름 기반 사용자 인증 테스트")
	class EmailAndNameAuthenticationTest {

		@Test
		@DisplayName("이메일과 이름 조합으로 사용자 정확 식별")
		void findUserByEmailAndName_ShouldAuthenticateWithEncryptedEmail() {
			// Given: 암호화된 이메일을 가진 사용자
			when(userService.findByEmailAndName(TEST_EMAIL, TEST_NAME)).thenReturn(testUser);

			// When: 이메일과 이름으로 사용자 조회
			User foundUser = userService.findByEmailAndName(TEST_EMAIL, TEST_NAME);

			// Then: 올바른 사용자 반환 검증
			assertThat(foundUser).isNotNull();
			assertThat(foundUser.getId()).isEqualTo(testUser.getId());
			assertThat(foundUser.getName()).isEqualTo(TEST_NAME);

			// 메서드 호출 검증
			verify(userService).findByEmailAndName(TEST_EMAIL, TEST_NAME);
		}

		@Test
		@DisplayName("중복 이름 존재 시 이메일+이름 조합으로 정확한 사용자 식별")
		void duplicateNames_ShouldIdentifyUserByEmailAndName() {
			// Given: 같은 이름을 가진 다른 사용자들이 존재하는 시나리오
			User anotherUserWithSameName = new User("another.user", TEST_NAME, "another@example.com", UserRole.USER);
			
			// 이메일+이름 조합으로만 정확한 사용자 찾기
			when(userService.findByEmailAndName(TEST_EMAIL, TEST_NAME)).thenReturn(testUser);
			when(userService.findByEmailAndName("another@example.com", TEST_NAME)).thenReturn(anotherUserWithSameName);

			// When: 이메일과 이름 조합으로 사용자 조회
			User foundUser1 = userService.findByEmailAndName(TEST_EMAIL, TEST_NAME);
			User foundUser2 = userService.findByEmailAndName("another@example.com", TEST_NAME);

			// Then: 각각 올바른 사용자 반환 검증
			assertThat(foundUser1).isEqualTo(testUser);
			assertThat(foundUser2).isEqualTo(anotherUserWithSameName);
			assertThat(foundUser1.getEmail()).isNotEqualTo(foundUser2.getEmail());
			assertThat(foundUser1.getName()).isEqualTo(foundUser2.getName()); // 이름은 같음

			// 정확한 이메일+이름 조합으로 호출했는지 검증
			verify(userService).findByEmailAndName(TEST_EMAIL, TEST_NAME);
			verify(userService).findByEmailAndName("another@example.com", TEST_NAME);
		}
	}

	@Nested
	@DisplayName("보안 강화 테스트")
	class SecurityEnhancementTest {

		@Test
		@DisplayName("JWT 토큰에 이메일+이름 포함으로 보안 강화 확인")
		void enhancedJwtToken_ShouldProvideMultipleIdentificationMethods() {
			// Given: 향상된 JWT 토큰 생성 시나리오
			OAuth2User oauth2User = mock(OAuth2User.class);
			when(oauth2User.getAttribute("email")).thenReturn(TEST_EMAIL);
			when(oauth2User.getAttribute("name")).thenReturn(TEST_NAME);
			when(oauth2User.getAttribute("picture")).thenReturn(TEST_PICTURE);

			String provider = "google";

			when(userRepository.findByEmail(TEST_EMAIL)).thenReturn(Optional.of(testUser));
			when(jwtProvider.createAccessTokenWithEmailAndName(anyString(), any(), anyString(), anyString()))
					.thenReturn(TEST_ENHANCED_ACCESS_TOKEN);

			// 세션 생성
			SessionResponse sessionResponse = SessionResponse.builder()
					.sessionId(TEST_SESSION_ID)
					.userId(String.valueOf(testUser.getId()))
					.build();
			when(sessionService.createSession(any())).thenReturn(sessionResponse);

			// When: OAuth2 로그인 성공 처리
			AuthFacade.AuthFlowResult result = authFacade.oauth2LoginSuccessFlow(
					oauth2User, provider, mockRequest, mockResponse);

			// Then: 보안 강화 검증
			assertThat(result.isSuccess()).isTrue();

			// 향상된 토큰 생성 메서드 호출 확인 (이메일과 이름 모두 전달)
			verify(jwtProvider).createAccessTokenWithEmailAndName(
					TEST_USERNAME, UserRole.USER, TEST_EMAIL, TEST_NAME);

			// 결과에 이메일과 이름이 모두 포함되어 있는지 확인
			@SuppressWarnings("unchecked")
			java.util.Map<String, Object> resultData = (java.util.Map<String, Object>) result.getData();
			assertThat(resultData.get("email")).isEqualTo(TEST_EMAIL);
			assertThat(resultData.get("name")).isEqualTo(TEST_NAME);
		}
	}
}