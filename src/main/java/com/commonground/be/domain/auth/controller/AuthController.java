package com.commonground.be.domain.auth.controller;

import com.commonground.be.global.exception.AuthExceptions;
import com.commonground.be.global.response.HttpResponseDto;
import com.commonground.be.global.response.ResponseCodeEnum;
import com.commonground.be.global.response.ResponseUtils;
import com.commonground.be.global.security.JwtProvider;
import com.commonground.be.global.security.TokenManager;
import com.commonground.be.domain.session.service.SessionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final JwtProvider jwtProvider;
    private final TokenManager tokenManager;
    private final SessionService sessionService;

    /**
     * Access Token 재발급 (OAuth2 방식)
     * POST /api/v1/auth/reissue
     */
    @PostMapping("/reissue")
    public ResponseEntity<HttpResponseDto> reissueAccessToken(
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        log.info("토큰 재발급 요청");

        // 1. 쿠키에서 Refresh Token 추출
        String refreshToken = jwtProvider.getRefreshTokenFromCookie(request);
        if (refreshToken == null) {
            log.warn("Refresh Token이 쿠키에 없음");
            throw AuthExceptions.invalidRefreshToken();
        }

        // 2. Refresh Token 검증
        if (!jwtProvider.validateAccessToken(refreshToken)) {
            log.warn("유효하지 않은 Refresh Token");
            throw AuthExceptions.invalidRefreshToken();
        }

        // 3. 토큰에서 사용자 정보 추출
        String username = jwtProvider.getUsernameFromToken(refreshToken);
        String roleStr = jwtProvider.getClaimFromToken(refreshToken, "auth");
        String sessionId = jwtProvider.getClaimFromToken(refreshToken, "sessionId");
        String tokenId = jwtProvider.getClaimFromToken(refreshToken, "tokenId");

        if (username == null) {
            log.warn("Refresh Token에서 사용자 정보 추출 실패");
            throw AuthExceptions.invalidRefreshToken();
        }

        // 4. 세션 유효성 검증 (세션 ID가 있는 경우)
        if (sessionId != null && !sessionService.validateSession(sessionId)) {
            log.warn("유효하지 않은 세션 - sessionId: {}", sessionId);
            throw AuthExceptions.invalidRefreshToken();
        }

        // 5. 토큰 버전 검증
        String tokenVersionStr = jwtProvider.getClaimFromToken(refreshToken, "tokenVersion");
        if (tokenVersionStr != null) {
            Long tokenVersion = Long.parseLong(tokenVersionStr);
            if (!tokenManager.isTokenVersionValid(username, tokenVersion)) {
                log.warn("유효하지 않은 토큰 버전 - username: {}, version: {}", username, tokenVersion);
                throw AuthExceptions.invalidRefreshToken();
            }
        }

        // 6. 기존 Refresh Token 무효화 (로테이션)
        if (tokenId != null) {
            tokenManager.invalidateToken(tokenId);
            log.debug("기존 Refresh Token 무효화 - tokenId: {}", tokenId);
        }

        // 7. 새로운 토큰 쌍 생성
        UserRole userRole = UserRole.valueOf(roleStr);
        
        String newAccessToken;
        String newRefreshToken;
        
        if (sessionId != null) {
            // 세션이 있는 경우 - 세션 ID 포함하여 생성
            newAccessToken = jwtProvider.createAccessTokenWithSession(username, userRole, sessionId);
            newRefreshToken = jwtProvider.createRefreshTokenWithSession(username, userRole, sessionId);
            
            // 세션 마지막 접근 시간 업데이트
            sessionService.updateSessionAccess(sessionId);
        } else {
            // 레거시 토큰인 경우
            newAccessToken = jwtProvider.createAccessToken(username, userRole);
            newRefreshToken = jwtProvider.createRefreshToken(username, userRole);
        }

        // 8. 새로운 Refresh Token을 쿠키에 설정
        jwtProvider.setRefreshTokenCookie(response, newRefreshToken);

        log.info("토큰 재발급 완료 - username: {}, sessionId: {}", username, sessionId);
        return ResponseUtils.of(
                ResponseCodeEnum.REISSUE_ACCESS_TOKEN, 
                Map.of("accessToken", newAccessToken)
        );
    }
}