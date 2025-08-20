package com.commonground.be.domain.searchhistory.service;

import com.commonground.be.domain.searchhistory.dto.response.SearchHistoryResponse;
import com.commonground.be.domain.searchhistory.entity.SearchHistory;
import com.commonground.be.domain.searchhistory.repository.SearchHistoryRepositoryInterface;
import com.commonground.be.global.application.exception.SearchHistoryExceptions;
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

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("SearchHistoryService 단위 테스트")
class SearchHistoryServiceTest {

    @Mock
    private SearchHistoryRepositoryInterface searchHistoryAdapter;

    @InjectMocks
    private SearchHistoryServiceImpl searchHistoryService;

    private String testUserId;
    private SearchHistory testSearchHistory;
    private Pageable testPageable;

    @BeforeEach
    void setUp() {
        testUserId = "testUser";
        testSearchHistory = SearchHistory.builder()
                .id(1L)
                .userId(testUserId)
                .query("테스트 검색어")
                .resultCount(10)
                .category("POLITICS")
                .filters("")
                .build();
        testPageable = PageRequest.of(0, 10);
    }

    @Test
    @DisplayName("검색 기록 저장 성공")
    void recordSearch_Success() {
        // Given
        String query = "테스트 검색어";
        Integer resultCount = 10;
        String category = "POLITICS";
        String filters = "";

        // When
        searchHistoryService.recordSearch(testUserId, query, resultCount, category, filters);

        // Then
        verify(searchHistoryAdapter).save(any(SearchHistory.class));
    }

    @Test
    @DisplayName("검색 히스토리 조회 성공")
    void getSearchHistory_Success() {
        // Given
        List<SearchHistory> histories = Arrays.asList(testSearchHistory);
        Page<SearchHistory> historyPage = new PageImpl<>(histories, testPageable, 1);
        
        given(searchHistoryAdapter.findByUserIdOrderByCreatedAtDesc(testUserId, testPageable))
                .willReturn(historyPage);
        given(searchHistoryAdapter.countByUserId(testUserId)).willReturn(1L);
        given(searchHistoryAdapter.countByUserIdAndCreatedAtAfter(eq(testUserId), any(LocalDateTime.class)))
                .willReturn(1L);

        // When
        SearchHistoryResponse response = searchHistoryService.getSearchHistory(testUserId, testPageable);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getHistory()).hasSize(1);
        assertThat(response.getHistory().get(0).getQuery()).isEqualTo("테스트 검색어");
        assertThat(response.getPageInfo().getCurrentPage()).isEqualTo(1);
    }

    @Test
    @DisplayName("최근 검색어 조회 성공")
    void getRecentSearchQueries_Success() {
        // Given
        List<String> recentQueries = Arrays.asList("검색어1", "검색어2", "검색어3");
        given(searchHistoryAdapter.findDistinctQueriesByUserId(eq(testUserId), any(Pageable.class)))
                .willReturn(recentQueries);

        // When
        List<String> result = searchHistoryService.getRecentSearchQueries(testUserId, 5);

        // Then
        assertThat(result).hasSize(3);
        assertThat(result).containsExactly("검색어1", "검색어2", "검색어3");
    }

    @Test
    @DisplayName("검색 통계 조회 성공")
    void getSearchStats_Success() {
        // Given
        given(searchHistoryAdapter.countByUserId(testUserId)).willReturn(100L);
        given(searchHistoryAdapter.countByUserIdAndCreatedAtAfter(eq(testUserId), any(LocalDateTime.class)))
                .willReturn(5L, 20L, 50L); // today, week, month
        given(searchHistoryAdapter.findDistinctQueriesByUserId(eq(testUserId), any(Pageable.class)))
                .willReturn(Arrays.asList("인기검색어1", "인기검색어2"));

        // When
        SearchHistoryResponse.SearchStats stats = searchHistoryService.getSearchStats(testUserId);

        // Then
        assertThat(stats.getTotalSearches()).isEqualTo(100L);
        assertThat(stats.getTodaySearches()).isEqualTo(5L);
        assertThat(stats.getThisWeekSearches()).isEqualTo(20L);
        assertThat(stats.getThisMonthSearches()).isEqualTo(50L);
        assertThat(stats.getTopQueries()).hasSize(2);
    }

    @Test
    @DisplayName("검색 히스토리 전체 삭제 성공")
    void clearSearchHistory_Success() {
        // Given
        List<SearchHistory> histories = Arrays.asList(testSearchHistory);
        given(searchHistoryAdapter.findTop10ByUserIdOrderByCreatedAtDesc(testUserId))
                .willReturn(histories);

        // When
        searchHistoryService.clearSearchHistory(testUserId);

        // Then
        verify(searchHistoryAdapter).deleteAll(histories);
    }

    @Test
    @DisplayName("특정 검색 기록 삭제 성공")
    void deleteSearchHistory_Success() {
        // Given
        Long historyId = 1L;
        given(searchHistoryAdapter.findById(historyId)).willReturn(testSearchHistory);

        // When
        searchHistoryService.deleteSearchHistory(testUserId, historyId);

        // Then
        verify(searchHistoryAdapter).delete(testSearchHistory);
    }

    @Test
    @DisplayName("다른 사용자의 검색 기록 삭제 시도 시 예외 발생")
    void deleteSearchHistory_UnauthorizedAccess_ThrowsException() {
        // Given
        Long historyId = 1L;
        String otherUserId = "otherUser";
        given(searchHistoryAdapter.findById(historyId)).willReturn(testSearchHistory);

        // When & Then
        assertThatThrownBy(() -> searchHistoryService.deleteSearchHistory(otherUserId, historyId))
                .isInstanceOf(RuntimeException.class);
    }
}