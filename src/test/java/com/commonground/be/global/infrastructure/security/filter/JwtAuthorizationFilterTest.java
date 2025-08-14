package com.commonground.be.global.infrastructure.security.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.commonground.be.domain.session.service.SessionService;
import com.commonground.be.global.domain.security.AdminUserDetails;
import com.commonground.be.global.infrastructure.security.admin.AdminTokenValidator;
import com.commonground.be.global.infrastructure.security.jwt.JwtProvider;
import com.commonground.be.global.infrastructure.security.jwt.TokenManager;
import com.commonground.be.global.infrastructure.security.service.CustomUserDetailsService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
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
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.context.ActiveProfiles;

/**
 * JwtAuthorizationFilter 단위 테스트 클래스
 * <p>
 * 이 테스트는 JwtAuthorizationFilter의 JWT 토큰 기반 인증/인가 로직을 검증합니다. Spring Security 필터 체인에서 JWT 토큰 검증, 세션
 * 검증, 관리자 토큰 검증 등의 핵심 보안 기능들을 테스트합니다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("JwtAuthorizationFilter 단위 테스트")
@ActiveProfiles("test")
class JwtAuthorizationFilterTest {

	@InjectMocks
	private JwtAuthorizationFilter jwtAuthorizationFilter;

	@Mock
	private JwtProvider jwtProvider;

	@Mock
	private SessionService sessionService;

	@Mock
	private TokenManager tokenManager;

	@Mock
	private AdminTokenValidator adminTokenValidator;

	@Mock
	private CustomUserDetailsService userDetailsService;

	@Mock
	private FilterChain filterChain;

	private MockHttpServletRequest request;
	private MockHttpServletResponse response;
	private UserDetails testUserDetails;

	private static final String TEST_TOKEN = "test.jwt.token";
	private static final String TEST_USERNAME = "testuser";
	private static final String TEST_SESSION_ID = "session123";

	/**
	 * 각 테스트 실행 전 Mock 객체들을 초기화합니다.
	 */
	@BeforeEach
	void setUp() {
		request = new MockHttpServletRequest();
		response = new MockHttpServletResponse();

		// SecurityContext 초기화
		SecurityContextHolder.clearContext();

		// 테스트용 UserDetails 생성
		testUserDetails = User.builder()
				.username(TEST_USERNAME)
				.password("password")
				.authorities("ROLE_USER")
				.build();

		// StringWriter를 통한 response writer 설정
		try {
			StringWriter stringWriter = new StringWriter();
			PrintWriter printWriter = new PrintWriter(stringWriter);
			when(response.getWriter()).thenReturn(printWriter);
		} catch (IOException e) {
			// Mock에서는 발생하지 않음
		}
	}

	@Nested
	@DisplayName("공개 경로 테스트")
	class PublicPathTest {

		@Test
		@DisplayName("소셜 로그인 경로는 인증 없이 통과")
		void publicPath_SocialLoginPath_ShouldPassWithoutAuthentication()
				throws ServletException, IOException {
			// Given: 소셜 로그인 경로 요청
			request.setRequestURI("/api/v1/auth/social/google");

			// When: 필터를 통과하면
			jwtAuthorizationFilter.doFilterInternal(request, response, filterChain);

			// Then: 인증 과정 없이 필터 체인이 계속되어야 함
			verify(filterChain).doFilter(request, response);
			verify(jwtProvider, never()).getAccessTokenFromHeader(any());
			assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
		}

		@Test
		@DisplayName("토큰 재발급 경로는 인증 없이 통과")
		void publicPath_TokenReissuePath_ShouldPassWithoutAuthentication()
				throws ServletException, IOException {
			// Given: 토큰 재발급 경로 요청
			request.setRequestURI("/api/v1/auth/reissue");

			// When: 필터를 통과하면
			jwtAuthorizationFilter.doFilterInternal(request, response, filterChain);

			// Then: 인증 과정 없이 필터 체인이 계속되어야 함
			verify(filterChain).doFilter(request, response);
			verify(jwtProvider, never()).getAccessTokenFromHeader(any());
			assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
		}

		@Test
		@DisplayName("헬스체크 경로는 인증 없이 통과")
		void publicPath_HealthCheckPath_ShouldPassWithoutAuthentication()
				throws ServletException, IOException {
			// Given: 헬스체크 경로 요청
			request.setRequestURI("/health");

			// When: 필터를 통과하면
			jwtAuthorizationFilter.doFilterInternal(request, response, filterChain);

			// Then: 인증 과정 없이 필터 체인이 계속되어야 함
			verify(filterChain).doFilter(request, response);
			verify(jwtProvider, never()).getAccessTokenFromHeader(any());
		}

		@Test
		@DisplayName("정적 리소스 경로는 인증 없이 통과")
		void publicPath_StaticResourcePath_ShouldPassWithoutAuthentication()
				throws ServletException, IOException {
			// Given: 정적 리소스 경로 요청
			request.setRequestURI("/static/css/style.css");

			// When: 필터를 통과하면
			jwtAuthorizationFilter.doFilterInternal(request, response, filterChain);

			// Then: 인증 과정 없이 필터 체인이 계속되어야 함
			verify(filterChain).doFilter(request, response);
			verify(jwtProvider, never()).getAccessTokenFromHeader(any());
		}

		@Test
		@DisplayName("레거시 OAuth 경로도 통과하되 경고 로그")
		void publicPath_LegacyOAuthPath_ShouldPassWithWarning()
				throws ServletException, IOException {
			// Given: 레거시 OAuth 경로 요청
			request.setRequestURI("/v1/users/oauth/google");

			// When: 필터를 통과하면
			jwtAuthorizationFilter.doFilterInternal(request, response, filterChain);

			// Then: 인증 과정 없이 필터 체인이 계속되어야 함
			verify(filterChain).doFilter(request, response);
			verify(jwtProvider, never()).getAccessTokenFromHeader(any());
		}
	}

	@Nested
	@DisplayName("관리자 토큰 인증 테스트")
	class AdminTokenAuthenticationTest {

		@Test
		@DisplayName("유효한 관리자 토큰으로 인증 성공")
		void adminTokenAuthentication_WithValidToken_ShouldSetAdminAuthentication()
				throws ServletException, IOException {
			// Given: 관리자 토큰이 포함된 요청
			request.setRequestURI("/api/v1/admin/users");
			request.addHeader("Authorization", "Bearer " + TEST_TOKEN);
			when(jwtProvider.getAccessTokenFromHeader(request)).thenReturn(TEST_TOKEN);
			when(adminTokenValidator.isValidAdminToken(TEST_TOKEN)).thenReturn(true);

			// When: 필터를 통과하면
			jwtAuthorizationFilter.doFilterInternal(request, response, filterChain);

			// Then: 관리자 인증이 설정되어야 함
			Authentication auth = SecurityContextHolder.getContext().getAuthentication();
			assertThat(auth).isNotNull();
			assertThat(auth.getPrincipal()).isInstanceOf(AdminUserDetails.class);
			assertThat(auth.getAuthorities()).hasSize(1);

			verify(adminTokenValidator).isValidAdminToken(TEST_TOKEN);
			verify(filterChain).doFilter(request, response);
		}

		@Test
		@DisplayName("유효하지 않은 관리자 토큰으로 JWT 토큰 검증 진행")
		void adminTokenAuthentication_WithInvalidToken_ShouldProceedToJwtValidation()
				throws ServletException, IOException {
			// Given: 유효하지 않은 관리자 토큰
			request.setRequestURI("/api/v1/users/profile");
			request.addHeader("Authorization", "Bearer " + TEST_TOKEN);
			when(jwtProvider.getAccessTokenFromHeader(request)).thenReturn(TEST_TOKEN);
			when(adminTokenValidator.isValidAdminToken(TEST_TOKEN)).thenReturn(false);
			when(jwtProvider.validateAccessToken(TEST_TOKEN)).thenReturn(true);
			when(jwtProvider.getUsernameFromToken(TEST_TOKEN)).thenReturn(TEST_USERNAME);
			when(jwtProvider.getClaimFromToken(TEST_TOKEN, "sessionId")).thenReturn(null);
			when(userDetailsService.loadUserByUsername(TEST_USERNAME)).thenReturn(testUserDetails);

			// When: 필터를 통과하면
			jwtAuthorizationFilter.doFilterInternal(request, response, filterChain);

			// Then: JWT 토큰 검증이 진행되어야 함
			verify(adminTokenValidator).isValidAdminToken(TEST_TOKEN);
			verify(jwtProvider).validateAccessToken(TEST_TOKEN);
			verify(userDetailsService).loadUserByUsername(TEST_USERNAME);
			verify(filterChain).doFilter(request, response);
		}
	}

	@Nested
	@DisplayName("JWT 토큰 인증 테스트")
	class JwtTokenAuthenticationTest {

		@Test
		@DisplayName("유효한 JWT 토큰으로 인증 성공")
		void jwtAuthentication_WithValidToken_ShouldSetAuthentication()
				throws ServletException, IOException {
			// Given: 유효한 JWT 토큰
			request.setRequestURI("/api/v1/users/profile");
			request.addHeader("Authorization", "Bearer " + TEST_TOKEN);
			when(jwtProvider.getAccessTokenFromHeader(request)).thenReturn(TEST_TOKEN);
			when(adminTokenValidator.isValidAdminToken(TEST_TOKEN)).thenReturn(false);
			when(jwtProvider.validateAccessToken(TEST_TOKEN)).thenReturn(true);
			when(jwtProvider.getUsernameFromToken(TEST_TOKEN)).thenReturn(TEST_USERNAME);
			when(jwtProvider.getClaimFromToken(TEST_TOKEN, "sessionId")).thenReturn(null);
			when(userDetailsService.loadUserByUsername(TEST_USERNAME)).thenReturn(testUserDetails);

			// When: 필터를 통과하면
			jwtAuthorizationFilter.doFilterInternal(request, response, filterChain);

			// Then: JWT 인증이 설정되어야 함
			Authentication auth = SecurityContextHolder.getContext().getAuthentication();
			assertThat(auth).isNotNull();
			assertThat(auth.getPrincipal()).isEqualTo(testUserDetails);
			assertThat(auth.getName()).isEqualTo(TEST_USERNAME);

			verify(jwtProvider).validateAccessToken(TEST_TOKEN);
			verify(userDetailsService).loadUserByUsername(TEST_USERNAME);
			verify(filterChain).doFilter(request, response);
		}

		@Test
		@DisplayName("세션 ID가 포함된 JWT 토큰으로 인증 성공")
		void jwtAuthentication_WithSessionToken_ShouldValidateSessionAndSetAuthentication()
				throws ServletException, IOException {
			// Given: 세션 ID가 포함된 JWT 토큰
			request.setRequestURI("/api/v1/users/profile");
			request.addHeader("Authorization", "Bearer " + TEST_TOKEN);
			when(jwtProvider.getAccessTokenFromHeader(request)).thenReturn(TEST_TOKEN);
			when(adminTokenValidator.isValidAdminToken(TEST_TOKEN)).thenReturn(false);
			when(jwtProvider.validateAccessToken(TEST_TOKEN)).thenReturn(true);
			when(jwtProvider.getUsernameFromToken(TEST_TOKEN)).thenReturn(TEST_USERNAME);
			when(jwtProvider.getClaimFromToken(TEST_TOKEN, "sessionId")).thenReturn(
					TEST_SESSION_ID);
			when(sessionService.validateSession(TEST_SESSION_ID)).thenReturn(true);
			doNothing().when(sessionService).updateSessionAccess(TEST_SESSION_ID);
			when(userDetailsService.loadUserByUsernameWithSession(TEST_USERNAME,
					TEST_SESSION_ID)).thenReturn(testUserDetails);

			// When: 필터를 통과하면
			jwtAuthorizationFilter.doFilterInternal(request, response, filterChain);

			// Then: 세션 검증과 JWT 인증이 모두 성공해야 함
			Authentication auth = SecurityContextHolder.getContext().getAuthentication();
			assertThat(auth).isNotNull();
			assertThat(auth.getPrincipal()).isEqualTo(testUserDetails);

			verify(sessionService).validateSession(TEST_SESSION_ID);
			verify(sessionService).updateSessionAccess(TEST_SESSION_ID);
			verify(userDetailsService).loadUserByUsernameWithSession(TEST_USERNAME,
					TEST_SESSION_ID);
			verify(filterChain).doFilter(request, response);
		}

		@Test
		@DisplayName("유효하지 않은 JWT 토큰으로 인증 실패")
		void jwtAuthentication_WithInvalidToken_ShouldReturnUnauthorized()
				throws ServletException, IOException {
			// Given: 유효하지 않은 JWT 토큰
			request.setRequestURI("/api/v1/users/profile");
			request.addHeader("Authorization", "Bearer " + TEST_TOKEN);
			when(jwtProvider.getAccessTokenFromHeader(request)).thenReturn(TEST_TOKEN);
			when(adminTokenValidator.isValidAdminToken(TEST_TOKEN)).thenReturn(false);
			when(jwtProvider.validateAccessToken(TEST_TOKEN)).thenReturn(false);

			// When: 필터를 통과하면
			jwtAuthorizationFilter.doFilterInternal(request, response, filterChain);

			// Then: 401 Unauthorized 응답이 반환되어야 함
			assertThat(response.getStatus()).isEqualTo(401);
			assertThat(response.getContentType()).isEqualTo("application/json");
			verify(filterChain, never()).doFilter(request, response);
			assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
		}

		@Test
		@DisplayName("토큰 버전이 무효한 경우 인증 실패")
		void jwtAuthentication_WithInvalidTokenVersion_ShouldReturnUnauthorized()
				throws ServletException, IOException {
			// Given: 토큰 버전이 무효한 JWT 토큰
			request.setRequestURI("/api/v1/users/profile");
			request.addHeader("Authorization", "Bearer " + TEST_TOKEN);
			when(jwtProvider.getAccessTokenFromHeader(request)).thenReturn(TEST_TOKEN);
			when(adminTokenValidator.isValidAdminToken(TEST_TOKEN)).thenReturn(false);
			when(jwtProvider.validateAccessToken(TEST_TOKEN)).thenReturn(true);
			when(jwtProvider.getUsernameFromToken(TEST_TOKEN)).thenReturn(TEST_USERNAME);
			when(jwtProvider.getClaimFromToken(TEST_TOKEN, "tokenVersion")).thenReturn("123");
			when(tokenManager.isTokenVersionValid(TEST_USERNAME, 123L)).thenReturn(false);

			// When: 필터를 통과하면
			jwtAuthorizationFilter.doFilterInternal(request, response, filterChain);

			// Then: 401 Unauthorized 응답이 반환되어야 함
			assertThat(response.getStatus()).isEqualTo(401);
			verify(tokenManager).isTokenVersionValid(TEST_USERNAME, 123L);
			verify(filterChain, never()).doFilter(request, response);
		}

		@Test
		@DisplayName("무효한 세션 ID로 인증 실패")
		void jwtAuthentication_WithInvalidSession_ShouldReturnUnauthorized()
				throws ServletException, IOException {
			// Given: 무효한 세션 ID가 포함된 JWT 토큰
			request.setRequestURI("/api/v1/users/profile");
			request.addHeader("Authorization", "Bearer " + TEST_TOKEN);
			when(jwtProvider.getAccessTokenFromHeader(request)).thenReturn(TEST_TOKEN);
			when(adminTokenValidator.isValidAdminToken(TEST_TOKEN)).thenReturn(false);
			when(jwtProvider.validateAccessToken(TEST_TOKEN)).thenReturn(true);
			when(jwtProvider.getUsernameFromToken(TEST_TOKEN)).thenReturn(TEST_USERNAME);
			when(jwtProvider.getClaimFromToken(TEST_TOKEN, "sessionId")).thenReturn(
					TEST_SESSION_ID);
			when(sessionService.validateSession(TEST_SESSION_ID)).thenReturn(false);

			// When: 필터를 통과하면
			jwtAuthorizationFilter.doFilterInternal(request, response, filterChain);

			// Then: 401 Unauthorized 응답이 반환되어야 함
			assertThat(response.getStatus()).isEqualTo(401);
			verify(sessionService).validateSession(TEST_SESSION_ID);
			verify(sessionService, never()).updateSessionAccess(anyString());
			verify(filterChain, never()).doFilter(request, response);
		}
	}

	@Nested
	@DisplayName("토큰 없는 요청 테스트")
	class NoTokenRequestTest {

		@Test
		@DisplayName("토큰 없는 요청은 인증 없이 통과")
		void noToken_ShouldPassWithoutAuthentication() throws ServletException, IOException {
			// Given: 토큰이 없는 요청 (보호된 경로)
			request.setRequestURI("/api/v1/users/profile");
			when(jwtProvider.getAccessTokenFromHeader(request)).thenReturn(null);

			// When: 필터를 통과하면
			jwtAuthorizationFilter.doFilterInternal(request, response, filterChain);

			// Then: 인증 없이 필터 체인이 계속되어야 함 (보안은 다른 필터에서 처리)
			verify(filterChain).doFilter(request, response);
			assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
		}

		@Test
		@DisplayName("빈 Authorization 헤더는 토큰 없는 것으로 처리")
		void emptyAuthorizationHeader_ShouldTreatAsNoToken() throws ServletException, IOException {
			// Given: 빈 Authorization 헤더
			request.setRequestURI("/api/v1/users/profile");
			request.addHeader("Authorization", "");
			when(jwtProvider.getAccessTokenFromHeader(request)).thenReturn("");

			// When: 필터를 통과하면
			jwtAuthorizationFilter.doFilterInternal(request, response, filterChain);

			// Then: 인증 없이 필터 체인이 계속되어야 함
			verify(filterChain).doFilter(request, response);
			assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
		}
	}

	@Nested
	@DisplayName("예외 처리 테스트")
	class ExceptionHandlingTest {

		@Test
		@DisplayName("JWT 토큰 파싱 중 예외 발생 시 인증 실패")
		void jwtTokenParsing_WithException_ShouldFailAuthentication()
				throws ServletException, IOException {
			// Given: JWT 토큰 파싱 중 예외가 발생하는 상황
			request.setRequestURI("/api/v1/users/profile");
			request.addHeader("Authorization", "Bearer " + TEST_TOKEN);
			when(jwtProvider.getAccessTokenFromHeader(request)).thenReturn(TEST_TOKEN);
			when(adminTokenValidator.isValidAdminToken(TEST_TOKEN)).thenReturn(false);
			when(jwtProvider.validateAccessToken(TEST_TOKEN)).thenReturn(true);
			when(jwtProvider.getUsernameFromToken(TEST_TOKEN)).thenThrow(
					new RuntimeException("Token parsing error"));

			// When: 필터를 통과하면
			jwtAuthorizationFilter.doFilterInternal(request, response, filterChain);

			// Then: 401 Unauthorized 응답이 반환되어야 함
			assertThat(response.getStatus()).isEqualTo(401);
			verify(filterChain, never()).doFilter(request, response);
		}

		@Test
		@DisplayName("UserDetailsService 호출 중 예외 발생 시 처리")
		void userDetailsService_WithException_ShouldHandleGracefully()
				throws ServletException, IOException {
			// Given: UserDetailsService에서 예외가 발생하는 상황
			request.setRequestURI("/api/v1/users/profile");
			request.addHeader("Authorization", "Bearer " + TEST_TOKEN);
			when(jwtProvider.getAccessTokenFromHeader(request)).thenReturn(TEST_TOKEN);
			when(adminTokenValidator.isValidAdminToken(TEST_TOKEN)).thenReturn(false);
			when(jwtProvider.validateAccessToken(TEST_TOKEN)).thenReturn(true);
			when(jwtProvider.getUsernameFromToken(TEST_TOKEN)).thenReturn(TEST_USERNAME);
			when(jwtProvider.getClaimFromToken(TEST_TOKEN, "sessionId")).thenReturn(null);
			when(userDetailsService.loadUserByUsername(TEST_USERNAME))
					.thenThrow(new RuntimeException("User not found"));

			// When: 필터를 통과하면
			jwtAuthorizationFilter.doFilterInternal(request, response, filterChain);

			// Then: 인증이 설정되지 않지만 필터 체인은 계속되어야 함
			assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
			verify(filterChain).doFilter(request, response);
		}

		@Test
		@DisplayName("토큰 버전 파싱 중 NumberFormatException 처리")
		void tokenVersionParsing_WithNumberFormatException_ShouldFailAuthentication()
				throws ServletException, IOException {
			// Given: 토큰 버전이 숫자가 아닌 경우
			request.setRequestURI("/api/v1/users/profile");
			request.addHeader("Authorization", "Bearer " + TEST_TOKEN);
			when(jwtProvider.getAccessTokenFromHeader(request)).thenReturn(TEST_TOKEN);
			when(adminTokenValidator.isValidAdminToken(TEST_TOKEN)).thenReturn(false);
			when(jwtProvider.validateAccessToken(TEST_TOKEN)).thenReturn(true);
			when(jwtProvider.getUsernameFromToken(TEST_TOKEN)).thenReturn(TEST_USERNAME);
			when(jwtProvider.getClaimFromToken(TEST_TOKEN, "tokenVersion")).thenReturn(
					"invalid-number");

			// When: 필터를 통과하면
			jwtAuthorizationFilter.doFilterInternal(request, response, filterChain);

			// Then: 401 Unauthorized 응답이 반환되어야 함
			assertThat(response.getStatus()).isEqualTo(401);
			verify(filterChain, never()).doFilter(request, response);
		}
	}

	@Nested
	@DisplayName("레거시 토큰 지원 테스트")
	class LegacyTokenSupportTest {

		@Test
		@DisplayName("세션 ID가 없는 레거시 토큰도 인증 성공")
		void legacyToken_WithoutSessionId_ShouldAuthenticate()
				throws ServletException, IOException {
			// Given: 세션 ID가 없는 레거시 JWT 토큰
			request.setRequestURI("/api/v1/users/profile");
			request.addHeader("Authorization", "Bearer " + TEST_TOKEN);
			when(jwtProvider.getAccessTokenFromHeader(request)).thenReturn(TEST_TOKEN);
			when(adminTokenValidator.isValidAdminToken(TEST_TOKEN)).thenReturn(false);
			when(jwtProvider.validateAccessToken(TEST_TOKEN)).thenReturn(true);
			when(jwtProvider.getUsernameFromToken(TEST_TOKEN)).thenReturn(TEST_USERNAME);
			when(jwtProvider.getClaimFromToken(TEST_TOKEN, "sessionId")).thenReturn(null);
			when(jwtProvider.getClaimFromToken(TEST_TOKEN, "tokenVersion")).thenReturn(null);
			when(userDetailsService.loadUserByUsername(TEST_USERNAME)).thenReturn(testUserDetails);

			// When: 필터를 통과하면
			jwtAuthorizationFilter.doFilterInternal(request, response, filterChain);

			// Then: 레거시 토큰으로도 인증이 성공해야 함
			Authentication auth = SecurityContextHolder.getContext().getAuthentication();
			assertThat(auth).isNotNull();
			assertThat(auth.getPrincipal()).isEqualTo(testUserDetails);

			verify(userDetailsService).loadUserByUsername(TEST_USERNAME);
			verify(sessionService, never()).validateSession(anyString());
			verify(filterChain).doFilter(request, response);
		}

		@Test
		@DisplayName("토큰 버전이 없는 레거시 토큰 처리")
		void legacyToken_WithoutTokenVersion_ShouldSkipVersionValidation()
				throws ServletException, IOException {
			// Given: 토큰 버전이 없는 레거시 JWT 토큰
			request.setRequestURI("/api/v1/users/profile");
			request.addHeader("Authorization", "Bearer " + TEST_TOKEN);
			when(jwtProvider.getAccessTokenFromHeader(request)).thenReturn(TEST_TOKEN);
			when(adminTokenValidator.isValidAdminToken(TEST_TOKEN)).thenReturn(false);
			when(jwtProvider.validateAccessToken(TEST_TOKEN)).thenReturn(true);
			when(jwtProvider.getUsernameFromToken(TEST_TOKEN)).thenReturn(TEST_USERNAME);
			when(jwtProvider.getClaimFromToken(TEST_TOKEN, "tokenVersion")).thenReturn(null);
			when(jwtProvider.getClaimFromToken(TEST_TOKEN, "sessionId")).thenReturn(null);
			when(userDetailsService.loadUserByUsername(TEST_USERNAME)).thenReturn(testUserDetails);

			// When: 필터를 통과하면
			jwtAuthorizationFilter.doFilterInternal(request, response, filterChain);

			// Then: 토큰 버전 검증 없이 인증이 성공해야 함
			Authentication auth = SecurityContextHolder.getContext().getAuthentication();
			assertThat(auth).isNotNull();

			verify(tokenManager, never()).isTokenVersionValid(anyString(), any());
			verify(filterChain).doFilter(request, response);
		}
	}
}