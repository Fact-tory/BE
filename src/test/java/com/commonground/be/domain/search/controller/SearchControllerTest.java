package com.commonground.be.domain.search.controller;

import com.commonground.be.config.TestSecurityConfig;
import com.commonground.be.domain.search.dto.response.*;
import com.commonground.be.domain.search.facade.SearchFacade;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(SearchController.class)
@Import(TestSecurityConfig.class)
@ActiveProfiles("test")
@DisplayName("SearchController 통합 테스트")
class SearchControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SearchFacade searchFacade;

    @Autowired
    private ObjectMapper objectMapper;

    private SearchResponse mockSearchResponse;
    private AutocompleteResponse mockAutocompleteResponse;
    private PopularSearchResponse mockPopularSearchResponse;
    private SearchHistoryResponse mockSearchHistoryResponse;
    private SearchStatisticsResponse mockSearchStatisticsResponse;

    @BeforeEach
    void setUp() {
        // Mock SearchResponse 설정
        mockSearchResponse = SearchResponse.of(
                Arrays.asList(
                        Map.of("id", "1", "title", "뉴스1", "content", "내용1"),
                        Map.of("id", "2", "title", "뉴스2", "content", "내용2")
                ),
                2L, // totalHits
                1,  // page
                20, // size
                false, // hasNext
                "테스트 검색어", // query
                "POLITICS", // category
                "neutral", // bias
                "latest", // sort
                Map.of("searchTime", LocalDateTime.now(), "source", "opensearch")
        );

        // Mock AutocompleteResponse 설정
        List<AutocompleteResponse.SuggestionItem> suggestions = Arrays.asList(
                new AutocompleteResponse.SuggestionItem("테스트", "keyword", 100, 0.9),
                new AutocompleteResponse.SuggestionItem("테스트 검색", "keyword", 80, 0.8),
                new AutocompleteResponse.SuggestionItem("테스트 뉴스", "keyword", 60, 0.7)
        );
        mockAutocompleteResponse = new AutocompleteResponse("테스트", suggestions);

        // Mock PopularSearchResponse 설정
        List<PopularSearchResponse.SearchRankItem> searchItems = Arrays.asList(
                PopularSearchResponse.SearchRankItem.builder()
                        .rank(1)
                        .term("인기검색어1")
                        .count(1000L)
                        .trend("up")
                        .build(),
                PopularSearchResponse.SearchRankItem.builder()
                        .rank(2)
                        .term("인기검색어2")
                        .count(900L)
                        .trend("same")
                        .build()
        );
        mockPopularSearchResponse = PopularSearchResponse.of(searchItems, 10);

        // Mock SearchHistoryResponse 설정
        List<SearchHistoryResponse.SearchHistoryItem> historyItems = Arrays.asList(
                new SearchHistoryResponse.SearchHistoryItem("검색어1", LocalDateTime.now(), 15, "POLITICS", ""),
                new SearchHistoryResponse.SearchHistoryItem("검색어2", LocalDateTime.now().minusHours(1), 20, "ECONOMY", "")
        );
        SearchHistoryResponse.PageInfo pageInfo = new SearchHistoryResponse.PageInfo(1, 1, 2, 20, false, false);
        mockSearchHistoryResponse = new SearchHistoryResponse(historyItems, pageInfo);

        // Mock SearchStatisticsResponse 설정
        SearchStatisticsResponse.GeneralStats generalStats = 
                new SearchStatisticsResponse.GeneralStats(1000L, 500L, 50L, 300L, 15.5);
        Map<String, Long> categoryStats = Map.of("POLITICS", 400L, "ECONOMY", 300L, "SOCIETY", 200L);
        Map<String, Long> timeRangeStats = Map.of("09:00", 50L, "10:00", 60L, "11:00", 45L);
        mockSearchStatisticsResponse = new SearchStatisticsResponse(generalStats, categoryStats, timeRangeStats);
    }

    @Test
    @DisplayName("뉴스 검색 성공")
    void searchNews_Success() throws Exception {
        // Given
        SearchFacade.SearchFlowResult successResult = SearchFacade.SearchFlowResult.success("뉴스 검색 완료", mockSearchResponse);
        given(searchFacade.searchNewsFlow(anyString(), anyInt(), anyInt(), anyString(), anyString(), anyString()))
                .willReturn(successResult);

        // When & Then
        mockMvc.perform(get("/api/v1/search/news")
                        .param("q", "테스트 검색어")
                        .param("page", "1")
                        .param("size", "20")
                        .param("category", "POLITICS")
                        .param("bias", "neutral")
                        .param("sort", "latest"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusCode").value(200))
                .andExpect(jsonPath("$.data.totalHits").value(2))
                .andExpect(jsonPath("$.data.query").value("테스트 검색어"))
                .andExpect(jsonPath("$.data.category").value("POLITICS"))
                .andExpect(jsonPath("$.data.articles").isArray())
                .andExpect(jsonPath("$.data.articles[0].title").value("뉴스1"));
    }

    @Test
    @DisplayName("뉴스 검색 - 기본 파라미터")
    void searchNews_DefaultParameters() throws Exception {
        // Given
        SearchFacade.SearchFlowResult successResult = SearchFacade.SearchFlowResult.success("뉴스 검색 완료", mockSearchResponse);
        given(searchFacade.searchNewsFlow(eq("테스트"), eq(1), eq(20), isNull(), isNull(), isNull()))
                .willReturn(successResult);

        // When & Then
        mockMvc.perform(get("/api/v1/search/news")
                        .param("q", "테스트"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusCode").value(200));
    }

    @Test
    @DisplayName("뉴스 검색 실패")
    void searchNews_Failure() throws Exception {
        // Given
        SearchFacade.SearchFlowResult failureResult = SearchFacade.SearchFlowResult.failure("뉴스 검색 실패", new RuntimeException("검색 오류"));
        given(searchFacade.searchNewsFlow(anyString(), anyInt(), anyInt(), anyString(), anyString(), anyString()))
                .willReturn(failureResult);

        // When & Then
        mockMvc.perform(get("/api/v1/search/news")
                        .param("q", "테스트"))
                .andDo(print())
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("뉴스 검색 예외 발생")
    void searchNews_Exception() throws Exception {
        // Given
        given(searchFacade.searchNewsFlow(anyString(), anyInt(), anyInt(), anyString(), anyString(), anyString()))
                .willThrow(new RuntimeException("서비스 오류"));

        // When & Then
        mockMvc.perform(get("/api/v1/search/news")
                        .param("q", "테스트"))
                .andDo(print())
                .andExpect(status().isInternalServerError());
    }

    @Test
    @DisplayName("내 분석 검색 성공")
    @WithMockUser
    void searchMyAnalyses_Success() throws Exception {
        // Given
        SearchFacade.SearchFlowResult successResult = SearchFacade.SearchFlowResult.success("내 분석 검색 완료", mockSearchResponse);
        given(searchFacade.searchMyAnalysesFlow(any(), anyString(), anyInt(), anyInt()))
                .willReturn(successResult);

        // When & Then
        mockMvc.perform(get("/api/v1/search/my-analyses")
                        .param("q", "테스트 분석")
                        .param("page", "1")
                        .param("size", "20"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusCode").value(200));
    }

    @Test
    @DisplayName("내 분석 검색 - 인증되지 않은 사용자")
    void searchMyAnalyses_Unauthorized() throws Exception {
        // When & Then
        mockMvc.perform(get("/api/v1/search/my-analyses")
                        .param("q", "테스트"))
                .andDo(print())
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("내 분석 검색 실패")
    @WithMockUser
    void searchMyAnalyses_Failure() throws Exception {
        // Given
        SearchFacade.SearchFlowResult failureResult = SearchFacade.SearchFlowResult.failure("내 분석 검색 실패", new RuntimeException("검색 오류"));
        given(searchFacade.searchMyAnalysesFlow(any(), anyString(), anyInt(), anyInt()))
                .willReturn(failureResult);

        // When & Then
        mockMvc.perform(get("/api/v1/search/my-analyses")
                        .param("q", "테스트"))
                .andDo(print())
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("인기 검색어 조회 성공")
    void getPopularSearches_Success() throws Exception {
        // Given
        SearchFacade.SearchFlowResult successResult = SearchFacade.SearchFlowResult.success("인기 검색어 조회 완료", mockPopularSearchResponse);
        given(searchFacade.getPopularSearchesFlow(anyInt()))
                .willReturn(successResult);

        // When & Then
        mockMvc.perform(get("/api/v1/search/popular")
                        .param("limit", "10"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusCode").value(200))
                .andExpect(jsonPath("$.data.searches").isArray())
                .andExpect(jsonPath("$.data.searches[0].rank").value(1))
                .andExpect(jsonPath("$.data.searches[0].term").value("인기검색어1"));
    }

    @Test
    @DisplayName("인기 검색어 조회 - 기본 파라미터")
    void getPopularSearches_DefaultLimit() throws Exception {
        // Given
        SearchFacade.SearchFlowResult successResult = SearchFacade.SearchFlowResult.success("인기 검색어 조회 완료", mockPopularSearchResponse);
        given(searchFacade.getPopularSearchesFlow(eq(10)))
                .willReturn(successResult);

        // When & Then
        mockMvc.perform(get("/api/v1/search/popular"))
                .andDo(print())
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("실시간 검색어 조회 성공")
    void getRealtimePopularSearches_Success() throws Exception {
        // Given
        SearchFacade.SearchFlowResult successResult = SearchFacade.SearchFlowResult.success("실시간 검색어 조회 완료", mockPopularSearchResponse);
        given(searchFacade.getRealtimePopularSearchesFlow())
                .willReturn(successResult);

        // When & Then
        mockMvc.perform(get("/api/v1/search/realtime-popular"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusCode").value(200));
    }

    @Test
    @DisplayName("실시간 검색어 조회 실패")
    void getRealtimePopularSearches_Failure() throws Exception {
        // Given
        SearchFacade.SearchFlowResult failureResult = SearchFacade.SearchFlowResult.failure("실시간 검색어 조회 실패", new RuntimeException("서비스 오류"));
        given(searchFacade.getRealtimePopularSearchesFlow())
                .willReturn(failureResult);

        // When & Then
        mockMvc.perform(get("/api/v1/search/realtime-popular"))
                .andDo(print())
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    @DisplayName("자동완성 조회 성공")
    void getAutocomplete_Success() throws Exception {
        // Given
        SearchFacade.SearchFlowResult successResult = SearchFacade.SearchFlowResult.success("자동완성 조회 완료", mockAutocompleteResponse);
        given(searchFacade.getAutocompleteFlow(anyString(), anyInt()))
                .willReturn(successResult);

        // When & Then
        mockMvc.perform(get("/api/v1/search/autocomplete")
                        .param("q", "테스트")
                        .param("limit", "10"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusCode").value(200))
                .andExpect(jsonPath("$.data.query").value("테스트"))
                .andExpect(jsonPath("$.data.suggestions").isArray())
                .andExpect(jsonPath("$.data.suggestions[0].text").value("테스트"));
    }

    @Test
    @DisplayName("자동완성 조회 - 빈 검색어")
    void getAutocomplete_EmptyQuery() throws Exception {
        // When & Then
        mockMvc.perform(get("/api/v1/search/autocomplete")
                        .param("q", ""))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.error").value("검색어를 입력해주세요."));
    }

    @Test
    @DisplayName("자동완성 조회 - null 검색어")
    void getAutocomplete_NullQuery() throws Exception {
        // When & Then
        mockMvc.perform(get("/api/v1/search/autocomplete"))
                .andDo(print())
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("자동완성 조회 - 기본 파라미터")
    void getAutocomplete_DefaultLimit() throws Exception {
        // Given
        SearchFacade.SearchFlowResult successResult = SearchFacade.SearchFlowResult.success("자동완성 조회 완료", mockAutocompleteResponse);
        given(searchFacade.getAutocompleteFlow(eq("테스트"), eq(10)))
                .willReturn(successResult);

        // When & Then
        mockMvc.perform(get("/api/v1/search/autocomplete")
                        .param("q", "테스트"))
                .andDo(print())
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("검색 통계 조회 성공")
    @WithMockUser
    void getSearchStatistics_Success() throws Exception {
        // Given
        SearchFacade.SearchFlowResult successResult = SearchFacade.SearchFlowResult.success("검색 통계 조회 완료", mockSearchStatisticsResponse);
        given(searchFacade.getSearchStatisticsFlow(any()))
                .willReturn(successResult);

        // When & Then
        mockMvc.perform(get("/api/v1/search/statistics"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusCode").value(200))
                .andExpect(jsonPath("$.data.general.totalSearches").value(1000))
                .andExpect(jsonPath("$.data.categoryStats.POLITICS").value(400));
    }

    @Test
    @DisplayName("검색 통계 조회 - 인증되지 않은 사용자")
    void getSearchStatistics_Unauthorized() throws Exception {
        // When & Then
        mockMvc.perform(get("/api/v1/search/statistics"))
                .andDo(print())
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("검색 히스토리 조회 성공")
    @WithMockUser
    void getSearchHistory_Success() throws Exception {
        // Given
        SearchFacade.SearchFlowResult successResult = SearchFacade.SearchFlowResult.success("검색 히스토리 조회 완료", mockSearchHistoryResponse);
        given(searchFacade.getSearchHistoryFlow(any(), anyInt(), anyInt()))
                .willReturn(successResult);

        // When & Then
        mockMvc.perform(get("/api/v1/search/history")
                        .param("page", "1")
                        .param("size", "20"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusCode").value(200))
                .andExpect(jsonPath("$.data.history").isArray())
                .andExpect(jsonPath("$.data.pageInfo.currentPage").value(1));
    }

    @Test
    @DisplayName("검색 히스토리 조회 - 기본 파라미터")
    @WithMockUser
    void getSearchHistory_DefaultParameters() throws Exception {
        // Given
        SearchFacade.SearchFlowResult successResult = SearchFacade.SearchFlowResult.success("검색 히스토리 조회 완료", mockSearchHistoryResponse);
        given(searchFacade.getSearchHistoryFlow(any(), eq(1), eq(20)))
                .willReturn(successResult);

        // When & Then
        mockMvc.perform(get("/api/v1/search/history"))
                .andDo(print())
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("검색 히스토리 조회 - 인증되지 않은 사용자")
    void getSearchHistory_Unauthorized() throws Exception {
        // When & Then
        mockMvc.perform(get("/api/v1/search/history"))
                .andDo(print())
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("검색 히스토리 조회 실패")
    @WithMockUser
    void getSearchHistory_Failure() throws Exception {
        // Given
        SearchFacade.SearchFlowResult failureResult = SearchFacade.SearchFlowResult.failure("검색 히스토리 조회 실패", new RuntimeException("오류"));
        given(searchFacade.getSearchHistoryFlow(any(), anyInt(), anyInt()))
                .willReturn(failureResult);

        // When & Then
        mockMvc.perform(get("/api/v1/search/history"))
                .andDo(print())
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("검색 통계 조회 예외 발생")
    @WithMockUser
    void getSearchStatistics_Exception() throws Exception {
        // Given
        given(searchFacade.getSearchStatisticsFlow(any()))
                .willThrow(new RuntimeException("서비스 오류"));

        // When & Then
        mockMvc.perform(get("/api/v1/search/statistics"))
                .andDo(print())
                .andExpect(status().isInternalServerError());
    }
}