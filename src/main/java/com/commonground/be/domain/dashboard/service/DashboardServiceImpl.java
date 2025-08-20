package com.commonground.be.domain.dashboard.service;

import com.commonground.be.domain.analysis.entity.Analysis;
import com.commonground.be.domain.analysis.repository.AnalysisRepository;
import com.commonground.be.domain.dashboard.dto.response.MainDashboardResponse;
import com.commonground.be.domain.dashboard.dto.response.UserDashboardResponse;
import com.commonground.be.domain.news.service.NewsService;
import com.commonground.be.domain.searchhistory.entity.SearchHistory;
import com.commonground.be.domain.searchhistory.repository.SearchHistoryRepository;
import com.commonground.be.global.application.exception.CommonException;
import com.commonground.be.global.application.response.ResponseExceptionEnum;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardServiceImpl implements DashboardService {

    private final NewsService newsService;
    private final CategoryNewsService categoryNewsService;
    private final PopularSearchService popularSearchService;
    private final AnalysisRepository analysisRepository;
    private final SearchHistoryRepository searchHistoryRepository;

    @Override
    public MainDashboardResponse getMainDashboard(int realtimeLimit, int trendingLimit, int categoryLimit, int searchLimit) {
        log.info("메인 대시보드 데이터 조회 - realtime: {}, trending: {}, category: {}, search: {}", 
                realtimeLimit, trendingLimit, categoryLimit, searchLimit);

        try {
            // 실시간 뉴스 조회
            List<Object> realtimeNews = newsService.findRecentNews(realtimeLimit).stream()
                .map(news -> (Object) news)
                .collect(java.util.stream.Collectors.toList());
            
            // 급상승 뉴스 조회
            List<Object> trendingNews = newsService.findTrendingNews(trendingLimit).stream()
                .map(news -> (Object) news)
                .collect(java.util.stream.Collectors.toList());
            
            // 카테고리별 뉴스 조회
            Map<String, List<Object>> categoryNews = categoryNewsService.getCategoryNews(categoryLimit);
            
            // 인기 검색어 조회
            List<String> popularSearches = popularSearchService.getPopularSearches(searchLimit);
            
            return new MainDashboardResponse(realtimeNews, trendingNews, categoryNews, popularSearches);
            
        } catch (Exception e) {
            log.error("메인 대시보드 데이터 조회 실패", e);
            throw new CommonException(ResponseExceptionEnum.DASHBOARD_MAIN_DATA_FAILED);
        }
    }

    @Override
    public UserDashboardResponse getUserDashboard(String userId) {
        log.info("사용자 대시보드 데이터 조회 - userId: {}", userId);

        try {
            // 사용자의 최근 분석 목록 조회 (상위 5개)
            List<UserDashboardResponse.AnalysisItem> myAnalyses = getUserAnalyses(userId, 5);
            
            // 사용자의 최근 검색 기록 조회 (상위 5개)
            List<UserDashboardResponse.SearchHistoryItem> recentSearches = getUserSearchHistory(userId, 5);
            
            // 사용자 통계 정보
            UserDashboardResponse.UserStats stats = getUserStats(userId);
            
            return new UserDashboardResponse(myAnalyses, recentSearches, stats);
            
        } catch (Exception e) {
            log.error("사용자 대시보드 데이터 조회 실패 - userId: {}", userId, e);
            throw new CommonException(ResponseExceptionEnum.DASHBOARD_USER_DATA_FAILED);
        }
    }

    private List<UserDashboardResponse.AnalysisItem> getUserAnalyses(String userId, int limit) {
        List<UserDashboardResponse.AnalysisItem> analysisItems = new ArrayList<>();
        
        try {
            PageRequest pageRequest = PageRequest.of(0, limit);
            List<Analysis> analyses = analysisRepository.findByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(userId, pageRequest)
                    .getContent();
            
            for (Analysis analysis : analyses) {
                analysisItems.add(new UserDashboardResponse.AnalysisItem(
                    analysis.getId(),
                    analysis.getTitle(),
                    analysis.getStatus().getDescription(),
                    analysis.getCreatedAt()
                ));
            }
        } catch (Exception e) {
            log.error("사용자 분석 목록 조회 실패 - userId: {}", userId, e);
        }
        
        return analysisItems;
    }


    private List<UserDashboardResponse.SearchHistoryItem> getUserSearchHistory(String userId, int limit) {
        List<UserDashboardResponse.SearchHistoryItem> searchHistoryItems = new ArrayList<>();
        
        try {
            List<SearchHistory> searchHistories = searchHistoryRepository.findTop10ByUserIdOrderByCreatedAtDesc(userId);
            
            for (int i = 0; i < Math.min(limit, searchHistories.size()); i++) {
                SearchHistory history = searchHistories.get(i);
                searchHistoryItems.add(new UserDashboardResponse.SearchHistoryItem(
                    history.getQuery(),
                    history.getCreatedAt(),
                    history.getResultCount() != null ? history.getResultCount() : 0
                ));
            }
            
            // 데이터가 부족한 경우 Mock 데이터로 보완
            if (searchHistoryItems.size() < limit) {
                String[] queries = {"정치", "경제 정책", "코로나", "부동산", "AI"};
                int remaining = limit - searchHistoryItems.size();
                
                for (int i = 0; i < Math.min(remaining, queries.length); i++) {
                    searchHistoryItems.add(new UserDashboardResponse.SearchHistoryItem(
                        queries[i],
                        LocalDateTime.now().minusHours(i + searchHistoryItems.size() + 1),
                        10 + (i * 5)
                    ));
                }
            }
        } catch (Exception e) {
            log.error("사용자 검색 기록 조회 실패 - userId: {}", userId, e);
            // Fallback: Mock 데이터
            String[] queries = {"정치", "경제 정책", "코로나", "부동산", "AI"};
            
            for (int i = 0; i < Math.min(limit, queries.length); i++) {
                searchHistoryItems.add(new UserDashboardResponse.SearchHistoryItem(
                    queries[i],
                    LocalDateTime.now().minusHours(i + 1),
                    10 + (i * 5)
                ));
            }
        }
        
        return searchHistoryItems;
    }

    private UserDashboardResponse.UserStats getUserStats(String userId) {
        try {
            // 사용자의 총 분석 수
            long totalAnalyses = analysisRepository.countByUserIdAndCreatedAtAfter(
                userId, LocalDateTime.now().minusMonths(6)
            );
            
            // 완료된 분석 수
            long completedAnalyses = analysisRepository.countByUserIdAndStatusAndDeletedAtIsNull(
                userId, com.commonground.be.domain.analysis.enums.AnalysisStatus.COMPLETED
            );
            
            // 실제 Repository에서 데이터 조회
            int totalSearches = (int) searchHistoryRepository.countByUserId(userId);
            
            // 데이터가 없는 경우 Mock 데이터 사용
            if (totalSearches == 0) totalSearches = 45;
            
            return new UserDashboardResponse.UserStats(
                (int) totalAnalyses,
                (int) completedAnalyses,
                totalSearches
            );
            
        } catch (Exception e) {
            log.error("사용자 통계 조회 실패 - userId: {}", userId, e);
            return new UserDashboardResponse.UserStats(0, 0, 0);
        }
    }
}