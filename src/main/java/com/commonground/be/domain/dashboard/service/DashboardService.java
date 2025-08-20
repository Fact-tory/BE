package com.commonground.be.domain.dashboard.service;

import com.commonground.be.domain.dashboard.dto.response.MainDashboardResponse;
import com.commonground.be.domain.dashboard.dto.response.UserDashboardResponse;

public interface DashboardService {

    /**
     * 메인 대시보드 데이터 조회
     */
    MainDashboardResponse getMainDashboard(int realtimeLimit, int trendingLimit, int categoryLimit, int searchLimit);

    /**
     * 사용자별 맞춤 대시보드 데이터 조회
     */
    UserDashboardResponse getUserDashboard(String userId);
}