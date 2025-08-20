package com.commonground.be.global.infrastructure.security.oauth2;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;

/**
 * OAuth2 로그인 실패 시 처리하는 핸들러
 * 프론트엔드의 에러 페이지로 리다이렉트
 */
@Slf4j
@Component
public class OAuth2FailureHandler extends SimpleUrlAuthenticationFailureHandler {

    @Value("${app.frontend.failure-redirect}")
    private String frontendFailureUrl;

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, 
                                      HttpServletResponse response, 
                                      AuthenticationException exception) throws IOException {
        
        String registrationId = extractRegistrationId(request);
        
        log.error("❌ OAuth2 로그인 실패: provider={}, error={}", registrationId, exception.getMessage());
        
        // 에러 정보를 포함한 프론트엔드 URL 생성
        String redirectUrl = UriComponentsBuilder.fromUriString(frontendFailureUrl)
                .queryParam("error", "oauth2_failed")
                .queryParam("provider", registrationId)
                .queryParam("message", exception.getMessage())
                .build()
                .toUriString();
        
        log.info("🔄 실패 페이지로 리다이렉트: url={}", redirectUrl);
        getRedirectStrategy().sendRedirect(request, response, redirectUrl);
    }

    /**
     * 요청 URL에서 OAuth2 provider 추출
     */
    private String extractRegistrationId(HttpServletRequest request) {
        String requestUri = request.getRequestURI();
        
        // /oauth2/authorization/{registrationId} 패턴에서 추출
        if (requestUri.contains("/oauth2/authorization/")) {
            String[] segments = requestUri.split("/");
            for (int i = 0; i < segments.length; i++) {
                if ("authorization".equals(segments[i]) && i + 1 < segments.length) {
                    return segments[i + 1];
                }
            }
        }
        
        // /login/oauth2/code/{registrationId} 패턴에서 추출
        if (requestUri.contains("/login/oauth2/code/")) {
            String[] segments = requestUri.split("/");
            for (int i = 0; i < segments.length; i++) {
                if ("code".equals(segments[i]) && i + 1 < segments.length) {
                    return segments[i + 1];
                }
            }
        }
        
        return "unknown";
    }
}