package com.commonground.be.global.infrastructure.security.oauth2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.commonground.be.config.TestSecurityConfig;
import com.commonground.be.domain.auth.facade.AuthFacade;
import com.commonground.be.domain.session.service.SessionService;
import com.commonground.be.domain.user.repository.UserRepository;
import com.commonground.be.global.infrastructure.security.jwt.JwtProvider;
import com.commonground.be.global.infrastructure.security.jwt.TokenManager;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * OAuth2 Handlers 통합 테스트
 * 
 * OAuth2SuccessHandler와 OAuth2FailureHandler의 동작을 검증
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestSecurityConfig.class)
@ActiveProfiles("test")
@DisplayName("OAuth2 Handlers 통합 테스트")
class OAuth2HandlersIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private OAuth2SuccessHandler oauth2SuccessHandler;

	@Autowired
	private OAuth2FailureHandler oauth2FailureHandler;

	        @MockitoBean
	private AuthFacade authFacade;

	        @MockitoBean
	private UserRepository userRepository;

	        @MockitoBean
	private JwtProvider jwtProvider;

	        @MockitoBean
	private TokenManager tokenManager;

	        @MockitoBean
	private SessionService sessionService;

	        @MockitoBean
	private OAuth2User oauth2User;

	private static final String FRONTEND_SUCCESS_URL = "http://localhost:3000/auth/success";
	private static final String FRONTEND_FAILURE_URL = "http://localhost:3000/auth/error";

	@BeforeEach
	void setUp() {
		// application.yml의 frontend URL 설정을 시뮬레이션
		org.springframework.test.util.ReflectionTestUtils.setField(
				oauth2SuccessHandler, "frontendSuccessUrl", FRONTEND_SUCCESS_URL);
		org.springframework.test.util.ReflectionTestUtils.setField(
				oauth2FailureHandler, "frontendFailureUrl", FRONTEND_FAILURE_URL);
	}

	@Nested
	@DisplayName("OAuth2 로그인 진입점 테스트")
	class OAuth2LoginEntryPointTest {

		@Test
		@DisplayName("Google OAuth2 로그인 진입점")
		void oauth2LoginEntryPoint_Google_ShouldRedirectToGoogleAuth() throws Exception {
			mockMvc.perform(get("/oauth2/authorization/google"))
					.andDo(print())
					.andExpect(status().is3xxRedirection())
					.andExpect(redirectedUrlPattern("https://accounts.google.com/oauth2/auth*"));
		}

		@Test
		@DisplayName("Kakao OAuth2 로그인 진입점")
		void oauth2LoginEntryPoint_Kakao_ShouldRedirectToKakaoAuth() throws Exception {
			mockMvc.perform(get("/oauth2/authorization/kakao"))
					.andDo(print())
					.andExpect(status().is3xxRedirection())
					.andExpect(redirectedUrlPattern("https://kauth.kakao.com/oauth/authorize*"));
		}
	}

	@Nested
	@DisplayName("OAuth2SuccessHandler 테스트")
	class OAuth2SuccessHandlerTest {

		@Test
		@DisplayName("OAuth2 로그인 성공 - AuthFacade 파이프라인 성공")
		void onAuthenticationSuccess_WhenFacadeSuccess_ShouldRedirectToSuccessUrl() throws Exception {
			// Given: AuthFacade가 성공 결과를 반환하도록 설정
			Map<String, Object> resultData = Map.of(
					"userId", 1L,
					"email", "test@example.com",
					"name", "테스트사용자",
					"provider", "google"
			);

			AuthFacade.AuthFlowResult successResult = AuthFacade.AuthFlowResult.success(
					"OAuth2 로그인 성공", resultData);

			when(authFacade.oauth2LoginSuccessFlow(
					any(OAuth2User.class), 
					anyString(), 
					any(HttpServletRequest.class), 
					any(HttpServletResponse.class)
			)).thenReturn(successResult);

			// When: OAuth2 성공 핸들러가 실행되면
			// 실제 OAuth2 flow는 MockMvc로 테스트하기 어려우므로 직접 handler 테스트
			org.springframework.mock.web.MockHttpServletRequest request = 
					new org.springframework.mock.web.MockHttpServletRequest();
			request.setRequestURI("/login/oauth2/code/google");
			
			org.springframework.mock.web.MockHttpServletResponse response = 
					new org.springframework.mock.web.MockHttpServletResponse();

			org.springframework.security.core.Authentication authentication = 
					org.mockito.Mockito.mock(org.springframework.security.core.Authentication.class);
			when(authentication.getPrincipal()).thenReturn(oauth2User);

			oauth2SuccessHandler.onAuthenticationSuccess(request, response, authentication);

			// Then: 성공 URL로 리다이렉트
			String redirectUrl = response.getRedirectedUrl();
			assertThat(redirectUrl).startsWith(FRONTEND_SUCCESS_URL);
			assertThat(redirectUrl).contains("success=true");
			assertThat(redirectUrl).contains("provider=google");

			// AuthFacade 호출 검증
			verify(authFacade).oauth2LoginSuccessFlow(
					eq(oauth2User), 
					eq("google"), 
					eq(request), 
					eq(response)
			);
		}

		@Test
		@DisplayName("OAuth2 로그인 성공 - AuthFacade 파이프라인 실패")
		void onAuthenticationSuccess_WhenFacadeFails_ShouldRedirectToErrorUrl() throws Exception {
			// Given: AuthFacade가 실패 결과를 반환하도록 설정
			AuthFacade.AuthFlowResult failureResult = AuthFacade.AuthFlowResult.failure(
					"사용자 생성 실패", new RuntimeException("Database error"));

			when(authFacade.oauth2LoginSuccessFlow(
					any(OAuth2User.class), 
					anyString(), 
					any(HttpServletRequest.class), 
					any(HttpServletResponse.class)
			)).thenReturn(failureResult);

			// When: OAuth2 성공 핸들러가 실행되면
			org.springframework.mock.web.MockHttpServletRequest request = 
					new org.springframework.mock.web.MockHttpServletRequest();
			request.setRequestURI("/login/oauth2/code/google");
			
			org.springframework.mock.web.MockHttpServletResponse response = 
					new org.springframework.mock.web.MockHttpServletResponse();

			org.springframework.security.core.Authentication authentication = 
					org.mockito.Mockito.mock(org.springframework.security.core.Authentication.class);
			when(authentication.getPrincipal()).thenReturn(oauth2User);

			oauth2SuccessHandler.onAuthenticationSuccess(request, response, authentication);

			// Then: 에러 URL로 리다이렉트
			String redirectUrl = response.getRedirectedUrl();
			assertThat(redirectUrl).startsWith(FRONTEND_FAILURE_URL);
			assertThat(redirectUrl).contains("error=login_failed");
			assertThat(redirectUrl).contains("message=사용자 생성 실패");
		}

		@Test
		@DisplayName("Kakao OAuth2 로그인 성공 처리")
		void onAuthenticationSuccess_Kakao_ShouldProcessCorrectly() throws Exception {
			// Given: Kakao OAuth2 성공 시나리오
			Map<String, Object> resultData = Map.of(
					"userId", 1L,
					"email", "test@kakao.com",
					"name", "카카오사용자",
					"provider", "kakao"
			);

			AuthFacade.AuthFlowResult successResult = AuthFacade.AuthFlowResult.success(
					"OAuth2 로그인 성공", resultData);

			when(authFacade.oauth2LoginSuccessFlow(
					any(OAuth2User.class), anyString(), any(), any())).thenReturn(successResult);

			// When: Kakao OAuth2 성공 핸들러 실행
			org.springframework.mock.web.MockHttpServletRequest request = 
					new org.springframework.mock.web.MockHttpServletRequest();
			request.setRequestURI("/login/oauth2/code/kakao");
			
			org.springframework.mock.web.MockHttpServletResponse response = 
					new org.springframework.mock.web.MockHttpServletResponse();

			org.springframework.security.core.Authentication authentication = 
					org.mockito.Mockito.mock(org.springframework.security.core.Authentication.class);
			when(authentication.getPrincipal()).thenReturn(oauth2User);

			oauth2SuccessHandler.onAuthenticationSuccess(request, response, authentication);

			// Then: 성공 URL로 리다이렉트 (provider=kakao)
			String redirectUrl = response.getRedirectedUrl();
			assertThat(redirectUrl).contains("provider=kakao");

			// AuthFacade가 kakao provider로 호출되었는지 검증
			verify(authFacade).oauth2LoginSuccessFlow(
					eq(oauth2User), eq("kakao"), eq(request), eq(response));
		}
	}

	@Nested
	@DisplayName("OAuth2FailureHandler 테스트")
	class OAuth2FailureHandlerTest {

		@Test
		@DisplayName("OAuth2 로그인 실패 - 사용자 취소")
		void onAuthenticationFailure_UserCancelled_ShouldRedirectToErrorUrl() throws Exception {
			// Given: 사용자가 OAuth2 승인을 취소한 경우
			org.springframework.mock.web.MockHttpServletRequest request = 
					new org.springframework.mock.web.MockHttpServletRequest();
			request.setParameter("error", "access_denied");
			request.setParameter("error_description", "User cancelled the authorization");
			
			org.springframework.mock.web.MockHttpServletResponse response = 
					new org.springframework.mock.web.MockHttpServletResponse();

			org.springframework.security.oauth2.core.OAuth2Error error = 
					new org.springframework.security.oauth2.core.OAuth2Error(
							"access_denied", "User cancelled the authorization", null);
			org.springframework.security.oauth2.core.OAuth2AuthenticationException exception = 
					new org.springframework.security.oauth2.core.OAuth2AuthenticationException(error);

			// When: OAuth2 실패 핸들러가 실행되면
			oauth2FailureHandler.onAuthenticationFailure(request, response, exception);

			// Then: 에러 URL로 리다이렉트
			String redirectUrl = response.getRedirectedUrl();
			assertThat(redirectUrl).startsWith(FRONTEND_FAILURE_URL);
			assertThat(redirectUrl).contains("error=oauth2_failed");
			assertThat(redirectUrl).contains("message=User cancelled the authorization");
		}

		@Test
		@DisplayName("OAuth2 로그인 실패 - 일반적인 인증 오류")
		void onAuthenticationFailure_GeneralError_ShouldRedirectToErrorUrl() throws Exception {
			// Given: 일반적인 OAuth2 인증 오류
			org.springframework.mock.web.MockHttpServletRequest request = 
					new org.springframework.mock.web.MockHttpServletRequest();
			
			org.springframework.mock.web.MockHttpServletResponse response = 
					new org.springframework.mock.web.MockHttpServletResponse();

			org.springframework.security.oauth2.core.OAuth2Error error = 
					new org.springframework.security.oauth2.core.OAuth2Error(
							"invalid_request", "OAuth2 authentication failed", null);
			org.springframework.security.oauth2.core.OAuth2AuthenticationException exception = 
					new org.springframework.security.oauth2.core.OAuth2AuthenticationException(error);

			// When: OAuth2 실패 핸들러가 실행되면
			oauth2FailureHandler.onAuthenticationFailure(request, response, exception);

			// Then: 에러 URL로 리다이렉트
			String redirectUrl = response.getRedirectedUrl();
			assertThat(redirectUrl).startsWith(FRONTEND_FAILURE_URL);
			assertThat(redirectUrl).contains("error=oauth2_failed");
			assertThat(redirectUrl).contains("message=OAuth2 authentication failed");
		}

		@Test
		@DisplayName("OAuth2 로그인 실패 - 네트워크 오류")
		void onAuthenticationFailure_NetworkError_ShouldRedirectToErrorUrl() throws Exception {
			// Given: 네트워크 관련 오류
			org.springframework.mock.web.MockHttpServletRequest request = 
					new org.springframework.mock.web.MockHttpServletRequest();
			
			org.springframework.mock.web.MockHttpServletResponse response = 
					new org.springframework.mock.web.MockHttpServletResponse();

			org.springframework.security.authentication.AuthenticationServiceException networkException = 
					new org.springframework.security.authentication.AuthenticationServiceException("Connection timeout");

			// When: OAuth2 실패 핸들러가 실행되면
			oauth2FailureHandler.onAuthenticationFailure(request, response, networkException);

			// Then: 에러 URL로 리다이렉트
			String redirectUrl = response.getRedirectedUrl();
			assertThat(redirectUrl).startsWith(FRONTEND_FAILURE_URL);
			assertThat(redirectUrl).contains("error=oauth2_failed");
			assertThat(redirectUrl).contains("message=Connection timeout");
		}
	}

	@Nested
	@DisplayName("RegistrationId 추출 테스트")
	class RegistrationIdExtractionTest {

		@Test
		@DisplayName("Google OAuth2 callback URL에서 registrationId 추출")
		void extractRegistrationId_GoogleCallback_ShouldReturnGoogle() {
			// Given: Google OAuth2 callback URL
			org.springframework.mock.web.MockHttpServletRequest request = 
					new org.springframework.mock.web.MockHttpServletRequest();
			request.setRequestURI("/login/oauth2/code/google");
			
			org.springframework.mock.web.MockHttpServletResponse response = 
					new org.springframework.mock.web.MockHttpServletResponse();

			AuthFacade.AuthFlowResult successResult = AuthFacade.AuthFlowResult.success(
					"OAuth2 로그인 성공", Map.of());
			when(authFacade.oauth2LoginSuccessFlow(any(), eq("google"), any(), any()))
					.thenReturn(successResult);

			org.springframework.security.core.Authentication authentication = 
					org.mockito.Mockito.mock(org.springframework.security.core.Authentication.class);
			when(authentication.getPrincipal()).thenReturn(oauth2User);

			// When: 성공 핸들러 실행
			try {
				oauth2SuccessHandler.onAuthenticationSuccess(request, response, authentication);
			} catch (Exception e) {
				// 테스트 환경에서 발생할 수 있는 예외 무시
			}

			// Then: google registrationId가 올바르게 추출되어 AuthFacade 호출
			verify(authFacade).oauth2LoginSuccessFlow(any(), eq("google"), any(), any());
		}

		@Test
		@DisplayName("Kakao OAuth2 callback URL에서 registrationId 추출")
		void extractRegistrationId_KakaoCallback_ShouldReturnKakao() {
			// Given: Kakao OAuth2 callback URL
			org.springframework.mock.web.MockHttpServletRequest request = 
					new org.springframework.mock.web.MockHttpServletRequest();
			request.setRequestURI("/login/oauth2/code/kakao");
			
			org.springframework.mock.web.MockHttpServletResponse response = 
					new org.springframework.mock.web.MockHttpServletResponse();

			AuthFacade.AuthFlowResult successResult = AuthFacade.AuthFlowResult.success(
					"OAuth2 로그인 성공", Map.of());
			when(authFacade.oauth2LoginSuccessFlow(any(), eq("kakao"), any(), any()))
					.thenReturn(successResult);

			org.springframework.security.core.Authentication authentication = 
					org.mockito.Mockito.mock(org.springframework.security.core.Authentication.class);
			when(authentication.getPrincipal()).thenReturn(oauth2User);

			// When: 성공 핸들러 실행
			try {
				oauth2SuccessHandler.onAuthenticationSuccess(request, response, authentication);
			} catch (Exception e) {
				// 테스트 환경에서 발생할 수 있는 예외 무시
			}

			// Then: kakao registrationId가 올바르게 추출되어 AuthFacade 호출
			verify(authFacade).oauth2LoginSuccessFlow(any(), eq("kakao"), any(), any());
		}
	}
}