package com.commonground.be.domain.user.repository;

import com.commonground.be.domain.user.entity.User;
import com.commonground.be.domain.user.utils.UserIdentity;

import java.util.List;

/**
 * User 도메인 Repository 인터페이스
 * 도메인 비즈니스 로직에 특화된 메서드만 정의
 */
public interface UserRepositoryInterface {
    
    /**
     * 활성 사용자 조회 (username)
     */
    User findByUsername(String username);
    
    /**
     * 활성 사용자 조회 (email)
     */
    User findByEmail(String email);
    
    /**
     * 활성 사용자 조회 (ID)
     */
    User findById(Long id);
    
    /**
     * 모든 사용자 조회
     */
    List<User> findAll();
    
    /**
     * 사용자명 존재 여부 확인
     */
    boolean existsByUsername(String username);
    
    /**
     * 사용자 저장
     */
    User save(User user);
    
    /**
     * 사용자 삭제
     */
    void delete(User user);
    
    /**
     * 사용자 삭제 상태 확인
     */
    void validateNotDeleted(String username);
    
    /**
     * 사용자 신원 정보로 조회
     */
    User findByUserIdentity(UserIdentity userIdentity);
    
    /**
     * 임시 비밀번호 생성
     */
    String generateTemporaryPassword();
}