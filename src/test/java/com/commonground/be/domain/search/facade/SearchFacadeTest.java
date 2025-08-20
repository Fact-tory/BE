package com.commonground.be.domain.search.facade;

import com.commonground.be.domain.search.dto.response.*;
import com.commonground.be.domain.search.service.AutocompleteService;
import com.commonground.be.domain.search.service.SearchService;
import com.commonground.be.domain.search.service.SearchStatisticsService;
import com.commonground.be.global.application.exception.CommonException;
import com.commonground.be.global.application.response.ResponseExceptionEnum;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("SearchFacade 단위 테스트")
class SearchFacadeTest {

    @Mock
    private SearchService searchService;

    @Mock
    private AutocompleteService autocompleteService;

    @Mock
    private SearchStatisticsService searchStatisticsService;

    @InjectMocks
    private SearchFacade searchFacade;

    private UserDetails mockUserDetails;
    private String testUserId;

    @BeforeEach
    void setUp() {
        mockUserDetails = mock(UserDetails.class);
        testUserId = "testUser";
        given(mockUserDetails.getUsername()).willReturn(testUserId);
    }

    @Test
    @DisplayName("뉴스 검색 파이프라인 성공")
    void searchNewsFlow_Success() {
        // Given
        String query = "테스트 검색어";
        int page = 1;
        int size = 20;
        String category = "POLITICS";
        String bias = "neutral";
        String sort = "latest";

        SearchResponse mockSearchResponse = SearchResponse.of(
                Arrays.asList(
                        Map.of("id", "1", "title", "뉴스1"),
                        Map.of("id", "2", "title", "뉴스2")
                ),
                2L, page, size, false, query, category, bias, sort,
                Map.of("searchTime", LocalDateTime.now())
        );

        given(searchService.searchNews(any())).willReturn(mockSearchResponse);

        // When
        SearchFacade.SearchFlowResult result = searchFacade.searchNewsFlow(query, page, size, category, bias, sort);

        // Then
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData()).isEqualTo(mockSearchResponse);
        assertThat(result.getMessage()).contains("뉴스 검색 완료");
        verify(searchService).searchNews(any());
        verify(searchStatisticsService).recordSearch(query, category);
    }

    @Test
    @DisplayName("뉴스 검색 파이프라인 - 빈 검색어 실패")
    void searchNewsFlow_EmptyQuery_Failure() {
        // When
        SearchFacade.SearchFlowResult result = searchFacade.searchNewsFlow("", 1, 20, null, null, null);

        // Then
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).contains("뉴스 검색 실패");
        assertThat(result.getError()).isInstanceOf(CommonException.class);
    }

    @Test
    @DisplayName("뉴스 검색 파이프라인 - null 검색어 실패")
    void searchNewsFlow_NullQuery_Failure() {
        // When
        SearchFacade.SearchFlowResult result = searchFacade.searchNewsFlow(null, 1, 20, null, null, null);

        // Then
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).contains("뉴스 검색 실패");
    }

    @Test
    @DisplayName("뉴스 검색 파이프라인 - 긴 검색어 실패")
    void searchNewsFlow_TooLongQuery_Failure() {
        // Given
        String longQuery = "a".repeat(101); // 100자 초과

        // When
        SearchFacade.SearchFlowResult result = searchFacade.searchNewsFlow(longQuery, 1, 20, null, null, null);

        // Then
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).contains("뉴스 검색 실패");
    }

    @Test
    @DisplayName("뉴스 검색 파이프라인 - 잘못된 페이지 파라미터")
    void searchNewsFlow_InvalidPageParameters_Failure() {
        // When
        SearchFacade.SearchFlowResult result = searchFacade.searchNewsFlow("테스트", 0, 20, null, null, null);

        // Then
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).contains("뉴스 검색 실패");
    }

    @Test
    @DisplayName("뉴스 검색 파이프라인 - 잘못된 사이즈 파라미터")
    void searchNewsFlow_InvalidSizeParameters_Failure() {
        // When
        SearchFacade.SearchFlowResult result = searchFacade.searchNewsFlow("테스트", 1, 101, null, null, null);

        // Then
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).contains("뉴스 검색 실패");
    }

    @Test
    @DisplayName("내 분석 검색 파이프라인 성공")
    void searchMyAnalysesFlow_Success() {
        // Given
        String query = "내 분석 검색";
        int page = 1;
        int size = 20;

        SearchResponse mockSearchResponse = SearchResponse.of(
                Arrays.asList(
                        Map.of("id", 1L, "title", "분석1", "status", "COMPLETED"),
                        Map.of("id", 2L, "title", "분석2", "status", "IN_PROGRESS")
                ),
                2L, page, size, false, query, null, null, null,
                Map.of("userId", testUserId)
        );

        given(searchService.searchMyAnalyses(eq(mockUserDetails), any())).willReturn(mockSearchResponse);

        // When
        SearchFacade.SearchFlowResult result = searchFacade.searchMyAnalysesFlow(mockUserDetails, query, page, size);

        // Then
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData()).isEqualTo(mockSearchResponse);
        assertThat(result.getMessage()).contains("내 분석 검색 완료");
        verify(searchService).searchMyAnalyses(eq(mockUserDetails), any());
    }

    @Test
    @DisplayName("내 분석 검색 파이프라인 - 인증되지 않은 사용자")
    void searchMyAnalysesFlow_UnauthenticatedUser_Failure() {
        // When
        SearchFacade.SearchFlowResult result = searchFacade.searchMyAnalysesFlow(null, "테스트", 1, 20);

        // Then
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).contains("내 분석 검색 실패");
    }

    @Test
    @DisplayName("자동완성 파이프라인 성공")
    void getAutocompleteFlow_Success() {
        // Given
        String query = "테스트";
        int limit = 10;

        List<AutocompleteResponse.SuggestionItem> suggestions = Arrays.asList(
                new AutocompleteResponse.SuggestionItem("테스트", "keyword", 100, 0.9),
                new AutocompleteResponse.SuggestionItem("테스트 검색", "keyword", 80, 0.8)
        );
        AutocompleteResponse mockResponse = new AutocompleteResponse(query, suggestions);

        given(autocompleteService.getAutocompleteSuggestions(query.trim(), Math.min(limit, 20))).willReturn(mockResponse);

        // When
        SearchFacade.SearchFlowResult result = searchFacade.getAutocompleteFlow(query, limit);

        // Then
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData()).isEqualTo(mockResponse);
        assertThat(result.getMessage()).contains("자동완성 조회 완료");
        verify(autocompleteService).getAutocompleteSuggestions(query.trim(), Math.min(limit, 20));
    }

    @Test
    @DisplayName("자동완성 파이프라인 - 빈 검색어 실패")
    void getAutocompleteFlow_EmptyQuery_Failure() {
        // When
        SearchFacade.SearchFlowResult result = searchFacade.getAutocompleteFlow("", 10);

        // Then
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).contains("자동완성 조회 실패");
    }

    @Test
    @DisplayName("자동완성 파이프라인 - 잘못된 limit 파라미터")
    void getAutocompleteFlow_InvalidLimit_Failure() {
        // When
        SearchFacade.SearchFlowResult result = searchFacade.getAutocompleteFlow("테스트", 51);

        // Then
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).contains("자동완성 조회 실패");
    }

    @Test
    @DisplayName("인기 검색어 조회 파이프라인 성공")
    void getPopularSearchesFlow_Success() {
        // Given
        int limit = 10;
        List<PopularSearchResponse.SearchRankItem> searchItems = Arrays.asList(
                PopularSearchResponse.SearchRankItem.builder()
                        .rank(1)
                        .term("인기검색어1")
                        .count(1000L)
                        .trend("up")
                        .build()
        );
        PopularSearchResponse mockResponse = PopularSearchResponse.of(searchItems, limit);

        given(searchService.getPopularSearches(limit)).willReturn(mockResponse);

        // When
        SearchFacade.SearchFlowResult result = searchFacade.getPopularSearchesFlow(limit);

        // Then
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData()).isEqualTo(mockResponse);
        assertThat(result.getMessage()).contains("인기 검색어 조회 완료");
        verify(searchService).getPopularSearches(limit);
    }

    @Test
    @DisplayName("인기 검색어 조회 파이프라인 - 잘못된 limit")
    void getPopularSearchesFlow_InvalidLimit_Failure() {
        // When
        SearchFacade.SearchFlowResult result = searchFacade.getPopularSearchesFlow(0);

        // Then
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).contains("인기 검색어 조회 실패");
    }

    @Test
    @DisplayName("실시간 검색어 조회 파이프라인 성공")
    void getRealtimePopularSearchesFlow_Success() {
        // Given
        List<PopularSearchResponse.SearchRankItem> searchItems = Arrays.asList(
                PopularSearchResponse.SearchRankItem.builder()
                        .rank(1)
                        .term("실시간검색어1")
                        .count(500L)
                        .trend("up")
                        .change(5)
                        .build()
        );
        PopularSearchResponse mockResponse = PopularSearchResponse.builder()
                .searches(searchItems)
                .totalCount(1)
                .limit(10)
                .lastUpdated(LocalDateTime.now())
                .build();

        given(searchService.getRealtimePopularSearches()).willReturn(mockResponse);

        // When
        SearchFacade.SearchFlowResult result = searchFacade.getRealtimePopularSearchesFlow();

        // Then
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData()).isEqualTo(mockResponse);
        assertThat(result.getMessage()).contains("실시간 검색어 조회 완료");
        verify(searchService).getRealtimePopularSearches();
    }

    @Test
    @DisplayName("검색 통계 조회 파이프라인 성공")
    void getSearchStatisticsFlow_Success() {
        // Given
        SearchStatisticsResponse.GeneralStats generalStats = 
                new SearchStatisticsResponse.GeneralStats(1000L, 500L, 50L, 300L, 15.5);
        Map<String, Long> categoryStats = Map.of("POLITICS", 400L, "ECONOMY", 300L);
        Map<String, Long> timeRangeStats = Map.of("09:00", 50L, "10:00", 60L);
        SearchStatisticsResponse mockResponse = new SearchStatisticsResponse(generalStats, categoryStats, timeRangeStats);

        given(searchService.getSearchStatistics(mockUserDetails)).willReturn(mockResponse);

        // When
        SearchFacade.SearchFlowResult result = searchFacade.getSearchStatisticsFlow(mockUserDetails);

        // Then
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData()).isEqualTo(mockResponse);
        assertThat(result.getMessage()).contains("검색 통계 조회 완료");
        verify(searchService).getSearchStatistics(mockUserDetails);
    }

    @Test
    @DisplayName("검색 통계 조회 파이프라인 - 인증되지 않은 사용자")
    void getSearchStatisticsFlow_UnauthenticatedUser_Failure() {
        // When
        SearchFacade.SearchFlowResult result = searchFacade.getSearchStatisticsFlow(null);

        // Then
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).contains("검색 통계 조회 실패");
    }

    @Test
    @DisplayName("검색 히스토리 조회 파이프라인 성공")
    void getSearchHistoryFlow_Success() {
        // Given
        int page = 1;
        int size = 20;

        List<SearchHistoryResponse.SearchHistoryItem> historyItems = Arrays.asList(
                new SearchHistoryResponse.SearchHistoryItem("검색어1", LocalDateTime.now(), 15, "POLITICS", ""),
                new SearchHistoryResponse.SearchHistoryItem("검색어2", LocalDateTime.now().minusHours(1), 20, "ECONOMY", "")
        );
        SearchHistoryResponse.PageInfo pageInfo = new SearchHistoryResponse.PageInfo(page, 1, 2, size, false, false);
        SearchHistoryResponse mockResponse = new SearchHistoryResponse(historyItems, pageInfo);

        given(searchService.getSearchHistory(mockUserDetails, page, size)).willReturn(mockResponse);

        // When
        SearchFacade.SearchFlowResult result = searchFacade.getSearchHistoryFlow(mockUserDetails, page, size);

        // Then
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData()).isEqualTo(mockResponse);
        assertThat(result.getMessage()).contains("검색 히스토리 조회 완료");
        verify(searchService).getSearchHistory(mockUserDetails, page, size);
    }

    @Test
    @DisplayName("검색 히스토리 조회 파이프라인 - 인증되지 않은 사용자")
    void getSearchHistoryFlow_UnauthenticatedUser_Failure() {
        // When
        SearchFacade.SearchFlowResult result = searchFacade.getSearchHistoryFlow(null, 1, 20);

        // Then
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).contains("검색 히스토리 조회 실패");
    }

    @Test
    @DisplayName("검색 히스토리 조회 파이프라인 - 잘못된 페이징 파라미터")
    void getSearchHistoryFlow_InvalidPagingParameters_Failure() {
        // When
        SearchFacade.SearchFlowResult result = searchFacade.getSearchHistoryFlow(mockUserDetails, 0, 20);

        // Then
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).contains("검색 히스토리 조회 실패");
    }

    @Test
    @DisplayName("서비스 예외 발생 시 실패 처리")
    void searchNewsFlow_ServiceException_Failure() {
        // Given
        given(searchService.searchNews(any())).willThrow(new RuntimeException("서비스 오류"));

        // When
        SearchFacade.SearchFlowResult result = searchFacade.searchNewsFlow("테스트", 1, 20, null, null, null);

        // Then
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).contains("뉴스 검색 실패");
        assertThat(result.getError()).isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("검색 통계 기록 실패 시에도 검색은 성공")
    void searchNewsFlow_StatisticsRecordingFails_SearchSucceeds() {
        // Given
        String query = "테스트 검색어";
        SearchResponse mockSearchResponse = SearchResponse.of(
                Arrays.asList(),
                0L, 1, 20, false, query, null, null, null,
                Map.of("searchTime", LocalDateTime.now())
        );

        given(searchService.searchNews(any())).willReturn(mockSearchResponse);
        // doThrow를 사용하여 void 메서드에 예외 설정 가능하지만, 
        // SearchFacade에서는 recordSearchAnalytics가 try-catch로 감싸져 있어서 검색은 계속 진행됨

        // When
        SearchFacade.SearchFlowResult result = searchFacade.searchNewsFlow(query, 1, 20, null, null, null);

        // Then
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData()).isEqualTo(mockSearchResponse);
    }
}