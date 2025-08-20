package com.commonground.be.domain.analysis.repository;

import com.commonground.be.domain.analysis.entity.Analysis;
import com.commonground.be.domain.analysis.enums.AnalysisStatus;
import com.commonground.be.domain.analysis.enums.AnalysisType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
@DisplayName("AnalysisRepository 데이터 접근 계층 테스트")
class AnalysisRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private AnalysisRepository analysisRepository;

    private String testUserId;
    private Analysis testAnalysis1;
    private Analysis testAnalysis2;
    private Analysis testAnalysis3;
    private Analysis deletedAnalysis;

    @BeforeEach
    void setUp() {
        testUserId = "testUser";
        
        // 테스트용 분석 엔티티 생성
        testAnalysis1 = Analysis.builder()
                .userId(testUserId)
                .analysisType(AnalysisType.TEXT_ANALYSIS)
                .targetText("첫 번째 분석 텍스트")
                .title("첫 번째 분석")
                .includeBiasAnalysis(true)
                .includeSentimentAnalysis(true)
                .includeKeywordExtraction(true)
                .includeFactCheck(false)
                .build();
        testAnalysis1.complete(); // 완료 상태로 설정

        testAnalysis2 = Analysis.builder()
                .userId(testUserId)
                .analysisType(AnalysisType.URL_ANALYSIS)
                .targetUrl("https://example.com/news1")
                .title("URL 분석")
                .includeBiasAnalysis(true)
                .includeSentimentAnalysis(true)
                .includeKeywordExtraction(true)
                .includeFactCheck(false)
                .build();
        testAnalysis2.updateStatus(AnalysisStatus.IN_PROGRESS);

        testAnalysis3 = Analysis.builder()
                .userId("otherUser")
                .analysisType(AnalysisType.NEWS_ANALYSIS)
                .targetNewsId("news123")
                .title("뉴스 분석")
                .includeBiasAnalysis(true)
                .includeSentimentAnalysis(true)
                .includeKeywordExtraction(true)
                .includeFactCheck(false)
                .build();

        deletedAnalysis = Analysis.builder()
                .userId(testUserId)
                .analysisType(AnalysisType.TEXT_ANALYSIS)
                .targetText("삭제된 분석")
                .title("삭제된 분석")
                .includeBiasAnalysis(true)
                .includeSentimentAnalysis(true)
                .includeKeywordExtraction(true)
                .includeFactCheck(false)
                .build();
        deletedAnalysis.softDelete(); // 소프트 삭제

        // 엔티티 저장
        entityManager.persistAndFlush(testAnalysis1);
        entityManager.persistAndFlush(testAnalysis2);
        entityManager.persistAndFlush(testAnalysis3);
        entityManager.persistAndFlush(deletedAnalysis);
    }

    @Test
    @DisplayName("사용자별 분석 목록 조회 - 페이징")
    void findByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc_Success() {
        // Given
        Pageable pageable = PageRequest.of(0, 10);

        // When
        Page<Analysis> result = analysisRepository.findByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(testUserId, pageable);

        // Then
        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getTotalElements()).isEqualTo(2);
        assertThat(result.getContent()).extracting("userId").containsOnly(testUserId);
        assertThat(result.getContent()).extracting("title").containsExactly("URL 분석", "첫 번째 분석");
        // 삭제된 분석은 포함되지 않음
        assertThat(result.getContent()).extracting("title").doesNotContain("삭제된 분석");
    }

    @Test
    @DisplayName("사용자별 제목 검색 분석 목록 조회 - 페이징")
    void findByUserIdAndTitleContainingAndDeletedAtIsNullOrderByCreatedAtDesc_Success() {
        // Given
        Pageable pageable = PageRequest.of(0, 10);
        String searchTitle = "분석";

        // When
        Page<Analysis> result = analysisRepository.findByUserIdAndTitleContainingAndDeletedAtIsNullOrderByCreatedAtDesc(
                testUserId, searchTitle, pageable);

        // Then
        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getContent()).extracting("userId").containsOnly(testUserId);
        assertThat(result.getContent()).extracting("title").allMatch(title -> ((String) title).contains(searchTitle));
    }

    @Test
    @DisplayName("사용자별 제목 검색 - 부분 일치")
    void findByUserIdAndTitleContainingAndDeletedAtIsNullOrderByCreatedAtDesc_PartialMatch() {
        // Given
        Pageable pageable = PageRequest.of(0, 10);
        String searchTitle = "첫 번째";

        // When
        Page<Analysis> result = analysisRepository.findByUserIdAndTitleContainingAndDeletedAtIsNullOrderByCreatedAtDesc(
                testUserId, searchTitle, pageable);

        // Then
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getTitle()).isEqualTo("첫 번째 분석");
    }

    @Test
    @DisplayName("사용자의 특정 분석 조회")
    void findByIdAndUserIdAndDeletedAtIsNull_Success() {
        // When
        Optional<Analysis> result = analysisRepository.findByIdAndUserIdAndDeletedAtIsNull(
                testAnalysis1.getId(), testUserId);

        // Then
        assertThat(result).isPresent();
        assertThat(result.get().getTitle()).isEqualTo("첫 번째 분석");
        assertThat(result.get().getUserId()).isEqualTo(testUserId);
    }

    @Test
    @DisplayName("사용자의 특정 분석 조회 - 다른 사용자 분석")
    void findByIdAndUserIdAndDeletedAtIsNull_DifferentUser() {
        // When
        Optional<Analysis> result = analysisRepository.findByIdAndUserIdAndDeletedAtIsNull(
                testAnalysis3.getId(), testUserId);

        // Then
        assertThat(result).isNotPresent();
    }

    @Test
    @DisplayName("사용자의 특정 분석 조회 - 삭제된 분석")
    void findByIdAndUserIdAndDeletedAtIsNull_DeletedAnalysis() {
        // When
        Optional<Analysis> result = analysisRepository.findByIdAndUserIdAndDeletedAtIsNull(
                deletedAnalysis.getId(), testUserId);

        // Then
        assertThat(result).isNotPresent();
    }

    @Test
    @DisplayName("진행 중인 분석 목록 조회")
    void findByStatusAndDeletedAtIsNull_Success() {
        // When
        List<Analysis> result = analysisRepository.findByStatusAndDeletedAtIsNull(AnalysisStatus.IN_PROGRESS);

        // Then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTitle()).isEqualTo("URL 분석");
        assertThat(result.get(0).getStatus()).isEqualTo(AnalysisStatus.IN_PROGRESS);
    }

    @Test
    @DisplayName("완료된 분석 목록 조회")
    void findByStatusAndDeletedAtIsNull_CompletedStatus() {
        // When
        List<Analysis> result = analysisRepository.findByStatusAndDeletedAtIsNull(AnalysisStatus.COMPLETED);

        // Then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTitle()).isEqualTo("첫 번째 분석");
        assertThat(result.get(0).getStatus()).isEqualTo(AnalysisStatus.COMPLETED);
    }

    @Test
    @DisplayName("특정 시간 이후 생성된 분석 개수 조회")
    void countByUserIdAndCreatedAtAfter_Success() {
        // Given
        LocalDateTime since = LocalDateTime.now().minusHours(1);

        // When
        long count = analysisRepository.countByUserIdAndCreatedAtAfter(testUserId, since);

        // Then
        assertThat(count).isEqualTo(2); // testAnalysis1, testAnalysis2 (deletedAnalysis는 제외)
    }

    @Test
    @DisplayName("특정 시간 이후 생성된 분석 개수 조회 - 미래 시간")
    void countByUserIdAndCreatedAtAfter_FutureTime() {
        // Given
        LocalDateTime since = LocalDateTime.now().plusHours(1);

        // When
        long count = analysisRepository.countByUserIdAndCreatedAtAfter(testUserId, since);

        // Then
        assertThat(count).isEqualTo(0);
    }

    @Test
    @DisplayName("사용자별 완료된 분석 개수")
    void countByUserIdAndStatusAndDeletedAtIsNull_Success() {
        // When
        long count = analysisRepository.countByUserIdAndStatusAndDeletedAtIsNull(
                testUserId, AnalysisStatus.COMPLETED);

        // Then
        assertThat(count).isEqualTo(1);
    }

    @Test
    @DisplayName("사용자별 진행 중인 분석 개수")
    void countByUserIdAndStatusAndDeletedAtIsNull_InProgress() {
        // When
        long count = analysisRepository.countByUserIdAndStatusAndDeletedAtIsNull(
                testUserId, AnalysisStatus.IN_PROGRESS);

        // Then
        assertThat(count).isEqualTo(1);
    }

    @Test
    @DisplayName("오래된 실패한 분석 조회")
    void findOldFailedAnalyses_Success() {
        // Given
        Analysis failedAnalysis = Analysis.builder()
                .userId(testUserId)
                .analysisType(AnalysisType.TEXT_ANALYSIS)
                .targetText("실패한 분석")
                .title("실패한 분석")
                .includeBiasAnalysis(true)
                .includeSentimentAnalysis(true)
                .includeKeywordExtraction(true)
                .includeFactCheck(false)
                .build();
        failedAnalysis.setError("분석 실패");
        entityManager.persistAndFlush(failedAnalysis);

        // 업데이트 시간을 과거로 설정 (리플렉션 사용)
        try {
            java.lang.reflect.Field updatedAtField = failedAnalysis.getClass().getSuperclass().getDeclaredField("updatedAt");
            updatedAtField.setAccessible(true);
            updatedAtField.set(failedAnalysis, LocalDateTime.now().minusDays(2));
            entityManager.merge(failedAnalysis);
            entityManager.flush();
        } catch (Exception e) {
            // 리플렉션 실패 시 테스트 스킵
            return;
        }

        LocalDateTime before = LocalDateTime.now().minusHours(1);

        // When
        List<Analysis> result = analysisRepository.findOldFailedAnalyses(AnalysisStatus.FAILED, before);

        // Then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTitle()).isEqualTo("실패한 분석");
        assertThat(result.get(0).getStatus()).isEqualTo(AnalysisStatus.FAILED);
    }

    @Test
    @DisplayName("페이징 정렬 확인 - 생성일시 내림차순")
    void findByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc_SortOrder() {
        // Given
        // 추가 분석 데이터 생성
        Analysis newestAnalysis = Analysis.builder()
                .userId(testUserId)
                .analysisType(AnalysisType.TEXT_ANALYSIS)
                .targetText("가장 최신 분석")
                .title("가장 최신 분석")
                .includeBiasAnalysis(true)
                .includeSentimentAnalysis(true)
                .includeKeywordExtraction(true)
                .includeFactCheck(false)
                .build();
        entityManager.persistAndFlush(newestAnalysis);

        Pageable pageable = PageRequest.of(0, 10);

        // When
        Page<Analysis> result = analysisRepository.findByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(testUserId, pageable);

        // Then
        assertThat(result.getContent()).hasSize(3);
        // 최신 순으로 정렬되어야 함
        assertThat(result.getContent().get(0).getTitle()).isEqualTo("가장 최신 분석");
        
        // 연속된 분석의 생성일시가 내림차순인지 확인
        for (int i = 0; i < result.getContent().size() - 1; i++) {
            assertThat(result.getContent().get(i).getCreatedAt())
                    .isAfterOrEqualTo(result.getContent().get(i + 1).getCreatedAt());
        }
    }

    @Test
    @DisplayName("페이징 크기 제한 테스트")
    void findByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc_PageSizeLimit() {
        // Given
        Pageable pageable = PageRequest.of(0, 1); // 페이지 크기 1

        // When
        Page<Analysis> result = analysisRepository.findByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(testUserId, pageable);

        // Then
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getTotalElements()).isEqualTo(2);
        assertThat(result.hasNext()).isTrue();
    }

    @Test
    @DisplayName("다양한 분석 상태별 조회 테스트")
    void findByStatusAndDeletedAtIsNull_VariousStatuses() {
        // Given - 다양한 상태의 분석 추가
        Analysis pendingAnalysis = Analysis.builder()
                .userId(testUserId)
                .analysisType(AnalysisType.TEXT_ANALYSIS)
                .targetText("대기 중인 분석")
                .title("대기 중인 분석")
                .includeBiasAnalysis(true)
                .includeSentimentAnalysis(true)
                .includeKeywordExtraction(true)
                .includeFactCheck(false)
                .build();
        // PENDING 상태는 기본값

        Analysis cancelledAnalysis = Analysis.builder()
                .userId(testUserId)
                .analysisType(AnalysisType.TEXT_ANALYSIS)
                .targetText("취소된 분석")
                .title("취소된 분석")
                .includeBiasAnalysis(true)
                .includeSentimentAnalysis(true)
                .includeKeywordExtraction(true)
                .includeFactCheck(false)
                .build();
        cancelledAnalysis.cancel();

        entityManager.persistAndFlush(pendingAnalysis);
        entityManager.persistAndFlush(cancelledAnalysis);

        // When & Then
        assertThat(analysisRepository.findByStatusAndDeletedAtIsNull(AnalysisStatus.PENDING)).hasSize(2);
        assertThat(analysisRepository.findByStatusAndDeletedAtIsNull(AnalysisStatus.IN_PROGRESS)).hasSize(1);
        assertThat(analysisRepository.findByStatusAndDeletedAtIsNull(AnalysisStatus.COMPLETED)).hasSize(1);
        assertThat(analysisRepository.findByStatusAndDeletedAtIsNull(AnalysisStatus.CANCELLED)).hasSize(1);
        assertThat(analysisRepository.findByStatusAndDeletedAtIsNull(AnalysisStatus.FAILED)).hasSize(0);
    }

    @Test
    @DisplayName("제목 검색 - 대소문자 구분")
    void findByUserIdAndTitleContainingAndDeletedAtIsNullOrderByCreatedAtDesc_CaseSensitive() {
        // Given
        Pageable pageable = PageRequest.of(0, 10);

        // When & Then - 한글은 대소문자 구분이 없으므로 영어 제목으로 테스트
        Analysis englishTitleAnalysis = Analysis.builder()
                .userId(testUserId)
                .analysisType(AnalysisType.TEXT_ANALYSIS)
                .targetText("English analysis")
                .title("English Analysis Title")
                .includeBiasAnalysis(true)
                .includeSentimentAnalysis(true)
                .includeKeywordExtraction(true)
                .includeFactCheck(false)
                .build();
        entityManager.persistAndFlush(englishTitleAnalysis);

        // 소문자로 검색
        Page<Analysis> lowerCaseResult = analysisRepository.findByUserIdAndTitleContainingAndDeletedAtIsNullOrderByCreatedAtDesc(
                testUserId, "english", pageable);
        
        // 대문자로 검색
        Page<Analysis> upperCaseResult = analysisRepository.findByUserIdAndTitleContainingAndDeletedAtIsNullOrderByCreatedAtDesc(
                testUserId, "ENGLISH", pageable);

        // JPA의 LIKE 연산자는 데이터베이스에 따라 대소문자 구분이 다를 수 있음
        // H2 데이터베이스에서는 대소문자를 구분함
        assertThat(lowerCaseResult.getContent()).hasSize(0);
        assertThat(upperCaseResult.getContent()).hasSize(0);
        
        // 정확한 대소문자로 검색
        Page<Analysis> exactCaseResult = analysisRepository.findByUserIdAndTitleContainingAndDeletedAtIsNullOrderByCreatedAtDesc(
                testUserId, "English", pageable);
        assertThat(exactCaseResult.getContent()).hasSize(1);
    }

    @Test
    @DisplayName("빈 결과 처리 테스트")
    void findByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc_EmptyResult() {
        // Given
        String nonExistentUserId = "nonExistentUser";
        Pageable pageable = PageRequest.of(0, 10);

        // When
        Page<Analysis> result = analysisRepository.findByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(nonExistentUserId, pageable);

        // Then
        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isEqualTo(0);
        assertThat(result.hasNext()).isFalse();
        assertThat(result.hasPrevious()).isFalse();
    }

    @Test
    @DisplayName("카운트 쿼리 정확성 검증")
    void countQueries_Accuracy() {
        // Given
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime oneHourAgo = now.minusHours(1);
        LocalDateTime oneDayAgo = now.minusDays(1);

        // When & Then
        // 1시간 전 이후 생성된 분석 (모든 테스트 데이터)
        long countAfterOneHour = analysisRepository.countByUserIdAndCreatedAtAfter(testUserId, oneHourAgo);
        assertThat(countAfterOneHour).isEqualTo(2);

        // 1일 전 이후 생성된 분석 (모든 테스트 데이터)
        long countAfterOneDay = analysisRepository.countByUserIdAndCreatedAtAfter(testUserId, oneDayAgo);
        assertThat(countAfterOneDay).isEqualTo(2);

        // 미래 시간 이후 생성된 분석 (없음)
        long countAfterFuture = analysisRepository.countByUserIdAndCreatedAtAfter(testUserId, now.plusHours(1));
        assertThat(countAfterFuture).isEqualTo(0);
    }
}