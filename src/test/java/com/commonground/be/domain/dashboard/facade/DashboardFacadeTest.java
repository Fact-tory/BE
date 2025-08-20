package com.commonground.be.domain.dashboard.facade;

import com.commonground.be.domain.dashboard.dto.response.MainDashboardResponse;
import com.commonground.be.domain.dashboard.dto.response.UserDashboardResponse;
import com.commonground.be.domain.dashboard.service.DashboardService;
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

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
@DisplayName("DashboardFacade 단위 테스트")
class DashboardFacadeTest {

    @Mock
    private DashboardService dashboardService;

    @InjectMocks
    private DashboardFacade dashboardFacade;

    private UserDetails mockUserDetails;
    private String testUserId;

    @BeforeEach
    void setUp() {
        mockUserDetails = mock(UserDetails.class);
        testUserId = "testUser";
        given(mockUserDetails.getUsername()).willReturn(testUserId);
    }

    @Test
    @DisplayName("메인 대시보드 파이프라인 성공")
    void getMainDashboardFlow_Success() {
        // Given
        int realtimeLimit = 20;
        int trendingLimit = 10;
        int categoryLimit = 5;
        int searchLimit = 10;
        
        List<String> popularSearches = Arrays.asList("검색어1", "검색어2", "검색어3");
        
        MainDashboardResponse mockResponse = new MainDashboardResponse(
                Arrays.asList(), // realtime
                Arrays.asList(), // trending
                java.util.Map.of(), // categories
                popularSearches
        );
        
        given(dashboardService.getMainDashboard(realtimeLimit, trendingLimit, categoryLimit, searchLimit))
                .willReturn(mockResponse);

        // When
        DashboardFacade.DashboardFlowResult result = 
                dashboardFacade.getMainDashboardFlow(realtimeLimit, trendingLimit, categoryLimit, searchLimit);

        // Then
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData()).isEqualTo(mockResponse);
        assertThat(result.getMessage()).contains("메인 대시보드 집계 완료");
    }

    @Test
    @DisplayName("사용자 대시보드 파이프라인 성공")
    void getUserDashboardFlow_Success() {
        // Given
        List<UserDashboardResponse.AnalysisItem> analyses = Arrays.asList(
                new UserDashboardResponse.AnalysisItem(1L, "분석1", "COMPLETED", LocalDateTime.now())
        );
        
        List<UserDashboardResponse.SearchHistoryItem> searches = Arrays.asList(
                new UserDashboardResponse.SearchHistoryItem("검색어1", LocalDateTime.now(), 10)
        );
        
        UserDashboardResponse.UserStats stats = new UserDashboardResponse.UserStats(5, 3, 100);
        
        UserDashboardResponse mockResponse = new UserDashboardResponse(analyses, searches, stats);
        
        given(dashboardService.getUserDashboard(testUserId)).willReturn(mockResponse);

        // When
        DashboardFacade.DashboardFlowResult result = 
                dashboardFacade.getUserDashboardFlow(mockUserDetails);

        // Then
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData()).isEqualTo(mockResponse);
        assertThat(result.getMessage()).contains("사용자 대시보드 집계 완료");
    }

    @Test
    @DisplayName("대시보드 새로고침 파이프라인 성공")
    void refreshDashboardFlow_Success() {
        // Given
        List<UserDashboardResponse.AnalysisItem> analyses = Arrays.asList();
        List<UserDashboardResponse.SearchHistoryItem> searches = Arrays.asList();
        UserDashboardResponse.UserStats stats = new UserDashboardResponse.UserStats(0, 0, 0);
        
        UserDashboardResponse mockResponse = new UserDashboardResponse(analyses, searches, stats);
        
        given(dashboardService.getUserDashboard(testUserId)).willReturn(mockResponse);

        // When
        DashboardFacade.DashboardFlowResult result = 
                dashboardFacade.refreshDashboardFlow(mockUserDetails);

        // Then
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData()).isEqualTo(mockResponse);
        assertThat(result.getMessage()).contains("대시보드 새로고침 완료");
    }

    @Test
    @DisplayName("인증되지 않은 사용자 요청 시 실패")
    void getUserDashboardFlow_UnauthenticatedUser_Failure() {
        // When
        DashboardFacade.DashboardFlowResult result = 
                dashboardFacade.getUserDashboardFlow(null);

        // Then
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).contains("사용자 대시보드 조회 실패");
    }

    @Test
    @DisplayName("잘못된 파라미터로 메인 대시보드 요청 시 실패")
    void getMainDashboardFlow_InvalidParameters_Failure() {
        // When
        DashboardFacade.DashboardFlowResult result = 
                dashboardFacade.getMainDashboardFlow(-1, -1, -1, -1);

        // Then
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).contains("메인 대시보드 조회 실패");
    }

    @Test
    @DisplayName("서비스 예외 발생 시 실패")
    void getMainDashboardFlow_ServiceException_Failure() {
        // Given
        given(dashboardService.getMainDashboard(anyInt(), anyInt(), anyInt(), anyInt()))
                .willThrow(new RuntimeException("서비스 오류"));

        // When
        DashboardFacade.DashboardFlowResult result = 
                dashboardFacade.getMainDashboardFlow(20, 10, 5, 10);

        // Then
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).contains("메인 대시보드 조회 실패");
    }
}