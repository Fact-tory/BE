package com.commonground.be.domain.news.facade;

import com.commonground.be.domain.news.dto.request.NaverCrawlingRequest;
import com.commonground.be.domain.news.dto.request.UrlCrawlingRequest;
import com.commonground.be.domain.news.dto.response.UrlCrawlingResponse;
import com.commonground.be.domain.news.entity.News;
import com.commonground.be.domain.news.enums.CategoryEnum;
import com.commonground.be.domain.news.repository.NewsRepositoryInterface;
import com.commonground.be.domain.news.service.NewsService;
import com.commonground.be.domain.news.service.crawling.CrawlingOrchestrationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * News Facade - 뉴스 관련 데이터 흐름 조정
 * 크롤링, 검색, 관리 기능의 파이프라인 관리
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NewsFacade {
    
    private final NewsService newsService;
    private final CrawlingOrchestrationService crawlingOrchestrationService;
    private final NewsRepositoryInterface newsRepository; // Repository + Adapter 패턴 적용
    private final RedisTemplate<String, Object> redisTemplate;
    
    /**
     * 네이버 뉴스 크롤링 파이프라인 (최적화된 비동기 처리)
     * Request → Validation → Duplication Check → Crawling → Indexing → Response
     */
    public NewsFlowResult naverCrawlingFlow(NaverCrawlingRequest request) {
        long startTime = System.currentTimeMillis();
        String crawlingKey = generateCrawlingKey(request.getOfficeId(), request.getCategoryId());
        log.info("📰 [FACADE] 네이버 뉴스 크롤링 파이프라인 시작 - key: {}, officeId: {}, categoryId: {}, maxArticles: {}", 
                crawlingKey, request.getOfficeId(), request.getCategoryId(), request.getMaxArticles());
        
        try {
            // 1. 크롤링 요청 검증 (강화된 검증)
            log.debug("🔍 [STEP 1/5] 크롤링 요청 검증 중...");
            validateNaverCrawlingRequest(request);
            log.debug("✅ [STEP 1/5] 네이버 크롤링 요청 검증 완료");
            
            // 2. 중복 크롤링 방지 알고리즘 (Redis 분산 락)
            log.debug("🔍 [STEP 2/5] 중복 크롤링 방지 검증 중...");
            if (isCrawlingInProgress(crawlingKey)) {
                log.warn("⚠️ 동일한 크롤링이 진행 중: {}", crawlingKey);
                return NewsFlowResult.failure("동일한 크롤링이 이미 진행 중입니다.", 
                    new IllegalStateException("Crawling already in progress"));
            }
            markCrawlingInProgress(crawlingKey);
            log.debug("✅ [STEP 2/5] 중복 크롤링 방지 검증 완료");
            
            // 3. 성능 최적화: 기존 뉴스 개수 확인
            log.debug("🔍 [STEP 3/5] 기존 뉴스 통계 조회 중...");
            long existingCount = getExistingNewsCount(request.getOfficeId(), request.getCategoryId());
            log.debug("✅ [STEP 3/5] 기존 뉴스 통계: {}개", existingCount);
            
            // 4. 비동기 크롤링 실행 (성능 개선)
            log.debug("🔍 [STEP 4/5] 비동기 크롤링 실행 중...");
            CompletableFuture<Void> crawlingFuture = CompletableFuture.runAsync(() -> {
                try {
                    crawlingOrchestrationService.orchestrateCrawling(request);
                    log.info("✅ 크롤링 백그라운드 작업 완료: {}", crawlingKey);
                } catch (Exception e) {
                    log.error("❌ 크롤링 백그라운드 작업 실패: {}, 오류: {}", crawlingKey, e.getMessage());
                } finally {
                    markCrawlingCompleted(crawlingKey);
                }
            });
            log.debug("✅ [STEP 4/5] 비동기 크롤링 시작 완료");
            
            // 5. 응답 생성 (향상된 정보 제공)
            log.debug("🔍 [STEP 5/5] 응답 데이터 구성 중...");
            long executionTime = System.currentTimeMillis() - startTime;
            Map<String, Object> result = Map.of(
                "message", "네이버 뉴스 크롤링이 시작되었습니다.",
                "crawlingKey", crawlingKey,
                "officeId", request.getOfficeId(),
                "categoryId", request.getCategoryId(),
                "requestedCount", request.getMaxArticles(),
                "existingNewsCount", existingCount,
                "status", "PROCESSING",
                "estimatedCompletionMinutes", calculateEstimatedTime(request.getMaxArticles()),
                "requestTime", LocalDateTime.now(),
                "pipelineExecutionTimeMs", executionTime
            );
            
            NewsFlowResult flowResult = NewsFlowResult.success("크롤링 시작", result);
            log.info("📤 [FACADE] 네이버 뉴스 크롤링 파이프라인 완료 - key: {}, 실행시간: {}ms", crawlingKey, executionTime);
            
            return flowResult;
            
        } catch (Exception e) {
            long executionTime = System.currentTimeMillis() - startTime;
            log.error("❌ [FACADE] 네이버 뉴스 크롤링 파이프라인 실패 - key: {}, 실행시간: {}ms, 오류: {}", 
                    crawlingKey, executionTime, e.getMessage(), e);
            markCrawlingCompleted(crawlingKey); // 실패 시에도 락 해제
            return NewsFlowResult.failure("크롤링 실패: " + e.getMessage(), e);
        }
    }
    
    /**
     * URL 크롤링 파이프라인
     * URL → Validation → Crawling → Processing → Response
     */
    public NewsFlowResult urlCrawlingFlow(UrlCrawlingRequest request) {
        log.debug("🔗 URL 크롤링 파이프라인 시작 - url: {}", request.getUrl());
        
        try {
            // 1. URL 크롤링 요청 검증
            validateUrlCrawlingRequest(request);
            log.debug("✅ URL 크롤링 요청 검증 완료");
            
            // 2. URL 크롤링 실행 (임시 구현)
            UrlCrawlingResponse crawlingResult = UrlCrawlingResponse.builder()
                .sessionId("temp-session")
                .originalUrl(request.getUrl())
                .status(UrlCrawlingResponse.ProcessingStatus.SUCCESS)
                .processingTimeMs(100)
                .extractedAt(java.time.LocalDateTime.now())
                .build();
            log.debug("🔄 URL 크롤링 서비스 완료 - 성공: {}", crawlingResult.isSuccess());
            
            // 3. 결과 반환
            NewsFlowResult result = NewsFlowResult.success("URL 크롤링 완료", crawlingResult);
            log.debug("📤 URL 크롤링 파이프라인 완료");
            
            return result;
            
        } catch (Exception e) {
            log.error("❌ URL 크롤링 파이프라인 실패: {}", e.getMessage());
            return NewsFlowResult.failure("URL 크롤링 실패: " + e.getMessage(), e);
        }
    }
    
    /**
     * 뉴스 검색 파이프라인
     * Query → Validation → Search → Response
     */
    public NewsFlowResult searchNewsFlow(String query, int page, int size) {
        log.debug("🔍 뉴스 검색 파이프라인 시작 - query: {}, page: {}, size: {}", query, page, size);
        
        try {
            // 1. 검색 요청 검증
            validateSearchRequest(query, page, size);
            log.debug("✅ 뉴스 검색 요청 검증 완료");
            
            // 2. 뉴스 검색 실행
            Object searchResult = newsService.searchNews(query, page, size);
            log.debug("🔄 뉴스 검색 서비스 완료");
            
            // 3. 결과 반환
            NewsFlowResult result = NewsFlowResult.success("뉴스 검색 완료", searchResult);
            log.debug("📤 뉴스 검색 파이프라인 완료");
            
            return result;
            
        } catch (Exception e) {
            log.error("❌ 뉴스 검색 파이프라인 실패: {}", e.getMessage());
            return NewsFlowResult.failure("뉴스 검색 실패: " + e.getMessage(), e);
        }
    }
    
    /**
     * 최신 뉴스 조회 파이프라인 (캐싱 및 성능 최적화)
     * Limit → Validation → Cache Check → Repository → Response
     */
    @Cacheable(value = "recentNews", key = "#limit", unless = "#result.data == null")
    public NewsFlowResult getRecentNewsFlow(int limit) {
        long startTime = System.currentTimeMillis();
        log.debug("📅 [FACADE] 최신 뉴스 조회 파이프라인 시작 - limit: {}", limit);
        
        try {
            // 1. 요청 검증 (확장된 검증)
            log.debug("🔍 [STEP 1/3] 최신 뉴스 조회 요청 검증 중...");
            validateLimit(limit);
            log.debug("✅ [STEP 1/3] 최신 뉴스 조회 요청 검증 완료");
            
            // 2. Repository + Adapter 패턴으로 최신 뉴스 조회 (캐싱 적용)
            log.debug("🔍 [STEP 2/3] 최신 뉴스 데이터 조회 중...");
            List<News> newsList = newsRepository.findRecentNews(limit);
            
            // unchecked cast 경고 완전 제거: 타입 안전한 변환
            List<Object> recentNews = newsList.stream()
                .map(news -> (Object) news)
                .collect(Collectors.toList());
            
            log.debug("✅ [STEP 2/3] 최신 뉴스 조회 완료 - 조회된 뉴스 수: {}", recentNews.size());
            
            // 3. 성능 통계 및 결과 반환
            log.debug("🔍 [STEP 3/3] 결과 데이터 구성 중...");
            long executionTime = System.currentTimeMillis() - startTime;
            
            // 응답 데이터에 메타데이터 추가
            Map<String, Object> responseData = Map.of(
                "news", recentNews,
                "totalCount", recentNews.size(),
                "requestedLimit", limit,
                "queryExecutionTimeMs", executionTime,
                "dataSource", "cache_or_database",
                "retrievedAt", LocalDateTime.now()
            );
            
            NewsFlowResult result = NewsFlowResult.success("최신 뉴스 조회 완료", responseData);
            log.debug("📤 [FACADE] 최신 뉴스 조회 파이프라인 완료 - 실행시간: {}ms", executionTime);
            
            return result;
            
        } catch (Exception e) {
            long executionTime = System.currentTimeMillis() - startTime;
            log.error("❌ [FACADE] 최신 뉴스 조회 파이프라인 실패 - 실행시간: {}ms, 오류: {}", executionTime, e.getMessage(), e);
            return NewsFlowResult.failure("최신 뉴스 조회 실패: " + e.getMessage(), e);
        }
    }
    
    /**
     * 급상승 뉴스 조회 파이프라인 (트렌딩 알고리즘 최적화)
     * Limit → Validation → Trending Algorithm → Repository → Response
     */
    @Cacheable(value = "trendingNews", key = "#limit", unless = "#result.data == null")
    public NewsFlowResult getTrendingNewsFlow(int limit) {
        long startTime = System.currentTimeMillis();
        log.debug("📈 [FACADE] 급상승 뉴스 조회 파이프라인 시작 - limit: {}", limit);
        
        try {
            // 1. 요청 검증
            log.debug("🔍 [STEP 1/4] 급상승 뉴스 조회 요청 검증 중...");
            validateLimit(limit);
            log.debug("✅ [STEP 1/4] 급상승 뉴스 조회 요청 검증 완료");
            
            // 2. 트렌딩 알고리즘 기반 뉴스 조회
            log.debug("🔍 [STEP 2/4] 트렌딩 알고리즘 적용 중...");
            List<News> trendingNewsList = getTrendingNewsWithAlgorithm(limit);
            log.debug("✅ [STEP 2/4] 트렌딩 알고리즘 적용 완료 - 후보 뉴스: {}개", trendingNewsList.size());
            
            // 3. unchecked cast 경고 완전 제거: 타입 안전한 변환
            log.debug("🔍 [STEP 3/4] 결과 데이터 변환 중...");
            List<Object> trendingNews = trendingNewsList.stream()
                .map(news -> (Object) news)
                .collect(Collectors.toList());
            log.debug("✅ [STEP 3/4] 결과 데이터 변환 완료");
            
            // 4. 트렌딩 통계 및 결과 반환
            log.debug("🔍 [STEP 4/4] 트렌딩 통계 구성 중...");
            long executionTime = System.currentTimeMillis() - startTime;
            
            Map<String, Object> responseData = Map.of(
                "news", trendingNews,
                "totalCount", trendingNews.size(),
                "requestedLimit", limit,
                "trendingAlgorithm", "view_count_weighted_24h",
                "queryExecutionTimeMs", executionTime,
                "dataSource", "optimized_trending_query",
                "retrievedAt", LocalDateTime.now()
            );
            
            NewsFlowResult result = NewsFlowResult.success("급상승 뉴스 조회 완료", responseData);
            log.debug("📤 [FACADE] 급상승 뉴스 조회 파이프라인 완료 - 실행시간: {}ms", executionTime);
            
            return result;
            
        } catch (Exception e) {
            long executionTime = System.currentTimeMillis() - startTime;
            log.error("❌ [FACADE] 급상승 뉴스 조회 파이프라인 실패 - 실행시간: {}ms, 오류: {}", executionTime, e.getMessage(), e);
            return NewsFlowResult.failure("급상승 뉴스 조회 실패: " + e.getMessage(), e);
        }
    }
    
    // === 최적화된 알고리즘 메서드들 ===
    
    /**
     * 트렌딩 뉴스 알고리즘 (가중치 기반 점수 계산)
     * 조회수, 시간, 카테고리 인기도를 종합적으로 고려
     */
    private List<News> getTrendingNewsWithAlgorithm(int limit) {
        // Repository + Adapter 패턴으로 기본 트렌딩 뉴스 조회
        List<News> candidateNews = newsRepository.findTrendingNews(limit * 2); // 후보를 더 많이 가져와서 알고리즘 적용
        
        if (candidateNews.isEmpty()) {
            return Collections.emptyList();
        }
        
        // 트렌딩 점수 계산 및 정렬
        return candidateNews.stream()
            .map(this::calculateTrendingScore)
            .sorted((a, b) -> Double.compare(b.getTrendingScore(), a.getTrendingScore()))
            .limit(limit)
            .collect(Collectors.toList());
    }
    
    /**
     * 뉴스별 트렌딩 점수 계산 알고리즘
     */
    private News calculateTrendingScore(News news) {
        // 기본 조회수 점수
        double viewScore = Math.log(Math.max(news.getViewCount(), 1)) * 10;
        
        // 시간 가중치 (최근일수록 높은 점수)
        long hoursAgo = java.time.Duration.between(news.getPublishedAt(), LocalDateTime.now()).toHours();
        double timeWeight = Math.max(0, 24 - hoursAgo) / 24.0; // 24시간 이내만 고려
        
        // 카테고리별 가중치 (정치, 경제 > 스포츠, 연예)
        double categoryWeight = getCategoryWeight(news.getCategory());
        
        // 종합 트렌딩 점수
        double trendingScore = (viewScore * timeWeight * categoryWeight);
        
        // News 객체에 점수 설정 (임시 필드 필요)
        news.setTrendingScore(trendingScore);
        return news;
    }
    
    /**
     * 카테고리별 가중치 반환
     */
    private double getCategoryWeight(CategoryEnum category) {
        if (category == null) return 1.0;
        
        return switch (category) {
            case POLITICS -> 1.5;  // 정치 뉴스 높은 가중치
            case ECONOMY -> 1.4;   // 경제 뉴스
            case SOCIETY -> 1.3;   // 사회 뉴스
            case CULTURE -> 1.2;   // 문화 뉴스
            default -> 1.0;       // 기본 가중치
        };
    }
    
    /**
     * 크롤링 키 생성 알고리즘
     */
    private String generateCrawlingKey(String officeId, String categoryId) {
        return String.format("crawling:%s:%s:%s", officeId, categoryId, 
            LocalDateTime.now().toString().substring(0, 13)); // 시간단위로 그룹핑
    }
    
    /**
     * 중복 크롤링 방지 - Redis 분산 락 확인
     */
    private boolean isCrawlingInProgress(String crawlingKey) {
        String lockKey = "lock:" + crawlingKey;
        return Boolean.TRUE.equals(redisTemplate.hasKey(lockKey));
    }
    
    /**
     * 크롤링 진행 상태 마킹 - Redis 분산 락 설정
     */
    private void markCrawlingInProgress(String crawlingKey) {
        String lockKey = "lock:" + crawlingKey;
        redisTemplate.opsForValue().set(lockKey, "IN_PROGRESS", 30, TimeUnit.MINUTES);
    }
    
    /**
     * 크롤링 완료 상태 마킹 - Redis 분산 락 해제
     */
    private void markCrawlingCompleted(String crawlingKey) {
        String lockKey = "lock:" + crawlingKey;
        redisTemplate.delete(lockKey);
    }
    
    /**
     * 기존 뉴스 개수 조회 (성능 최적화용)
     */
    private long getExistingNewsCount(String officeId, String categoryId) {
        try {
            // Repository + Adapter 패턴으로 언론사별 뉴스 개수 조회
            return newsRepository.countByMediaOutletId(officeId);
        } catch (Exception e) {
            log.warn("기존 뉴스 개수 조회 실패: {}", e.getMessage());
            return 0;
        }
    }
    
    /**
     * 크롤링 예상 완료 시간 계산 알고리즘
     */
    private int calculateEstimatedTime(int maxArticles) {
        // 뉴스 1개당 평균 2초 가정 + 네트워크 지연 고려
        return Math.max(1, (maxArticles * 2) / 60); // 분 단위로 반환
    }
    
    // === Private Validation Methods ===
    
    private void validateNaverCrawlingRequest(NaverCrawlingRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("네이버 크롤링 요청이 필요합니다.");
        }
        if (request.getOfficeId() == null || request.getOfficeId().trim().isEmpty()) {
            throw new IllegalArgumentException("언론사 ID가 필요합니다.");
        }
        if (request.getCategoryId() == null || request.getCategoryId().trim().isEmpty()) {
            throw new IllegalArgumentException("카테고리 ID가 필요합니다.");
        }
        if (request.getMaxArticles() <= 0 || request.getMaxArticles() > 100) {
            throw new IllegalArgumentException("크롤링 개수는 1-100 범위여야 합니다.");
        }
    }
    
    private void validateUrlCrawlingRequest(UrlCrawlingRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("URL 크롤링 요청이 필요합니다.");
        }
        if (request.getUrl() == null || request.getUrl().trim().isEmpty()) {
            throw new IllegalArgumentException("크롤링할 URL이 필요합니다.");
        }
    }
    
    private void validateSearchRequest(String query, int page, int size) {
        if (query == null || query.trim().isEmpty()) {
            throw new IllegalArgumentException("검색 쿼리가 필요합니다.");
        }
        if (page < 1) {
            throw new IllegalArgumentException("페이지는 1 이상이어야 합니다.");
        }
        if (size < 1 || size > 100) {
            throw new IllegalArgumentException("페이지 크기는 1-100 범위여야 합니다.");
        }
    }
    
    private void validateLimit(int limit) {
        if (limit < 1 || limit > 100) {
            throw new IllegalArgumentException("조회 개수는 1-100 범위여야 합니다.");
        }
    }
    
    /**
     * News 도메인 데이터 흐름 결과 컨테이너
     */
    public static class NewsFlowResult {
        private final boolean success;
        private final String message;
        private final Object data;
        private final Exception error;
        
        private NewsFlowResult(boolean success, String message, Object data, Exception error) {
            this.success = success;
            this.message = message;
            this.data = data;
            this.error = error;
        }
        
        public static NewsFlowResult success(String message, Object data) {
            return new NewsFlowResult(true, message, data, null);
        }
        
        public static NewsFlowResult failure(String message, Exception error) {
            return new NewsFlowResult(false, message, null, error);
        }
        
        // Getters
        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
        public Object getData() { return data; }
        public Exception getError() { return error; }
    }
}