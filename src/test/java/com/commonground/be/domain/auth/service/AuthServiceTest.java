package com.commonground.be.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.commonground.be.domain.auth.dto.request.LogoutRequest;
import com.commonground.be.domain.auth.dto.request.WithdrawRequest;
import com.commonground.be.domain.auth.dto.response.AuthResponse;
import com.commonground.be.domain.auth.dto.response.UserInfoResponse;
import com.commonground.be.domain.session.service.SessionService;
import com.commonground.be.domain.user.entity.User;
import com.commonground.be.domain.user.service.SocialUserServiceInterface;
import com.commonground.be.domain.user.utils.UserRole;
import com.commonground.be.global.application.exception.AuthExceptions;
import com.commonground.be.global.application.exception.CommonException;
import com.commonground.be.global.application.response.ResponseExceptionEnum;
import com.commonground.be.global.infrastructure.security.jwt.JwtProvider;
import com.commonground.be.global.infrastructure.security.jwt.TokenManager;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;
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
import org.springframework.security.core.userdetails.UserDetails;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthService 단위 테스트")
class AuthServiceTest {

    @InjectMocks
    private AuthServiceImpl authService;

    @Mock
    private JwtProvider jwtProvider;

    @Mock
    private TokenManager tokenManager;

    @Mock
    private SessionService sessionService;

    @Mock
    private SocialUserServiceInterface userService;

    @Mock
    private org.springframework.data.redis.core.RedisTemplate<String, String> redisTemplate;

    @Mock
    private org.springframework.data.redis.core.ValueOperations<String, String> valueOperations;

    @Mock
    private UserDetails userDetails;

    private HttpServletRequest request;
    private HttpServletResponse response;
    private User testUser;

