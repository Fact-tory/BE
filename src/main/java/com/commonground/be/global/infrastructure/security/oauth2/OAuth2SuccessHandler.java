package com.commonground.be.global.infrastructure.security.oauth2;

import com.commonground.be.domain.auth.facade.AuthFacade;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;

/**
 * OAuth2 로그인 성공 시 AuthFacade를 통한 파이프라인 처리
 * 기존 Facade 패턴을 준수하여 JWT + Redis 세션 관리 구조와 통합
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OAuth2SuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final AuthFacade authFacade;

    @Value("${app.frontend.success-redirect}")
    private String frontendSuccessUrl;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, 
                                      HttpServletResponse response, 
                                      Authentication authentication) throws IOException {
        
        OAuth2User oauth2User = (OAuth2User) authentication.getPrincipal();
        String registrationId = getRegistrationId(request);
        
        log.info("🔐 OAuth2 로그인 성공: provider={}, principal={}", registrationId, oauth2User.getName());
        
        // AuthFacade를 통한 OAuth2 로그인 성공 처리 파이프라인
        AuthFacade.AuthFlowResult flowResult = authFacade.oauth2LoginSuccessFlow(
                oauth2User, registrationId, request, response);
        
        if (flowResult.isSuccess()) {
            // 성공 시 프론트엔드로 리다이렉트 (AccessToken 포함)
            String redirectUrl = UriComponentsBuilder.fromUriString(frontendSuccessUrl)
                    .queryParam("success", "true")
                    .queryParam("provider", registrationId)
                    .queryParam("accessToken", flowResult.getAccessToken()) // AccessToken 추가
                    .build()
                    .toUriString();
            
            log.info("🔄 OAuth2 로그인 성공 - 프론트엔드 리다이렉트 (AccessToken 포함): provider={}", registrationId);
            getRedirectStrategy().sendRedirect(request, response, redirectUrl);
            
        } else {
            // 실패 시 에러 페이지로 리다이렉트
            String errorUrl = UriComponentsBuilder.fromUriString(frontendSuccessUrl.replace("/success", "/error"))
                    .queryParam("error", "login_failed")
                    .queryParam("message", flowResult.getMessage())
                    .build()
                    .toUriString();
            
            log.error("❌ OAuth2 로그인 처리 실패: {}", flowResult.getMessage());
            getRedirectStrategy().sendRedirect(request, response, errorUrl);
        }
    }


    /**
     * 요청에서 OAuth2 registration ID 추출
     */
    private String getRegistrationId(HttpServletRequest request) {
        String requestUri = request.getRequestURI();
        String[] pathSegments = requestUri.split("/");
        
        // /login/oauth2/code/{registrationId} 패턴에서 registrationId 추출
        for (int i = 0; i < pathSegments.length - 1; i++) {
            if ("code".equals(pathSegments[i])) {
                return pathSegments[i + 1];
            }
        }
        
        throw new IllegalStateException("OAuth2 registration ID를 찾을 수 없습니다: " + requestUri);
    }

}