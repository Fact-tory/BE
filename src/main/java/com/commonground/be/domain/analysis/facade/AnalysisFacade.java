package com.commonground.be.domain.analysis.facade;

import com.commonground.be.domain.analysis.dto.request.AnalysisStartRequest;
import com.commonground.be.domain.analysis.dto.request.AnalysisRestartRequest;
import com.commonground.be.domain.analysis.dto.response.AnalysisResponse;
import com.commonground.be.domain.analysis.dto.response.AnalysisResultResponse;
import com.commonground.be.domain.analysis.dto.response.MyAnalysesResponse;
import com.commonground.be.domain.analysis.service.AnalysisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Analysis Facade - 분석 관련 데이터 흐름 조정
 * 분석 생성, 조회, 상태 관리의 파이프라인 관리
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AnalysisFacade {
    
    private final AnalysisService analysisService;
    private final RedisTemplate<String, Object> redisTemplate;
    
    // AI 분석 최적화용 상수
    private static final int MAX_DAILY_ANALYSIS_QUOTA = 50;
    private static final int MAX_CONCURRENT_ANALYSIS = 3;
    
    /**
     * 분석 생성 파이프라인
     * Request → Validation → Quota Check → Service → Response
     */
    public AnalysisFlowResult createAnalysisFlow(UserDetails userDetails, AnalysisStartRequest request) {
        log.debug("🔬 분석 생성 파이프라인 시작 - type: {}, user: {}", 
                request.getType(), userDetails.getUsername());
        
        try {
            // 1. 인증 상태 검증
            validateAuthentication(userDetails);
            log.debug("✅ 사용자 인증 검증 완료: {}", userDetails.getUsername());
            
            // 2. 분석 요청 검증
            validateAnalysisRequest(request);
            log.debug("✅ 분석 요청 검증 완료 - type: {}", request.getType());
            
            // 3. 일일 분석 한도 및 동시 분석 한계 확인
            checkDailyAnalysisQuota(userDetails.getUsername());
            checkConcurrentAnalysisLimit(userDetails.getUsername());
            log.debug("✅ 일일 분석 한도 및 성능 한계 확인 완료");
            
            // 4. 분석 생성 및 실행
            AnalysisResponse analysisResponse = analysisService.startAnalysis(userDetails.getUsername(), request);
            log.debug("🔄 분석 생성 서비스 완료 - analysisId: {}", analysisResponse.getAnalysisId());
            
            // 5. 성공 결과 반환
            AnalysisFlowResult result = AnalysisFlowResult.success("분석이 생성되었습니다.", analysisResponse);
            log.debug("📤 분석 생성 파이프라인 완료");
            
            return result;
            
        } catch (Exception e) {
            log.error("❌ 분석 생성 파이프라인 실패: {}", e.getMessage());
            return AnalysisFlowResult.failure("분석 생성 실패: " + e.getMessage(), e);
        }
    }
    
    /**
     * 분석 조회 파이프라인
     * AnalysisId → Validation → Permission Check → Service → Response
     */
    public AnalysisFlowResult getAnalysisFlow(UserDetails userDetails, String analysisId) {
        log.debug("📋 분석 조회 파이프라인 시작 - analysisId: {}, user: {}", 
                analysisId, userDetails.getUsername());
        
        try {
            // 1. 인증 상태 검증
            validateAuthentication(userDetails);
            log.debug("✅ 사용자 인증 검증 완료: {}", userDetails.getUsername());
            
            // 2. 분석 ID 검증
            validateAnalysisId(analysisId);
            log.debug("✅ 분석 ID 검증 완료: {}", analysisId);
            
            // 3. 분석 조회 및 권한 확인
            Long analysisIdLong = Long.parseLong(analysisId);
            AnalysisResultResponse analysisResult = analysisService.getAnalysisResult(userDetails.getUsername(), analysisIdLong);
            log.debug("🔄 분석 조회 서비스 완료 - status: {}", analysisResult.getStatus());
            
            // 4. 성공 결과 반환
            AnalysisFlowResult result = AnalysisFlowResult.success("분석 조회 완료", analysisResult);
            log.debug("📤 분석 조회 파이프라인 완료");
            
            return result;
            
        } catch (Exception e) {
            log.error("❌ 분석 조회 파이프라인 실패: {}", e.getMessage());
            return AnalysisFlowResult.failure("분석 조회 실패: " + e.getMessage(), e);
        }
    }
    
    /**
     * 사용자 분석 목록 조회 파이프라인
     * User → Validation → Service → Pagination → Response
     */
    public AnalysisFlowResult getUserAnalysesFlow(UserDetails userDetails, int page, int size) {
        log.debug("📚 사용자 분석 목록 조회 파이프라인 시작 - user: {}, page: {}, size: {}", 
                userDetails.getUsername(), page, size);
        
        try {
            // 1. 인증 상태 검증
            validateAuthentication(userDetails);
            log.debug("✅ 사용자 인증 검증 완료: {}", userDetails.getUsername());
            
            // 2. 페이징 파라미터 검증
            validatePagingParameters(page, size);
            log.debug("✅ 페이징 파라미터 검증 완료 - page: {}, size: {}", page, size);
            
            // 3. 사용자 분석 목록 조회
            org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(page - 1, size);
            MyAnalysesResponse myAnalyses = analysisService.getMyAnalyses(userDetails.getUsername(), pageable);
            log.debug("🔄 사용자 분석 목록 조회 완료 - 총 {}개", myAnalyses.getAnalyses().size());
            
            // 4. 성공 결과 반환
            AnalysisFlowResult result = AnalysisFlowResult.success("분석 목록 조회 완료", myAnalyses);
            log.debug("📤 사용자 분석 목록 조회 파이프라인 완료");
            
            return result;
            
        } catch (Exception e) {
            log.error("❌ 사용자 분석 목록 조회 파이프라인 실패: {}", e.getMessage());
            return AnalysisFlowResult.failure("분석 목록 조회 실패: " + e.getMessage(), e);
        }
    }
    
    /**
     * 분석 삭제 파이프라인
     * AnalysisId → Validation → Permission Check → Service → Response
     */
    public AnalysisFlowResult deleteAnalysisFlow(UserDetails userDetails, String analysisId) {
        log.debug("🗑️ 분석 삭제 파이프라인 시작 - analysisId: {}, user: {}", 
                analysisId, userDetails.getUsername());
        
        try {
            // 1. 인증 상태 검증
            validateAuthentication(userDetails);
            log.debug("✅ 사용자 인증 검증 완료: {}", userDetails.getUsername());
            
            // 2. 분석 ID 검증
            validateAnalysisId(analysisId);
            log.debug("✅ 분석 ID 검증 완료: {}", analysisId);
            
            // 3. 분석 삭제 처리
            Long analysisIdLong = Long.parseLong(analysisId);
            analysisService.cancelAnalysis(userDetails.getUsername(), analysisIdLong);
            log.debug("🔄 분석 삭제 서비스 완료");
            
            // 4. 성공 결과 반환
            AnalysisFlowResult result = AnalysisFlowResult.successWithMessage("분석이 삭제되었습니다.");
            log.debug("📤 분석 삭제 파이프라인 완료");
            
            return result;
            
        } catch (Exception e) {
            log.error("❌ 분석 삭제 파이프라인 실패: {}", e.getMessage());
            return AnalysisFlowResult.failure("분석 삭제 실패: " + e.getMessage(), e);
        }
    }
    
    /**
     * 분석 재시작 파이프라인
     * AnalysisId → Validation → Status Check → Service → Response
     */
    public AnalysisFlowResult restartAnalysisFlow(UserDetails userDetails, String analysisId) {
        log.debug("🔄 분석 재시작 파이프라인 시작 - analysisId: {}, user: {}", 
                analysisId, userDetails.getUsername());
        
        try {
            // 1. 인증 상태 검증
            validateAuthentication(userDetails);
            log.debug("✅ 사용자 인증 검증 완료: {}", userDetails.getUsername());
            
            // 2. 분석 ID 검증
            validateAnalysisId(analysisId);
            log.debug("✅ 분석 ID 검증 완료: {}", analysisId);
            
            // 3. 분석 재시작 처리
            Long analysisIdLong = Long.parseLong(analysisId);
            AnalysisRestartRequest restartRequest = new AnalysisRestartRequest();
            AnalysisResponse restartedAnalysis = analysisService.restartAnalysis(userDetails.getUsername(), analysisIdLong, restartRequest);
            log.debug("🔄 분석 재시작 서비스 완료 - status: {}", restartedAnalysis.getStatus());
            
            // 4. 성공 결과 반환
            AnalysisFlowResult result = AnalysisFlowResult.success("분석이 재시작되었습니다.", restartedAnalysis);
            log.debug("📤 분석 재시작 파이프라인 완료");
            
            return result;
            
        } catch (Exception e) {
            log.error("❌ 분석 재시작 파이프라인 실패: {}", e.getMessage());
            return AnalysisFlowResult.failure("분석 재시작 실패: " + e.getMessage(), e);
        }
    }
    
    // === Private Validation Methods ===
    
    private void validateAuthentication(UserDetails userDetails) {
        if (userDetails == null) {
            throw new IllegalArgumentException("인증이 필요합니다.");
        }
    }
    
    private void validateAnalysisRequest(AnalysisStartRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("분석 요청이 필요합니다.");
        }
        if (request.getType() == null) {
            throw new IllegalArgumentException("분석 타입이 필요합니다.");
        }
        if ((request.getText() == null || request.getText().trim().isEmpty()) &&
            (request.getUrl() == null || request.getUrl().trim().isEmpty()) &&
            (request.getNewsId() == null || request.getNewsId().trim().isEmpty())) {
            throw new IllegalArgumentException("분석할 내용(텍스트, URL, 뉴스ID 중 하나)이 필요합니다.");
        }
    }
    
    private void validateAnalysisId(String analysisId) {
        if (analysisId == null || analysisId.trim().isEmpty()) {
            throw new IllegalArgumentException("분석 ID가 필요합니다.");
        }
    }
    
    private void validatePagingParameters(int page, int size) {
        if (page < 1) {
            throw new IllegalArgumentException("페이지는 1 이상이어야 합니다.");
        }
        if (size < 1 || size > 100) {
            throw new IllegalArgumentException("페이지 크기는 1-100 범위여야 합니다.");
        }
    }
    
    private void checkDailyAnalysisQuota(String username) {
        try {
            String key = "daily_analysis_quota:" + username;
            String currentCount = (String) redisTemplate.opsForValue().get(key);
            int analysisCount = currentCount != null ? Integer.parseInt(currentCount) : 0;
            
            if (analysisCount >= MAX_DAILY_ANALYSIS_QUOTA) {
                throw new IllegalStateException("일일 분석 한도를 초과했습니다. 내일 다시 시도해주세요.");
            }
            
            // 카운터 증가
            redisTemplate.opsForValue().increment(key);
            redisTemplate.expire(key, 24, TimeUnit.HOURS);
        } catch (NumberFormatException e) {
            log.warn("일일 분석 한도 확인 오류: {}", e.getMessage());
        }
    }
    
    /**
     * 동시 분석 한계 확인 (AI 성능 최적화)
     */
    private void checkConcurrentAnalysisLimit(String username) {
        try {
            String key = "concurrent_analysis:" + username;
            String currentCount = (String) redisTemplate.opsForValue().get(key);
            int concurrent = currentCount != null ? Integer.parseInt(currentCount) : 0;
            
            if (concurrent >= MAX_CONCURRENT_ANALYSIS) {
                throw new IllegalStateException("동시 분석 한계를 초과했습니다. 잠시 후 다시 시도해주세요.");
            }
        } catch (NumberFormatException e) {
            log.warn("동시 분석 한계 확인 오류: {}", e.getMessage());
        }
    }
    
    /**
     * Analysis 도메인 데이터 흐름 결과 컨테이너
     */
    public static class AnalysisFlowResult {
        private final boolean success;
        private final String message;
        private final Object data;
        private final Exception error;
        
        private AnalysisFlowResult(boolean success, String message, Object data, Exception error) {
            this.success = success;
            this.message = message;
            this.data = data;
            this.error = error;
        }
        
        public static AnalysisFlowResult success(String message, Object data) {
            return new AnalysisFlowResult(true, message, data, null);
        }
        
        public static AnalysisFlowResult successWithMessage(String message) {
            return new AnalysisFlowResult(true, message, null, null);
        }
        
        public static AnalysisFlowResult failure(String message, Exception error) {
            return new AnalysisFlowResult(false, message, null, error);
        }
        
        // Getters
        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
        public Object getData() { return data; }
        public Exception getError() { return error; }
    }
}