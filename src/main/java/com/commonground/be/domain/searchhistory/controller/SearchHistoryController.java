package com.commonground.be.domain.searchhistory.controller;

import com.commonground.be.domain.searchhistory.facade.SearchHistoryFacade;
import com.commonground.be.global.application.response.HttpResponseDto;
import com.commonground.be.global.application.response.ResponseCodeEnum;
import com.commonground.be.global.application.response.ResponseUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/search-history")
@RequiredArgsConstructor
public class SearchHistoryController {
    
    private final SearchHistoryFacade searchHistoryFacade;
    
    /**
     * 검색 히스토리 조회
     * GET /api/v1/search-history
     */
    @GetMapping
    public ResponseEntity<HttpResponseDto> getSearchHistory(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        if (userDetails == null) {
            return ResponseUtils.of(ResponseCodeEnum.UNAUTHORIZED_ACCESS, null);
        }
        
        log.info("검색 히스토리 조회 - userId: {}, page: {}, size: {}", 
                userDetails.getUsername(), page, size);
        
        try {
            Pageable pageable = PageRequest.of(page - 1, Math.min(size, 100));
            var result = searchHistoryFacade.getSearchHistoryFlow(userDetails, pageable);
            
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
    
    /**
     * 최근 검색어 조회
     * GET /api/v1/search-history/recent-queries
     */
    @GetMapping("/recent-queries")
    public ResponseEntity<HttpResponseDto> getRecentQueries(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(defaultValue = "10") int limit
    ) {
        if (userDetails == null) {
            return ResponseUtils.of(ResponseCodeEnum.UNAUTHORIZED_ACCESS, null);
        }
        
        log.info("최근 검색어 조회 - userId: {}, limit: {}", userDetails.getUsername(), limit);
        
        try {
            var result = searchHistoryFacade.getRecentQueriesFlow(userDetails, limit);
            
            if (result.isSuccess()) {
                return ResponseUtils.of(ResponseCodeEnum.SUCCESS, result.getData());
            } else {
                return ResponseUtils.of(ResponseCodeEnum.BAD_REQUEST, null);
            }
        } catch (Exception e) {
            log.error("최근 검색어 조회 예외 발생: userId={}", userDetails.getUsername(), e);
            return ResponseUtils.of(ResponseCodeEnum.INTERNAL_SERVER_ERROR, null);
        }
    }
    
    /**
     * 검색 히스토리 전체 삭제
     * DELETE /api/v1/search-history
     */
    @DeleteMapping
    public ResponseEntity<HttpResponseDto> clearSearchHistory(
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        if (userDetails == null) {
            return ResponseUtils.of(ResponseCodeEnum.UNAUTHORIZED_ACCESS, null);
        }
        
        log.info("검색 히스토리 전체 삭제 - userId: {}", userDetails.getUsername());
        
        try {
            var result = searchHistoryFacade.clearSearchHistoryFlow(userDetails);
            
            if (result.isSuccess()) {
                return ResponseUtils.of(
                        ResponseCodeEnum.SUCCESS, 
                        Map.of("message", result.getMessage())
                );
            } else {
                return ResponseUtils.of(ResponseCodeEnum.BAD_REQUEST, null);
            }
        } catch (Exception e) {
            log.error("검색 히스토리 삭제 예외 발생: userId={}", userDetails.getUsername(), e);
            return ResponseUtils.of(ResponseCodeEnum.INTERNAL_SERVER_ERROR, null);
        }
    }
    
    /**
     * 특정 검색 기록 삭제
     * DELETE /api/v1/search-history/{historyId}
     */
    @DeleteMapping("/{historyId}")
    public ResponseEntity<HttpResponseDto> deleteSearchHistory(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long historyId
    ) {
        if (userDetails == null) {
            return ResponseUtils.of(ResponseCodeEnum.UNAUTHORIZED_ACCESS, null);
        }
        
        log.info("검색 기록 삭제 - userId: {}, historyId: {}", userDetails.getUsername(), historyId);
        
        try {
            var result = searchHistoryFacade.deleteSearchHistoryFlow(userDetails, historyId);
            
            if (result.isSuccess()) {
                return ResponseUtils.of(
                        ResponseCodeEnum.SUCCESS, 
                        Map.of("message", result.getMessage())
                );
            } else {
                return ResponseUtils.of(ResponseCodeEnum.NOT_FOUND, null);
            }
        } catch (Exception e) {
            log.error("검색 기록 삭제 예외 발생: userId={}, historyId={}", userDetails.getUsername(), historyId, e);
            return ResponseUtils.of(ResponseCodeEnum.INTERNAL_SERVER_ERROR, null);
        }
    }
}