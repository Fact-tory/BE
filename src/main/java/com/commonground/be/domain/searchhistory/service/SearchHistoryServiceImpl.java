package com.commonground.be.domain.searchhistory.service;

import com.commonground.be.domain.searchhistory.dto.response.SearchHistoryResponse;
import com.commonground.be.domain.searchhistory.entity.SearchHistory;
import com.commonground.be.domain.searchhistory.repository.SearchHistoryRepositoryInterface;
import com.commonground.be.global.application.exception.SearchHistoryExceptions;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class SearchHistoryServiceImpl implements SearchHistoryService {
    
    private final SearchHistoryRepositoryInterface searchHistoryAdapter;
    
    @Override
    @Transactional
    public void recordSearch(String userId, String query, Integer resultCount, String category, String filters) {
        try {
            SearchHistory searchHistory = SearchHistory.create(userId, query, resultCount, category, filters);
            searchHistoryAdapter.save(searchHistory);
            log.debug("검색 기록 저장 완료: userId={}, query={}", userId, query);
        } catch (Exception e) {
            log.error("검색 기록 저장 실패: userId={}, query={}", userId, query, e);
            // 검색 기록 실패는 치명적이지 않으므로 예외를 던지지 않음
        }
    }
    
    @Override
    public SearchHistoryResponse getSearchHistory(String userId, Pageable pageable) {
        try {
            Page<SearchHistory> historyPage = searchHistoryAdapter.findByUserIdOrderByCreatedAtDesc(userId, pageable);
            
            List<SearchHistoryResponse.SearchHistoryItem> historyItems = historyPage.getContent().stream()
                    .map(history -> SearchHistoryResponse.SearchHistoryItem.builder()
                            .id(history.getId())
                            .query(history.getQuery())
                            .searchedAt(history.getCreatedAt())
                            .resultCount(history.getResultCount())
                            .category(history.getCategory())
                            .filters(history.getFilters())
                            .build())
                    .collect(Collectors.toList());
            
            SearchHistoryResponse.PageInfo pageInfo = SearchHistoryResponse.PageInfo.builder()
                    .currentPage(pageable.getPageNumber() + 1)
                    .totalPages(historyPage.getTotalPages())
                    .totalElements((int) historyPage.getTotalElements())
                    .size(pageable.getPageSize())
                    .hasNext(historyPage.hasNext())
                    .hasPrevious(historyPage.hasPrevious())
                    .build();
            
            SearchHistoryResponse.SearchStats stats = getSearchStats(userId);
            
            return SearchHistoryResponse.of(historyItems, pageInfo, stats);
            
        } catch (Exception e) {
            log.error("검색 히스토리 조회 실패: userId={}", userId, e);
            throw SearchHistoryExceptions.searchHistoryNotFound();
        }
    }
    
    @Override
    public List<String> getRecentSearchQueries(String userId, int limit) {
        try {
            Pageable pageable = PageRequest.of(0, limit);
            return searchHistoryAdapter.findDistinctQueriesByUserId(userId, pageable);
        } catch (Exception e) {
            log.error("최근 검색어 조회 실패: userId={}", userId, e);
            return List.of();
        }
    }
    
    @Override
    public SearchHistoryResponse.SearchStats getSearchStats(String userId) {
        try {
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime startOfDay = now.toLocalDate().atStartOfDay();
            LocalDateTime startOfWeek = now.minusWeeks(1);
            LocalDateTime startOfMonth = now.minusMonths(1);
            
            long totalSearches = searchHistoryAdapter.countByUserId(userId);
            long todaySearches = searchHistoryAdapter.countByUserIdAndCreatedAtAfter(userId, startOfDay);
            long thisWeekSearches = searchHistoryAdapter.countByUserIdAndCreatedAtAfter(userId, startOfWeek);
            long thisMonthSearches = searchHistoryAdapter.countByUserIdAndCreatedAtAfter(userId, startOfMonth);
            
            List<String> topQueries = getRecentSearchQueries(userId, 5);
            List<String> topCategories = List.of("ALL", "POLITICS", "ECONOMY", "SOCIETY"); // 임시 구현
            
            return SearchHistoryResponse.SearchStats.builder()
                    .totalSearches(totalSearches)
                    .todaySearches(todaySearches)
                    .thisWeekSearches(thisWeekSearches)
                    .thisMonthSearches(thisMonthSearches)
                    .topQueries(topQueries)
                    .topCategories(topCategories)
                    .build();
                    
        } catch (Exception e) {
            log.error("검색 통계 조회 실패: userId={}", userId, e);
            return SearchHistoryResponse.SearchStats.builder()
                    .totalSearches(0L)
                    .todaySearches(0L)
                    .thisWeekSearches(0L)
                    .thisMonthSearches(0L)
                    .topQueries(List.of())
                    .topCategories(List.of())
                    .build();
        }
    }
    
    @Override
    @Transactional
    public void clearSearchHistory(String userId) {
        try {
            List<SearchHistory> histories = searchHistoryAdapter.findTop10ByUserIdOrderByCreatedAtDesc(userId);
            searchHistoryAdapter.deleteAll(histories);
            log.info("검색 히스토리 전체 삭제 완료: userId={}", userId);
        } catch (Exception e) {
            log.error("검색 히스토리 삭제 실패: userId={}", userId, e);
            throw SearchHistoryExceptions.searchHistoryDeleteFailed();
        }
    }
    
    @Override
    @Transactional
    public void deleteSearchHistory(String userId, Long historyId) {
        try {
            SearchHistory history = searchHistoryAdapter.findById(historyId);
            
            if (!history.getUserId().equals(userId)) {
                throw SearchHistoryExceptions.searchHistoryNotFound();
            }
            
            searchHistoryAdapter.delete(history);
            log.info("검색 기록 삭제 완료: userId={}, historyId={}", userId, historyId);
            
        } catch (Exception e) {
            log.error("검색 기록 삭제 실패: userId={}, historyId={}", userId, historyId, e);
            throw SearchHistoryExceptions.searchHistoryDeleteFailed();
        }
    }
}