package com.commonground.be.domain.analysis.facade;

import com.commonground.be.domain.analysis.dto.request.AnalysisStartRequest;
import com.commonground.be.domain.analysis.dto.response.AnalysisResponse;
import com.commonground.be.domain.analysis.dto.response.AnalysisResultResponse;
import com.commonground.be.domain.analysis.dto.response.MyAnalysesResponse;
import com.commonground.be.domain.analysis.entity.Analysis;
import com.commonground.be.domain.analysis.enums.AnalysisStatus;
import com.commonground.be.domain.analysis.enums.AnalysisType;
import com.commonground.be.domain.analysis.service.AnalysisService;
import com.commonground.be.global.application.exception.CommonException;
import com.commonground.be.global.application.response.ResponseExceptionEnum;
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
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Arrays;
import java.util.Collections;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("AnalysisFacade 단위 테스트")
class AnalysisFacadeTest {

    @Mock
    private AnalysisService analysisService;

    @InjectMocks
    private AnalysisFacade analysisFacade;

    private UserDetails mockUserDetails;
    private AnalysisStartRequest mockStartRequest;
    private Analysis mockAnalysis;
    private AnalysisResponse mockAnalysisResponse;
    private AnalysisResultResponse mockResultResponse;
    private MyAnalysesResponse mockMyAnalysesResponse;

    @BeforeEach
    void setUp() {
        // Mock UserDetails
        mockUserDetails = User.builder()
                .username("testUser")
                .password("password")
                .authorities("ROLE_USER")
                .build();

        // Mock AnalysisStartRequest
        mockStartRequest = new AnalysisStartRequest();
        mockStartRequest.setType(AnalysisType.TEXT_ANALYSIS);
        mockStartRequest.setText("테스트 분석 텍스트");
        mockStartRequest.setTitle("테스트 분석");
        AnalysisStartRequest.AnalysisOptions options = new AnalysisStartRequest.AnalysisOptions();
        options.setIncludeBiasAnalysis(true);
        options.setIncludeSentimentAnalysis(true);
        options.setIncludeKeywordExtraction(true);
        options.setIncludeFactCheck(false);
        mockStartRequest.setOptions(options);

        // Mock Analysis Entity
        mockAnalysis = Analysis.builder()
                .userId("testUser")
                .analysisType(AnalysisType.TEXT_ANALYSIS)
                .targetText("테스트 분석 텍스트")
                .title("테스트 분석")
                .includeBiasAnalysis(true)
                .includeSentimentAnalysis(true)
                .includeKeywordExtraction(true)
                .includeFactCheck(false)
                .build();

        // Mock Responses
        mockAnalysisResponse = new AnalysisResponse(mockAnalysis);
        mockResultResponse = new AnalysisResultResponse(mockAnalysis);

        // Mock MyAnalysesResponse
        Page<Analysis> analysisPage = new PageImpl<>(Arrays.asList(mockAnalysis), PageRequest.of(0, 10), 1);
        mockMyAnalysesResponse = new MyAnalysesResponse(analysisPage);
    }

    @Test
    @DisplayName("분석 생성 파이프라인 성공")
    void createAnalysisFlow_Success() {
        // Given
        given(analysisService.startAnalysis(eq("testUser"), any(AnalysisStartRequest.class)))
                .willReturn(mockAnalysisResponse);

        // When
        AnalysisFacade.AnalysisFlowResult result = analysisFacade.createAnalysisFlow(mockUserDetails, mockStartRequest);

        // Then
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getMessage()).isEqualTo("분석이 생성되었습니다.");
        assertThat(result.getData()).isEqualTo(mockAnalysisResponse);
        assertThat(result.getError()).isNull();

