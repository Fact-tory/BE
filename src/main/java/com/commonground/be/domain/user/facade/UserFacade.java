package com.commonground.be.domain.user.facade;

import com.commonground.be.domain.user.dto.ProfileUpdateRequestDto;
import com.commonground.be.domain.user.dto.UserResponseDto;
import com.commonground.be.domain.user.entity.User;
import com.commonground.be.domain.user.service.UserServiceInterface;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * User Facade - 데이터 흐름을 명시적으로 보여주는 중간 계층
 * Controller와 Service 사이의 데이터 변환과 흐름을 조정
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserFacade {
    
    private final UserServiceInterface userService;
    
    /**
     * 사용자 조회 파이프라인
     * Request → Validation → Service → Response
     */
    public UserFlowResult getUserFlow(Long userId) {
        log.debug("👤 사용자 조회 파이프라인 시작 - userId: {}", userId);
        
        // 1. 입력 검증
        validateUserId(userId);
        log.debug("✅ 사용자 ID 검증 완료: {}", userId);
        
        // 2. 비즈니스 로직 실행
        UserResponseDto userResponse = userService.getUser(userId);
        log.debug("🔄 사용자 서비스 조회 완료: {}", userResponse.getUsername());
        
        // 3. 결과 구성
        UserFlowResult result = UserFlowResult.success(userResponse);
        log.debug("📤 사용자 조회 파이프라인 완료 - result: {}", result.isSuccess());
        
        return result;
    }
    
    /**
     * 프로필 업데이트 파이프라인
     * Request → Validation → Service → Response
     */
    public UserFlowResult updateProfileFlow(Long userId, ProfileUpdateRequestDto request) {
        log.debug("✏️ 프로필 업데이트 파이프라인 시작 - userId: {}", userId);
        
        // 1. 입력 검증
        validateUserId(userId);
        validateProfileUpdateRequest(request);
        log.debug("✅ 프로필 업데이트 요청 검증 완료");
        
        // 2. 비즈니스 로직 실행
        UserResponseDto updatedUser = userService.updateProfile(userId, request);
        log.debug("🔄 프로필 업데이트 서비스 완료: {}", updatedUser.getUsername());
        
        // 3. 결과 구성
        UserFlowResult result = UserFlowResult.success(updatedUser);
        log.debug("📤 프로필 업데이트 파이프라인 완료");
        
        return result;
    }
    
    /**
     * 회원 탈퇴 파이프라인
     * User → Validation → Service → Result
     */
    public UserFlowResult withdrawFlow(String username) {
        log.debug("🚪 회원 탈퇴 파이프라인 시작 - username: {}", username);
        
        // 1. 사용자 조회 및 검증
        User user = userService.findByUsername(username);
        log.debug("✅ 탈퇴 대상 사용자 확인: {}", user.getUsername());
        
        // 2. 탈퇴 처리
        userService.withdraw(user);
        log.debug("🔄 회원 탈퇴 서비스 완료: {}", username);
        
        // 3. 결과 구성
        UserFlowResult result = UserFlowResult.successWithMessage("회원 탈퇴가 완료되었습니다.");
        log.debug("📤 회원 탈퇴 파이프라인 완료");
        
        return result;
    }
    
    /**
     * 권한 승격 파이프라인
     */
    public UserFlowResult promoteToManagerFlow(Long userId) {
        log.debug("⬆️ 관리자 승격 파이프라인 시작 - userId: {}", userId);
        
        // 1. 입력 검증
        validateUserId(userId);
        log.debug("✅ 승격 대상 사용자 ID 검증 완료: {}", userId);
        
        // 2. 권한 승격 처리
        userService.promoteToManager(userId);
        log.debug("🔄 관리자 승격 서비스 완료: {}", userId);
        
        // 3. 결과 구성
        UserFlowResult result = UserFlowResult.successWithMessage("관리자 권한이 부여되었습니다.");
        log.debug("📤 관리자 승격 파이프라인 완료");
        
        return result;
    }
    
    // === Private Validation Methods ===
    
    private void validateUserId(Long userId) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("유효하지 않은 사용자 ID입니다.");
        }
    }
    
    private void validateProfileUpdateRequest(ProfileUpdateRequestDto request) {
        if (request == null) {
            throw new IllegalArgumentException("프로필 업데이트 요청이 필요합니다.");
        }
        // 추가 검증 로직...
    }
    
    /**
     * 데이터 흐름 결과를 담는 컨테이너
     */
    public static class UserFlowResult {
        private final boolean success;
        private final String message;
        private final UserResponseDto data;
        private final Exception error;
        
        private UserFlowResult(boolean success, String message, UserResponseDto data, Exception error) {
            this.success = success;
            this.message = message;
            this.data = data;
            this.error = error;
        }
        
        public static UserFlowResult success(UserResponseDto data) {
            return new UserFlowResult(true, "성공", data, null);
        }
        
        public static UserFlowResult successWithMessage(String message) {
            return new UserFlowResult(true, message, null, null);
        }
        
        public static UserFlowResult failure(String message, Exception error) {
            return new UserFlowResult(false, message, null, error);
        }
        
        // Getters
        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
        public UserResponseDto getData() { return data; }
        public Exception getError() { return error; }
    }
}