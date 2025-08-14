package com.commonground.be.integration.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.commonground.be.config.TestSecurityConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 공개 엔드포인트 보안 테스트 클래스
 * 
 * 이 테스트는 인증이 필요하지 않은 공개 경로들이 
 * 올바르게 접근 가능한지 검증합니다.
 */
@WebMvcTest
@Import(TestSecurityConfig.class)
@ActiveProfiles("test")
@DisplayName("공개 엔드포인트 보안 테스트")
class PublicEndpointsSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Nested
    @DisplayName("공개 경로 접근 테스트")
    class PublicEndpointAccessTest {

        @Test
        @DisplayName("소셜 로그인 URL 요청은 인증 없이 접근 가능")
        void socialLoginUrl_ShouldBeAccessibleWithoutAuth() throws Exception {
            mockMvc.perform(get("/api/v1/auth/social/google/url")
                    .param("redirectUri", "http://localhost:3000/callback"))
                    .andDo(print())
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("소셜 로그인 콜백은 인증 없이 접근 가능")
        void socialLoginCallback_ShouldBeAccessibleWithoutAuth() throws Exception {
            mockMvc.perform(get("/api/v1/auth/social/google/callback")
                    .param("code", "test_auth_code"))
                    .andDo(print())
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("토큰 재발급 API는 인증 없이 접근 가능")
        void tokenReissue_ShouldBeAccessibleWithoutAuth() throws Exception {
            mockMvc.perform(post("/api/v1/auth/reissue"))
                    .andDo(print())
                    .andExpect(status().isBadRequest()); // 쿠키가 없어서 400이지만 접근은 가능
        }

        @Test
        @DisplayName("헬스체크 엔드포인트는 인증 없이 접근 가능")
        void healthCheck_ShouldBeAccessibleWithoutAuth() throws Exception {
            mockMvc.perform(get("/health"))
                    .andDo(print())
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("정적 리소스는 인증 없이 접근 가능")
        void staticResources_ShouldBeAccessibleWithoutAuth() throws Exception {
            mockMvc.perform(get("/static/css/style.css"))
                    .andDo(print())
                    .andExpect(status().isNotFound()); // 실제 파일이 없으므로 404, 보안상 차단되지 않음
        }

        @Test
        @DisplayName("레거시 OAuth 경로도 통과하되 경고 로그")
        void legacyOAuthPath_ShouldBeAccessibleWithWarning() throws Exception {
            mockMvc.perform(get("/v1/users/oauth/google")
                    .param("code", "test_code"))
                    .andDo(print())
                    .andExpect(status().isOk()); // 공개 경로로 처리됨
        }
    }
}