package com.commonground.be.domain.analysis.service;

import com.commonground.be.domain.analysis.dto.request.AnalysisRestartRequest;
import com.commonground.be.domain.analysis.dto.request.AnalysisStartRequest;
import com.commonground.be.domain.analysis.dto.response.AnalysisResponse;
import com.commonground.be.domain.analysis.dto.response.AnalysisResultResponse;
import com.commonground.be.domain.analysis.dto.response.AnalysisStatusResponse;
import com.commonground.be.domain.analysis.dto.response.MyAnalysesResponse;
import com.commonground.be.domain.analysis.entity.Analysis;
import com.commonground.be.domain.analysis.enums.AnalysisStatus;
import com.commonground.be.domain.analysis.enums.AnalysisType;
import com.commonground.be.domain.analysis.repository.AnalysisRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.Arrays;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.atLeast;

import java.util.Collections;

@ExtendWith(MockitoExtension.class)
@DisplayName("AnalysisService 단위 테스트")
class AnalysisServiceTest {

    @Mock
    private AnalysisRepository analysisRepository;

    @Mock
    private BiasAnalysisService biasAnalysisService;

    @Mock
    private SentimentAnalysisService sentimentAnalysisService;

    @Mock
    private KeywordExtractionService keywordExtractionService;

    @InjectMocks
    private AnalysisServiceImpl analysisService;

    private String testUserId;
    private Analysis testAnalysis;
    private AnalysisStartRequest testStartRequest;

    @BeforeEach
    void setUp() {
        testUserId = "testUser";
        testAnalysis = Analysis.builder()
                .userId(testUserId)
                .analysisType(AnalysisType.TEXT_ANALYSIS)
                .targetText("테스트 분석 텍스트")
                .title("테스트 분석")
                .includeBiasAnalysis(true)
                .includeSentimentAnalysis(true)
                .includeKeywordExtraction(true)
                .includeFactCheck(false)
                .build();
        
        testStartRequest = new AnalysisStartRequest();
        testStartRequest.setType(AnalysisType.TEXT_ANALYSIS);
        testStartRequest.setText("테스트 분석 텍스트");
        testStartRequest.setTitle("테스트 분석");
        AnalysisStartRequest.AnalysisOptions options = new AnalysisStartRequest.AnalysisOptions();
        options.setIncludeBiasAnalysis(true);
        options.setIncludeSentimentAnalysis(true);
        options.setIncludeKeywordExtraction(true);
        options.setIncludeFactCheck(false);
        testStartRequest.setOptions(options);
    }

