package com.commonground.be.domain.searchhistory.repository;

import com.commonground.be.domain.searchhistory.entity.SearchHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;

/**
 * SearchHistory 도메인 Repository 인터페이스
 * 도메인 비즈니스 로직에 특화된 메서드만 정의
 */
public interface SearchHistoryRepositoryInterface {
    
    /**
     * 검색 기록 저장
     */
    void save(SearchHistory searchHistory);
    
    /**
     * 검색 기록 조회 (ID)
     */
    SearchHistory findById(Long id);
    
    /**
     * 사용자의 검색 히스토리 조회 (페이징)
     */
    Page<SearchHistory> findByUserIdOrderByCreatedAtDesc(String userId, Pageable pageable);
    
    /**
     * 사용자의 검색 수 조회
     */
    long countByUserId(String userId);
    
    /**
     * 특정 기간 이후 검색 수 조회
     */
    long countByUserIdAndCreatedAtAfter(String userId, LocalDateTime since);
    
    /**
     * 사용자의 최근 검색어 조회 (중복 제거)
     */
    List<String> findDistinctQueriesByUserId(String userId, Pageable pageable);
    
    /**
     * 사용자의 최근 검색 히스토리
     */
    List<SearchHistory> findTop10ByUserIdOrderByCreatedAtDesc(String userId);
    
    /**
     * 검색 기록 삭제
     */
    void delete(SearchHistory searchHistory);
    
    /**
     * 검색 기록 일괄 삭제
     */
    void deleteAll(List<SearchHistory> searchHistories);
}