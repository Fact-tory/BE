package com.commonground.be.domain.analysis.repository;

import com.commonground.be.domain.analysis.entity.Analysis;
import com.commonground.be.domain.analysis.enums.AnalysisStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface AnalysisRepository extends JpaRepository<Analysis, Long> {

    // 사용자별 분석 목록 조회 (페이징)
    Page<Analysis> findByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(String userId, Pageable pageable);

    // 사용자별 제목 검색 분석 목록 조회 (페이징)
    Page<Analysis> findByUserIdAndTitleContainingAndDeletedAtIsNullOrderByCreatedAtDesc(String userId, String title, Pageable pageable);

    // 사용자의 특정 분석 조회
    Optional<Analysis> findByIdAndUserIdAndDeletedAtIsNull(Long id, String userId);

    // 진행 중인 분석 목록 조회
    List<Analysis> findByStatusAndDeletedAtIsNull(AnalysisStatus status);

    // 특정 시간 이후 생성된 분석 개수 조회
    @Query("SELECT COUNT(a) FROM Analysis a WHERE a.userId = :userId AND a.createdAt >= :since AND a.deletedAt IS NULL")
    long countByUserIdAndCreatedAtAfter(@Param("userId") String userId, @Param("since") LocalDateTime since);

    // 사용자별 완료된 분석 개수
    long countByUserIdAndStatusAndDeletedAtIsNull(String userId, AnalysisStatus status);

    // 오래된 실패한 분석 조회 (정리용)
    @Query("SELECT a FROM Analysis a WHERE a.status = :status AND a.updatedAt < :before AND a.deletedAt IS NULL")
    List<Analysis> findOldFailedAnalyses(@Param("status") AnalysisStatus status, @Param("before") LocalDateTime before);
}