    @Test
    @DisplayName("분석 시작 성공")
    void startAnalysis_Success() {
        // Given
        given(analysisRepository.save(any(Analysis.class))).willReturn(testAnalysis);

        // When
        AnalysisResponse response = analysisService.startAnalysis(testUserId, testStartRequest);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getType()).isEqualTo(AnalysisType.TEXT_ANALYSIS);
        assertThat(response.getStatus()).isEqualTo(AnalysisStatus.PENDING);
        verify(analysisRepository).save(any(Analysis.class));
    }

    @Test
    @DisplayName("분석 결과 조회 성공")
    void getAnalysisResult_Success() {
        // Given
        Long analysisId = 1L;
        testAnalysis.complete();
        given(analysisRepository.findByIdAndUserIdAndDeletedAtIsNull(analysisId, testUserId))
                .willReturn(Optional.of(testAnalysis));

        // When
        AnalysisResultResponse response = analysisService.getAnalysisResult(testUserId, analysisId);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(AnalysisStatus.COMPLETED);
        assertThat(response.getProgressPercentage()).isEqualTo(100);
    }

    @Test
    @DisplayName("분석 상태 조회 성공")
    void getAnalysisStatus_Success() {
        // Given
        Long analysisId = 1L;
        given(analysisRepository.findByIdAndUserIdAndDeletedAtIsNull(analysisId, testUserId))
                .willReturn(Optional.of(testAnalysis));

        // When
        AnalysisStatusResponse response = analysisService.getAnalysisStatus(testUserId, analysisId);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(AnalysisStatus.PENDING);
        assertThat(response.getProgressPercentage()).isEqualTo(0);
    }

    @Test
    @DisplayName("내 분석 목록 조회 성공")
    void getMyAnalyses_Success() {
        // Given
        Pageable pageable = PageRequest.of(0, 10);
        Page<Analysis> analysisPage = new PageImpl<>(Arrays.asList(testAnalysis), pageable, 1);
        given(analysisRepository.findByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(testUserId, pageable))
                .willReturn(analysisPage);

        // When
        MyAnalysesResponse response = analysisService.getMyAnalyses(testUserId, pageable);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getAnalyses()).hasSize(1);
        assertThat(response.getPageInfo().getCurrentPage()).isEqualTo(1);
        assertThat(response.getPageInfo().getTotalElements()).isEqualTo(1);
    }

    @Test
    @DisplayName("분석 취소 성공")
    void cancelAnalysis_Success() {
        // Given
        Long analysisId = 1L;
        given(analysisRepository.findByIdAndUserIdAndDeletedAtIsNull(analysisId, testUserId))
                .willReturn(Optional.of(testAnalysis));

        // When
        analysisService.cancelAnalysis(testUserId, analysisId);

        // Then
        assertThat(testAnalysis.getStatus()).isEqualTo(AnalysisStatus.CANCELLED);
        verify(analysisRepository).save(testAnalysis);
    }

    @Test
    @DisplayName("분석 재시작 성공")
    void restartAnalysis_Success() {
        // Given
        Long analysisId = 1L;
        testAnalysis.setError("이전 오류");
        AnalysisRestartRequest restartRequest = new AnalysisRestartRequest();
        
        given(analysisRepository.findByIdAndUserIdAndDeletedAtIsNull(analysisId, testUserId))
                .willReturn(Optional.of(testAnalysis));

        // When
        AnalysisResponse response = analysisService.restartAnalysis(testUserId, analysisId, restartRequest);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(AnalysisStatus.PENDING);
        assertThat(testAnalysis.getProgressPercentage()).isEqualTo(0);
        verify(analysisRepository).save(testAnalysis);
    }

    @Test
    @DisplayName("새 분석 시작 가능 여부 확인 - 가능")
    void canStartNewAnalysis_True() {
        // Given
        given(analysisRepository.countByUserIdAndCreatedAtAfter(
                eq(testUserId), any())).willReturn(5L); // 일일 한도 미만

        // When
        boolean canStart = analysisService.canStartNewAnalysis(testUserId);

        // Then
        assertThat(canStart).isTrue();
    }

    @Test
    @DisplayName("새 분석 시작 가능 여부 확인 - 불가능")
    void canStartNewAnalysis_False() {
        // Given
        given(analysisRepository.countByUserIdAndCreatedAtAfter(
                eq(testUserId), any())).willReturn(10L); // 일일 한도 초과

        // When
        boolean canStart = analysisService.canStartNewAnalysis(testUserId);

        // Then
        assertThat(canStart).isFalse();
    }

    @Test
    @DisplayName("존재하지 않는 분석 조회 시 예외 발생")
    void getAnalysisResult_NotFound_ThrowsException() {
        // Given
        Long nonExistentId = 999L;
        given(analysisRepository.findByIdAndUserIdAndDeletedAtIsNull(nonExistentId, testUserId))
                .willReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> analysisService.getAnalysisResult(testUserId, nonExistentId))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("분석 재시작 - 재시작 불가능한 상태에서 예외 발생")
    void restartAnalysis_CannotRestart_ThrowsException() {
        // Given
        Long analysisId = 1L;
        testAnalysis.complete(); // 완료된 상태로 변경
        AnalysisRestartRequest restartRequest = new AnalysisRestartRequest();
        
        given(analysisRepository.findByIdAndUserIdAndDeletedAtIsNull(analysisId, testUserId))
                .willReturn(Optional.of(testAnalysis));

        // When & Then
        assertThatThrownBy(() -> analysisService.restartAnalysis(testUserId, analysisId, restartRequest))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("분석 취소 - 이미 완료된 분석 취소 시 예외 발생")
    void cancelAnalysis_AlreadyCompleted_ThrowsException() {
        // Given
        Long analysisId = 1L;
        testAnalysis.complete(); // 완료된 상태로 변경
        
        given(analysisRepository.findByIdAndUserIdAndDeletedAtIsNull(analysisId, testUserId))
                .willReturn(Optional.of(testAnalysis));

        // When & Then
        assertThatThrownBy(() -> analysisService.cancelAnalysis(testUserId, analysisId))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("분석 시작 - 일일 한도 초과 시 예외 발생")
    void startAnalysis_DailyLimitExceeded_ThrowsException() {
        // Given
        given(analysisRepository.countByUserIdAndCreatedAtAfter(eq(testUserId), any()))
                .willReturn(50L); // 일일 한도 초과

        // When & Then
        assertThatThrownBy(() -> analysisService.startAnalysis(testUserId, testStartRequest))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("URL 분석 요청 처리")
    void startAnalysis_UrlAnalysis_Success() {
        // Given
        AnalysisStartRequest urlRequest = new AnalysisStartRequest();
        urlRequest.setType(AnalysisType.URL_ANALYSIS);
        urlRequest.setUrl("https://example.com/news");
        urlRequest.setTitle("URL 분석 테스트");

        Analysis urlAnalysis = Analysis.builder()
                .userId(testUserId)
                .analysisType(AnalysisType.URL_ANALYSIS)
                .targetUrl("https://example.com/news")
                .title("URL 분석 테스트")
                .includeBiasAnalysis(true)
                .includeSentimentAnalysis(true)
                .includeKeywordExtraction(true)
                .includeFactCheck(false)
                .build();

        given(analysisRepository.save(any(Analysis.class))).willReturn(urlAnalysis);

        // When
        AnalysisResponse response = analysisService.startAnalysis(testUserId, urlRequest);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getType()).isEqualTo(AnalysisType.URL_ANALYSIS);
        verify(analysisRepository).save(any(Analysis.class));
    }

    @Test
    @DisplayName("뉴스 분석 요청 처리")
    void startAnalysis_NewsAnalysis_Success() {
        // Given
        AnalysisStartRequest newsRequest = new AnalysisStartRequest();
        newsRequest.setType(AnalysisType.NEWS_ANALYSIS);
        newsRequest.setNewsId("news123");
        newsRequest.setTitle("뉴스 분석 테스트");

        Analysis newsAnalysis = Analysis.builder()
                .userId(testUserId)
                .analysisType(AnalysisType.NEWS_ANALYSIS)
                .targetNewsId("news123")
                .title("뉴스 분석 테스트")
                .includeBiasAnalysis(true)
                .includeSentimentAnalysis(true)
                .includeKeywordExtraction(true)
                .includeFactCheck(false)
                .build();

        given(analysisRepository.save(any(Analysis.class))).willReturn(newsAnalysis);

        // When
        AnalysisResponse response = analysisService.startAnalysis(testUserId, newsRequest);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getType()).isEqualTo(AnalysisType.NEWS_ANALYSIS);
        verify(analysisRepository).save(any(Analysis.class));
    }

    @Test
    @DisplayName("분석 옵션 없이 요청 시 기본값 적용")
    void startAnalysis_NoOptions_DefaultValues() {
        // Given
        AnalysisStartRequest requestWithoutOptions = new AnalysisStartRequest();
        requestWithoutOptions.setType(AnalysisType.TEXT_ANALYSIS);
        requestWithoutOptions.setText("텍스트");
        requestWithoutOptions.setOptions(null);

        given(analysisRepository.save(any(Analysis.class))).willReturn(testAnalysis);

        // When
        AnalysisResponse response = analysisService.startAnalysis(testUserId, requestWithoutOptions);

        // Then
        assertThat(response).isNotNull();
        verify(analysisRepository).save(any(Analysis.class));
    }

    @Test
    @DisplayName("분석 제목 자동 생성 - URL 분석")
    void generateTitle_UrlAnalysis() {
        // Given
        AnalysisStartRequest urlRequest = new AnalysisStartRequest();
        urlRequest.setType(AnalysisType.URL_ANALYSIS);
        urlRequest.setUrl("https://news.example.com/article/123");
        urlRequest.setTitle(null); // 제목 없음

        Analysis expectedAnalysis = Analysis.builder()
                .userId(testUserId)
                .analysisType(AnalysisType.URL_ANALYSIS)
                .targetUrl("https://news.example.com/article/123")
                .title("URL 분석: news.example.com")
                .includeBiasAnalysis(true)
                .includeSentimentAnalysis(true)
                .includeKeywordExtraction(true)
                .includeFactCheck(false)
                .build();

        given(analysisRepository.save(any(Analysis.class))).willReturn(expectedAnalysis);

        // When
        AnalysisResponse response = analysisService.startAnalysis(testUserId, urlRequest);

        // Then
        assertThat(response).isNotNull();
        verify(analysisRepository).save(any(Analysis.class));
    }

    @Test
    @DisplayName("분석 제목 자동 생성 - 텍스트 분석")
    void generateTitle_TextAnalysis() {
        // Given
        String longText = "이것은 매우 긴 텍스트입니다. 이 텍스트는 50자를 초과하므로 자동으로 잘려야 합니다. 추가 텍스트...";
        AnalysisStartRequest textRequest = new AnalysisStartRequest();
        textRequest.setType(AnalysisType.TEXT_ANALYSIS);
        textRequest.setText(longText);
        textRequest.setTitle(null); // 제목 없음

        given(analysisRepository.save(any(Analysis.class))).willReturn(testAnalysis);

        // When
        AnalysisResponse response = analysisService.startAnalysis(testUserId, textRequest);

        // Then
        assertThat(response).isNotNull();
        verify(analysisRepository).save(any(Analysis.class));
    }

    @Test
    @DisplayName("분석 수행 중 편향 분석 실행")
    void performAnalysis_BiasAnalysisEnabled() throws Exception {
        // Given
        testAnalysis.updateStatus(AnalysisStatus.PENDING);
        given(biasAnalysisService.analyzeBias(any())).willReturn("{\"biasScore\": 50}");
        given(analysisRepository.save(any(Analysis.class))).willReturn(testAnalysis);

        // When
        analysisService.performAnalysis(testAnalysis);

        // Then
        verify(biasAnalysisService).analyzeBias(any());
        verify(analysisRepository, atLeast(2)).save(testAnalysis);
    }

    @Test
    @DisplayName("분석 수행 중 감정 분석 실행")
    void performAnalysis_SentimentAnalysisEnabled() throws Exception {
        // Given
        testAnalysis.updateStatus(AnalysisStatus.PENDING);
        given(sentimentAnalysisService.analyzeSentiment(any())).willReturn("{\"sentiment\": \"positive\"}");
        given(analysisRepository.save(any(Analysis.class))).willReturn(testAnalysis);

        // When
        analysisService.performAnalysis(testAnalysis);

        // Then
        verify(sentimentAnalysisService).analyzeSentiment(any());
        verify(analysisRepository, atLeast(2)).save(testAnalysis);
    }

    @Test
    @DisplayName("분석 수행 중 키워드 추출 실행")
    void performAnalysis_KeywordExtractionEnabled() throws Exception {
        // Given
        testAnalysis.updateStatus(AnalysisStatus.PENDING);
        given(keywordExtractionService.extractKeywords(any())).willReturn("{\"keywords\": [\"test\"]}");
        given(analysisRepository.save(any(Analysis.class))).willReturn(testAnalysis);

        // When
        analysisService.performAnalysis(testAnalysis);

        // Then
        verify(keywordExtractionService).extractKeywords(any());
        verify(analysisRepository, atLeast(2)).save(testAnalysis);
    }

    @Test
    @DisplayName("분석 수행 중 오류 처리")
    void performAnalysis_ErrorHandling() throws Exception {
        // Given
        testAnalysis.updateStatus(AnalysisStatus.PENDING);
        given(biasAnalysisService.analyzeBias(any())).willThrow(new RuntimeException("분석 오류"));
        given(analysisRepository.save(any(Analysis.class))).willReturn(testAnalysis);
        given(analysisRepository.findById(any())).willReturn(Optional.of(testAnalysis));

        // When
        analysisService.performAnalysis(testAnalysis);

        // Then
        verify(analysisRepository).findById(any());
        verify(analysisRepository, atLeast(1)).save(testAnalysis);
    }

    @Test
    @DisplayName("분석 오류 처리")
    void handleAnalysisError_Success() {
        // Given
        Long analysisId = 1L;
        String errorMessage = "분석 중 오류 발생";
        given(analysisRepository.findById(analysisId)).willReturn(Optional.of(testAnalysis));

        // When
        analysisService.handleAnalysisError(analysisId, errorMessage);

        // Then
        verify(analysisRepository).findById(analysisId);
        verify(analysisRepository).save(testAnalysis);
    }

    @Test
    @DisplayName("분석 옵션이 포함된 재시작 요청")
    void restartAnalysis_WithOptions() {
        // Given
        Long analysisId = 1L;
        testAnalysis.setError("이전 오류");
        
        AnalysisRestartRequest restartRequest = new AnalysisRestartRequest();
        AnalysisRestartRequest.AnalysisOptions options = new AnalysisRestartRequest.AnalysisOptions();
        options.setIncludeBiasAnalysis(false);
        options.setIncludeSentimentAnalysis(true);
        restartRequest.setOptions(options);
        
        given(analysisRepository.findByIdAndUserIdAndDeletedAtIsNull(analysisId, testUserId))
                .willReturn(Optional.of(testAnalysis));
        given(analysisRepository.save(any(Analysis.class))).willReturn(testAnalysis);

        // When
        AnalysisResponse response = analysisService.restartAnalysis(testUserId, analysisId, restartRequest);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(AnalysisStatus.PENDING);
        verify(analysisRepository).save(testAnalysis);
    }

    @Test
    @DisplayName("일일 분석 한도 확인 - 경계값 테스트")
    void canStartNewAnalysis_BoundaryValue() {
        // Given - 정확히 한도에 도달
        given(analysisRepository.countByUserIdAndCreatedAtAfter(eq(testUserId), any()))
                .willReturn(50L); // DAILY_ANALYSIS_LIMIT = 50

        // When
        boolean canStart = analysisService.canStartNewAnalysis(testUserId);

        // Then
        assertThat(canStart).isFalse();
    }

    @Test
    @DisplayName("일일 분석 한도 확인 - 한도 미만")
    void canStartNewAnalysis_BelowLimit() {
        // Given
        given(analysisRepository.countByUserIdAndCreatedAtAfter(eq(testUserId), any()))
                .willReturn(49L); // 한도 미만

        // When
        boolean canStart = analysisService.canStartNewAnalysis(testUserId);

        // Then
        assertThat(canStart).isTrue();
    }

    @Test
    @DisplayName("내 분석 목록 조회 - 빈 결과")
    void getMyAnalyses_EmptyResult() {
        // Given
        Pageable pageable = PageRequest.of(0, 10);
        Page<Analysis> emptyPage = new PageImpl<>(Collections.emptyList(), pageable, 0);
        given(analysisRepository.findByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(testUserId, pageable))
                .willReturn(emptyPage);

        // When
        MyAnalysesResponse response = analysisService.getMyAnalyses(testUserId, pageable);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getAnalyses()).isEmpty();
        assertThat(response.getPageInfo().getTotalElements()).isEqualTo(0);
    }

    @Test
    @DisplayName("분석 상태 조회 - 진행 중인 분석")
    void getAnalysisStatus_InProgress() {
        // Given
        Long analysisId = 1L;
        testAnalysis.updateStatus(AnalysisStatus.IN_PROGRESS);
        testAnalysis.updateProgress(50);
        
        given(analysisRepository.findByIdAndUserIdAndDeletedAtIsNull(analysisId, testUserId))
                .willReturn(Optional.of(testAnalysis));

        // When
        AnalysisStatusResponse response = analysisService.getAnalysisStatus(testUserId, analysisId);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(AnalysisStatus.IN_PROGRESS);
        assertThat(response.getProgressPercentage()).isEqualTo(50);
    }

    @Test
    @DisplayName("분석 상태 조회 - 실패한 분석")
    void getAnalysisStatus_Failed() {
        // Given
        Long analysisId = 1L;
        testAnalysis.setError("분석 실패");
        
        given(analysisRepository.findByIdAndUserIdAndDeletedAtIsNull(analysisId, testUserId))
                .willReturn(Optional.of(testAnalysis));

        // When
        AnalysisStatusResponse response = analysisService.getAnalysisStatus(testUserId, analysisId);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(AnalysisStatus.FAILED);
    }
}