package com.commonground.be.domain.dashboard.service;

import com.commonground.be.domain.dashboard.dto.response.MainDashboardResponse;
import com.commonground.be.domain.dashboard.dto.response.UserDashboardResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.ArgumentMatchers.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("DashboardService 단위 테스트")
class DashboardServiceTest {

    @Mock
    private PopularSearchService popularSearchService;

    @Mock
    private CategoryNewsService categoryNewsService;

    @InjectMocks
    private DashboardServiceImpl dashboardService;

    private String testUserId;

    @BeforeEach
    void setUp() {
        testUserId = "testUser";
    }

    @Test
    @DisplayName("메인 대시보드 조회 성공")
    void getMainDashboard_Success() {
        // Given
        int realtimeLimit = 20;
        int trendingLimit = 10;
        int categoryLimit = 5;
        int searchLimit = 10;
        
        List<String> popularSearches = Arrays.asList("검색어1", "검색어2", "검색어3");
        given(popularSearchService.getPopularSearches(searchLimit)).willReturn(popularSearches);

        // When
        MainDashboardResponse response = dashboardService.getMainDashboard(
                realtimeLimit, trendingLimit, categoryLimit, searchLimit);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getRealtime()).isNotNull();
        assertThat(response.getTrending()).isNotNull();
        assertThat(response.getPopularSearches()).hasSize(3);
        assertThat(response.getPopularSearches()).containsExactly("검색어1", "검색어2", "검색어3");
        assertThat(response.getLastUpdated()).isNotNull();
    }

    @Test
    @DisplayName("사용자 대시보드 조회 성공")
    void getUserDashboard_Success() {
        // When
        UserDashboardResponse response = dashboardService.getUserDashboard(testUserId);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getMyAnalyses()).isNotNull();
        assertThat(response.getRecentSearches()).isNotNull();
        assertThat(response.getStats()).isNotNull();
        assertThat(response.getLastUpdated()).isNotNull();
        
        // 통계 검증
        assertThat(response.getStats().getTotalAnalyses()).isGreaterThanOrEqualTo(0);
        assertThat(response.getStats().getCompletedAnalyses()).isGreaterThanOrEqualTo(0);
        assertThat(response.getStats().getTotalSearches()).isGreaterThanOrEqualTo(0);
    }

    @Test
    @DisplayName("메인 대시보드 - 제한값이 최대값을 초과하는 경우")
    void getMainDashboard_ExceedsMaxLimits() {
        // Given
        int excessiveLimit = 1000;
        List<String> popularSearches = Arrays.asList("검색어1");
        given(popularSearchService.getPopularSearches(any(Integer.class))).willReturn(popularSearches);

        // When
        MainDashboardResponse response = dashboardService.getMainDashboard(
                excessiveLimit, excessiveLimit, excessiveLimit, excessiveLimit);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getRealtime().getArticles().size()).isLessThanOrEqualTo(50);
        assertThat(response.getTrending().getArticles().size()).isLessThanOrEqualTo(20);
    }

    @Test
    @DisplayName("메인 대시보드 - 최소값 미만인 경우")
    void getMainDashboard_BelowMinLimits() {
        // Given
        int minimumLimit = 0;
        List<String> popularSearches = Arrays.asList("검색어1");
        given(popularSearchService.getPopularSearches(any(Integer.class))).willReturn(popularSearches);

        // When
        MainDashboardResponse response = dashboardService.getMainDashboard(
                minimumLimit, minimumLimit, minimumLimit, minimumLimit);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getRealtime().getArticles().size()).isGreaterThanOrEqualTo(1);
        assertThat(response.getTrending().getArticles().size()).isGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("사용자 대시보드 - 분석 데이터가 있는 경우")
    void getUserDashboard_WithAnalysisData() {
        // When
        UserDashboardResponse response = dashboardService.getUserDashboard(testUserId);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getMyAnalyses()).isNotEmpty();
        
        // 첫 번째 분석 항목 검증
        UserDashboardResponse.AnalysisItem firstAnalysis = response.getMyAnalyses().get(0);
        assertThat(firstAnalysis.getAnalysisId()).isNotNull();
        assertThat(firstAnalysis.getTitle()).isNotBlank();
        assertThat(firstAnalysis.getStatus()).isNotNull();
        // AnalysisItem에는 analysisType 필드가 없으므로 제거
        assertThat(firstAnalysis.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("사용자 대시보드 - 검색 기록이 있는 경우")
    void getUserDashboard_WithSearchHistory() {
        // When
        UserDashboardResponse response = dashboardService.getUserDashboard(testUserId);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getRecentSearches()).isNotEmpty();
        
        // 첫 번째 검색 기록 검증
        UserDashboardResponse.SearchHistoryItem firstSearch = response.getRecentSearches().get(0);
        assertThat(firstSearch.getQuery()).isNotBlank();
        assertThat(firstSearch.getSearchedAt()).isNotNull();
        assertThat(firstSearch.getResultCount()).isGreaterThanOrEqualTo(0);
    }

    @Test
    @DisplayName("메인 대시보드 - 인기 검색어 서비스 오류 시 빈 목록 반환")
    void getMainDashboard_PopularSearchServiceError_ReturnsEmptyList() {
        // Given
        given(popularSearchService.getPopularSearches(anyInt())).willThrow(new RuntimeException("서비스 오류"));

        // When
        MainDashboardResponse response = dashboardService.getMainDashboard(20, 10, 5, 10);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getPopularSearches()).isEmpty();
        assertThat(response.getRealtime()).isNotNull();
        assertThat(response.getTrending()).isNotNull();
    }
}