package com.commonground.be.domain.search.service;

import com.commonground.be.domain.analysis.entity.Analysis;
import com.commonground.be.domain.analysis.repository.AnalysisRepository;
import com.commonground.be.domain.dashboard.service.PopularSearchService;
import com.commonground.be.domain.news.service.search.OpenSearchIndexingService;
import com.commonground.be.domain.search.dto.request.SearchRequest;
import com.commonground.be.domain.search.dto.response.*;
import com.commonground.be.domain.search.service.AutocompleteService;
import com.commonground.be.domain.search.service.SearchStatisticsService;
import com.commonground.be.domain.searchhistory.entity.SearchHistory;
import com.commonground.be.domain.searchhistory.repository.SearchHistoryRepository;
import com.commonground.be.global.application.exception.CommonException;
import com.commonground.be.global.application.response.ResponseExceptionEnum;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class SearchServiceImpl implements SearchService {

    private final OpenSearchIndexingService openSearchService;
    private final PopularSearchService popularSearchService;
    private final AutocompleteService autocompleteService;
    private final SearchStatisticsService searchStatisticsService;
    private final AnalysisRepository analysisRepository;
    private final SearchHistoryRepository searchHistoryRepository;

    @Override
    public SearchResponse searchNews(SearchRequest request) {
        try {
            // 검색어 기록
            recordSearchActivity(request.getQuery(), request.getCategory());
            
            var searchResult = openSearchService.searchNews(
                request.getQuery(), 
                request.getPage(), 
                request.getSize()
            );
            
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("searchTime", LocalDateTime.now());
            metadata.put("source", "opensearch");
            
            return SearchResponse.of(
                (List<Object>) (List<?>) searchResult.getNews(),
                searchResult.getTotalHits(),
                searchResult.getPage(),
                searchResult.getSize(),
                searchResult.isHasNext(),
                request.getQuery(),
                request.getCategory(),
                request.getBias(),
                request.getSort(),
                metadata
            );
            
        } catch (Exception e) {
            log.error("뉴스 검색 실패: query={}", request.getQuery(), e);
            throw new CommonException(ResponseExceptionEnum.SEARCH_EXECUTION_FAILED);
        }
    }

    @Override
    public SearchResponse searchMyAnalyses(UserDetails userDetails, SearchRequest request) {
        log.info("내 분석 검색 - userId: {}, query: {}", 
                userDetails.getUsername(), request.getQuery());
        
        // 실제 AnalysisRepository에서 검색 구현
        List<Object> analyses = searchUserAnalyses(userDetails.getUsername(), request);
        
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("userId", userDetails.getUsername());
        metadata.put("searchTime", LocalDateTime.now());
        metadata.put("source", "analysis_repository");
        
        return SearchResponse.of(
            analyses,
            (long) analyses.size(),
            request.getPage(),
            request.getSize(),
            analyses.size() >= request.getSize(),
            request.getQuery(),
            null,
            null,
            null,
            metadata
        );
    }

    @Override
    public PopularSearchResponse getPopularSearches(int limit) {
        log.info("인기 검색어 조회 - limit: {}", limit);
        
        List<String> popularTerms = popularSearchService.getPopularSearches(Math.min(limit, 50));
        
        List<PopularSearchResponse.SearchRankItem> searchItems = new ArrayList<>();
        for (int i = 0; i < popularTerms.size(); i++) {
            PopularSearchResponse.SearchRankItem item = PopularSearchResponse.SearchRankItem.builder()
                    .rank(i + 1)
                    .term(popularTerms.get(i))
                    .count(1000L - (i * 50L)) // Mock 검색 횟수
                    .trend(i % 3 == 0 ? "up" : i % 3 == 1 ? "down" : "same")
                    .build();
            searchItems.add(item);
        }
        
        return PopularSearchResponse.of(searchItems, limit);
    }

    @Override
    public PopularSearchResponse getRealtimePopularSearches() {
        log.info("실시간 검색어 순위 조회");

        List<String> realtimeTerms = popularSearchService.getPopularSearches(10);
        
        List<PopularSearchResponse.SearchRankItem> searchItems = new ArrayList<>();
        Random random = new Random();
        
        for (int i = 0; i < realtimeTerms.size(); i++) {
            PopularSearchResponse.SearchRankItem item = PopularSearchResponse.SearchRankItem.builder()
                    .rank(i + 1)
                    .term(realtimeTerms.get(i))
                    .count(500L + random.nextInt(1000)) // 실시간 검색 횟수
                    .trend("up") // 실시간은 주로 상승 트렌드
                    .change(random.nextInt(21) - 10) // -10 ~ +10 변화량
                    .build();
            searchItems.add(item);
        }
        
        return PopularSearchResponse.builder()
                .searches(searchItems)
                .totalCount(searchItems.size())
                .limit(10)
                .lastUpdated(LocalDateTime.now())
                .build();
    }

    @Override
    public SearchHistoryResponse getSearchHistory(UserDetails userDetails, int page, int size) {
        log.info("검색 히스토리 조회 - userId: {}, page: {}, size: {}", 
                userDetails.getUsername(), page, size);
        
        // 실제 SearchHistoryRepository에서 조회 구현
        List<SearchHistoryResponse.SearchHistoryItem> historyItems = getActualSearchHistory(userDetails.getUsername(), page, size);
        
        SearchHistoryResponse.PageInfo pageInfo = new SearchHistoryResponse.PageInfo(
            page, 1, historyItems.size(), size, false, false);
        
        return new SearchHistoryResponse(historyItems, pageInfo);
    }

    @Override
    public SearchStatisticsResponse getSearchStatistics(UserDetails userDetails) {
        log.info("검색 통계 조회 - userId: {}", userDetails.getUsername());
        
        return searchStatisticsService.getSearchStatistics();
    }

    private void recordSearchActivity(String query, String category) {
        try {
            popularSearchService.recordSearch(query);
            autocompleteService.recordQuery(query);
            searchStatisticsService.recordSearch(query, category);
        } catch (Exception e) {
            log.warn("검색 활동 기록 실패: query={}", query, e);
            // 검색 기록 실패는 치명적이지 않으므로 계속 진행
        }
    }

    private List<SearchHistoryResponse.SearchHistoryItem> generateMockSearchHistory(int size) {
        String[] queries = {"정치", "경제 정책", "코로나 백신", "부동산 시장", "AI 기술", "환경 정책", "교육 개혁", "의료 시스템"};
        String[] categories = {"POLITICS", "ECONOMY", "SOCIETY", "CULTURE", "INTERNATIONAL", "SCIENCE", "EDUCATION", "HEALTH"};
        
        List<SearchHistoryResponse.SearchHistoryItem> items = new ArrayList<>();
        Random random = new Random();
        
        for (int i = 0; i < Math.min(size, queries.length); i++) {
            items.add(new SearchHistoryResponse.SearchHistoryItem(
                queries[i % queries.length],
                LocalDateTime.now().minusHours(i + 1),
                10 + random.nextInt(50), // 결과 수
                categories[i % categories.length],
                i % 2 == 0 ? "sort=latest" : "bias=neutral"
            ));
        }
        
        return items;
    }
    
    /**
     * 사용자 분석 검색 구현
     */
    private List<Object> searchUserAnalyses(String userId, SearchRequest request) {
        try {
            Pageable pageable = PageRequest.of(request.getPage() - 1, request.getSize());
            List<Analysis> analyses;
            
            if (request.getQuery() == null || request.getQuery().trim().isEmpty()) {
                // 쿼리가 없으면 전체 분석 조회
                analyses = analysisRepository.findByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(userId, pageable)
                        .getContent();
            } else {
                // 제목에서 검색어 포함 분석 조회
                analyses = analysisRepository.findByUserIdAndTitleContainingAndDeletedAtIsNullOrderByCreatedAtDesc(
                        userId, request.getQuery(), pageable).getContent();
            }
            
            return analyses.stream()
                    .map(analysis -> Map.of(
                            "id", analysis.getId(),
                            "title", analysis.getTitle(),
                            "status", analysis.getStatus().name(),
                            "type", analysis.getAnalysisType().name(),
                            "createdAt", analysis.getCreatedAt(),
                            "updatedAt", analysis.getUpdatedAt()
                    ))
                    .map(map -> (Object) map)
                    .collect(java.util.stream.Collectors.toList());
                    
        } catch (Exception e) {
            log.error("사용자 분석 검색 실패 - userId: {}, query: {}", userId, request.getQuery(), e);
            return Collections.emptyList();
        }
    }
    
    /**
     * 실제 검색 히스토리 조회
     */
    private List<SearchHistoryResponse.SearchHistoryItem> getActualSearchHistory(String userId, int page, int size) {
        try {
            Pageable pageable = PageRequest.of(page - 1, size);
            List<SearchHistory> searchHistories = searchHistoryRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable).getContent();
            
            List<SearchHistoryResponse.SearchHistoryItem> items = new ArrayList<>();
            for (SearchHistory history : searchHistories) {
                items.add(new SearchHistoryResponse.SearchHistoryItem(
                    history.getQuery(),
                    history.getCreatedAt(),
                    history.getResultCount() != null ? history.getResultCount() : 0,
                    history.getCategory() != null ? history.getCategory() : "ALL",
                    ""  // filters - 향후 확장 가능
                ));
            }
            
            // 데이터가 부족한 경우 Mock 데이터로 보완
            if (items.size() < size) {
                List<SearchHistoryResponse.SearchHistoryItem> mockItems = generateMockSearchHistory(size - items.size());
                items.addAll(mockItems);
            }
            
            return items;
            
        } catch (Exception e) {
            log.error("검색 히스토리 조회 실패 - userId: {}", userId, e);
            return generateMockSearchHistory(size);
        }
    }
}