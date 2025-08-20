package com.commonground.be.domain.analysis.entity;

import com.commonground.be.domain.analysis.enums.AnalysisStatus;
import com.commonground.be.domain.analysis.enums.AnalysisType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

@DisplayName("Analysis 엔티티 단위 테스트")
class AnalysisTest {

    private Analysis analysis;
    private String testUserId;

    @BeforeEach
    void setUp() {
        testUserId = "testUser";
        analysis = Analysis.builder()
                .userId(testUserId)
                .analysisType(AnalysisType.TEXT_ANALYSIS)
                .targetText("테스트 분석 텍스트")
                .title("테스트 분석")
                .includeBiasAnalysis(true)
                .includeSentimentAnalysis(true)
                .includeKeywordExtraction(true)
                .includeFactCheck(false)
                .build();
    }

    @Test
    @DisplayName("빌더 패턴을 사용한 Analysis 생성")
    void createAnalysis_WithBuilder_Success() {
        // When
        Analysis newAnalysis = Analysis.builder()
                .userId("user123")
                .analysisType(AnalysisType.URL_ANALYSIS)
                .targetUrl("https://example.com")
                .title("URL 분석 테스트")
                .includeBiasAnalysis(true)
                .includeSentimentAnalysis(false)
                .includeKeywordExtraction(true)
                .includeFactCheck(true)
                .build();

        // Then
        assertThat(newAnalysis.getUserId()).isEqualTo("user123");
        assertThat(newAnalysis.getAnalysisType()).isEqualTo(AnalysisType.URL_ANALYSIS);
        assertThat(newAnalysis.getTargetUrl()).isEqualTo("https://example.com");
        assertThat(newAnalysis.getTitle()).isEqualTo("URL 분석 테스트");
        assertThat(newAnalysis.getIncludeBiasAnalysis()).isTrue();
        assertThat(newAnalysis.getIncludeSentimentAnalysis()).isFalse();
        assertThat(newAnalysis.getIncludeKeywordExtraction()).isTrue();
        assertThat(newAnalysis.getIncludeFactCheck()).isTrue();
        
        // 기본값 확인
        assertThat(newAnalysis.getStatus()).isEqualTo(AnalysisStatus.PENDING);
        assertThat(newAnalysis.getProgressPercentage()).isEqualTo(0);
    }

    @Test
    @DisplayName("Analysis 생성 시 기본값 설정")
    void createAnalysis_DefaultValues() {
        // When
        Analysis defaultAnalysis = Analysis.builder()
                .userId(testUserId)
                .analysisType(AnalysisType.TEXT_ANALYSIS)
                .targetText("텍스트")
                .title("기본값 테스트")
                .build();

        // Then
        assertThat(defaultAnalysis.getStatus()).isEqualTo(AnalysisStatus.PENDING);
        assertThat(defaultAnalysis.getProgressPercentage()).isEqualTo(0);
        assertThat(defaultAnalysis.getIncludeBiasAnalysis()).isTrue();
        assertThat(defaultAnalysis.getIncludeSentimentAnalysis()).isTrue();
        assertThat(defaultAnalysis.getIncludeKeywordExtraction()).isTrue();
        assertThat(defaultAnalysis.getIncludeFactCheck()).isFalse();
    }

    @Test
    @DisplayName("Analysis 생성 시 null 옵션 처리")
    void createAnalysis_NullOptionsHandling() {
        // When
        Analysis analysisWithNullOptions = Analysis.builder()
                .userId(testUserId)
                .analysisType(AnalysisType.TEXT_ANALYSIS)
                .targetText("텍스트")
                .title("null 옵션 테스트")
                .includeBiasAnalysis(null)
                .includeSentimentAnalysis(null)
                .includeKeywordExtraction(null)
                .includeFactCheck(null)
                .build();

        // Then
        assertThat(analysisWithNullOptions.getIncludeBiasAnalysis()).isTrue();
        assertThat(analysisWithNullOptions.getIncludeSentimentAnalysis()).isTrue();
        assertThat(analysisWithNullOptions.getIncludeKeywordExtraction()).isTrue();
        assertThat(analysisWithNullOptions.getIncludeFactCheck()).isFalse();
    }

    @Test
    @DisplayName("분석 상태 업데이트")
    void updateStatus_Success() {
        // Given
        assertThat(analysis.getStatus()).isEqualTo(AnalysisStatus.PENDING);

        // When
        analysis.updateStatus(AnalysisStatus.IN_PROGRESS);

        // Then
        assertThat(analysis.getStatus()).isEqualTo(AnalysisStatus.IN_PROGRESS);
    }

    @Test
    @DisplayName("분석 진행률 업데이트")
    void updateProgress_Success() {
        // Given
        assertThat(analysis.getProgressPercentage()).isEqualTo(0);

        // When
        analysis.updateProgress(50);

        // Then
        assertThat(analysis.getProgressPercentage()).isEqualTo(50);
    }

