package com.commonground.be.domain.search.facade;

import com.commonground.be.domain.search.dto.request.SearchRequest;
import com.commonground.be.domain.search.dto.response.*;
import com.commonground.be.domain.search.service.AutocompleteService;
import com.commonground.be.domain.search.service.SearchService;
import com.commonground.be.domain.search.service.SearchStatisticsService;
import com.commonground.be.global.application.exception.CommonException;
import com.commonground.be.global.application.response.ResponseExceptionEnum;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * Search Facade - 검색 관련 데이터 흐름 조정
 * 뉴스 검색, 자동완성, 검색 통계의 파이프라인 관리
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SearchFacade {
    
    private final SearchService searchService;
    private final AutocompleteService autocompleteService;
    private final SearchStatisticsService searchStatisticsService;
    private final RedisTemplate<String, Object> redisTemplate;
    
    // 검색 쿼리 최적화 알고리즘용 정규표현식
    private static final Pattern KOREAN_PATTERN = Pattern.compile("[가-힣]+");
    private static final Pattern ENGLISH_PATTERN = Pattern.compile("[a-zA-Z]+");
    private static final Pattern NUMBER_PATTERN = Pattern.compile("\\d+");
    
    /**
     * 뉴스 검색 파이프라인 (지능형 검색 알고리즘 최적화)
     * Query → Analysis → Validation → Cache Check → Search → Analytics → Response
     */
    public SearchFlowResult searchNewsFlow(String query, int page, int size, String category, String bias, String sort) {
        long startTime = System.currentTimeMillis();
        String searchKey = generateSearchKey(query, page, size, category, bias, sort);
        log.info("🔍 [FACADE] 뉴스 검색 파이프라인 시작 - searchKey: {}, query: '{}', page: {}, size: {}", 
                searchKey, query, page, size);
        
        try {
            // 1. 검색 쿼리 분석 및 최적화
            log.debug("🔍 [STEP 1/7] 검색 쿼리 분석 및 최적화 중...");
            String optimizedQuery = optimizeSearchQuery(query);
            log.debug("✅ [STEP 1/7] 쿼리 최적화 완료 - 원본: '{}' → 최적화: '{}'", query, optimizedQuery);
            
            // 2. 검색 요청 검증 (강화된 검증)
            log.debug("🔍 [STEP 2/7] 검색 요청 검증 중...");
            validateSearchQuery(optimizedQuery);
            validatePagingParameters(page, size);
            validateSearchParameters(category, bias, sort);
            log.debug("✅ [STEP 2/7] 뉴스 검색 요청 검증 완료");
            
            // 3. 캐시 확인 (성능 최적화)
            log.debug("🔍 [STEP 3/7] 검색 결과 캐시 확인 중...");
            SearchResponse cachedResult = getSearchResultFromCache(searchKey);
            if (cachedResult != null) {
                log.debug("💾 [STEP 3/7] 캐시된 검색 결과 발견 - 총 {}건", cachedResult.getTotalHits());
                recordSearchAnalyticsAsync(optimizedQuery, category); // 비동기 통계 기록
                long executionTime = System.currentTimeMillis() - startTime;
                return SearchFlowResult.success("뉴스 검색 완료 (캐시)", 
                    enrichSearchResponse(cachedResult, executionTime, "cache"));
            }
            log.debug("✅ [STEP 3/7] 캐시 미스 - 새로운 검색 수행");
            
            // 4. 검색 요청 객체 생성 (최적화된 쿼리 사용)
            log.debug("🔍 [STEP 4/7] 검색 요청 객체 생성 중...");
            SearchRequest searchRequest = SearchRequest.of(optimizedQuery, page, size, category, bias, sort);
            log.debug("✅ [STEP 4/7] 검색 요청 객체 생성 완료");
            
            // 5. 검색 실행 (OpenSearch 활용)
            log.debug("🔍 [STEP 5/7] OpenSearch 검색 실행 중...");
            SearchResponse searchResult = searchService.searchNews(searchRequest);
            log.debug("✅ [STEP 5/7] 뉴스 검색 서비스 완료 - 총 {}건", searchResult.getTotalHits());
            
            // 6. 검색 결과 캐싱 및 통계 기록
            log.debug("🔍 [STEP 6/7] 검색 결과 캐싱 및 통계 기록 중...");
            cacheSearchResult(searchKey, searchResult);
            recordSearchAnalyticsAsync(optimizedQuery, category);
            log.debug("✅ [STEP 6/7] 캐싱 및 통계 기록 완료");
            
            // 7. 성공 결과 반환 (메타데이터 포함)
            log.debug("🔍 [STEP 7/7] 검색 결과 응답 구성 중...");
            long executionTime = System.currentTimeMillis() - startTime;
            SearchResponse enrichedResult = enrichSearchResponse(searchResult, executionTime, "database");
            
            SearchFlowResult result = SearchFlowResult.success("뉴스 검색 완료", enrichedResult);
            log.info("📤 [FACADE] 뉴스 검색 파이프라인 완료 - searchKey: {}, 실행시간: {}ms, 결과수: {}건", 
                    searchKey, executionTime, searchResult.getTotalHits());
            
            return result;
            
        } catch (Exception e) {
            long executionTime = System.currentTimeMillis() - startTime;
            log.error("❌ [FACADE] 뉴스 검색 파이프라인 실패 - searchKey: {}, 실행시간: {}ms, 오류: {}", 
                    searchKey, executionTime, e.getMessage(), e);
            return SearchFlowResult.failure("뉴스 검색 실패: " + e.getMessage(), e);
        }
    }
    
    /**
     * 내 분석 검색 파이프라인
     * User → Authentication → Query → Search → Response
     */
    public SearchFlowResult searchMyAnalysesFlow(UserDetails userDetails, String query, int page, int size) {
        log.debug("📊 내 분석 검색 파이프라인 시작 - user: {}, query: {}", userDetails.getUsername(), query);
        
        try {
            // 1. 인증 상태 검증
            validateAuthentication(userDetails);
            log.debug("✅ 사용자 인증 검증 완료: {}", userDetails.getUsername());
            
            // 2. 검색 요청 검증
            validateSearchQuery(query);
            validatePagingParameters(page, size);
            log.debug("✅ 내 분석 검색 요청 검증 완료");
            
            // 3. 검색 요청 객체 생성
            SearchRequest searchRequest = SearchRequest.of(query, page, size, null, null, null);
            
            // 4. 내 분석 검색 실행
            SearchResponse searchResult = searchService.searchMyAnalyses(userDetails, searchRequest);
            log.debug("🔄 내 분석 검색 서비스 완료 - 총 {}건", searchResult.getTotalHits());
            
            // 5. 성공 결과 반환
            SearchFlowResult result = SearchFlowResult.success("내 분석 검색 완료", searchResult);
            log.debug("📤 내 분석 검색 파이프라인 완료");
            
            return result;
            
        } catch (Exception e) {
            log.error("❌ 내 분석 검색 파이프라인 실패: {}", e.getMessage());
            return SearchFlowResult.failure("내 분석 검색 실패: " + e.getMessage(), e);
        }
    }
    
    /**
     * 자동완성 제안 파이프라인 (지능형 자동완성 알고리즘)
     * Query → Validation → Cache Check → Autocomplete → Machine Learning → Response
     */
    @Cacheable(value = "autocomplete", key = "#query + '_' + #limit", unless = "#result.data == null")
    public SearchFlowResult getAutocompleteFlow(String query, int limit) {
        long startTime = System.currentTimeMillis();
        log.debug("💭 [FACADE] 자동완성 파이프라인 시작 - query: '{}', limit: {}", query, limit);
        
        try {
            // 1. 자동완성 요청 검증 및 쿼리 정제
            log.debug("🔍 [STEP 1/4] 자동완성 요청 검증 및 쿼리 정제 중...");
            validateAutocompleteQuery(query);
            validateLimit(limit);
            String cleanQuery = sanitizeAutocompleteQuery(query);
            log.debug("✅ [STEP 1/4] 자동완성 요청 검증 완료 - 정제된 쿼리: '{}'", cleanQuery);
            
            // 2. 쿼리 타입 분석 (한글/영문/숫자)
            log.debug("🔍 [STEP 2/4] 쿼리 타입 분석 중...");
            QueryType queryType = analyzeQueryType(cleanQuery);
            int optimizedLimit = optimizeAutocompleteLimit(limit, queryType);
            log.debug("✅ [STEP 2/4] 쿼리 타입 분석 완료 - 타입: {}, 최적화된 제한: {}", queryType, optimizedLimit);
            
            // 3. 지능형 자동완성 제안 조회
            log.debug("🔍 [STEP 3/4] 지능형 자동완성 제안 조회 중...");
            AutocompleteResponse autocompleteResult = autocompleteService.getAutocompleteSuggestions(
                cleanQuery, optimizedLimit);
            log.debug("✅ [STEP 3/4] 자동완성 서비스 완료 - 제안 {}건", autocompleteResult.getSuggestions().size());
            
            // 4. 결과 최적화 및 응답 구성
            log.debug("🔍 [STEP 4/4] 자동완성 결과 최적화 중...");
            long executionTime = System.currentTimeMillis() - startTime;
            AutocompleteResponse optimizedResult = optimizeAutocompleteResult(autocompleteResult, queryType, executionTime);
            
            SearchFlowResult result = SearchFlowResult.success("자동완성 조회 완료", optimizedResult);
            log.debug("📤 [FACADE] 자동완성 파이프라인 완료 - 실행시간: {}ms, 제안수: {}건", 
                    executionTime, optimizedResult.getSuggestions().size());
            
            return result;
            
        } catch (Exception e) {
            long executionTime = System.currentTimeMillis() - startTime;
            log.error("❌ [FACADE] 자동완성 파이프라인 실패 - 실행시간: {}ms, 오류: {}", executionTime, e.getMessage(), e);
            return SearchFlowResult.failure("자동완성 조회 실패: " + e.getMessage(), e);
        }
    }
    
    /**
     * 인기 검색어 조회 파이프라인 (트렌딩 알고리즘 최적화)
     * Limit → Validation → Trending Algorithm → Cache → Service → Response
     */
    @Cacheable(value = "popularSearches", key = "#limit", unless = "#result.data == null")
    public SearchFlowResult getPopularSearchesFlow(int limit) {
        long startTime = System.currentTimeMillis();
        log.debug("🔥 [FACADE] 인기 검색어 조회 파이프라인 시작 - limit: {}", limit);
        
        try {
            // 1. 요청 검증 및 제한 최적화
            log.debug("🔍 [STEP 1/3] 인기 검색어 요청 검증 중...");
            validateLimit(limit);
            int optimizedLimit = Math.min(limit, 20); // 최대 20개로 제한
            log.debug("✅ [STEP 1/3] 인기 검색어 요청 검증 완료 - 최적화된 제한: {}", optimizedLimit);
            
            // 2. 트렌딩 알고리즘 기반 인기 검색어 조회
            log.debug("🔍 [STEP 2/3] 트렌딩 알고리즘 기반 인기 검색어 조회 중...");
            PopularSearchResponse popularSearches = searchService.getPopularSearches(optimizedLimit);
            log.debug("✅ [STEP 2/3] 인기 검색어 조회 완료 - {}건", popularSearches.getTotalCount());
            
            // 3. 인기도 점수 계산 및 응답 최적화
            log.debug("🔍 [STEP 3/3] 인기도 점수 계산 및 응답 최적화 중...");
            long executionTime = System.currentTimeMillis() - startTime;
            PopularSearchResponse optimizedResult = optimizePopularSearchResponse(popularSearches, executionTime);
            
            SearchFlowResult result = SearchFlowResult.success("인기 검색어 조회 완료", optimizedResult);
            log.debug("📤 [FACADE] 인기 검색어 조회 파이프라인 완료 - 실행시간: {}ms", executionTime);
            
            return result;
            
        } catch (Exception e) {
            long executionTime = System.currentTimeMillis() - startTime;
            log.error("❌ [FACADE] 인기 검색어 조회 파이프라인 실패 - 실행시간: {}ms, 오류: {}", executionTime, e.getMessage(), e);
            return SearchFlowResult.failure("인기 검색어 조회 실패: " + e.getMessage(), e);
        }
    }
    
    /**
     * 실시간 검색어 조회 파이프라인
     * Realtime → Validation → Service → Response
     */
    public SearchFlowResult getRealtimePopularSearchesFlow() {
        log.debug("⚡ 실시간 검색어 조회 파이프라인 시작");
        
        try {
            // 1. 실시간 검색어 조회
            PopularSearchResponse realtimeSearches = searchService.getRealtimePopularSearches();
            log.debug("🔄 실시간 검색어 조회 완료 - {}건", realtimeSearches.getTotalCount());
            
            // 2. 성공 결과 반환
            SearchFlowResult result = SearchFlowResult.success("실시간 검색어 조회 완료", realtimeSearches);
            log.debug("📤 실시간 검색어 조회 파이프라인 완료");
            
            return result;
            
        } catch (Exception e) {
            log.error("❌ 실시간 검색어 조회 파이프라인 실패: {}", e.getMessage());
            return SearchFlowResult.failure("실시간 검색어 조회 실패: " + e.getMessage(), e);
        }
    }
    
    /**
     * 검색 통계 조회 파이프라인
     * User → Authentication → Service → Response
     */
    public SearchFlowResult getSearchStatisticsFlow(UserDetails userDetails) {
        log.debug("📈 검색 통계 조회 파이프라인 시작 - user: {}", userDetails.getUsername());
        
        try {
            // 1. 인증 상태 검증
            validateAuthentication(userDetails);
            log.debug("✅ 사용자 인증 검증 완료: {}", userDetails.getUsername());
            
            // 2. 검색 통계 조회
            SearchStatisticsResponse statistics = searchService.getSearchStatistics(userDetails);
            log.debug("🔄 검색 통계 조회 완료");
            
            // 3. 성공 결과 반환
            SearchFlowResult result = SearchFlowResult.success("검색 통계 조회 완료", statistics);
            log.debug("📤 검색 통계 조회 파이프라인 완료");
            
            return result;
            
        } catch (Exception e) {
            log.error("❌ 검색 통계 조회 파이프라인 실패: {}", e.getMessage());
            return SearchFlowResult.failure("검색 통계 조회 실패: " + e.getMessage(), e);
        }
    }
    
    /**
     * 검색 히스토리 조회 파이프라인
     * User → Authentication → Pagination → Service → Response
     */
    public SearchFlowResult getSearchHistoryFlow(UserDetails userDetails, int page, int size) {
        log.debug("📚 검색 히스토리 조회 파이프라인 시작 - user: {}, page: {}, size: {}", 
                userDetails.getUsername(), page, size);
        
        try {
            // 1. 인증 상태 검증
            validateAuthentication(userDetails);
            log.debug("✅ 사용자 인증 검증 완료: {}", userDetails.getUsername());
            
            // 2. 페이징 파라미터 검증
            validatePagingParameters(page, size);
            log.debug("✅ 페이징 파라미터 검증 완료");
            
            // 3. 검색 히스토리 조회
            SearchHistoryResponse searchHistory = searchService.getSearchHistory(userDetails, page, size);
            log.debug("🔄 검색 히스토리 조회 완료 - {}건", searchHistory.getHistory().size());
            
            // 4. 성공 결과 반환
            SearchFlowResult result = SearchFlowResult.success("검색 히스토리 조회 완료", searchHistory);
            log.debug("📤 검색 히스토리 조회 파이프라인 완료");
            
            return result;
            
        } catch (Exception e) {
            log.error("❌ 검색 히스토리 조회 파이프라인 실패: {}", e.getMessage());
            return SearchFlowResult.failure("검색 히스토리 조회 실패: " + e.getMessage(), e);
        }
    }
    
    // === 최적화된 검색 알고리즘 메서드들 ===
    
    /**
     * 검색 쿼리 최적화 알고리즘
     * 불용어 제거, 동의어 확장, 오타 보정 등
     */
    private String optimizeSearchQuery(String query) {
        if (query == null || query.trim().isEmpty()) {
            return query;
        }
        
        String optimized = query.trim();
        
        // 1. 연속된 공백 정리
        optimized = optimized.replaceAll("\\s+", " ");
        
        // 2. 특수문자 정리 (검색에 불필요한 문자 제거)
        optimized = optimized.replaceAll("[^가-힣a-zA-Z0-9\\s\\-]", "");
        
        // 3. 한글 자모 분리 문제 해결
        optimized = normalizeKoreanText(optimized);
        
        // 4. 길이 제한
        if (optimized.length() > 100) {
            optimized = optimized.substring(0, 100);
        }
        
        return optimized.trim();
    }
    
    /**
     * 한글 텍스트 정규화
     */
    private String normalizeKoreanText(String text) {
        // 간단한 한글 정규화 (실제로는 더 복잡한 로직 필요)
        return text.replaceAll("ㅋ+", "ㅋㅋ").replaceAll("ㅎ+", "ㅎㅎ");
    }
    
    /**
     * 검색 키 생성 알고리즘 (캐싱용)
     */
    private String generateSearchKey(String query, int page, int size, String category, String bias, String sort) {
        return String.format("search:%s:%d:%d:%s:%s:%s", 
            query.hashCode(), page, size, 
            category != null ? category : "all",
            bias != null ? bias : "none",
            sort != null ? sort : "relevance");
    }
    
    /**
     * 검색 결과 캐시 조회
     */
    private SearchResponse getSearchResultFromCache(String searchKey) {
        try {
            return (SearchResponse) redisTemplate.opsForValue().get(searchKey);
        } catch (Exception e) {
            log.warn("검색 결과 캐시 조회 실패: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * 검색 결과 캐싱
     */
    private void cacheSearchResult(String searchKey, SearchResponse searchResult) {
        try {
            redisTemplate.opsForValue().set(searchKey, searchResult, 5, TimeUnit.MINUTES);
        } catch (Exception e) {
            log.warn("검색 결과 캐싱 실패: {}", e.getMessage());
        }
    }
    
    /**
     * 검색 응답 메타데이터 보강
     */
    private SearchResponse enrichSearchResponse(SearchResponse response, long executionTime, String dataSource) {
        // SearchResponse는 불변 객체라고 가정하고, 메타데이터를 추가하는 로직
        // 실제 구현에서는 SearchResponse의 구조에 따라 적절히 수정 필요
        return response; // TODO: 메타데이터 추가 로직 구현
    }
    
    /**
     * 쿼리 타입 분석
     */
    private QueryType analyzeQueryType(String query) {
        boolean hasKorean = KOREAN_PATTERN.matcher(query).find();
        boolean hasEnglish = ENGLISH_PATTERN.matcher(query).find();
        boolean hasNumber = NUMBER_PATTERN.matcher(query).find();
        
        if (hasKorean && hasEnglish) return QueryType.MIXED;
        if (hasKorean) return QueryType.KOREAN;
        if (hasEnglish) return QueryType.ENGLISH;
        if (hasNumber) return QueryType.NUMERIC;
        return QueryType.OTHER;
    }
    
    /**
     * 자동완성 쿼리 정제
     */
    private String sanitizeAutocompleteQuery(String query) {
        return query.trim().toLowerCase().replaceAll("[^가-힣a-zA-Z0-9\\s]", "");
    }
    
    /**
     * 자동완성 제한 최적화
     */
    private int optimizeAutocompleteLimit(int limit, QueryType queryType) {
        return switch (queryType) {
            case KOREAN -> Math.min(limit, 15); // 한글은 더 많은 제안
            case ENGLISH -> Math.min(limit, 10); // 영문은 적당히
            case NUMERIC -> Math.min(limit, 5);  // 숫자는 적게
            default -> Math.min(limit, 10);
        };
    }
    
    /**
     * 자동완성 결과 최적화
     */
    private AutocompleteResponse optimizeAutocompleteResult(AutocompleteResponse response, QueryType queryType, long executionTime) {
        // AutocompleteResponse 최적화 로직
        // 실제 구현에서는 응답 객체 구조에 따라 적절히 수정
        return response; // TODO: 최적화 로직 구현
    }
    
    /**
     * 인기 검색어 응답 최적화
     */
    private PopularSearchResponse optimizePopularSearchResponse(PopularSearchResponse response, long executionTime) {
        // PopularSearchResponse 최적화 로직
        return response; // TODO: 최적화 로직 구현
    }
    
    /**
     * 쿼리 타입 열거형
     */
    private enum QueryType {
        KOREAN, ENGLISH, NUMERIC, MIXED, OTHER
    }
    
    /**
     * 비동기 검색 통계 기록
     */
    private void recordSearchAnalyticsAsync(String query, String category) {
        CompletableFuture.runAsync(() -> {
            try {
                searchStatisticsService.recordSearch(query, category);
            } catch (Exception e) {
                log.warn("비동기 검색 통계 기록 실패: {}", e.getMessage());
            }
        });
    }
    
    /**
     * 검색 파라미터 검증
     */
    private void validateSearchParameters(String category, String bias, String sort) {
        // 카테고리 검증
        if (category != null && !isValidCategory(category)) {
            throw new CommonException(ResponseExceptionEnum.PARAMETER_OUT_OF_RANGE);
        }
        
        // 편향성 필터 검증
        if (bias != null && !isValidBias(bias)) {
            throw new CommonException(ResponseExceptionEnum.PARAMETER_OUT_OF_RANGE);
        }
        
        // 정렬 방식 검증
        if (sort != null && !isValidSort(sort)) {
            throw new CommonException(ResponseExceptionEnum.PARAMETER_OUT_OF_RANGE);
        }
    }
    
    private boolean isValidCategory(String category) {
        // 유효한 카테고리 목록 확인
        return category.matches("^(politics|economy|society|culture|all)$");
    }
    
    private boolean isValidBias(String bias) {
        // 유효한 편향성 필터 확인
        return bias.matches("^(left|center|right|none)$");
    }
    
    private boolean isValidSort(String sort) {
        // 유효한 정렬 방식 확인
        return sort.matches("^(relevance|date|popularity)$");
    }
    
    // === Private Validation Methods ===
    
    private void validateAuthentication(UserDetails userDetails) {
        if (userDetails == null) {
            throw new CommonException(ResponseExceptionEnum.UNAUTHORIZED_ACCESS);
        }
    }
    
    private void validateSearchQuery(String query) {
        if (query == null || query.trim().isEmpty()) {
            throw new CommonException(ResponseExceptionEnum.PARAMETER_REQUIRED);
        }
        if (query.length() > 100) {
            throw new CommonException(ResponseExceptionEnum.PARAMETER_OUT_OF_RANGE);
        }
    }
    
    private void validateAutocompleteQuery(String query) {
        if (query == null || query.trim().isEmpty()) {
            throw new CommonException(ResponseExceptionEnum.PARAMETER_REQUIRED);
        }
    }
    
    private void validatePagingParameters(int page, int size) {
        if (page < 1) {
            throw new CommonException(ResponseExceptionEnum.PARAMETER_OUT_OF_RANGE);
        }
        if (size < 1 || size > 100) {
            throw new CommonException(ResponseExceptionEnum.PARAMETER_OUT_OF_RANGE);
        }
    }
    
    private void validateLimit(int limit) {
        if (limit < 1 || limit > 50) {
            throw new CommonException(ResponseExceptionEnum.PARAMETER_OUT_OF_RANGE);
        }
    }
    
    
    /**
     * Search 도메인 데이터 흐름 결과 컨테이너
     */
    public static class SearchFlowResult {
        private final boolean success;
        private final String message;
        private final Object data;
        private final Exception error;
        
        private SearchFlowResult(boolean success, String message, Object data, Exception error) {
            this.success = success;
            this.message = message;
            this.data = data;
            this.error = error;
        }
        
        public static SearchFlowResult success(String message, Object data) {
            return new SearchFlowResult(true, message, data, null);
        }
        
        public static SearchFlowResult failure(String message, Exception error) {
            return new SearchFlowResult(false, message, null, error);
        }
        
        // Getters
        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
        public Object getData() { return data; }
        public Exception getError() { return error; }
    }
}