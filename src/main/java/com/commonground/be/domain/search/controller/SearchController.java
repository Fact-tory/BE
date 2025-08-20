package com.commonground.be.domain.search.controller;

import com.commonground.be.domain.search.dto.request.SearchRequest;
import com.commonground.be.domain.search.dto.response.*;
import com.commonground.be.domain.search.facade.SearchFacade;
import com.commonground.be.global.application.response.HttpResponseDto;
import com.commonground.be.global.application.response.ResponseCodeEnum;
import com.commonground.be.global.application.response.ResponseUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 🔍 검색 컨트롤러
 */
@RestController
@RequestMapping("/api/v1/search")
@RequiredArgsConstructor
@Slf4j
public class SearchController {

    private final SearchFacade searchFacade;

    /**
     * 통합 뉴스 검색
     */
    @GetMapping("/news")
    public ResponseEntity<HttpResponseDto> searchNews(
            @RequestParam String q,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String bias,
            @RequestParam(required = false) String sort) {

        try {
            var result = searchFacade.searchNewsFlow(q, page, size, category, bias, sort);
            
            if (result.isSuccess()) {
                return ResponseUtils.of(ResponseCodeEnum.SUCCESS, result.getData());
            } else {
                log.error("뉴스 검색 실패: {}", result.getMessage());
                return ResponseUtils.of(ResponseCodeEnum.BAD_REQUEST, null);
            }
        } catch (Exception e) {
            log.error("뉴스 검색 예외 발생: q={}", q, e);
            return ResponseUtils.of(ResponseCodeEnum.INTERNAL_SERVER_ERROR, null);
        }
    }

    /**
     * 내 분석 검색
     */
    @GetMapping("/my-analyses")
    public ResponseEntity<HttpResponseDto> searchMyAnalyses(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam String q,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        
        if (userDetails == null) {
            return ResponseUtils.of(ResponseCodeEnum.UNAUTHORIZED_ACCESS, null);
        }
        
        try {
            var result = searchFacade.searchMyAnalysesFlow(userDetails, q, page, size);
            
            if (result.isSuccess()) {
                return ResponseUtils.of(ResponseCodeEnum.SUCCESS, result.getData());
            } else {
                return ResponseUtils.of(ResponseCodeEnum.BAD_REQUEST, null);
            }
        } catch (Exception e) {
            log.error("내 분석 검색 예외 발생: userId={}, q={}", userDetails.getUsername(), q, e);
            return ResponseUtils.of(ResponseCodeEnum.INTERNAL_SERVER_ERROR, null);
        }
    }

    /**
     * 인기 검색어
     */
    @GetMapping("/popular")
    public ResponseEntity<HttpResponseDto> getPopularSearches(
            @RequestParam(defaultValue = "10") int limit) {
        
        try {
            var result = searchFacade.getPopularSearchesFlow(limit);
            
            if (result.isSuccess()) {
                return ResponseUtils.of(ResponseCodeEnum.SUCCESS, result.getData());
            } else {
                return ResponseUtils.of(ResponseCodeEnum.BAD_REQUEST, null);
            }
        } catch (Exception e) {
            log.error("인기 검색어 조회 예외 발생: limit={}", limit, e);
            return ResponseUtils.of(ResponseCodeEnum.INTERNAL_SERVER_ERROR, null);
        }
    }

    /**
     * 실시간 검색어 순위
     */
    @GetMapping("/realtime-popular")
    public ResponseEntity<HttpResponseDto> getRealtimePopularSearches() {
        
        try {
            var result = searchFacade.getRealtimePopularSearchesFlow();
            
            if (result.isSuccess()) {
                return ResponseUtils.of(ResponseCodeEnum.SUCCESS, result.getData());
            } else {
                return ResponseUtils.of(ResponseCodeEnum.SERVICE_UNAVAILABLE, null);
            }
        } catch (Exception e) {
            log.error("실시간 검색어 조회 예외 발생", e);
            return ResponseUtils.of(ResponseCodeEnum.INTERNAL_SERVER_ERROR, null);
        }
    }

    /**
     * 검색 자동완성
     */
    @GetMapping("/autocomplete")
    public ResponseEntity<HttpResponseDto> getAutocomplete(
            @RequestParam String q,
            @RequestParam(defaultValue = "10") int limit) {
        
        log.info("자동완성 조회 - query: {}, limit: {}", q, limit);
        
        if (q == null || q.trim().isEmpty()) {
            return ResponseUtils.of(ResponseCodeEnum.BAD_REQUEST, 
                Map.of("error", "검색어를 입력해주세요."));
        }
        
        var result = searchFacade.getAutocompleteFlow(q, limit);
        
        if (result.isSuccess()) {
            return ResponseUtils.of(ResponseCodeEnum.SUCCESS, result.getData());
        } else {
            return ResponseUtils.of(ResponseCodeEnum.BAD_REQUEST, null);
        }
    }

    /**
     * 검색 통계
     */
    @GetMapping("/statistics")
    public ResponseEntity<HttpResponseDto> getSearchStatistics(
            @AuthenticationPrincipal UserDetails userDetails) {
        
        if (userDetails == null) {
            return ResponseUtils.of(ResponseCodeEnum.UNAUTHORIZED_ACCESS, null);
        }
        
        try {
            var result = searchFacade.getSearchStatisticsFlow(userDetails);
            
            if (result.isSuccess()) {
                return ResponseUtils.of(ResponseCodeEnum.SUCCESS, result.getData());
            } else {
                return ResponseUtils.of(ResponseCodeEnum.BAD_REQUEST, null);
            }
        } catch (Exception e) {
            log.error("검색 통계 조회 예외 발생: userId={}", userDetails.getUsername(), e);
            return ResponseUtils.of(ResponseCodeEnum.INTERNAL_SERVER_ERROR, null);
        }
    }

    /**
     * 검색 히스토리
     */
    @GetMapping("/history")
    public ResponseEntity<HttpResponseDto> getSearchHistory(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        
        if (userDetails == null) {
            return ResponseUtils.of(ResponseCodeEnum.UNAUTHORIZED_ACCESS, null);
        }
        
        try {
            var result = searchFacade.getSearchHistoryFlow(userDetails, page, size);
            
            if (result.isSuccess()) {
                return ResponseUtils.of(ResponseCodeEnum.SUCCESS, result.getData());
            } else {
                return ResponseUtils.of(ResponseCodeEnum.BAD_REQUEST, null);
            }
        } catch (Exception e) {
            log.error("검색 히스토리 조회 예외 발생: userId={}", userDetails.getUsername(), e);
            return ResponseUtils.of(ResponseCodeEnum.INTERNAL_SERVER_ERROR, null);
        }
    }

}