    @Test
    @DisplayName("분석 오류 설정")
    void setError_Success() {
        // Given
        String errorMessage = "분석 중 오류가 발생했습니다";

        // When
        analysis.setError(errorMessage);

        // Then
        assertThat(analysis.getStatus()).isEqualTo(AnalysisStatus.FAILED);
        assertThat(analysis.getErrorMessage()).isEqualTo(errorMessage);
    }

    @Test
    @DisplayName("편향 분석 결과 설정")
    void setBiasAnalysisResult_Success() {
        // Given
        String biasResult = "{\"biasScore\": 50, \"biasType\": \"NEUTRAL\"}";

        // When
        analysis.setBiasAnalysisResult(biasResult);

        // Then
        assertThat(analysis.getBiasAnalysisResult()).isEqualTo(biasResult);
    }

    @Test
    @DisplayName("감정 분석 결과 설정")
    void setSentimentAnalysisResult_Success() {
        // Given
        String sentimentResult = "{\"sentimentScore\": 0.7, \"primaryEmotion\": \"POSITIVE\"}";

        // When
        analysis.setSentimentAnalysisResult(sentimentResult);

        // Then
        assertThat(analysis.getSentimentAnalysisResult()).isEqualTo(sentimentResult);
    }

    @Test
    @DisplayName("키워드 추출 결과 설정")
    void setKeywordExtractionResult_Success() {
        // Given
        String keywordResult = "{\"keywords\": [\"정부\", \"정책\", \"경제\"]}";

        // When
        analysis.setKeywordExtractionResult(keywordResult);

        // Then
        assertThat(analysis.getKeywordExtractionResult()).isEqualTo(keywordResult);
    }

    @Test
    @DisplayName("팩트 체크 결과 설정")
    void setFactCheckResult_Success() {
        // Given
        String factCheckResult = "{\"factCheckScore\": 85, \"verified\": true}";

        // When
        analysis.setFactCheckResult(factCheckResult);

        // Then
        assertThat(analysis.getFactCheckResult()).isEqualTo(factCheckResult);
    }

    @Test
    @DisplayName("분석 완료 처리")
    void complete_Success() {
        // Given
        analysis.updateStatus(AnalysisStatus.IN_PROGRESS);
        analysis.updateProgress(90);

        // When
        analysis.complete();

        // Then
        assertThat(analysis.getStatus()).isEqualTo(AnalysisStatus.COMPLETED);
        assertThat(analysis.getProgressPercentage()).isEqualTo(100);
    }

    @Test
    @DisplayName("분석 취소 처리")
    void cancel_Success() {
        // Given
        analysis.updateStatus(AnalysisStatus.IN_PROGRESS);

        // When
        analysis.cancel();

        // Then
        assertThat(analysis.getStatus()).isEqualTo(AnalysisStatus.CANCELLED);
    }

    @Test
    @DisplayName("분석 재시작 처리")
    void restart_Success() {
        // Given
        analysis.setError("이전 오류");
        analysis.setBiasAnalysisResult("이전 편향 결과");
        analysis.setSentimentAnalysisResult("이전 감정 결과");
        analysis.setKeywordExtractionResult("이전 키워드 결과");
        analysis.setFactCheckResult("이전 팩트체크 결과");
        analysis.updateProgress(50);

        // When
        analysis.restart();

        // Then
        assertThat(analysis.getStatus()).isEqualTo(AnalysisStatus.PENDING);
        assertThat(analysis.getProgressPercentage()).isEqualTo(0);
        assertThat(analysis.getErrorMessage()).isNull();
        assertThat(analysis.getBiasAnalysisResult()).isNull();
        assertThat(analysis.getSentimentAnalysisResult()).isNull();
        assertThat(analysis.getKeywordExtractionResult()).isNull();
        assertThat(analysis.getFactCheckResult()).isNull();
    }

    @Test
    @DisplayName("진행 중 상태 확인")
    void isInProgress_Success() {
        // Given
        analysis.updateStatus(AnalysisStatus.PENDING);
        assertThat(analysis.isInProgress()).isFalse();

        // When
        analysis.updateStatus(AnalysisStatus.IN_PROGRESS);

        // Then
        assertThat(analysis.isInProgress()).isTrue();
    }

    @Test
    @DisplayName("완료 상태 확인")
    void isCompleted_Success() {
        // Given
        analysis.updateStatus(AnalysisStatus.PENDING);
        assertThat(analysis.isCompleted()).isFalse();

        // When
        analysis.complete();

        // Then
        assertThat(analysis.isCompleted()).isTrue();
    }

