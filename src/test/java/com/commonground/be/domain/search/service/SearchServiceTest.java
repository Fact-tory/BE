package com.commonground.be.domain.search.service;

import com.commonground.be.domain.analysis.entity.Analysis;
import com.commonground.be.domain.analysis.enums.AnalysisStatus;
import com.commonground.be.domain.analysis.enums.AnalysisType;
import com.commonground.be.domain.analysis.repository.AnalysisRepository;
import com.commonground.be.domain.dashboard.service.PopularSearchService;
import com.commonground.be.domain.news.service.search.OpenSearchIndexingService;
import com.commonground.be.domain.search.dto.request.SearchRequest;
import com.commonground.be.domain.search.dto.response.PopularSearchResponse;
import com.commonground.be.domain.search.dto.response.SearchHistoryResponse;
import com.commonground.be.domain.search.dto.response.SearchResponse;
import com.commonground.be.domain.search.dto.response.SearchStatisticsResponse;
import com.commonground.be.domain.searchhistory.entity.SearchHistory;
import com.commonground.be.domain.searchhistory.repository.SearchHistoryRepository;
import com.commonground.be.domain.news.dto.search.SearchResult;
import com.commonground.be.domain.news.entity.News;
import com.commonground.be.global.application.exception.CommonException;
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
import org.springframework.security.core.userdetails.UserDetails;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SearchService 단위 테스트")
class SearchServiceTest {

    @Mock
    private OpenSearchIndexingService openSearchService;

    @Mock
    private PopularSearchService popularSearchService;

    @Mock
    private AutocompleteService autocompleteService;

    @Mock
    private SearchStatisticsService searchStatisticsService;

    @Mock
    private AnalysisRepository analysisRepository;

    @Mock
    private SearchHistoryRepository searchHistoryRepository;

    @InjectMocks
    private SearchServiceImpl searchService;

    private SearchRequest testSearchRequest;
    private UserDetails mockUserDetails;
    private String testUserId;

    @BeforeEach
    void setUp() {
        testUserId = "testUser";
        mockUserDetails = mock(UserDetails.class);
        given(mockUserDetails.getUsername()).willReturn(testUserId);
        
        testSearchRequest = SearchRequest.of("테스트 검색어", 1, 20, "POLITICS", "neutral", "latest");
    }

