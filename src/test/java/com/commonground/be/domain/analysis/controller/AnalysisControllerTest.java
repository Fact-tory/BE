package com.commonground.be.domain.analysis.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.commonground.be.config.TestSecurityConfig;
import com.commonground.be.domain.analysis.dto.request.AnalysisRestartRequest;
import com.commonground.be.domain.analysis.dto.request.AnalysisStartRequest;
import com.commonground.be.domain.analysis.dto.response.AnalysisResponse;
import com.commonground.be.domain.analysis.dto.response.AnalysisResultResponse;
import com.commonground.be.domain.analysis.dto.response.AnalysisStatusResponse;
import com.commonground.be.domain.analysis.dto.response.MyAnalysesResponse;
import com.commonground.be.domain.analysis.entity.Analysis;
import com.commonground.be.domain.analysis.enums.AnalysisType;
import com.commonground.be.domain.analysis.service.AnalysisService;
import com.commonground.be.global.application.exception.CommonException;
import com.commonground.be.global.application.response.ResponseExceptionEnum;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Arrays;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AnalysisController.class)
@Import(TestSecurityConfig.class)
@ActiveProfiles("test")
@DisplayName("AnalysisController 통합 테스트")
class AnalysisControllerTest {

	@Autowired
	private MockMvc mockMvc;

	                @MockitoBean
    private AnalysisService analysisService;

	@Autowired
	private ObjectMapper objectMapper;

	private Analysis mockAnalysis;
	private AnalysisStartRequest mockStartRequest;
	private AnalysisRestartRequest mockRestartRequest;
	private AnalysisResponse mockAnalysisResponse;
	private AnalysisResultResponse mockResultResponse;
	private AnalysisStatusResponse mockStatusResponse;
	private MyAnalysesResponse mockMyAnalysesResponse;

	@BeforeEach
	void setUp() {
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

		// Mock AnalysisRestartRequest
		mockRestartRequest = new AnalysisRestartRequest();
		AnalysisRestartRequest.AnalysisOptions restartOptions = new AnalysisRestartRequest.AnalysisOptions();
		restartOptions.setIncludeBiasAnalysis(true);
		restartOptions.setIncludeSentimentAnalysis(true);
		restartOptions.setIncludeKeywordExtraction(true);
		restartOptions.setIncludeFactCheck(false);
		mockRestartRequest.setOptions(restartOptions);

		// Mock Responses
		mockAnalysisResponse = new AnalysisResponse(mockAnalysis);
		mockResultResponse = new AnalysisResultResponse(mockAnalysis);
		mockStatusResponse = new AnalysisStatusResponse(mockAnalysis);

		// Mock MyAnalysesResponse
		Page<Analysis> analysisPage = new PageImpl<>(Arrays.asList(mockAnalysis),
				PageRequest.of(0, 10), 1);
		mockMyAnalysesResponse = new MyAnalysesResponse(analysisPage);
	}