    @Test
    @DisplayName("재시작 가능 상태 확인 - 실패 상태")
    void canRestart_FailedStatus() {
        // Given
        analysis.setError("분석 실패");

        // When & Then
        assertThat(analysis.canRestart()).isTrue();
    }

    @Test
    @DisplayName("재시작 가능 상태 확인 - 취소 상태")
    void canRestart_CancelledStatus() {
        // Given
        analysis.cancel();

        // When & Then
        assertThat(analysis.canRestart()).isTrue();
    }

    @Test
    @DisplayName("재시작 불가능 상태 확인 - 완료 상태")
    void canRestart_CompletedStatus() {
        // Given
        analysis.complete();

        // When & Then
        assertThat(analysis.canRestart()).isFalse();
    }

    @Test
    @DisplayName("재시작 불가능 상태 확인 - 진행 중 상태")
    void canRestart_InProgressStatus() {
        // Given
        analysis.updateStatus(AnalysisStatus.IN_PROGRESS);

        // When & Then
        assertThat(analysis.canRestart()).isFalse();
    }

    @Test
    @DisplayName("재시작 불가능 상태 확인 - 대기 상태")
    void canRestart_PendingStatus() {
        // Given (기본적으로 PENDING 상태)

        // When & Then
        assertThat(analysis.canRestart()).isFalse();
    }

    @Test
    @DisplayName("다양한 분석 타입별 생성 테스트")
    void createAnalysis_VariousTypes() {
        // Given & When & Then
        
        // TEXT_ANALYSIS
        Analysis textAnalysis = Analysis.builder()
                .userId(testUserId)
                .analysisType(AnalysisType.TEXT_ANALYSIS)
                .targetText("텍스트 분석")
                .title("텍스트 분석")
                .build();
        assertThat(textAnalysis.getAnalysisType()).isEqualTo(AnalysisType.TEXT_ANALYSIS);
        assertThat(textAnalysis.getTargetText()).isEqualTo("텍스트 분석");

        // URL_ANALYSIS
        Analysis urlAnalysis = Analysis.builder()
                .userId(testUserId)
                .analysisType(AnalysisType.URL_ANALYSIS)
                .targetUrl("https://example.com")
                .title("URL 분석")
                .build();
        assertThat(urlAnalysis.getAnalysisType()).isEqualTo(AnalysisType.URL_ANALYSIS);
        assertThat(urlAnalysis.getTargetUrl()).isEqualTo("https://example.com");

        // NEWS_ANALYSIS
        Analysis newsAnalysis = Analysis.builder()
                .userId(testUserId)
                .analysisType(AnalysisType.NEWS_ANALYSIS)
                .targetNewsId("news123")
                .title("뉴스 분석")
                .build();
        assertThat(newsAnalysis.getAnalysisType()).isEqualTo(AnalysisType.NEWS_ANALYSIS);
        assertThat(newsAnalysis.getTargetNewsId()).isEqualTo("news123");
    }

    @Test
    @DisplayName("분석 상태 전환 시나리오 테스트")
    void analysisStatusTransitionScenario() {
        // Given - 초기 상태는 PENDING
        assertThat(analysis.getStatus()).isEqualTo(AnalysisStatus.PENDING);
        assertThat(analysis.getProgressPercentage()).isEqualTo(0);

        // When & Then - PENDING → IN_PROGRESS
        analysis.updateStatus(AnalysisStatus.IN_PROGRESS);
        analysis.updateProgress(25);
        assertThat(analysis.getStatus()).isEqualTo(AnalysisStatus.IN_PROGRESS);
        assertThat(analysis.getProgressPercentage()).isEqualTo(25);
        assertThat(analysis.isInProgress()).isTrue();

        // When & Then - 진행률 업데이트
        analysis.updateProgress(75);
        assertThat(analysis.getProgressPercentage()).isEqualTo(75);

        // When & Then - IN_PROGRESS → COMPLETED
        analysis.complete();
        assertThat(analysis.getStatus()).isEqualTo(AnalysisStatus.COMPLETED);
        assertThat(analysis.getProgressPercentage()).isEqualTo(100);
        assertThat(analysis.isCompleted()).isTrue();
        assertThat(analysis.isInProgress()).isFalse();
    }

    @Test
    @DisplayName("분석 실패 시나리오 테스트")
    void analysisFailureScenario() {
        // Given - 진행 중 상태
        analysis.updateStatus(AnalysisStatus.IN_PROGRESS);
        analysis.updateProgress(50);

        // When - 오류 발생
        String errorMessage = "네트워크 연결 오류";
        analysis.setError(errorMessage);

        // Then
        assertThat(analysis.getStatus()).isEqualTo(AnalysisStatus.FAILED);
        assertThat(analysis.getErrorMessage()).isEqualTo(errorMessage);
        assertThat(analysis.canRestart()).isTrue();
        assertThat(analysis.isInProgress()).isFalse();
        assertThat(analysis.isCompleted()).isFalse();
    }