    private static final String REFRESH_TOKEN = "test-refresh-token";
    private static final String ACCESS_TOKEN = "test-access-token";
    private static final String USERNAME = "testuser";
    private static final String SESSION_ID = "test-session-id";
    private static final String TOKEN_ID = "test-token-id";

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        testUser = User.builder()
            .username(USERNAME)
            .name("testuser")
            .email("test@test.com")
            .role(UserRole.USER)
            .build();
        // User ID 설정 (리플렉션 사용)
        try {
            java.lang.reflect.Field idField = User.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(testUser, 1L);
        } catch (Exception e) {
            // 테스트에서는 무시
        }
    }

    @Nested
    @DisplayName("토큰 재발급 테스트")
    class ReissueAccessToken {

        @Test
        @DisplayName("성공 - 유효한 리프레시 토큰")
        void reissueAccessToken_success() {
            // given
            when(jwtProvider.getRefreshTokenFromCookie(request)).thenReturn(REFRESH_TOKEN);
            when(jwtProvider.validateAccessToken(REFRESH_TOKEN)).thenReturn(true);
            when(jwtProvider.getUsernameFromToken(REFRESH_TOKEN)).thenReturn(USERNAME);
            when(jwtProvider.getClaimFromToken(REFRESH_TOKEN, "auth")).thenReturn("USER");
            when(jwtProvider.getClaimFromToken(REFRESH_TOKEN, "sessionId")).thenReturn(SESSION_ID);
            when(jwtProvider.getClaimFromToken(REFRESH_TOKEN, "tokenId")).thenReturn(TOKEN_ID);
            when(sessionService.validateSession(SESSION_ID)).thenReturn(true);
            when(jwtProvider.getClaimFromToken(REFRESH_TOKEN, "tokenVersion")).thenReturn("1");
            when(tokenManager.isTokenVersionValid(USERNAME, 1L)).thenReturn(true);
            when(userService.findByUsername(USERNAME)).thenReturn(testUser);
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get("RT:user:1")).thenReturn(REFRESH_TOKEN);
            when(jwtProvider.createAccessTokenWithSession(USERNAME, UserRole.USER, SESSION_ID)).thenReturn(ACCESS_TOKEN);
            when(jwtProvider.createRefreshTokenWithSession(USERNAME, UserRole.USER, SESSION_ID)).thenReturn(REFRESH_TOKEN);

            // when
            Map<String, Object> result = authService.reissueAccessToken(request, response);

            // then
            assertThat(result).containsKey("accessToken");
            assertThat(result.get("accessToken")).isEqualTo(ACCESS_TOKEN);
            verify(tokenManager).invalidateToken(TOKEN_ID);
            verify(sessionService).updateSessionAccess(SESSION_ID);
            verify(jwtProvider).setRefreshTokenCookie(response, REFRESH_TOKEN);
            verify(response).setHeader("Authorization", ACCESS_TOKEN);
        }

        @Test
        @DisplayName("실패 - 리프레시 토큰 없음")
        void reissueAccessToken_fail_noRefreshToken() {
            // given
            when(jwtProvider.getRefreshTokenFromCookie(request)).thenReturn(null);

            // when & then
            assertThatThrownBy(() -> authService.reissueAccessToken(request, response))
                .isInstanceOf(CommonException.class)
                .hasFieldOrPropertyWithValue("responseExceptionEnum", ResponseExceptionEnum.INVALID_REFRESHTOKEN);
        }
    }

    @Nested
    @DisplayName("현재 사용자 정보 조회 테스트")
    class GetCurrentUser {

        @Test
        @DisplayName("성공")
        void getCurrentUser_success() {
            // given
            when(userDetails.getUsername()).thenReturn(USERNAME);
            when(userService.findByUsername(USERNAME)).thenReturn(testUser);

            // when
            UserInfoResponse result = authService.getCurrentUser(userDetails);

            // then
            assertThat(result.getUsername()).isEqualTo(USERNAME);
            assertThat(result.getName()).isEqualTo("testuser");
            assertThat(result.getEmail()).isEqualTo("test@test.com");
        }

        @Test
        @DisplayName("실패 - 인증되지 않은 사용자")
        void getCurrentUser_fail_unauthenticated() {
            // when & then
            assertThatThrownBy(() -> authService.getCurrentUser(null))
                .isInstanceOf(CommonException.class)
                .hasFieldOrPropertyWithValue("responseExceptionEnum", ResponseExceptionEnum.UNAUTHORIZED_ACCESS);
        }
    }

    @Nested
    @DisplayName("로그아웃 테스트")
    class Logout {

        @Test
        @DisplayName("성공 - 현재 세션만 로그아웃")
        void logout_success_currentSession() {
            // given
            LogoutRequest logoutRequest = new LogoutRequest();
            logoutRequest.setEverywhere(false);
            when(userDetails.getUsername()).thenReturn(USERNAME);
            when(userService.findByUsername(USERNAME)).thenReturn(testUser);
            when(jwtProvider.getAccessTokenFromHeader(request)).thenReturn(ACCESS_TOKEN);
            when(jwtProvider.getClaimFromToken(ACCESS_TOKEN, "tokenId")).thenReturn(TOKEN_ID);
            when(jwtProvider.getClaimFromToken(ACCESS_TOKEN, "sessionId")).thenReturn(SESSION_ID);

            // when
            AuthResponse result = authService.logout(userDetails, logoutRequest, request, response);

            // then
            assertThat(result.isSuccess()).isTrue();
            verify(tokenManager).invalidateToken(TOKEN_ID);
            verify(sessionService).invalidateSession(SESSION_ID);
            verify(redisTemplate).delete("RT:user:1");
            verify(jwtProvider).clearCookie(response, JwtProvider.ACCESS_TOKEN_COOKIE_NAME);
            verify(jwtProvider).clearCookie(response, JwtProvider.REFRESH_TOKEN_COOKIE_NAME);
        }

        @Test
        @DisplayName("성공 - 모든 기기에서 로그아웃")
        void logout_success_everywhere() {
            // given
            LogoutRequest logoutRequest = new LogoutRequest();
            logoutRequest.setEverywhere(true);
            when(userDetails.getUsername()).thenReturn(USERNAME);
            when(userService.findByUsername(USERNAME)).thenReturn(testUser);

            // when
            AuthResponse result = authService.logout(userDetails, logoutRequest, request, response);

            // then
            assertThat(result.isSuccess()).isTrue();
            verify(tokenManager).invalidateAllUserTokens(USERNAME);
            verify(sessionService).invalidateAllUserSessions(USERNAME);
            verify(redisTemplate).delete("RT:user:1");
            verify(jwtProvider).clearCookie(response, JwtProvider.ACCESS_TOKEN_COOKIE_NAME);
            verify(jwtProvider).clearCookie(response, JwtProvider.REFRESH_TOKEN_COOKIE_NAME);
        }
    }

    @Nested
    @DisplayName("회원 탈퇴 테스트")
    class Withdraw {

        @Test
        @DisplayName("성공")
        void withdraw_success() {
            // given
            WithdrawRequest withdrawRequest = new WithdrawRequest();
            when(userDetails.getUsername()).thenReturn(USERNAME);
            when(userService.findByUsername(USERNAME)).thenReturn(testUser);

            // when
            AuthResponse result = authService.withdraw(userDetails, withdrawRequest, response);

            // then
            assertThat(result.isSuccess()).isTrue();
            verify(userService).disconnectSocialAccount(testUser);
            verify(tokenManager).invalidateAllUserTokens(USERNAME);
            verify(sessionService).invalidateAllUserSessions(USERNAME);
            verify(redisTemplate).delete("RT:user:1");
            verify(jwtProvider).clearCookie(response, JwtProvider.ACCESS_TOKEN_COOKIE_NAME);
            verify(jwtProvider).clearCookie(response, JwtProvider.REFRESH_TOKEN_COOKIE_NAME);
        }

        @Test
        @DisplayName("실패 - 인증되지 않은 사용자")
        void withdraw_fail_unauthenticated() {
            // when & then
            assertThatThrownBy(() -> authService.withdraw(null, new WithdrawRequest(), response))
                .isInstanceOf(CommonException.class)
                .hasFieldOrPropertyWithValue("responseExceptionEnum", ResponseExceptionEnum.UNAUTHORIZED_ACCESS);
        }
    }
}
