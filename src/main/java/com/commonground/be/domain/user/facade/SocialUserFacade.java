package com.commonground.be.domain.user.facade;

import com.commonground.be.domain.user.dto.UserResponseDto;
import com.commonground.be.domain.user.entity.User;
import com.commonground.be.domain.user.service.SocialUserServiceInterface;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 소셜 로그인 특화 User Facade
 * 데이터 흐름 가시성과 소셜 로그인 중심 비즈니스 로직 조정
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SocialUserFacade {
    
    private final SocialUserServiceInterface socialUserService;
    
    /**
     * 사용자 조회 파이프라인
     * 소셜 로그인 사용자에 특화된 조회 로직
     */
    public SocialUserFlowResult getUserFlow(Long userId) {
        long startTime = System.currentTimeMillis();
        log.info("👤 [FACADE] 소셜 사용자 조회 파이프라인 시작 - userId: {}", userId);
        
        try {
            // 1. 입력 검증
            log.debug("🔍 [STEP 1/4] 사용자 ID 검증 중...");
            validateUserId(userId);
            log.debug("✅ [STEP 1/4] 사용자 ID 검증 완료: {}", userId);
            
            // 2. 소셜 사용자 조회
            log.debug("🔍 [STEP 2/4] 소셜 사용자 데이터 조회 중...");
            UserResponseDto userResponse = socialUserService.getUser(userId);
            log.debug("✅ [STEP 2/4] 소셜 사용자 조회 완료: username={}", userResponse.getUsername());
            
            // 3. 활성 상태 확인
            log.debug("🔍 [STEP 3/4] 사용자 활성 상태 확인 중...");
            boolean isActive = socialUserService.isUserActive(userResponse.getUsername());
            if (!isActive) {
                log.warn("⚠️ [STEP 3/4] 비활성 소셜 계정 접근 시도: {}", userResponse.getUsername());
                return SocialUserFlowResult.failure("연결 해제된 소셜 계정입니다.", null);
            }
            log.debug("✅ [STEP 3/4] 사용자 활성 상태 확인 완료: active=true");
            
            // 4. 성공 결과 반환
            log.debug("🔍 [STEP 4/4] 결과 데이터 구성 중...");
            SocialUserFlowResult result = SocialUserFlowResult.success(userResponse);
            
            long executionTime = System.currentTimeMillis() - startTime;
            log.info("✅ [FACADE] 소셜 사용자 조회 파이프라인 완료 - userId: {}, 실행시간: {}ms", userId, executionTime);
            
            return result;
            
        } catch (Exception e) {
            long executionTime = System.currentTimeMillis() - startTime;
            log.error("❌ [FACADE] 소셜 사용자 조회 파이프라인 실패 - userId: {}, 실행시간: {}ms, 오류: {}", 
                    userId, executionTime, e.getMessage(), e);
            return SocialUserFlowResult.failure("사용자 조회 실패: " + e.getMessage(), e);
        }
    }
    
    /**
     * 소셜 계정 연결 해제 파이프라인
     * 소셜 로그인 특화 탈퇴 로직
     */
    public SocialUserFlowResult disconnectSocialAccountFlow(String username) {
        long startTime = System.currentTimeMillis();
        log.info("🔗 [FACADE] 소셜 계정 연결 해제 파이프라인 시작 - username: {}", username);
        
        try {
            // 1. 사용자 조회 및 검증
            log.debug("🔍 [STEP 1/4] 연결 해제 대상 계정 조회 중...");
            User user = socialUserService.findByUsername(username);
            log.debug("✅ [STEP 1/4] 연결 해제 대상 소셜 계정 확인: username={}, userId={}", user.getUsername(), user.getId());
            
            // 2. 활성 상태 확인
            log.debug("🔍 [STEP 2/4] 계정 활성 상태 확인 중...");
            if (!socialUserService.isUserActive(username)) {
                log.warn("⚠️ [STEP 2/4] 이미 연결 해제된 소셜 계정: {}", username);
                return SocialUserFlowResult.failure("이미 연결 해제된 계정입니다.", null);
            }
            log.debug("✅ [STEP 2/4] 계정 활성 상태 확인 완료: active=true");
            
            // 3. 소셜 계정 연결 해제 처리
            log.debug("🔍 [STEP 3/4] 소셜 계정 연결 해제 처리 중...");
            socialUserService.disconnectSocialAccount(user);
            log.debug("✅ [STEP 3/4] 소셜 계정 연결 해제 서비스 완료: {}", username);
            
            // 4. 성공 결과 반환
            log.debug("🔍 [STEP 4/4] 성공 응답 구성 중...");
            SocialUserFlowResult result = SocialUserFlowResult.successWithMessage("소셜 계정 연결이 해제되었습니다.");
            
            long executionTime = System.currentTimeMillis() - startTime;
            log.info("✅ [FACADE] 소셜 계정 연결 해제 파이프라인 완료 - username: {}, 실행시간: {}ms", username, executionTime);
            
            return result;
            
        } catch (Exception e) {
            long executionTime = System.currentTimeMillis() - startTime;
            log.error("❌ [FACADE] 소셜 계정 연결 해제 파이프라인 실패 - username: {}, 실행시간: {}ms, 오류: {}", 
                    username, executionTime, e.getMessage(), e);
            return SocialUserFlowResult.failure("계정 연결 해제 실패: " + e.getMessage(), e);
        }
    }
    
    /**
     * 관리자 권한 승격 파이프라인
     */
    public SocialUserFlowResult promoteToManagerFlow(Long userId) {
        log.debug("⬆️ 관리자 권한 승격 파이프라인 시작 - userId: {}", userId);
        
        try {
            // 1. 입력 검증
            validateUserId(userId);
            log.debug("✅ 승격 대상 사용자 ID 검증 완료: {}", userId);
            
            // 2. 권한 승격 처리
            socialUserService.promoteToManager(userId);
            log.debug("🔄 관리자 권한 승격 서비스 완료: {}", userId);
            
            // 3. 성공 결과 반환
            SocialUserFlowResult result = SocialUserFlowResult.successWithMessage("관리자 권한이 부여되었습니다.");
            log.debug("📤 관리자 권한 승격 파이프라인 완료");
            
            return result;
            
        } catch (Exception e) {
            log.error("❌ 관리자 권한 승격 파이프라인 실패: {}", e.getMessage());
            return SocialUserFlowResult.failure("권한 승격 실패: " + e.getMessage(), e);
        }
    }
    
    /**
     * 전체 소셜 사용자 목록 조회 파이프라인 (관리자용)
     */
    public SocialUserFlowResult getAllSocialUsersFlow() {
        log.debug("📋 전체 소셜 사용자 목록 조회 파이프라인 시작");
        
        try {
            // 1. 모든 사용자 조회
            List<UserResponseDto> allUsers = socialUserService.getAllUsers();
            log.debug("🔄 전체 소셜 사용자 조회 완료 - 총 {}명", allUsers.size());
            
            // 2. 성공 결과 반환
            SocialUserFlowResult result = SocialUserFlowResult.success(allUsers);
            log.debug("📤 전체 소셜 사용자 목록 조회 파이프라인 완료");
            
            return result;
            
        } catch (Exception e) {
            log.error("❌ 전체 소셜 사용자 목록 조회 파이프라인 실패: {}", e.getMessage());
            return SocialUserFlowResult.failure("사용자 목록 조회 실패: " + e.getMessage(), e);
        }
    }
    
    // === Private Validation Methods ===
    
    private void validateUserId(Long userId) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("유효하지 않은 사용자 ID입니다.");
        }
    }
    
    /**
     * 소셜 로그인 특화 데이터 흐름 결과 컨테이너
     */
    public static class SocialUserFlowResult {
        private final boolean success;
        private final String message;
        private final Object data;
        private final Exception error;
        
        private SocialUserFlowResult(boolean success, String message, Object data, Exception error) {
            this.success = success;
            this.message = message;
            this.data = data;
            this.error = error;
        }
        
        public static SocialUserFlowResult success(Object data) {
            return new SocialUserFlowResult(true, "성공", data, null);
        }
        
        public static SocialUserFlowResult successWithMessage(String message) {
            return new SocialUserFlowResult(true, message, null, null);
        }
        
        public static SocialUserFlowResult failure(String message, Exception error) {
            return new SocialUserFlowResult(false, message, null, error);
        }
        
        // Getters
        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
        public Object getData() { return data; }
        public Exception getError() { return error; }
    }
}