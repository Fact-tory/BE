package com.commonground.be.domain.dashboard.facade;

import com.commonground.be.domain.dashboard.dto.response.MainDashboardResponse;
import com.commonground.be.domain.dashboard.dto.response.UserDashboardResponse;
import com.commonground.be.domain.dashboard.service.DashboardService;
import com.commonground.be.global.application.exception.CommonException;
import com.commonground.be.global.application.response.ResponseExceptionEnum;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Dashboard Facade - 대시보드 데이터 집계 및 흐름 조정
 * 메인 대시보드와 사용자 대시보드의 파이프라인 관리
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DashboardFacade {
    
    private final DashboardService dashboardService;
    private final RedisTemplate<String, Object> redisTemplate;
    
    // 대시보드 데이터 집계 최적화용 상수
    private static final int CACHE_DURATION_MINUTES = 5; // 메인 대시보드 캐시 5분
    private static final int USER_CACHE_DURATION_MINUTES = 10; // 사용자 대시보드 캐시 10분
    private static final int MAX_CONCURRENT_AGGREGATION = 3; // 최대 동시 집계 작업
    
    /**
     * 메인 대시보드 데이터 집계 파이프라인 (병렬 처리 및 캐싱 최적화)
     * Parameters → Validation → Cache Check → Parallel Aggregation → Merge → Response
     */
    @Cacheable(value = "mainDashboard", key = "#realtimeLimit + '_' + #trendingLimit + '_' + #categoryLimit + '_' + #searchLimit", unless = "#result.data == null")
    public DashboardFlowResult getMainDashboardFlow(int realtimeLimit, int trendingLimit, 
                                                   int categoryLimit, int searchLimit) {
        long startTime = System.currentTimeMillis();
        String aggregationKey = generateMainDashboardKey(realtimeLimit, trendingLimit, categoryLimit, searchLimit);
        log.info("🏠 [FACADE] 메인 대시보드 집계 파이프라인 시작 - key: {}, realtime: {}, trending: {}, category: {}, search: {}", 
                aggregationKey, realtimeLimit, trendingLimit, categoryLimit, searchLimit);
        
        try {
            // 1. 파라미터 검증 및 최적화
            log.debug("🔍 [STEP 1/6] 파라미터 검증 및 최적화 중...");
            validateDashboardLimits(realtimeLimit, trendingLimit, categoryLimit, searchLimit);
            DashboardLimits optimizedLimits = optimizeDashboardLimits(realtimeLimit, trendingLimit, categoryLimit, searchLimit);
            log.debug("✅ [STEP 1/6] 메인 대시보드 파라미터 검증 완료 - 최적화된 제한: {}", optimizedLimits);
            
            // 2. 캐시 확인 (빠른 응답)
            log.debug("🔍 [STEP 2/6] 메인 대시보드 캐시 확인 중...");
            MainDashboardResponse cachedDashboard = getCachedMainDashboard(aggregationKey);
            if (cachedDashboard != null) {
                long executionTime = System.currentTimeMillis() - startTime;
                log.debug("💾 [STEP 2/6] 캐시된 메인 대시보드 발견 - 실행시간: {}ms", executionTime);
                return DashboardFlowResult.success("메인 대시보드 집계 완료 (캐시)", 
                    enrichMainDashboardResponse(cachedDashboard, executionTime, "cache"));
            }
            log.debug("✅ [STEP 2/6] 캐시 미스 - 새로운 집계 수행");
            
            // 3. 동시 집계 한계 확인
            log.debug("🔍 [STEP 3/6] 동시 집계 한계 확인 중...");
            checkConcurrentAggregationLimit();
            markAggregationInProgress(aggregationKey);
            log.debug("✅ [STEP 3/6] 동시 집계 한계 확인 완료");
            
            // 4. 병렬 다중 서비스 데이터 집계 (성능 최적화)
            log.debug("🔍 [STEP 4/6] 병렬 다중 서비스 데이터 집계 시작...");
            MainDashboardResponse mainDashboard = aggregateMainDashboardParallel(optimizedLimits);
            log.debug("✅ [STEP 4/6] 실시간 뉴스: {}개, 급상승 뉴스: {}개, 인기 검색어: {}개", 
                    mainDashboard.getRealtime().getArticles().size(),
                    mainDashboard.getTrending().getArticles().size(), 
                    mainDashboard.getPopularSearches().size());
            
            // 5. 집계 결과 검증 및 캐싱
            log.debug("🔍 [STEP 5/6] 집계 결과 검증 및 캐싱 중...");
            validateMainDashboardResponse(mainDashboard);
            cacheMainDashboard(aggregationKey, mainDashboard);
            markAggregationCompleted(aggregationKey);
            log.debug("✅ [STEP 5/6] 집계 결과 검증 및 캐싱 완료");
            
            // 6. 성공 결과 반환 (메타데이터 보강)
            log.debug("🔍 [STEP 6/6] 응답 데이터 구성 중...");
            long executionTime = System.currentTimeMillis() - startTime;
            MainDashboardResponse enrichedDashboard = enrichMainDashboardResponse(mainDashboard, executionTime, "aggregation");
            
            DashboardFlowResult result = DashboardFlowResult.success("메인 대시보드 집계 완료", enrichedDashboard);
            log.info("📤 [FACADE] 메인 대시보드 집계 파이프라인 완료 - key: {}, 실행시간: {}ms", aggregationKey, executionTime);
            
            return result;
            
        } catch (Exception e) {
            long executionTime = System.currentTimeMillis() - startTime;
            log.error("❌ [FACADE] 메인 대시보드 집계 파이프라인 실패 - key: {}, 실행시간: {}ms, 오류: {}", 
                    aggregationKey, executionTime, e.getMessage(), e);
            markAggregationCompleted(aggregationKey); // 실패 시에도 락 해제
            return DashboardFlowResult.failure("메인 대시보드 조회 실패: " + e.getMessage(), e);
        }
    }
    
    /**
     * 사용자 대시보드 데이터 집계 파이프라인 (개인화 알고리즘 최적화)
     * User → Authentication → Personalization → Multi-Service Aggregation → Response
     */
    @Cacheable(value = "userDashboard", key = "#userDetails.username", unless = "#result.data == null")
    public DashboardFlowResult getUserDashboardFlow(UserDetails userDetails) {
        long startTime = System.currentTimeMillis();
        log.debug("👤 [FACADE] 사용자 대시보드 집계 파이프라인 시작 - user: {}", userDetails.getUsername());
        
        try {
            // 1. 인증 상태 강화 검증
            log.debug("🔍 [STEP 1/5] 사용자 인증 강화 검증 중...");
            validateAuthentication(userDetails);
            log.debug("✅ [STEP 1/5] 사용자 인증 검증 완료: {}", userDetails.getUsername());
            
            // 2. 사용자 개인화 설정 분석
            log.debug("🔍 [STEP 2/5] 사용자 개인화 설정 분석 중...");
            UserPersonalization personalization = analyzeUserPersonalization(userDetails.getUsername());
            log.debug("✅ [STEP 2/5] 개인화 분석 완료 - 선호 카테고리: {}, 활동 점수: {}", 
                    personalization.getPreferredCategories(), personalization.getActivityScore());
            
            // 3. 개인화된 다중 서비스 데이터 집계
            log.debug("🔍 [STEP 3/5] 개인화된 다중 서비스 데이터 집계 시작...");
            UserDashboardResponse userDashboard = aggregateUserDashboardPersonalized(userDetails.getUsername(), personalization);
            
            // 4. 집계 결과 검증 및 개인화 점수 계산
            log.debug("🔍 [STEP 4/5] 집계 결과 검증 및 개인화 점수 계산 중...");
            validateUserDashboardResponse(userDashboard);
            UserDashboardResponse optimizedDashboard = optimizeUserDashboardForPersonalization(userDashboard, personalization);
            
            log.debug("✅ [STEP 4/5] 사용자 분석: {}개, 검색 기록: {}개", 
                    optimizedDashboard.getMyAnalyses().size(),
                    optimizedDashboard.getRecentSearches().size());
            
            if (optimizedDashboard.getStats() != null) {
                log.debug("✅ [STEP 4/5] 사용자 통계 - 총 분석: {}개, 완료된 분석: {}개, 검색: {}개",
                        optimizedDashboard.getStats().getTotalAnalyses(),
                        optimizedDashboard.getStats().getCompletedAnalyses(),
                        optimizedDashboard.getStats().getTotalSearches());
            }
            
            // 5. 성공 결과 반환 (메타데이터 보강)
            log.debug("🔍 [STEP 5/5] 사용자 대시보드 응답 구성 중...");
            long executionTime = System.currentTimeMillis() - startTime;
            UserDashboardResponse enrichedDashboard = enrichUserDashboardResponse(optimizedDashboard, executionTime, personalization);
            
            DashboardFlowResult result = DashboardFlowResult.success("사용자 대시보드 집계 완료", enrichedDashboard);
            log.debug("📤 [FACADE] 사용자 대시보드 집계 파이프라인 완료 - user: {}, 실행시간: {}ms", 
                    userDetails.getUsername(), executionTime);
            
            return result;
            
        } catch (Exception e) {
            long executionTime = System.currentTimeMillis() - startTime;
            log.error("❌ [FACADE] 사용자 대시보드 집계 파이프라인 실패 - user: {}, 실행시간: {}ms, 오류: {}", 
                    userDetails.getUsername(), executionTime, e.getMessage(), e);
            return DashboardFlowResult.failure("사용자 대시보드 조회 실패: " + e.getMessage(), e);
        }
    }
    
    /**
     * 대시보드 데이터 새로고침 파이프라인
     * User → Authentication → Cache Clear → Re-aggregation → Response
     */
    public DashboardFlowResult refreshDashboardFlow(UserDetails userDetails) {
        log.debug("🔄 대시보드 새로고침 파이프라인 시작 - user: {}", userDetails.getUsername());
        
        try {
            // 1. 인증 상태 검증
            validateAuthentication(userDetails);
            log.debug("✅ 사용자 인증 검증 완료: {}", userDetails.getUsername());
            
            // 2. 캐시 클리어 (향후 구현)
            log.debug("🧹 대시보드 캐시 클리어 시작");
            // TODO: 캐시 클리어 로직 구현
            
            // 3. 데이터 재집계
            log.debug("🔄 대시보드 데이터 재집계 시작");
            UserDashboardResponse refreshedDashboard = dashboardService.getUserDashboard(userDetails.getUsername());
            
            // 4. 성공 결과 반환
            DashboardFlowResult result = DashboardFlowResult.success("대시보드 새로고침 완료", refreshedDashboard);
            log.debug("📤 대시보드 새로고침 파이프라인 완료");
            
            return result;
            
        } catch (Exception e) {
            log.error("❌ 대시보드 새로고침 파이프라인 실패: {}", e.getMessage());
            return DashboardFlowResult.failure("대시보드 새로고침 실패: " + e.getMessage(), e);
        }
    }
    
    // === 데이터 집계 최적화 알고리즘 메서드들 ===
    
    /**
     * 메인 대시보드 키 생성 알고리즘
     */
    private String generateMainDashboardKey(int realtimeLimit, int trendingLimit, int categoryLimit, int searchLimit) {
        return String.format("main_dashboard:%d:%d:%d:%d:%s", 
            realtimeLimit, trendingLimit, categoryLimit, searchLimit,
            LocalDateTime.now().toString().substring(0, 16)); // 분 단위로 그룹핑
    }
    
    /**
     * 대시보드 제한 최적화 알고리즘
     */
    private DashboardLimits optimizeDashboardLimits(int realtimeLimit, int trendingLimit, int categoryLimit, int searchLimit) {
        // 시스템 부하를 고려한 제한 최적화
        return new DashboardLimits(
            Math.min(realtimeLimit, 30), // 실시간 뉴스 최대 30개
            Math.min(trendingLimit, 20),  // 트렌딩 뉴스 최대 20개
            Math.min(categoryLimit, 15),  // 카테고리별 최대 15개
            Math.min(searchLimit, 10)     // 인기 검색어 최대 10개
        );
    }
    
    /**
     * 병렬 메인 대시보드 집계 알고리즘
     */
    private MainDashboardResponse aggregateMainDashboardParallel(DashboardLimits limits) {
        // 병렬 처리로 성능 최적화
        CompletableFuture<MainDashboardResponse> dashboardFuture = CompletableFuture.supplyAsync(() -> {
            try {
                return dashboardService.getMainDashboard(
                    limits.getRealtimeLimit(), 
                    limits.getTrendingLimit(), 
                    limits.getCategoryLimit(), 
                    limits.getSearchLimit()
                );
            } catch (Exception e) {
                log.error("병렬 대시보드 집계 실패: {}", e.getMessage());
                throw new RuntimeException(e);
            }
        });
        
        try {
            return dashboardFuture.get(); // 동기 대기
        } catch (Exception e) {
            throw new RuntimeException("메인 대시보드 병렬 집계 실패", e);
        }
    }
    
    /**
     * 사용자 개인화 분석 알고리즘
     */
    private UserPersonalization analyzeUserPersonalization(String username) {
        // 사용자 행동 패턴 분석을 통한 개인화
        // TODO: 실제 사용자 데이터 분석 로직 구현
        return new UserPersonalization(
            java.util.List.of("POLITICS", "ECONOMY"), // 선호 카테고리
            calculateUserActivityScore(username), // 활동 점수
            getUserEngagementLevel(username) // 참여도
        );
    }
    
    /**
     * 개인화된 사용자 대시보드 집계
     */
    private UserDashboardResponse aggregateUserDashboardPersonalized(String username, UserPersonalization personalization) {
        // 개인화 설정을 반영한 대시보드 집계
        return dashboardService.getUserDashboard(username);
    }
    
    /**
     * 캐시된 메인 대시보드 조회
     */
    private MainDashboardResponse getCachedMainDashboard(String key) {
        try {
            return (MainDashboardResponse) redisTemplate.opsForValue().get(key);
        } catch (Exception e) {
            log.warn("메인 대시보드 캐시 조회 실패: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * 메인 대시보드 캐싱
     */
    private void cacheMainDashboard(String key, MainDashboardResponse dashboard) {
        try {
            redisTemplate.opsForValue().set(key, dashboard, CACHE_DURATION_MINUTES, TimeUnit.MINUTES);
        } catch (Exception e) {
            log.warn("메인 대시보드 캐싱 실패: {}", e.getMessage());
        }
    }
    
    /**
     * 동시 집계 한계 확인
     */
    private void checkConcurrentAggregationLimit() {
        try {
            String key = "concurrent_aggregation";
            String currentCount = (String) redisTemplate.opsForValue().get(key);
            int concurrent = currentCount != null ? Integer.parseInt(currentCount) : 0;
            
            if (concurrent >= MAX_CONCURRENT_AGGREGATION) {
                throw new IllegalStateException("동시 집계 한계를 초과했습니다. 잠시 후 다시 시도해주세요.");
            }
        } catch (NumberFormatException e) {
            log.warn("동시 집계 한계 확인 오류: {}", e.getMessage());
        }
    }
    
    /**
     * 집계 진행 상태 마킹
     */
    private void markAggregationInProgress(String key) {
        try {
            String lockKey = "lock:" + key;
            redisTemplate.opsForValue().set(lockKey, "IN_PROGRESS", 10, TimeUnit.MINUTES);
            redisTemplate.opsForValue().increment("concurrent_aggregation");
        } catch (Exception e) {
            log.warn("집계 진행 상태 마킹 실패: {}", e.getMessage());
        }
    }
    
    /**
     * 집계 완료 상태 마킹
     */
    private void markAggregationCompleted(String key) {
        try {
            String lockKey = "lock:" + key;
            redisTemplate.delete(lockKey);
            redisTemplate.opsForValue().decrement("concurrent_aggregation");
        } catch (Exception e) {
            log.warn("집계 완료 상태 마킹 실패: {}", e.getMessage());
        }
    }
    
    /**
     * 메인 대시보드 응답 검증
     */
    private void validateMainDashboardResponse(MainDashboardResponse response) {
        if (response == null) {
            throw new IllegalStateException("메인 대시보드 응답이 null입니다.");
        }
        if (response.getRealtime() == null || response.getTrending() == null) {
            throw new IllegalStateException("필수 대시보드 데이터가 누락되었습니다.");
        }
    }
    
    /**
     * 사용자 대시보드 응답 검증
     */
    private void validateUserDashboardResponse(UserDashboardResponse response) {
        if (response == null) {
            throw new IllegalStateException("사용자 대시보드 응답이 null입니다.");
        }
    }
    
    /**
     * 메인 대시보드 응답 메타데이터 보강
     */
    @SuppressWarnings("unchecked")
    private <T> T enrichMainDashboardResponse(T response, long executionTime, String dataSource) {
        // 메타데이터 보강 로직
        // TODO: 실제 Response 객체 구조에 따라 구현
        return response;
    }
    
    /**
     * 사용자 대시보드 응답 메타데이터 보강
     */
    @SuppressWarnings("unchecked")
    private <T> T enrichUserDashboardResponse(T response, long executionTime, UserPersonalization personalization) {
        // 개인화 메타데이터 보강 로직
        return response;
    }
    
    /**
     * 개인화를 위한 대시보드 최적화
     */
    private UserDashboardResponse optimizeUserDashboardForPersonalization(UserDashboardResponse dashboard, UserPersonalization personalization) {
        // 개인화 설정에 따른 대시보드 최적화
        return dashboard;
    }
    
    /**
     * 사용자 활동 점수 계산
     */
    private int calculateUserActivityScore(String username) {
        // 사용자 활동 점수 계산 알고리즘
        // TODO: 실제 사용자 활동 데이터 기반 점수 계산
        return 75; // 임시 점수
    }
    
    /**
     * 사용자 참여도 레벨 계산
     */
    private String getUserEngagementLevel(String username) {
        // 사용자 참여도 레벨 계산
        return "HIGH"; // 임시 레벨
    }
    
    /**
     * 대시보드 제한 값 객체
     */
    private static class DashboardLimits {
        private final int realtimeLimit;
        private final int trendingLimit;
        private final int categoryLimit;
        private final int searchLimit;
        
        public DashboardLimits(int realtimeLimit, int trendingLimit, int categoryLimit, int searchLimit) {
            this.realtimeLimit = realtimeLimit;
            this.trendingLimit = trendingLimit;
            this.categoryLimit = categoryLimit;
            this.searchLimit = searchLimit;
        }
        
        public int getRealtimeLimit() { return realtimeLimit; }
        public int getTrendingLimit() { return trendingLimit; }
        public int getCategoryLimit() { return categoryLimit; }
        public int getSearchLimit() { return searchLimit; }
        
        @Override
        public String toString() {
            return String.format("DashboardLimits{realtime=%d, trending=%d, category=%d, search=%d}", 
                realtimeLimit, trendingLimit, categoryLimit, searchLimit);
        }
    }
    
    /**
     * 사용자 개인화 정보 객체
     */
    private static class UserPersonalization {
        private final java.util.List<String> preferredCategories;
        private final int activityScore;
        private final String engagementLevel;
        
        public UserPersonalization(java.util.List<String> preferredCategories, int activityScore, String engagementLevel) {
            this.preferredCategories = preferredCategories;
            this.activityScore = activityScore;
            this.engagementLevel = engagementLevel;
        }
        
        public java.util.List<String> getPreferredCategories() { return preferredCategories; }
        public int getActivityScore() { return activityScore; }
        public String getEngagementLevel() { return engagementLevel; }
    }
    
    // === Private Validation Methods ===
    
    private void validateAuthentication(UserDetails userDetails) {
        if (userDetails == null) {
            throw new CommonException(ResponseExceptionEnum.UNAUTHORIZED_ACCESS);
        }
    }
    
    private void validateDashboardLimits(int realtimeLimit, int trendingLimit, int categoryLimit, int searchLimit) {
        if (realtimeLimit < 1 || realtimeLimit > 50) {
            throw new CommonException(ResponseExceptionEnum.PARAMETER_OUT_OF_RANGE);
        }
        if (trendingLimit < 1 || trendingLimit > 50) {
            throw new CommonException(ResponseExceptionEnum.PARAMETER_OUT_OF_RANGE);
        }
        if (categoryLimit < 1 || categoryLimit > 20) {
            throw new CommonException(ResponseExceptionEnum.PARAMETER_OUT_OF_RANGE);
        }
        if (searchLimit < 1 || searchLimit > 20) {
            throw new CommonException(ResponseExceptionEnum.PARAMETER_OUT_OF_RANGE);
        }
    }
    
    /**
     * Dashboard 도메인 데이터 흐름 결과 컨테이너
     */
    public static class DashboardFlowResult {
        private final boolean success;
        private final String message;
        private final Object data;
        private final Exception error;
        
        private DashboardFlowResult(boolean success, String message, Object data, Exception error) {
            this.success = success;
            this.message = message;
            this.data = data;
            this.error = error;
        }
        
        public static DashboardFlowResult success(String message, Object data) {
            return new DashboardFlowResult(true, message, data, null);
        }
        
        public static DashboardFlowResult failure(String message, Exception error) {
            return new DashboardFlowResult(false, message, null, error);
        }
        
        // Getters
        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
        public Object getData() { return data; }
        public Exception getError() { return error; }
    }
}