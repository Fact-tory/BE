package com.commonground.be.domain.dashboard.controller;

import com.commonground.be.domain.dashboard.dto.response.MainDashboardResponse;
import com.commonground.be.domain.dashboard.dto.response.UserDashboardResponse;
import com.commonground.be.domain.dashboard.service.DashboardService;
import com.commonground.be.config.TestSecurityConfig;
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

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(DashboardController.class)
@Import(TestSecurityConfig.class)
@ActiveProfiles("test")
@DisplayName("DashboardController 통합 테스트")
class DashboardControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DashboardService dashboardService;

    @Autowired
    private ObjectMapper objectMapper;

    private MainDashboardResponse mockMainDashboardResponse;
    private UserDashboardResponse mockUserDashboardResponse;

    @BeforeEach
    void setUp() {
        // Mock 메인 대시보드 응답 설정
        List<String> popularSearches = Arrays.asList("검색어1", "검색어2", "검색어3");
        
        mockMainDashboardResponse = new MainDashboardResponse(
                Arrays.asList(),
                Arrays.asList(),
                java.util.Map.of(),
                popularSearches
        );

        // Mock 사용자 대시보드 응답 설정
        List<UserDashboardResponse.AnalysisItem> analyses = Arrays.asList(
                new UserDashboardResponse.AnalysisItem(1L, "분석1", "COMPLETED", LocalDateTime.now())
        );
        
        List<UserDashboardResponse.SearchHistoryItem> searches = Arrays.asList(
                new UserDashboardResponse.SearchHistoryItem("검색어1", LocalDateTime.now(), 10)
        );
        
        UserDashboardResponse.UserStats stats = new UserDashboardResponse.UserStats(5, 3, 100);
        
        mockUserDashboardResponse = new UserDashboardResponse(analyses, searches, stats);
    }

    @Test
    @DisplayName("메인 대시보드 조회 성공")
    void getMainDashboard_Success() throws Exception {
        // Given
        given(dashboardService.getMainDashboard(anyInt(), anyInt(), anyInt(), anyInt()))
                .willReturn(mockMainDashboardResponse);

        // When & Then
        mockMvc.perform(get("/api/v1/dashboard/main")
                        .param("realtime_limit", "20")
                        .param("trending_limit", "10")
                        .param("category_limit", "5")
                        .param("search_limit", "10"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusCode").value(200))
                .andExpect(jsonPath("$.data.popularSearches").isArray())
                .andExpect(jsonPath("$.data.popularSearches[0]").value("검색어1"));
    }

    @Test
    @DisplayName("메인 대시보드 조회 - 기본 파라미터")
    void getMainDashboard_DefaultParameters() throws Exception {
        // Given
        given(dashboardService.getMainDashboard(20, 10, 5, 10))
                .willReturn(mockMainDashboardResponse);

        // When & Then
        mockMvc.perform(get("/api/v1/dashboard/main"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusCode").value(200));
    }

    @Test
    @DisplayName("사용자 대시보드 조회 성공")
    @WithMockUser
    void getUserDashboard_Success() throws Exception {
        // Given
        given(dashboardService.getUserDashboard(any()))
                .willReturn(mockUserDashboardResponse);

        // When & Then
        mockMvc.perform(get("/api/v1/dashboard/user"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusCode").value(200))
                .andExpect(jsonPath("$.data.myAnalyses").isArray())
                .andExpect(jsonPath("$.data.recentSearches").isArray())
                .andExpect(jsonPath("$.data.stats.totalAnalyses").value(5));
    }

    @Test
    @DisplayName("인증되지 않은 사용자 대시보드 조회 시 401 반환")
    void getUserDashboard_Unauthorized() throws Exception {
        // When & Then
        mockMvc.perform(get("/api/v1/dashboard/user"))
                .andDo(print())
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("잘못된 파라미터로 메인 대시보드 조회")
    void getMainDashboard_InvalidParameters() throws Exception {
        // Given
        given(dashboardService.getMainDashboard(anyInt(), anyInt(), anyInt(), anyInt()))
                .willReturn(mockMainDashboardResponse);

        // When & Then
        mockMvc.perform(get("/api/v1/dashboard/main")
                        .param("realtime_limit", "999")
                        .param("trending_limit", "999")
                        .param("category_limit", "999")
                        .param("search_limit", "999"))
                .andDo(print())
                .andExpect(status().isOk()) // 컨트롤러에서 파라미터 범위 제한
                .andExpect(jsonPath("$.statusCode").value(200));
    }

    @Test
    @DisplayName("서비스 예외 발생 시 500 반환")
    void getMainDashboard_ServiceException() throws Exception {
        // Given
        given(dashboardService.getMainDashboard(anyInt(), anyInt(), anyInt(), anyInt()))
                .willThrow(new RuntimeException("서비스 오류"));

        // When & Then
        mockMvc.perform(get("/api/v1/dashboard/main"))
                .andDo(print())
                .andExpect(status().isInternalServerError());
    }

    @Test
    @DisplayName("사용자 대시보드 서비스 예외 발생 시 500 반환")
    @WithMockUser
    void getUserDashboard_ServiceException() throws Exception {
        // Given
        given(dashboardService.getUserDashboard(any()))
                .willThrow(new RuntimeException("서비스 오류"));

        // When & Then
        mockMvc.perform(get("/api/v1/dashboard/user"))
                .andDo(print())
                .andExpect(status().isInternalServerError());
    }
}