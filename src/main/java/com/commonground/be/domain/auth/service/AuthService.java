package com.commonground.be.domain.auth.service;

import com.commonground.be.domain.auth.dto.request.LogoutRequest;
import com.commonground.be.domain.auth.dto.request.WithdrawRequest;
import com.commonground.be.domain.auth.dto.response.AuthResponse;
import com.commonground.be.domain.auth.dto.response.UserInfoResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Map;

public interface AuthService {
    
    /**
     * 토큰 재발급
     */
    Map<String, Object> reissueAccessToken(HttpServletRequest request, HttpServletResponse response);
    
    /**
     * 현재 사용자 정보 조회
     */
    UserInfoResponse getCurrentUser(UserDetails userDetails);
    
    /**
     * 통합 로그아웃 (일반 로그아웃 + 카카오 로그아웃)
     */
    AuthResponse logout(UserDetails userDetails, LogoutRequest request, 
                       HttpServletRequest httpRequest, HttpServletResponse response);
    
    	// 회원탈퇴 기능 제거 - 소셜 로그인 전용 서비스

}