	@Test
	@DisplayName("분석 시작 성공")
	@WithMockUser(username = "testUser")
	void startAnalysis_Success() throws Exception {
		// Given
		given(analysisService.startAnalysis(eq("testUser"), any(AnalysisStartRequest.class)))
				.willReturn(mockAnalysisResponse);

		// When & Then
		mockMvc.perform(post("/api/v1/analysis/start")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(mockStartRequest)))
				.andDo(print())
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.statusCode").value(200))
				.andExpect(jsonPath("$.data.type").value("TEXT_ANALYSIS"))
				.andExpect(jsonPath("$.data.status").value("PENDING"))
				.andExpect(jsonPath("$.data.title").value("테스트 분석"));
	}

	@Test
	@DisplayName("분석 시작 - 인증되지 않은 사용자")
	void startAnalysis_Unauthorized() throws Exception {
		// When & Then
		mockMvc.perform(post("/api/v1/analysis/start")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(mockStartRequest)))
				.andDo(print())
				.andExpect(status().isUnauthorized());
	}

	@Test
	@DisplayName("분석 시작 - 잘못된 요청")
	@WithMockUser(username = "testUser")
	void startAnalysis_InvalidRequest() throws Exception {
		// Given
		AnalysisStartRequest invalidRequest = new AnalysisStartRequest();
		invalidRequest.setType(AnalysisType.TEXT_ANALYSIS);
		// text, url, newsId 모두 없음

		// When & Then
		mockMvc.perform(post("/api/v1/analysis/start")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(invalidRequest)))
				.andDo(print())
				.andExpect(status().isBadRequest());
	}

	@Test
	@DisplayName("분석 시작 - 서비스 예외")
	@WithMockUser(username = "testUser")
	void startAnalysis_ServiceException() throws Exception {
		// Given
		given(analysisService.startAnalysis(eq("testUser"), any(AnalysisStartRequest.class)))
				.willThrow(new CommonException(ResponseExceptionEnum.ANALYSIS_LIMIT_EXCEEDED));

		// When & Then
		mockMvc.perform(post("/api/v1/analysis/start")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(mockStartRequest)))
				.andDo(print())
				.andExpect(status().isBadRequest());
	}

	@Test
	@DisplayName("분석 결과 조회 성공")
	@WithMockUser(username = "testUser")
	void getAnalysisResult_Success() throws Exception {
		// Given
		Long analysisId = 1L;
		given(analysisService.getAnalysisResult("testUser", analysisId))
				.willReturn(mockResultResponse);

		// When & Then
		mockMvc.perform(get("/api/v1/analysis/result/{analysisId}", analysisId))
				.andDo(print())
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.statusCode").value(200))
				.andExpect(jsonPath("$.data.status").value("PENDING"));
	}

	@Test
	@DisplayName("분석 결과 조회 - 존재하지 않는 분석")
	@WithMockUser(username = "testUser")
	void getAnalysisResult_NotFound() throws Exception {
		// Given
		Long analysisId = 999L;
		given(analysisService.getAnalysisResult("testUser", analysisId))
				.willThrow(new CommonException(ResponseExceptionEnum.ANALYSIS_NOT_FOUND));

		// When & Then
		mockMvc.perform(get("/api/v1/analysis/result/{analysisId}", analysisId))
				.andDo(print())
				.andExpect(status().isNotFound());
	}

	@Test
	@DisplayName("분석 상태 조회 성공")
	@WithMockUser(username = "testUser")
	void getAnalysisStatus_Success() throws Exception {
		// Given
		Long analysisId = 1L;
		given(analysisService.getAnalysisStatus("testUser", analysisId))
				.willReturn(mockStatusResponse);

		// When & Then
		mockMvc.perform(get("/api/v1/analysis/status/{analysisId}", analysisId))
				.andDo(print())
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.statusCode").value(200))
				.andExpect(jsonPath("$.data.status").value("PENDING"))
				.andExpect(jsonPath("$.data.progressPercentage").value(0));
	}

	@Test
	@DisplayName("내 분석 목록 조회 성공")
	@WithMockUser(username = "testUser")
	void getMyAnalyses_Success() throws Exception {
		// Given
		given(analysisService.getMyAnalyses(eq("testUser"), any(Pageable.class)))
				.willReturn(mockMyAnalysesResponse);

		// When & Then
		mockMvc.perform(get("/api/v1/analysis/my")
						.param("page", "1")
						.param("limit", "10"))
				.andDo(print())
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.statusCode").value(200))
				.andExpect(jsonPath("$.data.analyses").isArray())
				.andExpect(jsonPath("$.data.pageInfo.currentPage").value(1));
	}

	@Test
	@DisplayName("내 분석 목록 조회 - 기본 파라미터")
	@WithMockUser(username = "testUser")
	void getMyAnalyses_DefaultParameters() throws Exception {
		// Given
		given(analysisService.getMyAnalyses(eq("testUser"), any(Pageable.class)))
				.willReturn(mockMyAnalysesResponse);

		// When & Then
		mockMvc.perform(get("/api/v1/analysis/my"))
				.andDo(print())
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.statusCode").value(200));
	}

	@Test
	@DisplayName("내 분석 목록 조회 - 페이지 크기 제한")
	@WithMockUser(username = "testUser")
	void getMyAnalyses_LimitPageSize() throws Exception {
		// Given
		given(analysisService.getMyAnalyses(eq("testUser"), any(Pageable.class)))
				.willReturn(mockMyAnalysesResponse);

		// When & Then
		mockMvc.perform(get("/api/v1/analysis/my")
						.param("page", "1")
						.param("limit", "100")) // 50으로 제한되어야 함
				.andDo(print())
				.andExpect(status().isOk());
	}

	@Test
	@DisplayName("분석 취소 성공")
	@WithMockUser(username = "testUser")
	void cancelAnalysis_Success() throws Exception {
		// Given
		Long analysisId = 1L;
		doNothing().when(analysisService).cancelAnalysis("testUser", analysisId);

		// When & Then
		mockMvc.perform(delete("/api/v1/analysis/{analysisId}", analysisId))
				.andDo(print())
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.statusCode").value(200))
				.andExpect(jsonPath("$.data.message").value("분석이 취소되었습니다."));
	}

	@Test
	@DisplayName("분석 취소 - 이미 완료된 분석")
	@WithMockUser(username = "testUser")
	void cancelAnalysis_AlreadyCompleted() throws Exception {
		// Given
		Long analysisId = 1L;
		doThrow(new CommonException(ResponseExceptionEnum.ANALYSIS_ALREADY_COMPLETED))
				.when(analysisService).cancelAnalysis("testUser", analysisId);

		// When & Then
		mockMvc.perform(delete("/api/v1/analysis/{analysisId}", analysisId))
				.andDo(print())
				.andExpect(status().isBadRequest());
	}

	@Test
	@DisplayName("분석 재시작 성공")
	@WithMockUser(username = "testUser")
	void restartAnalysis_Success() throws Exception {
		// Given
		Long analysisId = 1L;
		given(analysisService.restartAnalysis(eq("testUser"), eq(analysisId),
				any(AnalysisRestartRequest.class)))
				.willReturn(mockAnalysisResponse);

		// When & Then
		mockMvc.perform(post("/api/v1/analysis/{analysisId}/restart", analysisId)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(mockRestartRequest)))
				.andDo(print())
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.statusCode").value(200))
				.andExpect(jsonPath("$.data.status").value("PENDING"));
	}

	@Test
	@DisplayName("분석 재시작 - 재시작할 수 없는 상태")
	@WithMockUser(username = "testUser")
	void restartAnalysis_CannotRestart() throws Exception {
		// Given
		Long analysisId = 1L;
		given(analysisService.restartAnalysis(eq("testUser"), eq(analysisId),
				any(AnalysisRestartRequest.class)))
				.willThrow(new CommonException(ResponseExceptionEnum.ANALYSIS_CANNOT_RESTART));

		// When & Then
		mockMvc.perform(post("/api/v1/analysis/{analysisId}/restart", analysisId)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(mockRestartRequest)))
				.andDo(print())
				.andExpect(status().isBadRequest());
	}

	@Test
	@DisplayName("분석 사용량 체크 성공 - 시작 가능")
	@WithMockUser(username = "testUser")
	void checkAnalysisUsage_CanStart() throws Exception {
		// Given
		given(analysisService.canStartNewAnalysis("testUser")).willReturn(true);

		// When & Then
		mockMvc.perform(get("/api/v1/analysis/usage"))
				.andDo(print())
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.statusCode").value(200))
				.andExpect(jsonPath("$.data.canStartNewAnalysis").value(true))
				.andExpect(jsonPath("$.data.message").value("새로운 분석을 시작할 수 있습니다."));
	}

	@Test
	@DisplayName("분석 사용량 체크 성공 - 시작 불가능")
	@WithMockUser(username = "testUser")
	void checkAnalysisUsage_CannotStart() throws Exception {
		// Given
		given(analysisService.canStartNewAnalysis("testUser")).willReturn(false);

		// When & Then
		mockMvc.perform(get("/api/v1/analysis/usage"))
				.andDo(print())
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.statusCode").value(200))
				.andExpect(jsonPath("$.data.canStartNewAnalysis").value(false))
				.andExpect(jsonPath("$.data.message").value("일일 분석 한도에 도달했습니다."));
	}

	@Test
	@DisplayName("분석 사용량 체크 - 인증되지 않은 사용자")
	void checkAnalysisUsage_Unauthorized() throws Exception {
		// When & Then
		mockMvc.perform(get("/api/v1/analysis/usage"))
				.andDo(print())
				.andExpect(status().isUnauthorized());
	}

	@Test
	@DisplayName("URL 분석 시작 성공")
	@WithMockUser(username = "testUser")
	void startUrlAnalysis_Success() throws Exception {
		// Given
		AnalysisStartRequest urlRequest = new AnalysisStartRequest();
		urlRequest.setType(AnalysisType.URL_ANALYSIS);
		urlRequest.setUrl("https://example.com/news");
		urlRequest.setTitle("URL 분석 테스트");

		Analysis urlAnalysis = Analysis.builder()
				.userId("testUser")
				.analysisType(AnalysisType.URL_ANALYSIS)
				.targetUrl("https://example.com/news")
				.title("URL 분석 테스트")
				.includeBiasAnalysis(true)
				.includeSentimentAnalysis(true)
				.includeKeywordExtraction(true)
				.includeFactCheck(false)
				.build();

		AnalysisResponse urlResponse = new AnalysisResponse(urlAnalysis);

		given(analysisService.startAnalysis(eq("testUser"), any(AnalysisStartRequest.class)))
				.willReturn(urlResponse);

		// When & Then
		mockMvc.perform(post("/api/v1/analysis/start")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(urlRequest)))
				.andDo(print())
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.statusCode").value(200))
				.andExpect(jsonPath("$.data.type").value("URL_ANALYSIS"));
	}

	@Test
	@DisplayName("뉴스 분석 시작 성공")
	@WithMockUser(username = "testUser")
	void startNewsAnalysis_Success() throws Exception {
		// Given
		AnalysisStartRequest newsRequest = new AnalysisStartRequest();
		newsRequest.setType(AnalysisType.NEWS_ANALYSIS);
		newsRequest.setNewsId("news123");
		newsRequest.setTitle("뉴스 분석 테스트");

		Analysis newsAnalysis = Analysis.builder()
				.userId("testUser")
				.analysisType(AnalysisType.NEWS_ANALYSIS)
				.targetNewsId("news123")
				.title("뉴스 분석 테스트")
				.includeBiasAnalysis(true)
				.includeSentimentAnalysis(true)
				.includeKeywordExtraction(true)
				.includeFactCheck(false)
				.build();

		AnalysisResponse newsResponse = new AnalysisResponse(newsAnalysis);

		given(analysisService.startAnalysis(eq("testUser"), any(AnalysisStartRequest.class)))
				.willReturn(newsResponse);

		// When & Then
		mockMvc.perform(post("/api/v1/analysis/start")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(newsRequest)))
				.andDo(print())
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.statusCode").value(200))
				.andExpect(jsonPath("$.data.type").value("NEWS_ANALYSIS"));
	}

	@Test
	@DisplayName("분석 시작 - 누락된 Content-Type")
	@WithMockUser(username = "testUser")
	void startAnalysis_MissingContentType() throws Exception {
		// When & Then
		mockMvc.perform(post("/api/v1/analysis/start")
						.content(objectMapper.writeValueAsString(mockStartRequest)))
				.andDo(print())
				.andExpect(status().isUnsupportedMediaType());
	}

	@Test
	@DisplayName("분석 시작 - 잘못된 JSON")
	@WithMockUser(username = "testUser")
	void startAnalysis_InvalidJson() throws Exception {
		// When & Then
		mockMvc.perform(post("/api/v1/analysis/start")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{invalid json}"))
				.andDo(print())
				.andExpect(status().isBadRequest());
	}

	@Test
	@DisplayName("분석 ID로 조회 - 잘못된 ID 형식")
	@WithMockUser(username = "testUser")
	void getAnalysisResult_InvalidIdFormat() throws Exception {
		// When & Then
		mockMvc.perform(get("/api/v1/analysis/result/invalid"))
				.andDo(print())
				.andExpect(status().isBadRequest());
	}

	@Test
	@DisplayName("내 분석 목록 조회 - 잘못된 페이지 번호")
	@WithMockUser(username = "testUser")
	void getMyAnalyses_InvalidPageNumber() throws Exception {
		// When & Then
		mockMvc.perform(get("/api/v1/analysis/my")
						.param("page", "0")
						.param("limit", "10"))
				.andDo(print())
				.andExpect(status().isOk()); // 컨트롤러에서 Math.max(page-1, 0) 처리
	}

	@Test
	@DisplayName("내 분석 목록 조회 - 음수 페이지 크기")
	@WithMockUser(username = "testUser")
	void getMyAnalyses_NegativePageSize() throws Exception {
		// Given
		given(analysisService.getMyAnalyses(eq("testUser"), any(Pageable.class)))
				.willReturn(mockMyAnalysesResponse);

		// When & Then
		mockMvc.perform(get("/api/v1/analysis/my")
						.param("page", "1")
						.param("limit", "-1"))
				.andDo(print())
				.andExpect(status().isOk()); // 컨트롤러에서 Math.min 처리
	}
}