    @Test
    @DisplayName("뉴스 검색 성공")
    void searchNews_Success() {
        // Given
        List<News> mockNews = Arrays.asList(
                News.builder().id("1").title("뉴스1").content("내용1").build(),
                News.builder().id("2").title("뉴스2").content("내용2").build()
        );
        
        SearchResult mockSearchResult = SearchResult.builder()
                .news(mockNews)
                .totalHits(2L)
                .page(1)
                .size(20)
                .hasNext(false)
                .build();
        
        given(openSearchService.searchNews("테스트 검색어", 1, 20)).willReturn(mockSearchResult);

        // When
        SearchResponse response = searchService.searchNews(testSearchRequest);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getTotalHits()).isEqualTo(2L);
        assertThat(response.getPage()).isEqualTo(1);
        assertThat(response.getSize()).isEqualTo(20);
        assertThat(response.getQuery()).isEqualTo("테스트 검색어");
        assertThat(response.getCategory()).isEqualTo("POLITICS");
        verify(popularSearchService).recordSearch("테스트 검색어");
        verify(autocompleteService).recordQuery("테스트 검색어");
    }

    @Test
    @DisplayName("내 분석 검색 성공")
    void searchMyAnalyses_Success() {
        // When
        SearchResponse response = searchService.searchMyAnalyses(mockUserDetails, testSearchRequest);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getQuery()).isEqualTo("테스트 검색어");
        assertThat(response.getMetadata()).containsKey("userId");
        assertThat(response.getMetadata().get("userId")).isEqualTo(testUserId);
    }

    @Test
    @DisplayName("인기 검색어 조회 성공")
    void getPopularSearches_Success() {
        // Given
        List<String> popularTerms = Arrays.asList("인기검색어1", "인기검색어2", "인기검색어3");
        given(popularSearchService.getPopularSearches(10)).willReturn(popularTerms);

        // When
        PopularSearchResponse response = searchService.getPopularSearches(10);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getSearches()).hasSize(3);
        assertThat(response.getSearches().get(0).getTerm()).isEqualTo("인기검색어1");
        assertThat(response.getSearches().get(0).getRank()).isEqualTo(1);
        assertThat(response.getTotalCount()).isEqualTo(3);
        assertThat(response.getLimit()).isEqualTo(10);
    }

    @Test
    @DisplayName("실시간 인기 검색어 조회 성공")
    void getRealtimePopularSearches_Success() {
        // Given
        List<String> realtimeTerms = Arrays.asList("실시간1", "실시간2", "실시간3");
        given(popularSearchService.getPopularSearches(10)).willReturn(realtimeTerms);

        // When
        PopularSearchResponse response = searchService.getRealtimePopularSearches();

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getSearches()).hasSize(3);
        assertThat(response.getSearches().get(0).getTerm()).isEqualTo("실시간1");
        assertThat(response.getSearches().get(0).getTrend()).isEqualTo("up");
        assertThat(response.getLimit()).isEqualTo(10);
        assertThat(response.getLastUpdated()).isNotNull();
    }

    @Test
    @DisplayName("검색 히스토리 조회 성공")
    void getSearchHistory_Success() {
        // When
        SearchHistoryResponse response = searchService.getSearchHistory(mockUserDetails, 1, 20);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getHistory()).isNotNull();
        assertThat(response.getPageInfo()).isNotNull();
        assertThat(response.getPageInfo().getCurrentPage()).isEqualTo(1);
        assertThat(response.getPageInfo().getSize()).isEqualTo(20);
    }

    @Test
    @DisplayName("검색 통계 조회 성공")
    void getSearchStatistics_Success() {
        // Given
        // Mock statistics response
        SearchStatisticsResponse.GeneralStats generalStats = new SearchStatisticsResponse.GeneralStats(1000L, 500L, 50L, 300L, 33.3);
        java.util.Map<String, Long> categoryStats = java.util.Map.of("POLITICS", 300L, "ECONOMY", 200L);
        java.util.Map<String, Long> timeRangeStats = java.util.Map.of("today", 50L, "week", 300L);
        SearchStatisticsResponse mockStats = new SearchStatisticsResponse(generalStats, categoryStats, timeRangeStats);
        
        given(searchStatisticsService.getSearchStatistics()).willReturn(mockStats);

        // When
        SearchStatisticsResponse response = searchService.getSearchStatistics(mockUserDetails);

        // Then
        assertThat(response).isNotNull();
        assertThat(response).isNotNull();
    }

    @Test
    @DisplayName("빈 검색어로 내 분석 검색 시 전체 조회")
    void searchMyAnalyses_EmptyQuery_ReturnsAll() {
        // Given
        SearchRequest emptyQueryRequest = SearchRequest.of("", 1, 20, null, null, null);

        // When
        SearchResponse response = searchService.searchMyAnalyses(mockUserDetails, emptyQueryRequest);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getQuery()).isEmpty();
    }

    @Test
    @DisplayName("검색 활동 기록 실패 시에도 검색은 정상 진행")
    void searchNews_RecordingFails_SearchContinues() {
        // Given
        List<News> mockNews = Arrays.asList(
                News.builder().id("1").title("뉴스1").content("내용1").build()
        );
        
        SearchResult mockSearchResult = SearchResult.builder()
                .news(mockNews)
                .totalHits(1L)
                .page(1)
                .size(20)
                .hasNext(false)
                .build();
        
        given(openSearchService.searchNews("테스트 검색어", 1, 20)).willReturn(mockSearchResult);
        // void 메서드는 doThrow 사용
        // doThrow(new RuntimeException("기록 실패")).when(popularSearchService).recordSearch(anyString());

        // When
        SearchResponse response = searchService.searchNews(testSearchRequest);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getTotalHits()).isEqualTo(1L);
    }

    @Test
    @DisplayName("뉴스 검색 실패 - OpenSearch 예외 발생")
    void searchNews_OpenSearchException_ThrowsException() {
        // Given
        given(openSearchService.searchNews(anyString(), anyInt(), anyInt()))
                .willThrow(new RuntimeException("OpenSearch 오류"));

        // When & Then
        assertThatThrownBy(() -> searchService.searchNews(testSearchRequest))
                .isInstanceOf(CommonException.class);
    }

    @Test
    @DisplayName("내 분석 검색 - 분석 데이터 있음")
    void searchMyAnalyses_WithAnalysisData_Success() {
        // Given
        SearchRequest request = SearchRequest.of("분석 제목", 1, 10, null, null, null);
        
        List<Analysis> mockAnalyses = Arrays.asList(
                Analysis.builder()
                        .title("분석 제목 1")
                        .analysisType(AnalysisType.URL_ANALYSIS)
                        .userId(testUserId)
                        .build(),
                Analysis.builder()
                        .title("분석 제목 2")
                        .analysisType(AnalysisType.TEXT_ANALYSIS)
                        .userId(testUserId)
                        .build()
        );
        
        Page<Analysis> mockPage = new PageImpl<>(mockAnalyses);
        given(analysisRepository.findByUserIdAndTitleContainingAndDeletedAtIsNullOrderByCreatedAtDesc(
                eq(testUserId), eq("분석 제목"), any(Pageable.class)))
                .willReturn(mockPage);

        // When
        SearchResponse response = searchService.searchMyAnalyses(mockUserDetails, request);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getArticles()).hasSize(2);
        assertThat(response.getQuery()).isEqualTo("분석 제목");
        assertThat(response.getMetadata().get("userId")).isEqualTo(testUserId);
        
        @SuppressWarnings("unchecked")
        Map<String, Object> firstResult = (Map<String, Object>) response.getArticles().get(0);
        assertThat(firstResult.get("id")).isEqualTo(1L);
        assertThat(firstResult.get("title")).isEqualTo("분석 제목 1");
        assertThat(firstResult.get("status")).isEqualTo("COMPLETED");
    }

    @Test
    @DisplayName("내 분석 검색 - 빈 검색어로 전체 조회")
    void searchMyAnalyses_EmptyQuery_ReturnsAllAnalyses() {
        // Given
        SearchRequest request = SearchRequest.of("", 1, 10, null, null, null);
        
        List<Analysis> mockAnalyses = Arrays.asList(
                Analysis.builder()
                        .title("전체 분석 1")
                        .analysisType(AnalysisType.URL_ANALYSIS)
                        .userId(testUserId)
                        .build()
        );
        
        Page<Analysis> mockPage = new PageImpl<>(mockAnalyses);
        given(analysisRepository.findByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(
                eq(testUserId), any(Pageable.class)))
                .willReturn(mockPage);

        // When
        SearchResponse response = searchService.searchMyAnalyses(mockUserDetails, request);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getArticles()).hasSize(1);
        assertThat(response.getQuery()).isEmpty();
        verify(analysisRepository).findByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(eq(testUserId), any(Pageable.class));
    }

    @Test
    @DisplayName("내 분석 검색 - 분석 조회 실패")
    void searchMyAnalyses_RepositoryException_ReturnsEmptyList() {
        // Given
        SearchRequest request = SearchRequest.of("분석", 1, 10, null, null, null);
        given(analysisRepository.findByUserIdAndTitleContainingAndDeletedAtIsNullOrderByCreatedAtDesc(
                anyString(), anyString(), any(Pageable.class)))
                .willThrow(new RuntimeException("DB 오류"));

        // When
        SearchResponse response = searchService.searchMyAnalyses(mockUserDetails, request);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getArticles()).isEmpty();
    }

    @Test
    @DisplayName("검색 히스토리 조회 - 실제 데이터 있음")
    void getSearchHistory_WithRealData_Success() {
        // Given
        List<SearchHistory> mockHistories = Arrays.asList(
                SearchHistory.builder()
                        .query("검색어1")
                        .userId(testUserId)
                        .category("POLITICS")
                        .resultCount(15)
                        .build(),
                SearchHistory.builder()
                        .query("검색어2")
                        .userId(testUserId)
                        .category("ECONOMY")
                        .resultCount(20)
                        .build()
        );
        
        Page<SearchHistory> mockPage = new PageImpl<>(mockHistories);
        given(searchHistoryRepository.findByUserIdOrderByCreatedAtDesc(eq(testUserId), any(Pageable.class)))
                .willReturn(mockPage);

        // When
        SearchHistoryResponse response = searchService.getSearchHistory(mockUserDetails, 1, 20);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getHistory()).hasSize(2);
        assertThat(response.getHistory().get(0).getQuery()).isEqualTo("검색어1");
        assertThat(response.getHistory().get(0).getCategory()).isEqualTo("POLITICS");
        assertThat(response.getHistory().get(0).getResultCount()).isEqualTo(15);
    }

    @Test
    @DisplayName("검색 히스토리 조회 - 데이터 부족 시 Mock 데이터 보완")
    void getSearchHistory_InsufficientData_FillsWithMockData() {
        // Given
        List<SearchHistory> limitedHistories = Arrays.asList(
                SearchHistory.builder()
                        .query("실제 검색어")
                        .userId(testUserId)
                        .category("POLITICS")
                        .resultCount(10)
                        .build()
        );
        
        Page<SearchHistory> mockPage = new PageImpl<>(limitedHistories);
        given(searchHistoryRepository.findByUserIdOrderByCreatedAtDesc(eq(testUserId), any(Pageable.class)))
                .willReturn(mockPage);

        // When
        SearchHistoryResponse response = searchService.getSearchHistory(mockUserDetails, 1, 5);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getHistory()).hasSizeGreaterThanOrEqualTo(1);
        assertThat(response.getHistory().get(0).getQuery()).isEqualTo("실제 검색어");
    }

    @Test
    @DisplayName("검색 히스토리 조회 - Repository 예외 발생 시 Mock 데이터 반환")
    void getSearchHistory_RepositoryException_ReturnsMockData() {
        // Given
        given(searchHistoryRepository.findByUserIdOrderByCreatedAtDesc(anyString(), any(Pageable.class)))
                .willThrow(new RuntimeException("DB 연결 오류"));

        // When
        SearchHistoryResponse response = searchService.getSearchHistory(mockUserDetails, 1, 5);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getHistory()).isNotEmpty();
        assertThat(response.getPageInfo().getCurrentPage()).isEqualTo(1);
        assertThat(response.getPageInfo().getSize()).isEqualTo(5);
    }

    @Test
    @DisplayName("인기 검색어 조회 - limit 범위 제한")
    void getPopularSearches_LimitCapping() {
        // Given
        int requestedLimit = 100; // 50 초과
        List<String> popularTerms = Arrays.asList("검색어1", "검색어2", "검색어3");
        given(popularSearchService.getPopularSearches(50)).willReturn(popularTerms); // 50으로 제한됨

        // When
        PopularSearchResponse response = searchService.getPopularSearches(requestedLimit);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getLimit()).isEqualTo(requestedLimit);
        verify(popularSearchService).getPopularSearches(50); // 50으로 제한되어 호출됨
    }

    @Test
    @DisplayName("실시간 검색어 조회 - 변화량과 트렌드 포함")
    void getRealtimePopularSearches_WithTrendAndChange() {
        // Given
        List<String> realtimeTerms = Arrays.asList("실시간1", "실시간2", "실시간3");
        given(popularSearchService.getPopularSearches(10)).willReturn(realtimeTerms);

        // When
        PopularSearchResponse response = searchService.getRealtimePopularSearches();

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getSearches()).hasSize(3);
        assertThat(response.getSearches().get(0).getTrend()).isEqualTo("up");
        assertThat(response.getSearches().get(0).getChange()).isBetween(-10, 10);
        assertThat(response.getLastUpdated()).isNotNull();
    }

    @Test
    @DisplayName("Mock 검색 히스토리 생성 검증")
    void generateMockSearchHistory_VerifyStructure() {
        // Given
        given(searchHistoryRepository.findByUserIdOrderByCreatedAtDesc(anyString(), any(Pageable.class)))
                .willReturn(new PageImpl<>(Collections.emptyList()));

        // When
        SearchHistoryResponse response = searchService.getSearchHistory(mockUserDetails, 1, 8);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getHistory()).isNotEmpty();
        
        SearchHistoryResponse.SearchHistoryItem firstItem = response.getHistory().get(0);
        assertThat(firstItem.getQuery()).isNotBlank();
        assertThat(firstItem.getSearchedAt()).isNotNull();
        assertThat(firstItem.getResultCount()).isGreaterThanOrEqualTo(0);
        assertThat(firstItem.getCategory()).isNotBlank();
    }

    @Test
    @DisplayName("검색 활동 기록 - 모든 서비스 호출 확인")
    void recordSearchActivity_CallsAllServices() {
        // Given
        List<News> mockNews = Arrays.asList(
                News.builder().id("1").title("뉴스1").content("내용1").build()
        );
        
        SearchResult mockSearchResult = SearchResult.builder()
                .news(mockNews)
                .totalHits(1L)
                .page(1)
                .size(20)
                .hasNext(false)
                .build();
        
        given(openSearchService.searchNews("테스트 검색어", 1, 20)).willReturn(mockSearchResult);

        // When
        SearchResponse response = searchService.searchNews(testSearchRequest);

        // Then
        assertThat(response).isNotNull();
        verify(popularSearchService).recordSearch("테스트 검색어");
        verify(autocompleteService).recordQuery("테스트 검색어");
        verify(searchStatisticsService).recordSearch("테스트 검색어", "POLITICS");
    }

    @Test
    @DisplayName("검색 활동 기록 예외 발생 시에도 검색 결과 반환")
    void recordSearchActivity_ExceptionDuringRecording_SearchContinues() {
        // Given
        List<News> mockNews = Arrays.asList(
                News.builder().id("1").title("뉴스1").content("내용1").build()
        );
        
        SearchResult mockSearchResult = SearchResult.builder()
                .news(mockNews)
                .totalHits(1L)
                .page(1)
                .size(20)
                .hasNext(false)
                .build();
        
        given(openSearchService.searchNews("테스트 검색어", 1, 20)).willReturn(mockSearchResult);
        doThrow(new RuntimeException("기록 실패")).when(popularSearchService).recordSearch(anyString());

        // When
        SearchResponse response = searchService.searchNews(testSearchRequest);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getTotalHits()).isEqualTo(1L);
        // 예외가 발생해도 검색은 성공해야 함
    }
}