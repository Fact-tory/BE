package com.commonground.be.domain.auth.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.commonground.be.config.TestSecurityConfig;
import com.commonground.be.domain.auth.facade.AuthFacade;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * AuthController의 토큰 검증 API 테스트
 */
@WebMvcTest(AuthController.class)
@Import(TestSecurityConfig.class)
@ActiveProfiles("test")
@DisplayName("AuthController 토큰 검증 테스트")
class AuthControllerValidateTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthFacade authFacade;

    @Test
    @DisplayName("인증된 사용자의 토큰 검증 성공")
    @WithMockUser(username = "testuser", roles = {"USER"})
    void validateToken_WithAuthenticatedUser_ShouldReturnValid() throws Exception {
        // When & Then: 인증된 사용자로 토큰 검증
        mockMvc.perform(get("/api/v1/auth/validate"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusCode").value(200))
                .andExpect(jsonPath("$.data.valid").value(true))
                .andExpect(jsonPath("$.data.username").value("testuser"))
                .andExpect(jsonPath("$.data.authorities[0].authority").value("ROLE_USER"));
    }

    @Test
    @DisplayName("인증되지 않은 사용자의 토큰 검증 실패")
    void validateToken_WithUnauthenticatedUser_ShouldReturnUnauthorized() throws Exception {
        // When & Then: 인증되지 않은 사용자로 토큰 검증 (401 또는 302 가능)
        mockMvc.perform(get("/api/v1/auth/validate"))
                .andDo(print())
                .andExpect(status().is4xxClientError()); // 401 또는 302 등 클라이언트 오류
    }

    @Test
    @DisplayName("관리자 사용자의 토큰 검증 성공")
    @WithMockUser(username = "admin", roles = {"ADMIN", "USER"})
    void validateToken_WithAdminUser_ShouldReturnValidWithAdminRole() throws Exception {
        // When & Then: 관리자 사용자로 토큰 검증
        mockMvc.perform(get("/api/v1/auth/validate"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusCode").value(200))
                .andExpect(jsonPath("$.data.valid").value(true))
                .andExpect(jsonPath("$.data.username").value("admin"))
                .andExpect(jsonPath("$.data.authorities[0].authority").value("ROLE_ADMIN"));
    }
}
