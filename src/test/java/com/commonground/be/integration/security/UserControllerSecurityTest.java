package com.commonground.be.integration.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.commonground.be.config.TestSecurityConfig;
import com.commonground.be.domain.user.controller.UserController;
import com.commonground.be.domain.user.entity.User;
import com.commonground.be.domain.user.service.UserService;
import com.commonground.be.domain.user.utils.UserRole;
import com.commonground.be.global.infrastructure.security.jwt.JwtProvider;
import com.commonground.be.global.infrastructure.security.jwt.TokenManager;
import com.commonground.be.global.infrastructure.security.service.CustomUserDetailsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * UserController Security 통합 테스트 클래스
 * 
 * 이 테스트는 UserController의 보안 관련 기능을 검증합니다.
 * JWT 인증, 권한 검사, 접근 제어 등을 테스트합니다.
 */
@WebMvcTest(UserController.class)
@Import(TestSecurityConfig.class)
@ActiveProfiles("test")
@DisplayName("UserController Security 통합 테스트")
class UserControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private JwtProvider jwtProvider;

    @MockitoBean
    private TokenManager tokenManager;

    @MockitoBean
    private CustomUserDetailsService userDetailsService;

    // 테스트 데이터
    private User testUser;
    private User managerUser;
    private String userAccessToken;
    private String managerAccessToken;

    @BeforeEach
    void setUp() {
        // 테스트용 사용자 생성
        testUser = User.builder()
                .username("testuser")
                .name("테스트 사용자")
                .email("test@example.com")
                .role(UserRole.USER)
                .build();

        managerUser = User.builder()
                .username("manager")
                .name("관리자")
                .email("manager@example.com")
                .role(UserRole.MANAGER)
                .build();

        // 테스트용 토큰 생성
        userAccessToken = "Bearer user.access.token";
        managerAccessToken = "Bearer manager.access.token";
    }

    @Nested
    @DisplayName("JWT 토큰 기반 인증 테스트")
    class JwtAuthenticationTest {

        @Test
        @DisplayName("유효한 JWT 토큰으로 사용자 프로필 접근 성공")
        void getUserProfile_WithValidJWT_ShouldAllowAccess() throws Exception {
            // When & Then: 유효한 JWT 토큰으로 접근 시 성공
            mockMvc.perform(get("/api/v1/users/profile")
                    .header("Authorization", userAccessToken)
                    .contentType(MediaType.APPLICATION_JSON))
                    .andDo(print())
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("JWT 토큰 없이 보호된 리소스 접근 시 401 Unauthorized")
        void getUserProfile_WithoutJWT_ShouldReturn401() throws Exception {
            // When & Then: JWT 토큰 없이 접근 시 401
            mockMvc.perform(get("/api/v1/users/profile")
                    .contentType(MediaType.APPLICATION_JSON))
                    .andDo(print())
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value(401));
        }

        @Test
        @DisplayName("유효하지 않은 JWT 토큰으로 접근 시 401 Unauthorized")
        void getUserProfile_WithInvalidJWT_ShouldReturn401() throws Exception {
            // When & Then: 유효하지 않은 JWT 토큰으로 접근 시 401
            mockMvc.perform(get("/api/v1/users/profile")
                    .header("Authorization", "Bearer invalid.jwt.token")
                    .contentType(MediaType.APPLICATION_JSON))
                    .andDo(print())
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value(401));
        }
    }

    @Nested
    @DisplayName("권한 기반 접근 제어 테스트")
    class RoleBasedAccessControlTest {

        @Test
        @DisplayName("일반 사용자 권한으로 사용자 API 접근 성공")
        void userEndpoint_WithUserRole_ShouldAllowAccess() throws Exception {
            // When & Then: 일반 사용자 권한으로 접근 성공
            mockMvc.perform(get("/api/v1/users/profile")
                    .header("Authorization", userAccessToken)
                    .contentType(MediaType.APPLICATION_JSON))
                    .andDo(print())
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("관리자 권한으로 사용자 API 접근 성공")
        void userEndpoint_WithManagerRole_ShouldAllowAccess() throws Exception {
            // When & Then: 관리자 권한으로 접근 성공
            mockMvc.perform(get("/api/v1/users/profile")
                    .header("Authorization", managerAccessToken)
                    .contentType(MediaType.APPLICATION_JSON))
                    .andDo(print())
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("관리자 권한으로 관리자 전용 API 접근 성공")
        void managerEndpoint_WithManagerRole_ShouldAllowAccess() throws Exception {
            // When & Then: 관리자 권한으로 관리자 전용 API 접근 성공
            mockMvc.perform(get("/api/v1/users/all")
                    .header("Authorization", managerAccessToken)
                    .contentType(MediaType.APPLICATION_JSON))
                    .andDo(print())
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("일반 사용자 권한으로 관리자 전용 API 접근 시 403 Forbidden")
        void managerEndpoint_WithUserRole_ShouldReturn403() throws Exception {
            // When & Then: 일반 사용자 권한으로 관리자 전용 API 접근 시 403
            mockMvc.perform(get("/api/v1/users/all")
                    .header("Authorization", userAccessToken)
                    .contentType(MediaType.APPLICATION_JSON))
                    .andDo(print())
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value(403));
        }
    }

    @Nested
    @DisplayName("보안 헤더 및 응답 검증 테스트")
    class SecurityHeaderTest {

        @Test
        @DisplayName("인증 실패 시 적절한 HTTP 상태 코드와 메시지 반환")
        void authenticationFailure_ShouldReturnProperErrorResponse() throws Exception {
            // When & Then: 인증 실패 시 적절한 응답
            mockMvc.perform(get("/api/v1/users/profile")
                    .header("Authorization", "Bearer invalid.token")
                    .contentType(MediaType.APPLICATION_JSON))
                    .andDo(print())
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value(401));
        }

        @Test
        @DisplayName("권한 부족 시 적절한 HTTP 상태 코드 반환")
        void authorizationFailure_ShouldReturnProperErrorResponse() throws Exception {
            // When & Then: 권한 부족 시 적절한 응답
            mockMvc.perform(get("/api/v1/users/all")
                    .header("Authorization", userAccessToken)
                    .contentType(MediaType.APPLICATION_JSON))
                    .andDo(print())
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value(403));
        }
    }
}