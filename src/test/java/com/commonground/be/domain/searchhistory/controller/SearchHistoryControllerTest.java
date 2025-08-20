package com.commonground.be.domain.searchhistory.controller;

import com.commonground.be.domain.searchhistory.dto.response.SearchHistoryResponse;
import com.commonground.be.domain.searchhistory.facade.SearchHistoryFacade;
import com.commonground.be.config.TestSecurityConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Pageable;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(SearchHistoryController.class)
@Import(TestSecurityConfig.class)
@ActiveProfiles("test")
@DisplayName("SearchHistoryController 통합 테스트")
class SearchHistoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SearchHistoryFacade searchHistoryFacade;

    @Autowired
    private ObjectMapper objectMapper;

    private SearchHistoryResponse mockSearchHistoryResponse;

    @BeforeEach
    void setUp() {
        // Mock 검색 히스토리 응답 설정
        SearchHistoryResponse.SearchHistoryItem historyItem = SearchHistoryResponse.SearchHistoryItem.builder()
                .id(1L)
                .query("테스트 검색어")
                .searchedAt(LocalDateTime.now())
                .resultCount(10)
                .category("POLITICS")
                .filters("")
                .build();

        SearchHistoryResponse.PageInfo pageInfo = SearchHistoryResponse.PageInfo.builder()
                .currentPage(1)
                .totalPages(1)
                .totalElements(1)
                .size(20)
                .hasNext(false)
                .hasPrevious(false)
                .build();

        SearchHistoryResponse.SearchStats stats = SearchHistoryResponse.SearchStats.builder()
                .totalSearches(100L)
                .todaySearches(5L)
                .thisWeekSearches(20L)
                .thisMonthSearches(50L)
                .topQueries(Arrays.asList("인기검색어1", "인기검색어2"))
                .topCategories(Arrays.asList("POLITICS", "ECONOMY"))
                .build();

        mockSearchHistoryResponse = SearchHistoryResponse.of(
                Arrays.asList(historyItem), pageInfo, stats);
    }

    @Test
    @DisplayName("검색 히스토리 조회 성공")
    @WithMockUser
    void getSearchHistory_Success() throws Exception {
        // Given
        SearchHistoryFacade.SearchHistoryFlowResult successResult = 
                SearchHistoryFacade.SearchHistoryFlowResult.success("조회 완료", mockSearchHistoryResponse);
        given(searchHistoryFacade.getSearchHistoryFlow(any(), any(Pageable.class)))
                .willReturn(successResult);

        // When & Then
        mockMvc.perform(get("/api/v1/search-history")
                        .param("page", "1")
                        .param("size", "20"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusCode").value(200))
                .andExpect(jsonPath("$.data.history").isArray())
                .andExpect(jsonPath("$.data.history[0].query").value("테스트 검색어"))
                .andExpect(jsonPath("$.data.pageInfo.currentPage").value(1))
                .andExpect(jsonPath("$.data.stats.totalSearches").value(100));
    }

    @Test
    @DisplayName("최근 검색어 조회 성공")
    @WithMockUser
    void getRecentQueries_Success() throws Exception {
        // Given
        List<String> recentQueries = Arrays.asList("검색어1", "검색어2", "검색어3");
        SearchHistoryFacade.SearchHistoryFlowResult successResult = 
                SearchHistoryFacade.SearchHistoryFlowResult.success("조회 완료", recentQueries);
        given(searchHistoryFacade.getRecentQueriesFlow(any(), anyInt()))
                .willReturn(successResult);

        // When & Then
        mockMvc.perform(get("/api/v1/search-history/recent-queries")
                        .param("limit", "10"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusCode").value(200))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0]").value("검색어1"));
    }

    @Test
    @DisplayName("검색 히스토리 전체 삭제 성공")
    @WithMockUser
    void clearSearchHistory_Success() throws Exception {
        // Given
        SearchHistoryFacade.SearchHistoryFlowResult successResult = 
                SearchHistoryFacade.SearchHistoryFlowResult.successWithMessage("검색 히스토리가 삭제되었습니다.");
        given(searchHistoryFacade.clearSearchHistoryFlow(any()))
                .willReturn(successResult);

        // When & Then
        mockMvc.perform(delete("/api/v1/search-history"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusCode").value(200))
                .andExpect(jsonPath("$.data.message").value("검색 히스토리가 삭제되었습니다."));
    }

    @Test
    @DisplayName("특정 검색 기록 삭제 성공")
    @WithMockUser
    void deleteSearchHistory_Success() throws Exception {
        // Given
        Long historyId = 1L;
        SearchHistoryFacade.SearchHistoryFlowResult successResult = 
                SearchHistoryFacade.SearchHistoryFlowResult.successWithMessage("검색 기록이 삭제되었습니다.");
        given(searchHistoryFacade.deleteSearchHistoryFlow(any(), eq(historyId)))
                .willReturn(successResult);

        // When & Then
        mockMvc.perform(delete("/api/v1/search-history/{historyId}", historyId))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusCode").value(200))
                .andExpect(jsonPath("$.data.message").value("검색 기록이 삭제되었습니다."));
    }

    @Test
    @DisplayName("인증되지 않은 사용자 요청 시 401 반환")
    void getSearchHistory_Unauthorized() throws Exception {
        // When & Then
        mockMvc.perform(get("/api/v1/search-history"))
                .andDo(print())
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("잘못된 페이지 파라미터로 요청 시 400 반환")
    @WithMockUser
    void getSearchHistory_BadRequest() throws Exception {
        // Given
        SearchHistoryFacade.SearchHistoryFlowResult failureResult = 
                SearchHistoryFacade.SearchHistoryFlowResult.failure("잘못된 요청", new IllegalArgumentException());
        given(searchHistoryFacade.getSearchHistoryFlow(any(), any(Pageable.class)))
                .willReturn(failureResult);

        // When & Then
        mockMvc.perform(get("/api/v1/search-history")
                        .param("page", "-1")
                        .param("size", "20"))
                .andDo(print())
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("존재하지 않는 검색 기록 삭제 시 404 반환")
    @WithMockUser
    void deleteSearchHistory_NotFound() throws Exception {
        // Given
        Long nonExistentHistoryId = 999L;
        SearchHistoryFacade.SearchHistoryFlowResult failureResult = 
                SearchHistoryFacade.SearchHistoryFlowResult.failure("검색 기록을 찾을 수 없습니다", new RuntimeException());
        given(searchHistoryFacade.deleteSearchHistoryFlow(any(), eq(nonExistentHistoryId)))
                .willReturn(failureResult);

        // When & Then
        mockMvc.perform(delete("/api/v1/search-history/{historyId}", nonExistentHistoryId))
                .andDo(print())
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("서버 오류 발생 시 500 반환")
    @WithMockUser
    void getSearchHistory_InternalServerError() throws Exception {
        // Given
        given(searchHistoryFacade.getSearchHistoryFlow(any(), any(Pageable.class)))
                .willThrow(new RuntimeException("서버 내부 오류"));

        // When & Then
        mockMvc.perform(get("/api/v1/search-history"))
                .andDo(print())
                .andExpect(status().isInternalServerError());
    }
}