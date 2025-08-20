package com.commonground.be.domain.user.controller;

import com.commonground.be.domain.user.facade.SocialUserFacade;
import com.commonground.be.global.application.response.HttpResponseDto;
import com.commonground.be.global.application.response.ResponseCodeEnum;
import com.commonground.be.global.application.response.ResponseUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

/**
 * 소셜 로그인 특화 User Controller
 * Facade 패턴을 통한 간소화된 사용자 관리
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/users")
public class SimplifiedUserController {

    private final SocialUserFacade socialUserFacade;


    /**
     * 소셜 계정 연결 해제 (탈퇴)
     */
    @DeleteMapping("/disconnect")
    public ResponseEntity<HttpResponseDto> disconnectSocialAccount(
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        // 🔗 Facade를 통한 소셜 계정 연결 해제 파이프라인 (이메일 기반)
        String email = userDetails.getUsername(); // username이 실제로는 email
        SocialUserFacade.SocialUserFlowResult flowResult = 
                socialUserFacade.disconnectSocialAccountFlow(email);
        
        ResponseCodeEnum responseCode = flowResult.isSuccess() ? 
                ResponseCodeEnum.SUCCESS : ResponseCodeEnum.INTERNAL_SERVER_ERROR;
        
        return ResponseUtils.of(responseCode, flowResult.getMessage());
    }

    /**
     * 관리자 권한 승격 (관리자 전용)
     */
    @PostMapping("/{userId}/promote")
    public ResponseEntity<HttpResponseDto> promoteToManager(
            @PathVariable Long userId,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        // ⬆️ Facade를 통한 권한 승격 파이프라인
        SocialUserFacade.SocialUserFlowResult flowResult = 
                socialUserFacade.promoteToManagerFlow(userId);
        
        ResponseCodeEnum responseCode = flowResult.isSuccess() ? 
                ResponseCodeEnum.SUCCESS : ResponseCodeEnum.INTERNAL_SERVER_ERROR;
        
        return ResponseUtils.of(responseCode, flowResult.getMessage());
    }

    /**
     * 전체 사용자 목록 조회 (관리자 전용)
     */
    @GetMapping("/all")
    public ResponseEntity<HttpResponseDto> getAllUsers(
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        // 📋 Facade를 통한 전체 사용자 조회 파이프라인
        SocialUserFacade.SocialUserFlowResult flowResult = 
                socialUserFacade.getAllSocialUsersFlow();
        
        if (flowResult.isSuccess()) {
            return ResponseUtils.of(ResponseCodeEnum.SUCCESS, flowResult.getData());
        } else {
            return ResponseUtils.of(ResponseCodeEnum.INTERNAL_SERVER_ERROR, 
                    flowResult.getMessage());
        }
    }
}