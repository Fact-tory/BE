package com.commonground.be.domain.social.controller;

import static com.commonground.be.global.application.response.ResponseUtils.*;

import com.commonground.be.domain.social.SocialAuthService;
import com.commonground.be.global.application.exception.SocialExceptions;
import com.commonground.be.global.application.response.HttpResponseDto;
import com.commonground.be.global.application.response.ResponseCodeEnum;
import com.fasterxml.jackson.core.JsonProcessingException;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/v1/auth/social")
@RequiredArgsConstructor
public class SocialAuthController {

    private final List<SocialAuthService> socialAuthServices;

    /**
     * 소셜 로그인 URL 생성
     * GET /api/v1/auth/social/{provider}/url?redirect_uri={uri}
     */
    @GetMapping("/{provider}/url")
    public ResponseEntity<HttpResponseDto> getSocialAuthUrl(
            @PathVariable String provider,
            @RequestParam("redirect_uri") String redirectUri
    ) {
        log.info("소셜 로그인 URL 요청 - provider: {}, redirect_uri: {}", provider, redirectUri);
        
        SocialAuthService socialAuthService = getSocialAuthService(provider);
        String authUrl = socialAuthService.getAuthUrl(redirectUri);
        
        log.info("소셜 로그인 URL 생성 완료 - provider: {}", provider);
        return of(ResponseCodeEnum.SOCIAL_AUTH_URL_SUCCESS, Map.of("authUrl", authUrl));
    }

    /**
     * 소셜 로그인 콜백 처리
     * GET /api/v1/auth/social/{provider}/callback?code={code}
     */
    @GetMapping("/{provider}/callback")
    public ResponseEntity<HttpResponseDto> socialLoginCallback(
            @PathVariable String provider,
            @RequestParam String code,
            HttpServletResponse response
    ) throws JsonProcessingException {
        log.info("소셜 로그인 콜백 요청 - provider: {}, code: {}", provider, code);
        
        SocialAuthService socialAuthService = getSocialAuthService(provider);
        String accessToken = socialAuthService.socialLogin(code, response);
        
        log.info("소셜 로그인 완료 - provider: {}", provider);
        return of(ResponseCodeEnum.SOCIAL_LOGIN_SUCCESS, Map.of("accessToken", accessToken));
    }

    private SocialAuthService getSocialAuthService(String provider) {
        return socialAuthServices.stream()
                .filter(service -> service.getProviderName().equals(provider))
                .findFirst()
                .orElseThrow(SocialExceptions::invalidProvider);
    }
}