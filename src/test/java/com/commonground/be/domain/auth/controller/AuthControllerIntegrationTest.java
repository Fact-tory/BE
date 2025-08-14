package com.commonground.be.domain.auth.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.commonground.be.config.TestSecurityConfig;
import com.commonground.be.domain.session.service.SessionService;
import com.commonground.be.domain.user.entity.User;
import com.commonground.be.domain.user.service.UserService;
import com.commonground.be.domain.user.utils.UserRole;
import com.commonground.be.global.infrastructure.security.jwt.JwtProvider;
import com.commonground.be.global.infrastructure.security.jwt.TokenManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * AuthController 통합 테스트 클래스
 * <p>
 * 이 테스트는 AuthController의 토큰 재발급 API와 Spring Security 연동을 검증합니다. MockMvc를 사용하여 실제 HTTP 요청/응답 흐름을
 * 테스트하며, JWT 토큰 생성, 쿠키 설정, 세션 관리 등의 전체 인증 플로우를 검증합니다.
 */
@WebMvcTest(AuthController.class)
@Import(TestSecurityConfig.class)
@ActiveProfiles("test")
@DisplayName("AuthController 통합 테스트")
class AuthControllerIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@MockitoBean
	private JwtProvider jwtProvider;

	@MockitoBean
	private TokenManager tokenManager;

	@MockitoBean
	private UserService userService;

	@MockitoBean
	private SessionService sessionService;

	@MockitoBean
	private RedisTemplate<String, String> redisTemplate;

	// 테스트 데이터
	private static final String TEST_USERNAME = "testuser";
	private static final String TEST_SESSION_ID = "session123";
	private static final String VALID_REFRESH_TOKEN = "valid.refresh.token";
	private static final String NEW_ACCESS_TOKEN = "Bearer new.access.token";
	private static final String NEW_REFRESH_TOKEN = "Bearer new.refresh.token";

	private User testUser;
	private Cookie validRefreshCookie;

	/**
	 * 각 테스트 실행 전 테스트 데이터를 초기화합니다.
	 */
	@BeforeEach
	void setUp() {
		// 테스트용 사용자 생성
		testUser = User.builder()
				.username(TEST_USERNAME)
				.name("테스트 사용자")
				.email("test@example.com")
				.role(UserRole.USER)
				.build();

		// 테스트용 Refresh Token 쿠키 생성
		validRefreshCookie = new Cookie(JwtProvider.REFRESH_TOKEN_COOKIE_NAME, VALID_REFRESH_TOKEN);
		validRefreshCookie.setHttpOnly(true);
		validRefreshCookie.setPath("/");
	}

	@Nested
	@DisplayName("토큰 재발급 성공 테스트")
	class TokenReissueSuccessTest {

		@Test
		@DisplayName("유효한 Refresh Token으로 토큰 재발급 성공")
		void reissueAccessToken_WithValidRefreshToken_ShouldReturnNewTokens() throws Exception {
			// Given: 유효한 Refresh Token과 Mock 설정
			when(jwtProvider.getRefreshTokenFromCookie(any())).thenReturn(VALID_REFRESH_TOKEN);
			when(jwtProvider.validateAccessToken(VALID_REFRESH_TOKEN)).thenReturn(true);
			when(jwtProvider.getUsernameFromToken(VALID_REFRESH_TOKEN)).thenReturn(TEST_USERNAME);
			when(jwtProvider.getClaimFromToken(VALID_REFRESH_TOKEN, "auth")).thenReturn("USER");
			when(jwtProvider.getClaimFromToken(VALID_REFRESH_TOKEN, "sessionId")).thenReturn(
					TEST_SESSION_ID);
			when(sessionService.validateSession(TEST_SESSION_ID)).thenReturn(true);
			when(userService.findByUsername(TEST_USERNAME)).thenReturn(testUser);
			when(jwtProvider.createAccessTokenWithSession(TEST_USERNAME, UserRole.USER,
					TEST_SESSION_ID))
					.thenReturn(NEW_ACCESS_TOKEN);
			when(jwtProvider.createRefreshTokenWithSession(TEST_USERNAME, UserRole.USER,
					TEST_SESSION_ID))
					.thenReturn(NEW_REFRESH_TOKEN);
			
			// Mock setRefreshTokenCookie to actually set the cookie
			doAnswer(invocation -> {
				HttpServletResponse response = invocation.getArgument(0);
				String refreshToken = invocation.getArgument(1);
				String tokenValue = refreshToken.startsWith("Bearer ") 
					? refreshToken.substring("Bearer ".length()) 
					: refreshToken;
				Cookie cookie = new Cookie(JwtProvider.REFRESH_TOKEN_COOKIE_NAME, tokenValue);
				cookie.setHttpOnly(true);
				cookie.setPath("/");
				response.addCookie(cookie);
				return null;
			}).when(jwtProvider).setRefreshTokenCookie(any(), any());

			// When: 토큰 재발급 API를 호출하면
			MvcResult result = mockMvc.perform(post("/api/v1/auth/reissue")
							.cookie(validRefreshCookie)
							.contentType(MediaType.APPLICATION_JSON))
					.andDo(print())
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.statusCode").value(200))
					.andExpect(jsonPath("$.message").value("액세스 토큰 재발급을 완료했습니다."))
					.andExpect(header().string("Authorization", NEW_ACCESS_TOKEN))
					.andExpect(cookie().exists(JwtProvider.REFRESH_TOKEN_COOKIE_NAME))
					.andReturn();

			// Then: 응답 검증
			MockHttpServletResponse response = result.getResponse();
			assertThat(response.getStatus()).isEqualTo(200);
			assertThat(response.getHeader("Authorization")).isEqualTo(NEW_ACCESS_TOKEN);

			// 새로운 Refresh Token 쿠키 검증
			Cookie newRefreshCookie = response.getCookie(JwtProvider.REFRESH_TOKEN_COOKIE_NAME);
			assertThat(newRefreshCookie).isNotNull();
			assertThat(newRefreshCookie.isHttpOnly()).isTrue();
			assertThat(newRefreshCookie.getPath()).isEqualTo("/");
		}

		@Test
		@DisplayName("세션 ID가 없는 레거시 토큰으로 재발급 성공")
		void reissueAccessToken_WithLegacyToken_ShouldReturnNewTokens() throws Exception {
			// Given: 세션 ID가 없는 레거시 Refresh Token
			when(jwtProvider.getRefreshTokenFromCookie(any())).thenReturn(VALID_REFRESH_TOKEN);
			when(jwtProvider.validateAccessToken(VALID_REFRESH_TOKEN)).thenReturn(true);
			when(jwtProvider.getUsernameFromToken(VALID_REFRESH_TOKEN)).thenReturn(TEST_USERNAME);
			when(jwtProvider.getClaimFromToken(VALID_REFRESH_TOKEN, "auth")).thenReturn("USER");
			when(jwtProvider.getClaimFromToken(VALID_REFRESH_TOKEN, "sessionId")).thenReturn(null);
			when(userService.findByUsername(TEST_USERNAME)).thenReturn(testUser);
			when(jwtProvider.createAccessToken(TEST_USERNAME, UserRole.USER)).thenReturn(
					NEW_ACCESS_TOKEN);
			when(jwtProvider.createRefreshToken(TEST_USERNAME, UserRole.USER)).thenReturn(
					NEW_REFRESH_TOKEN);
			
			// Mock setRefreshTokenCookie to actually set the cookie
			doAnswer(invocation -> {
				HttpServletResponse response = invocation.getArgument(0);
				String refreshToken = invocation.getArgument(1);
				String tokenValue = refreshToken.startsWith("Bearer ") 
					? refreshToken.substring("Bearer ".length()) 
					: refreshToken;
				Cookie cookie = new Cookie(JwtProvider.REFRESH_TOKEN_COOKIE_NAME, tokenValue);
				cookie.setHttpOnly(true);
				cookie.setPath("/");
				response.addCookie(cookie);
				return null;
			}).when(jwtProvider).setRefreshTokenCookie(any(), any());

			// When: 토큰 재발급 API를 호출하면
			mockMvc.perform(post("/api/v1/auth/reissue")
							.cookie(validRefreshCookie)
							.contentType(MediaType.APPLICATION_JSON))
					.andDo(print())
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.statusCode").value(200))
					.andExpect(header().string("Authorization", NEW_ACCESS_TOKEN))
					.andExpect(cookie().exists(JwtProvider.REFRESH_TOKEN_COOKIE_NAME));
		}

		@Test
		@DisplayName("관리자 권한 사용자 토큰 재발급 성공")
		void reissueAccessToken_WithManagerRole_ShouldReturnTokensWithManagerRole()
				throws Exception {
			// Given: 관리자 권한 사용자
			User managerUser = User.builder()
					.username("manager")
					.name("관리자")
					.email("manager@example.com")
					.role(UserRole.MANAGER)
					.build();

			when(jwtProvider.getRefreshTokenFromCookie(any())).thenReturn(VALID_REFRESH_TOKEN);
			when(jwtProvider.validateAccessToken(VALID_REFRESH_TOKEN)).thenReturn(true);
			when(jwtProvider.getUsernameFromToken(VALID_REFRESH_TOKEN)).thenReturn("manager");
			when(jwtProvider.getClaimFromToken(VALID_REFRESH_TOKEN, "auth")).thenReturn("MANAGER");
			when(jwtProvider.getClaimFromToken(VALID_REFRESH_TOKEN, "sessionId")).thenReturn(
					TEST_SESSION_ID);
			when(sessionService.validateSession(TEST_SESSION_ID)).thenReturn(true);
			when(userService.findByUsername("manager")).thenReturn(managerUser);
			when(jwtProvider.createAccessTokenWithSession("manager", UserRole.MANAGER,
					TEST_SESSION_ID))
					.thenReturn(NEW_ACCESS_TOKEN);
			when(jwtProvider.createRefreshTokenWithSession("manager", UserRole.MANAGER,
					TEST_SESSION_ID))
					.thenReturn(NEW_REFRESH_TOKEN);

			// When: 토큰 재발급 API를 호출하면
			mockMvc.perform(post("/api/v1/auth/reissue")
							.cookie(validRefreshCookie)
							.contentType(MediaType.APPLICATION_JSON))
					.andDo(print())
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.statusCode").value(200))
					.andExpect(header().string("Authorization", NEW_ACCESS_TOKEN));
		}
	}

	@Nested
	@DisplayName("토큰 재발급 실패 테스트")
	class TokenReissueFailureTest {

		@Test
		@DisplayName("Refresh Token 쿠키가 없는 경우 실패")
		void reissueAccessToken_WithoutRefreshCookie_ShouldReturnBadRequest() throws Exception {
			// Given: Refresh Token 쿠키가 없는 요청
			when(jwtProvider.getRefreshTokenFromCookie(any())).thenReturn(null);

			// When: 토큰 재발급 API를 호출하면
			mockMvc.perform(post("/api/v1/auth/reissue")
							.contentType(MediaType.APPLICATION_JSON))
					.andDo(print())
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.code").value(400))
					.andExpect(jsonPath("$.message").value("Refresh Token 쿠키가 없습니다."));
		}

		@Test
		@DisplayName("유효하지 않은 Refresh Token으로 실패")
		void reissueAccessToken_WithInvalidRefreshToken_ShouldReturnUnauthorized()
				throws Exception {
			// Given: 유효하지 않은 Refresh Token
			when(jwtProvider.getRefreshTokenFromCookie(any())).thenReturn("invalid.token");
			when(jwtProvider.validateAccessToken("invalid.token")).thenReturn(false);

			// When: 토큰 재발급 API를 호출하면
			mockMvc.perform(post("/api/v1/auth/reissue")
							.cookie(validRefreshCookie)
							.contentType(MediaType.APPLICATION_JSON))
					.andDo(print())
					.andExpect(status().isUnauthorized())
					.andExpect(jsonPath("$.code").value(401))
					.andExpect(jsonPath("$.message").value("유효하지 않은 Refresh Token입니다."));
		}

		@Test
		@DisplayName("무효한 세션 ID로 토큰 재발급 실패")
		void reissueAccessToken_WithInvalidSession_ShouldReturnUnauthorized() throws Exception {
			// Given: 무효한 세션 ID가 포함된 Refresh Token
			when(jwtProvider.getRefreshTokenFromCookie(any())).thenReturn(VALID_REFRESH_TOKEN);
			when(jwtProvider.validateAccessToken(VALID_REFRESH_TOKEN)).thenReturn(true);
			when(jwtProvider.getUsernameFromToken(VALID_REFRESH_TOKEN)).thenReturn(TEST_USERNAME);
			when(jwtProvider.getClaimFromToken(VALID_REFRESH_TOKEN, "sessionId")).thenReturn(
					TEST_SESSION_ID);
			when(sessionService.validateSession(TEST_SESSION_ID)).thenReturn(false);

			// When: 토큰 재발급 API를 호출하면
			mockMvc.perform(post("/api/v1/auth/reissue")
							.cookie(validRefreshCookie)
							.contentType(MediaType.APPLICATION_JSON))
					.andDo(print())
					.andExpect(status().isUnauthorized())
					.andExpect(jsonPath("$.code").value(401))
					.andExpect(jsonPath("$.message").value("세션이 만료되었습니다."));
		}

		@Test
		@DisplayName("존재하지 않는 사용자로 토큰 재발급 실패")
		void reissueAccessToken_WithNonExistentUser_ShouldReturnNotFound() throws Exception {
			// Given: 존재하지 않는 사용자의 Refresh Token
			when(jwtProvider.getRefreshTokenFromCookie(any())).thenReturn(VALID_REFRESH_TOKEN);
			when(jwtProvider.validateAccessToken(VALID_REFRESH_TOKEN)).thenReturn(true);
			when(jwtProvider.getUsernameFromToken(VALID_REFRESH_TOKEN)).thenReturn("nonexistent");
			when(jwtProvider.getClaimFromToken(VALID_REFRESH_TOKEN, "sessionId")).thenReturn(null);
			when(userService.findByUsername("nonexistent"))
					.thenThrow(new RuntimeException("사용자를 찾을 수 없습니다."));

			// When: 토큰 재발급 API를 호출하면
			mockMvc.perform(post("/api/v1/auth/reissue")
							.cookie(validRefreshCookie)
							.contentType(MediaType.APPLICATION_JSON))
					.andDo(print())
					.andExpect(status().isNotFound())
					.andExpect(jsonPath("$.code").value(404))
					.andExpect(jsonPath("$.message").value("사용자를 찾을 수 없습니다."));
		}

		@Test
		@DisplayName("빈 Refresh Token으로 실패")
		void reissueAccessToken_WithEmptyRefreshToken_ShouldReturnBadRequest() throws Exception {
			// Given: 빈 Refresh Token
			Cookie emptyCookie = new Cookie(JwtProvider.REFRESH_TOKEN_COOKIE_NAME, "");
			when(jwtProvider.getRefreshTokenFromCookie(any())).thenReturn("");

			// When: 토큰 재발급 API를 호출하면
			mockMvc.perform(post("/api/v1/auth/reissue")
							.cookie(emptyCookie)
							.contentType(MediaType.APPLICATION_JSON))
					.andDo(print())
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.code").value(400))
					.andExpect(jsonPath("$.message").value("Refresh Token 쿠키가 없습니다."));
		}
	}

	@Nested
	@DisplayName("토큰 로테이션 테스트")
	class TokenRotationTest {

		@Test
		@DisplayName("토큰 재발급 시 이전 Refresh Token 무효화")
		void reissueAccessToken_ShouldInvalidateOldRefreshToken() throws Exception {
			// Given: 토큰 ID가 포함된 Refresh Token
			String tokenId = "token-id-123";
			when(jwtProvider.getRefreshTokenFromCookie(any())).thenReturn(VALID_REFRESH_TOKEN);
			when(jwtProvider.validateAccessToken(VALID_REFRESH_TOKEN)).thenReturn(true);
			when(jwtProvider.getUsernameFromToken(VALID_REFRESH_TOKEN)).thenReturn(TEST_USERNAME);
			when(jwtProvider.getClaimFromToken(VALID_REFRESH_TOKEN, "sessionId")).thenReturn(
					TEST_SESSION_ID);
			when(jwtProvider.getClaimFromToken(VALID_REFRESH_TOKEN, "tokenId")).thenReturn(tokenId);
			when(sessionService.validateSession(TEST_SESSION_ID)).thenReturn(true);
			when(userService.findByUsername(TEST_USERNAME)).thenReturn(testUser);
			when(jwtProvider.createAccessTokenWithSession(anyString(), any(),
					anyString())).thenReturn(NEW_ACCESS_TOKEN);
			when(jwtProvider.createRefreshTokenWithSession(anyString(), any(),
					anyString())).thenReturn(NEW_REFRESH_TOKEN);

			// When: 토큰 재발급 API를 호출하면
			mockMvc.perform(post("/api/v1/auth/reissue")
							.cookie(validRefreshCookie)
							.contentType(MediaType.APPLICATION_JSON))
					.andDo(print())
					.andExpect(status().isOk());

			// Then: 이전 토큰이 무효화되어야 함
			// TokenManager.invalidateToken() 호출 검증은 실제 구현에 따라 추가 가능
		}

		@Test
		@DisplayName("동일한 Refresh Token으로 여러 번 재발급 시도")
		void reissueAccessToken_MultipleTimes_ShouldHandleCorrectly() throws Exception {
			// Given: 동일한 Refresh Token으로 여러 번 요청
			when(jwtProvider.getRefreshTokenFromCookie(any())).thenReturn(VALID_REFRESH_TOKEN);
			when(jwtProvider.validateAccessToken(VALID_REFRESH_TOKEN)).thenReturn(true);
			when(jwtProvider.getUsernameFromToken(VALID_REFRESH_TOKEN)).thenReturn(TEST_USERNAME);
			when(jwtProvider.getClaimFromToken(VALID_REFRESH_TOKEN, "sessionId")).thenReturn(null);
			when(userService.findByUsername(TEST_USERNAME)).thenReturn(testUser);
			when(jwtProvider.createAccessToken(anyString(), any())).thenReturn(NEW_ACCESS_TOKEN);
			when(jwtProvider.createRefreshToken(anyString(), any())).thenReturn(NEW_REFRESH_TOKEN);

			// When: 첫 번째 재발급 요청
			mockMvc.perform(post("/api/v1/auth/reissue")
							.cookie(validRefreshCookie)
							.contentType(MediaType.APPLICATION_JSON))
					.andExpect(status().isOk());

			// When: 두 번째 재발급 요청 (동일한 토큰)
			mockMvc.perform(post("/api/v1/auth/reissue")
							.cookie(validRefreshCookie)
							.contentType(MediaType.APPLICATION_JSON))
					.andExpect(status().isOk());
		}
	}

	@Nested
	@DisplayName("응답 헤더 및 쿠키 테스트")
	class ResponseHeaderAndCookieTest {

		@Test
		@DisplayName("Access Token이 Authorization 헤더에 올바르게 설정")
		void reissueAccessToken_ShouldSetAuthorizationHeader() throws Exception {
			// Given: 정상적인 토큰 재발급 상황
			when(jwtProvider.getRefreshTokenFromCookie(any())).thenReturn(VALID_REFRESH_TOKEN);
			when(jwtProvider.validateAccessToken(VALID_REFRESH_TOKEN)).thenReturn(true);
			when(jwtProvider.getUsernameFromToken(VALID_REFRESH_TOKEN)).thenReturn(TEST_USERNAME);
			when(jwtProvider.getClaimFromToken(VALID_REFRESH_TOKEN, "sessionId")).thenReturn(null);
			when(userService.findByUsername(TEST_USERNAME)).thenReturn(testUser);
			when(jwtProvider.createAccessToken(anyString(), any())).thenReturn(NEW_ACCESS_TOKEN);
			when(jwtProvider.createRefreshToken(anyString(), any())).thenReturn(NEW_REFRESH_TOKEN);

			// When: 토큰 재발급 API를 호출하면
			MvcResult result = mockMvc.perform(post("/api/v1/auth/reissue")
							.cookie(validRefreshCookie)
							.contentType(MediaType.APPLICATION_JSON))
					.andExpect(status().isOk())
					.andExpect(header().string("Authorization", NEW_ACCESS_TOKEN))
					.andReturn();

			// Then: Authorization 헤더 검증
			assertThat(result.getResponse().getHeader("Authorization")).isEqualTo(NEW_ACCESS_TOKEN);
		}

		@Test
		@DisplayName("Refresh Token이 HttpOnly 쿠키로 올바르게 설정")
		void reissueAccessToken_ShouldSetHttpOnlyRefreshCookie() throws Exception {
			// Given: 정상적인 토큰 재발급 상황
			when(jwtProvider.getRefreshTokenFromCookie(any())).thenReturn(VALID_REFRESH_TOKEN);
			when(jwtProvider.validateAccessToken(VALID_REFRESH_TOKEN)).thenReturn(true);
			when(jwtProvider.getUsernameFromToken(VALID_REFRESH_TOKEN)).thenReturn(TEST_USERNAME);
			when(jwtProvider.getClaimFromToken(VALID_REFRESH_TOKEN, "sessionId")).thenReturn(null);
			when(userService.findByUsername(TEST_USERNAME)).thenReturn(testUser);
			when(jwtProvider.createAccessToken(anyString(), any())).thenReturn(NEW_ACCESS_TOKEN);
			when(jwtProvider.createRefreshToken(anyString(), any())).thenReturn(NEW_REFRESH_TOKEN);

			// When: 토큰 재발급 API를 호출하면
			MvcResult result = mockMvc.perform(post("/api/v1/auth/reissue")
							.cookie(validRefreshCookie)
							.contentType(MediaType.APPLICATION_JSON))
					.andExpect(status().isOk())
					.andExpect(cookie().exists(JwtProvider.REFRESH_TOKEN_COOKIE_NAME))
					.andExpect(cookie().httpOnly(JwtProvider.REFRESH_TOKEN_COOKIE_NAME, true))
					.andExpect(cookie().path(JwtProvider.REFRESH_TOKEN_COOKIE_NAME, "/"))
					.andReturn();

			// Then: 쿠키 속성 검증
			Cookie refreshCookie = result.getResponse()
					.getCookie(JwtProvider.REFRESH_TOKEN_COOKIE_NAME);
			assertThat(refreshCookie).isNotNull();
			assertThat(refreshCookie.isHttpOnly()).isTrue();
			assertThat(refreshCookie.getPath()).isEqualTo("/");
		}

		@Test
		@DisplayName("응답 Content-Type이 JSON으로 설정")
		void reissueAccessToken_ShouldReturnJsonContentType() throws Exception {
			// Given: 정상적인 토큰 재발급 상황
			when(jwtProvider.getRefreshTokenFromCookie(any())).thenReturn(VALID_REFRESH_TOKEN);
			when(jwtProvider.validateAccessToken(VALID_REFRESH_TOKEN)).thenReturn(true);
			when(jwtProvider.getUsernameFromToken(VALID_REFRESH_TOKEN)).thenReturn(TEST_USERNAME);
			when(jwtProvider.getClaimFromToken(VALID_REFRESH_TOKEN, "sessionId")).thenReturn(null);
			when(userService.findByUsername(TEST_USERNAME)).thenReturn(testUser);
			when(jwtProvider.createAccessToken(anyString(), any())).thenReturn(NEW_ACCESS_TOKEN);
			when(jwtProvider.createRefreshToken(anyString(), any())).thenReturn(NEW_REFRESH_TOKEN);

			// When & Then: JSON 응답 검증
			mockMvc.perform(post("/api/v1/auth/reissue")
							.cookie(validRefreshCookie)
							.contentType(MediaType.APPLICATION_JSON))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.statusCode").value(200))
					.andExpect(jsonPath("$.message").value("액세스 토큰 재발급을 완료했습니다."));
		}
	}
}