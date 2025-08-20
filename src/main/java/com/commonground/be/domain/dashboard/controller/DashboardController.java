package com.commonground.be.domain.dashboard.controller;

import com.commonground.be.domain.dashboard.dto.response.MainDashboardResponse;
import com.commonground.be.domain.dashboard.dto.response.UserDashboardResponse;
import com.commonground.be.domain.dashboard.service.DashboardService;
import com.commonground.be.global.application.response.HttpResponseDto;
import com.commonground.be.global.application.response.ResponseCodeEnum;
import com.commonground.be.global.application.response.ResponseUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    /**
     * 메인 대시보드 통합 API
     * GET /api/v1/dashboard/main
     */
    @GetMapping("/main")
    public ResponseEntity<HttpResponseDto> getMainDashboard(
            @RequestParam(defaultValue = "20") int realtime_limit,
            @RequestParam(defaultValue = "10") int trending_limit,
            @RequestParam(defaultValue = "5") int category_limit,
            @RequestParam(defaultValue = "10") int search_limit
    ) {
        log.info("메인 대시보드 조회 - realtime: {}, trending: {}, category: {}, search: {}", 
                realtime_limit, trending_limit, category_limit, search_limit);

        // 파라미터 범위 제한
        realtime_limit = Math.min(Math.max(realtime_limit, 1), 50);
        trending_limit = Math.min(Math.max(trending_limit, 1), 20);
        category_limit = Math.min(Math.max(category_limit, 1), 10);
        search_limit = Math.min(Math.max(search_limit, 1), 20);

        MainDashboardResponse response = dashboardService.getMainDashboard(
                realtime_limit, trending_limit, category_limit, search_limit);

        return ResponseUtils.of(ResponseCodeEnum.SUCCESS, response);
    }

    /**
     * 사용자별 맞춤 대시보드
     * GET /api/v1/dashboard/user
     */
    @GetMapping("/user")
    public ResponseEntity<HttpResponseDto> getUserDashboard(
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        if (userDetails == null) {
            log.warn("인증되지 않은 사용자의 대시보드 요청");
            return ResponseUtils.of(ResponseCodeEnum.UNAUTHORIZED_ACCESS, null);
        }

        log.info("사용자 대시보드 조회 - userId: {}", userDetails.getUsername());

        UserDashboardResponse response = dashboardService.getUserDashboard(userDetails.getUsername());

        return ResponseUtils.of(ResponseCodeEnum.SUCCESS, response);
    }
}