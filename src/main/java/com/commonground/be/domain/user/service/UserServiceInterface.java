package com.commonground.be.domain.user.service;

import com.commonground.be.domain.user.dto.ProfileUpdateRequestDto;
import com.commonground.be.domain.user.dto.UserResponseDto;
import com.commonground.be.domain.user.entity.User;

import java.util.List;

/**
 * User 도메인 Service 인터페이스
 * 사용자 관련 비즈니스 로직 정의
 */
public interface UserServiceInterface {
    
    /**
     * 로그아웃 처리
     */
    void logout(String username);
    
    /**
     * 사용자 조회
     */
    UserResponseDto getUser(Long userId);
    
    /**
     * 회원 탈퇴 처리
     */
    void withdraw(User user);
    
    /**
     * 사용자명으로 사용자 조회
     */
    User findByUsername(String username);
    
    /**
     * 이메일로 사용자 조회
     */
    User findByEmail(String email);
    
    /**
     * 이메일과 이름으로 사용자 조회
     */
    User findByEmailAndName(String email, String name);
    
    /**
     * 사용자 저장
     */
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
     * 프로필 업데이트
     */
    UserResponseDto updateProfile(Long userId, ProfileUpdateRequestDto requestDto);
    
    /**
     * 임시 비밀번호 생성 및 전송
     */
    String generateAndSendTemporaryPassword(String username);
}