        verify(analysisService).startAnalysis("testUser", mockStartRequest);
    }

    @Test
    @DisplayName("분석 생성 파이프라인 - 사용자 인증 실패")
    void createAnalysisFlow_AuthenticationFailed() {
        // When
        AnalysisFacade.AnalysisFlowResult result = analysisFacade.createAnalysisFlow(null, mockStartRequest);

        // Then
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).contains("분석 생성 실패");
        assertThat(result.getData()).isNull();
        assertThat(result.getError()).isNotNull();
    }

    @Test
    @DisplayName("분석 생성 파이프라인 - 요청 검증 실패")
    void createAnalysisFlow_RequestValidationFailed() {
        // Given - 잘못된 요청 (type이 null)
        AnalysisStartRequest invalidRequest = new AnalysisStartRequest();
        invalidRequest.setText("텍스트");

        // When
        AnalysisFacade.AnalysisFlowResult result = analysisFacade.createAnalysisFlow(mockUserDetails, invalidRequest);

        // Then
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).contains("분석 생성 실패");
        assertThat(result.getError()).isNotNull();
    }

    @Test
    @DisplayName("분석 생성 파이프라인 - 분석 내용 누락")
    void createAnalysisFlow_MissingContent() {
        // Given - 분석할 내용이 없는 요청
        AnalysisStartRequest emptyRequest = new AnalysisStartRequest();
        emptyRequest.setType(AnalysisType.TEXT_ANALYSIS);

        // When
        AnalysisFacade.AnalysisFlowResult result = analysisFacade.createAnalysisFlow(mockUserDetails, emptyRequest);

        // Then
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).contains("분석 생성 실패");
        assertThat(result.getError()).isNotNull();
    }

    @Test
    @DisplayName("분석 생성 파이프라인 - 서비스 예외")
    void createAnalysisFlow_ServiceException() {
        // Given
        given(analysisService.startAnalysis(eq("testUser"), any(AnalysisStartRequest.class)))
                .willThrow(new CommonException(ResponseExceptionEnum.ANALYSIS_LIMIT_EXCEEDED));

        // When
        AnalysisFacade.AnalysisFlowResult result = analysisFacade.createAnalysisFlow(mockUserDetails, mockStartRequest);

        // Then
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).contains("분석 생성 실패");
        assertThat(result.getError()).isInstanceOf(CommonException.class);
    }

    @Test
    @DisplayName("분석 조회 파이프라인 성공")
    void getAnalysisFlow_Success() {
        // Given
        String analysisId = "1";
        given(analysisService.getAnalysisResult("testUser", 1L))
                .willReturn(mockResultResponse);

        // When
        AnalysisFacade.AnalysisFlowResult result = analysisFacade.getAnalysisFlow(mockUserDetails, analysisId);

        // Then
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getMessage()).isEqualTo("분석 조회 완료");
        assertThat(result.getData()).isEqualTo(mockResultResponse);
        assertThat(result.getError()).isNull();

        verify(analysisService).getAnalysisResult("testUser", 1L);
    }

    @Test
    @DisplayName("분석 조회 파이프라인 - 분석 ID 검증 실패")
    void getAnalysisFlow_InvalidAnalysisId() {
        // When - 잘못된 분석 ID
        AnalysisFacade.AnalysisFlowResult result = analysisFacade.getAnalysisFlow(mockUserDetails, "invalid");

        // Then
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).contains("분석 조회 실패");
        assertThat(result.getError()).isNotNull();
    }

    @Test
    @DisplayName("분석 조회 파이프라인 - 빈 분석 ID")
    void getAnalysisFlow_EmptyAnalysisId() {
        // When
        AnalysisFacade.AnalysisFlowResult result = analysisFacade.getAnalysisFlow(mockUserDetails, "");

        // Then
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).contains("분석 조회 실패");
        assertThat(result.getError()).isNotNull();
    }

    @Test
    @DisplayName("분석 조회 파이프라인 - 서비스 예외")
    void getAnalysisFlow_ServiceException() {
        // Given
        String analysisId = "1";
        given(analysisService.getAnalysisResult("testUser", 1L))
                .willThrow(new CommonException(ResponseExceptionEnum.ANALYSIS_NOT_FOUND));

        // When
        AnalysisFacade.AnalysisFlowResult result = analysisFacade.getAnalysisFlow(mockUserDetails, analysisId);

        // Then
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).contains("분석 조회 실패");
        assertThat(result.getError()).isInstanceOf(CommonException.class);
    }

    @Test
    @DisplayName("사용자 분석 목록 조회 파이프라인 성공")
    void getUserAnalysesFlow_Success() {
        // Given
        int page = 1;
        int size = 10;
        given(analysisService.getMyAnalyses(eq("testUser"), any(Pageable.class)))
                .willReturn(mockMyAnalysesResponse);

        // When
        AnalysisFacade.AnalysisFlowResult result = analysisFacade.getUserAnalysesFlow(mockUserDetails, page, size);

        // Then
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getMessage()).isEqualTo("분석 목록 조회 완료");
        assertThat(result.getData()).isEqualTo(mockMyAnalysesResponse);
        assertThat(result.getError()).isNull();

        verify(analysisService).getMyAnalyses(eq("testUser"), any(Pageable.class));
    }

    @Test
    @DisplayName("사용자 분석 목록 조회 파이프라인 - 페이징 파라미터 검증 실패")
    void getUserAnalysesFlow_InvalidPagingParameters() {
        // When - 잘못된 페이지 번호
        AnalysisFacade.AnalysisFlowResult result = analysisFacade.getUserAnalysesFlow(mockUserDetails, 0, 10);

        // Then
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).contains("분석 목록 조회 실패");
        assertThat(result.getError()).isNotNull();
    }

    @Test
    @DisplayName("사용자 분석 목록 조회 파이프라인 - 페이지 크기 초과")
    void getUserAnalysesFlow_PageSizeExceeded() {
        // When - 페이지 크기 초과
        AnalysisFacade.AnalysisFlowResult result = analysisFacade.getUserAnalysesFlow(mockUserDetails, 1, 101);

        // Then
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).contains("분석 목록 조회 실패");
        assertThat(result.getError()).isNotNull();
    }

    @Test
    @DisplayName("사용자 분석 목록 조회 파이프라인 - 서비스 예외")
    void getUserAnalysesFlow_ServiceException() {
        // Given
        given(analysisService.getMyAnalyses(eq("testUser"), any(Pageable.class)))
                .willThrow(new RuntimeException("서비스 오류"));

        // When
        AnalysisFacade.AnalysisFlowResult result = analysisFacade.getUserAnalysesFlow(mockUserDetails, 1, 10);

        // Then
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).contains("분석 목록 조회 실패");
        assertThat(result.getError()).isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("분석 삭제 파이프라인 성공")
    void deleteAnalysisFlow_Success() {
        // Given
        String analysisId = "1";

        // When
        AnalysisFacade.AnalysisFlowResult result = analysisFacade.deleteAnalysisFlow(mockUserDetails, analysisId);

        // Then
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getMessage()).isEqualTo("분석이 삭제되었습니다.");
        assertThat(result.getData()).isNull();
        assertThat(result.getError()).isNull();

        verify(analysisService).cancelAnalysis("testUser", 1L);
    }

    @Test
    @DisplayName("분석 삭제 파이프라인 - 분석 ID 검증 실패")
    void deleteAnalysisFlow_InvalidAnalysisId() {
        // When
        AnalysisFacade.AnalysisFlowResult result = analysisFacade.deleteAnalysisFlow(mockUserDetails, null);

        // Then
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).contains("분석 삭제 실패");
        assertThat(result.getError()).isNotNull();
    }

    @Test
    @DisplayName("분석 재시작 파이프라인 성공")
    void restartAnalysisFlow_Success() {
        // Given
        String analysisId = "1";
        given(analysisService.restartAnalysis(eq("testUser"), eq(1L), any()))
                .willReturn(mockAnalysisResponse);

        // When
        AnalysisFacade.AnalysisFlowResult result = analysisFacade.restartAnalysisFlow(mockUserDetails, analysisId);

        // Then
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getMessage()).isEqualTo("분석이 재시작되었습니다.");
        assertThat(result.getData()).isEqualTo(mockAnalysisResponse);
        assertThat(result.getError()).isNull();

        verify(analysisService).restartAnalysis(eq("testUser"), eq(1L), any());
    }

    @Test
    @DisplayName("분석 재시작 파이프라인 - 재시작 불가능한 상태")
    void restartAnalysisFlow_CannotRestart() {
        // Given
        String analysisId = "1";
        given(analysisService.restartAnalysis(eq("testUser"), eq(1L), any()))
                .willThrow(new CommonException(ResponseExceptionEnum.ANALYSIS_CANNOT_RESTART));

        // When
        AnalysisFacade.AnalysisFlowResult result = analysisFacade.restartAnalysisFlow(mockUserDetails, analysisId);

        // Then
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).contains("분석 재시작 실패");
        assertThat(result.getError()).isInstanceOf(CommonException.class);
    }

    @Test
    @DisplayName("URL 분석 요청 검증 성공")
    void createAnalysisFlow_UrlAnalysis_Success() {
        // Given
        AnalysisStartRequest urlRequest = new AnalysisStartRequest();
        urlRequest.setType(AnalysisType.URL_ANALYSIS);
        urlRequest.setUrl("https://example.com/news");
        urlRequest.setTitle("URL 분석");

        Analysis urlAnalysis = Analysis.builder()
                .userId("testUser")
                .analysisType(AnalysisType.URL_ANALYSIS)
                .targetUrl("https://example.com/news")
                .title("URL 분석")
                .includeBiasAnalysis(true)
                .includeSentimentAnalysis(true)
                .includeKeywordExtraction(true)
                .includeFactCheck(false)
                .build();

        AnalysisResponse urlResponse = new AnalysisResponse(urlAnalysis);

        given(analysisService.startAnalysis(eq("testUser"), any(AnalysisStartRequest.class)))
                .willReturn(urlResponse);

        // When
        AnalysisFacade.AnalysisFlowResult result = analysisFacade.createAnalysisFlow(mockUserDetails, urlRequest);

        // Then
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData()).isEqualTo(urlResponse);
    }

    @Test
    @DisplayName("뉴스 분석 요청 검증 성공")
    void createAnalysisFlow_NewsAnalysis_Success() {
        // Given
        AnalysisStartRequest newsRequest = new AnalysisStartRequest();
        newsRequest.setType(AnalysisType.NEWS_ANALYSIS);
        newsRequest.setNewsId("news123");
        newsRequest.setTitle("뉴스 분석");

        Analysis newsAnalysis = Analysis.builder()
                .userId("testUser")
                .analysisType(AnalysisType.NEWS_ANALYSIS)
                .targetNewsId("news123")
                .title("뉴스 분석")
                .includeBiasAnalysis(true)
                .includeSentimentAnalysis(true)
                .includeKeywordExtraction(true)
                .includeFactCheck(false)
                .build();

        AnalysisResponse newsResponse = new AnalysisResponse(newsAnalysis);

        given(analysisService.startAnalysis(eq("testUser"), any(AnalysisStartRequest.class)))
                .willReturn(newsResponse);

        // When
        AnalysisFacade.AnalysisFlowResult result = analysisFacade.createAnalysisFlow(mockUserDetails, newsRequest);

        // Then
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData()).isEqualTo(newsResponse);
    }

    @Test
    @DisplayName("AnalysisFlowResult 정적 팩토리 메서드 테스트")
    void analysisFlowResult_StaticFactoryMethods() {
        // Given
        String message = "테스트 메시지";
        Object data = "테스트 데이터";
        Exception error = new RuntimeException("테스트 오류");

        // When & Then - Success with data
        AnalysisFacade.AnalysisFlowResult successResult = AnalysisFacade.AnalysisFlowResult.success(message, data);
        assertThat(successResult.isSuccess()).isTrue();
        assertThat(successResult.getMessage()).isEqualTo(message);
        assertThat(successResult.getData()).isEqualTo(data);
        assertThat(successResult.getError()).isNull();

        // When & Then - Success with message only
        AnalysisFacade.AnalysisFlowResult successWithMessageResult = AnalysisFacade.AnalysisFlowResult.successWithMessage(message);
        assertThat(successWithMessageResult.isSuccess()).isTrue();
        assertThat(successWithMessageResult.getMessage()).isEqualTo(message);
        assertThat(successWithMessageResult.getData()).isNull();
        assertThat(successWithMessageResult.getError()).isNull();

        // When & Then - Failure
        AnalysisFacade.AnalysisFlowResult failureResult = AnalysisFacade.AnalysisFlowResult.failure(message, error);
        assertThat(failureResult.isSuccess()).isFalse();
        assertThat(failureResult.getMessage()).isEqualTo(message);
        assertThat(failureResult.getData()).isNull();
        assertThat(failureResult.getError()).isEqualTo(error);
    }

    @Test
    @DisplayName("인증 검증 - 유효한 사용자")
    void validateAuthentication_ValidUser() {
        // Given & When
        AnalysisFacade.AnalysisFlowResult result = analysisFacade.createAnalysisFlow(mockUserDetails, mockStartRequest);

        // Then - 인증 에러가 발생하지 않아야 함
        if (!result.isSuccess()) {
            assertThat(result.getMessage()).doesNotContain("인증이 필요합니다");
        }
    }

    @Test
    @DisplayName("분석 요청 검증 - 모든 필수 필드 존재")
    void validateAnalysisRequest_AllRequiredFieldsPresent() {
        // Given
        given(analysisService.startAnalysis(eq("testUser"), any(AnalysisStartRequest.class)))
                .willReturn(mockAnalysisResponse);

        // When
        AnalysisFacade.AnalysisFlowResult result = analysisFacade.createAnalysisFlow(mockUserDetails, mockStartRequest);

        // Then
        assertThat(result.isSuccess()).isTrue();
        verify(analysisService).startAnalysis("testUser", mockStartRequest);
    }

    @Test
    @DisplayName("페이징 파라미터 검증 - 유효한 범위")
    void validatePagingParameters_ValidRange() {
        // Given
        given(analysisService.getMyAnalyses(eq("testUser"), any(Pageable.class)))
                .willReturn(mockMyAnalysesResponse);

        // When
        AnalysisFacade.AnalysisFlowResult result = analysisFacade.getUserAnalysesFlow(mockUserDetails, 1, 50);

        // Then
        assertThat(result.isSuccess()).isTrue();
    }

    @Test
    @DisplayName("페이징 파라미터 검증 - 경계값 테스트")
    void validatePagingParameters_BoundaryValues() {
        // Given
        given(analysisService.getMyAnalyses(eq("testUser"), any(Pageable.class)))
                .willReturn(mockMyAnalysesResponse);

        // When & Then - 최소값
        AnalysisFacade.AnalysisFlowResult result1 = analysisFacade.getUserAnalysesFlow(mockUserDetails, 1, 1);
        assertThat(result1.isSuccess()).isTrue();

        // When & Then - 최대값
        AnalysisFacade.AnalysisFlowResult result2 = analysisFacade.getUserAnalysesFlow(mockUserDetails, 1, 100);
        assertThat(result2.isSuccess()).isTrue();
    }
}