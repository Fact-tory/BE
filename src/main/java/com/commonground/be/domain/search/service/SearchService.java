package com.commonground.be.domain.search.service;

import com.commonground.be.domain.search.dto.request.SearchRequest;
import com.commonground.be.domain.search.dto.response.SearchResponse;
import com.commonground.be.domain.search.dto.response.SearchHistoryResponse;
import com.commonground.be.domain.search.dto.response.SearchStatisticsResponse;
import com.commonground.be.domain.search.dto.response.PopularSearchResponse;
import org.springframework.security.core.userdetails.UserDetails;

public interface SearchService {
    
    /**
     * 통합 뉴스 검색
     */
    SearchResponse searchNews(SearchRequest request);
    
    /**
     * 내 분석 검색
     */
    SearchResponse searchMyAnalyses(UserDetails userDetails, SearchRequest request);
    
    /**
     * 인기 검색어 조회
     */
    PopularSearchResponse getPopularSearches(int limit);
    
    /**
     * 실시간 검색어 순위
     */
    PopularSearchResponse getRealtimePopularSearches();
    
    /**
     * 검색 히스토리 조회
     */
    SearchHistoryResponse getSearchHistory(UserDetails userDetails, int page, int size);
    
    /**
     * 검색 통계 조회
     */
    SearchStatisticsResponse getSearchStatistics(UserDetails userDetails);
}