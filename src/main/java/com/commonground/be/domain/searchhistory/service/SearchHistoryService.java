package com.commonground.be.domain.searchhistory.service;

import com.commonground.be.domain.searchhistory.dto.response.SearchHistoryResponse;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface SearchHistoryService {
    
    /**
     * 검색 기록 저장
     */
    void recordSearch(String userId, String query, Integer resultCount, String category, String filters);
    
    /**
     * 사용자의 검색 히스토리 조회 (페이징)
     */
    SearchHistoryResponse getSearchHistory(String userId, Pageable pageable);
    
    /**
     * 사용자의 최근 검색어 조회 (중복 제거)
     */
    List<String> getRecentSearchQueries(String userId, int limit);
    
    /**
     * 사용자의 검색 통계 조회
     */
    SearchHistoryResponse.SearchStats getSearchStats(String userId);
    
    /**
     * 검색 히스토리 삭제 (전체)
     */
    void clearSearchHistory(String userId);
    
    /**
     * 특정 검색 기록 삭제
     */
    void deleteSearchHistory(String userId, Long historyId);
}