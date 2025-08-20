package com.commonground.be.domain.user.service;

import com.commonground.be.domain.user.dto.UserResponseDto;
import com.commonground.be.domain.user.entity.User;

import java.util.List;

/**
 * 소셜 로그인 특화 User Service 인터페이스
 * 비밀번호 관련 기능 제거, 소셜 로그인 중심 기능만 유지
 */
public interface SocialUserServiceInterface {
    
    /**
     * 사용자 조회
     */
    UserResponseDto getUser(Long userId);
    
    /**
     * 사용자명으로 사용자 조회
     */
    User findByUsername(String username);

    User findByEmail(String email);

    User save(User user);
    
    /**
     * 모든 사용자 조회 (관리자용)
     */
    List<UserResponseDto> getAllUsers();
    
    /**
     * 관리자 승격
     */
    void promoteToManager(Long userId);
    
    /**
     * 관리자 권한 해제
     */
    void demoteToUser(Long userId);
    
    /**
     * 소셜 계정 연결 해제 (탈퇴)
     */
    void disconnectSocialAccount(User user);
    
    /**
     * 사용자 활성 상태 확인
     */
    boolean isUserActive(String username);
    
    /**
     * 사용자 계정 생성 경과일
     */
    long getUserAccountAge(String username);
}