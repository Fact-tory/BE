package com.commonground.be.domain.searchhistory.repository;

import com.commonground.be.domain.searchhistory.entity.SearchHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface SearchHistoryRepository extends JpaRepository<SearchHistory, Long> {
    
    /**
     * 사용자의 검색 히스토리 조회 (최신순)
     */
    Page<SearchHistory> findByUserIdOrderByCreatedAtDesc(String userId, Pageable pageable);
    
    /**
     * 사용자의 총 검색 수 조회
     */
    long countByUserId(String userId);
    
    /**
     * 특정 기간 내 검색 수 조회
     */
    long countByUserIdAndCreatedAtAfter(String userId, LocalDateTime since);
    
    /**
     * 최근 검색어 조회 (중복 제거)
     */
    @Query("SELECT DISTINCT s.query FROM SearchHistory s WHERE s.userId = :userId ORDER BY s.createdAt DESC")
    List<String> findDistinctQueriesByUserId(@Param("userId") String userId, Pageable pageable);
    
    /**
     * 사용자의 최근 검색 히스토리
     */
    List<SearchHistory> findTop10ByUserIdOrderByCreatedAtDesc(String userId);
}