    @Test
    @DisplayName("분석 재시작 시나리오 테스트")
    void analysisRestartScenario() {
        // Given - 실패 상태의 분석
        analysis.updateStatus(AnalysisStatus.IN_PROGRESS);
        analysis.updateProgress(30);
        analysis.setBiasAnalysisResult("부분 결과");
        analysis.setError("중간에 실패");

        assertThat(analysis.getStatus()).isEqualTo(AnalysisStatus.FAILED);
        assertThat(analysis.canRestart()).isTrue();

        // When - 재시작
        analysis.restart();

        // Then - 모든 상태가 초기화됨
        assertThat(analysis.getStatus()).isEqualTo(AnalysisStatus.PENDING);
        assertThat(analysis.getProgressPercentage()).isEqualTo(0);
        assertThat(analysis.getErrorMessage()).isNull();
        assertThat(analysis.getBiasAnalysisResult()).isNull();
        assertThat(analysis.canRestart()).isFalse();
    }

    @Test
    @DisplayName("분석 옵션 조합 테스트")
    void analysisOptionsVariations() {
        // Given & When - 모든 옵션 활성화
        Analysis allOptionsAnalysis = Analysis.builder()
                .userId(testUserId)
                .analysisType(AnalysisType.TEXT_ANALYSIS)
                .targetText("전체 분석")
                .title("전체 분석")
                .includeBiasAnalysis(true)
                .includeSentimentAnalysis(true)
                .includeKeywordExtraction(true)
                .includeFactCheck(true)
                .build();

        // Then
        assertThat(allOptionsAnalysis.getIncludeBiasAnalysis()).isTrue();
        assertThat(allOptionsAnalysis.getIncludeSentimentAnalysis()).isTrue();
        assertThat(allOptionsAnalysis.getIncludeKeywordExtraction()).isTrue();
        assertThat(allOptionsAnalysis.getIncludeFactCheck()).isTrue();

        // Given & When - 최소 옵션
        Analysis minimalAnalysis = Analysis.builder()
                .userId(testUserId)
                .analysisType(AnalysisType.TEXT_ANALYSIS)
                .targetText("최소 분석")
                .title("최소 분석")
                .includeBiasAnalysis(false)
                .includeSentimentAnalysis(false)
                .includeKeywordExtraction(false)
                .includeFactCheck(false)
                .build();

        // Then
        assertThat(minimalAnalysis.getIncludeBiasAnalysis()).isFalse();
        assertThat(minimalAnalysis.getIncludeSentimentAnalysis()).isFalse();
        assertThat(minimalAnalysis.getIncludeKeywordExtraction()).isFalse();
        assertThat(minimalAnalysis.getIncludeFactCheck()).isFalse();
    }

    @Test
    @DisplayName("분석 결과 설정 및 조회 테스트")
    void analysisResultsHandling() {
        // Given
        String biasResult = "{\"score\": 50}";
        String sentimentResult = "{\"emotion\": \"positive\"}";
        String keywordResult = "{\"keywords\": [\"test\"]}";
        String factCheckResult = "{\"verified\": true}";

        // When
        analysis.setBiasAnalysisResult(biasResult);
        analysis.setSentimentAnalysisResult(sentimentResult);
        analysis.setKeywordExtractionResult(keywordResult);
        analysis.setFactCheckResult(factCheckResult);

        // Then
        assertThat(analysis.getBiasAnalysisResult()).isEqualTo(biasResult);
        assertThat(analysis.getSentimentAnalysisResult()).isEqualTo(sentimentResult);
        assertThat(analysis.getKeywordExtractionResult()).isEqualTo(keywordResult);
        assertThat(analysis.getFactCheckResult()).isEqualTo(factCheckResult);
    }

    @Test
    @DisplayName("분석 메타데이터 검증")
    void analysisMetadataValidation() {
        // Given
        String userId = "user123";
        String title = "분석 제목";
        AnalysisType type = AnalysisType.URL_ANALYSIS;
        String targetUrl = "https://test.com";

        // When
        Analysis newAnalysis = Analysis.builder()
                .userId(userId)
                .analysisType(type)
                .targetUrl(targetUrl)
                .title(title)
                .build();

        // Then
        assertThat(newAnalysis.getUserId()).isEqualTo(userId);
        assertThat(newAnalysis.getTitle()).isEqualTo(title);
        assertThat(newAnalysis.getAnalysisType()).isEqualTo(type);
        assertThat(newAnalysis.getTargetUrl()).isEqualTo(targetUrl);
        assertThat(newAnalysis.getTargetText()).isNull();
        assertThat(newAnalysis.getTargetNewsId()).isNull();
    }
}