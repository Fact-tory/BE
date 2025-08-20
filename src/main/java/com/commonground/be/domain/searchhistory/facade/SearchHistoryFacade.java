package com.commonground.be.domain.searchhistory.facade;

import com.commonground.be.domain.searchhistory.dto.response.SearchHistoryResponse;
import com.commonground.be.domain.searchhistory.service.SearchHistoryService;
import com.commonground.be.global.application.exception.CommonException;
import com.commonground.be.global.application.response.ResponseExceptionEnum;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * SearchHistory Facade - 검색 히스토리 데이터 흐름 조정
 * 검색 기록 저장, 조회, 삭제의 파이프라인 관리
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SearchHistoryFacade {
    
    private final SearchHistoryService searchHistoryService;
    
    /**
     * 검색 기록 저장 파이프라인
     * SearchData → Validation → Service → Response
     */
    public SearchHistoryFlowResult recordSearchFlow(UserDetails userDetails, String query, 
                                                   Integer resultCount, String category, String filters) {
        log.debug("📝 검색 기록 저장 파이프라인 시작 - user: {}, query: {}", userDetails.getUsername(), query);
        
        try {
            // 1. 인증 상태 검증
            validateAuthentication(userDetails);
            log.debug("✅ 사용자 인증 검증 완료: {}", userDetails.getUsername());
            
            // 2. 검색 데이터 검증
            validateSearchData(query);
            log.debug("✅ 검색 데이터 검증 완료");
            
            // 3. 검색 기록 저장
            searchHistoryService.recordSearch(userDetails.getUsername(), query, resultCount, category, filters);
            log.debug("🔄 검색 기록 저장 완료");
            
            // 4. 성공 결과 반환
            SearchHistoryFlowResult result = SearchHistoryFlowResult.successWithMessage("검색 기록이 저장되었습니다.");
            log.debug("📤 검색 기록 저장 파이프라인 완료");
            
            return result;
            
        } catch (Exception e) {
            log.error("❌ 검색 기록 저장 파이프라인 실패: {}", e.getMessage());
            return SearchHistoryFlowResult.failure("검색 기록 저장 실패: " + e.getMessage(), e);
        }
    }
    
    /**
     * 검색 히스토리 조회 파이프라인
     * User → Authentication → Pagination → Service → Response
     */
    public SearchHistoryFlowResult getSearchHistoryFlow(UserDetails userDetails, Pageable pageable) {
        log.debug("📚 검색 히스토리 조회 파이프라인 시작 - user: {}, page: {}, size: {}", 
                userDetails.getUsername(), pageable.getPageNumber(), pageable.getPageSize());
        
        try {
            // 1. 인증 상태 검증
            validateAuthentication(userDetails);
            log.debug("✅ 사용자 인증 검증 완료: {}", userDetails.getUsername());
            
            // 2. 페이징 파라미터 검증
            validatePagingParameters(pageable);
            log.debug("✅ 페이징 파라미터 검증 완료");
            
            // 3. 검색 히스토리 조회
            SearchHistoryResponse searchHistory = searchHistoryService.getSearchHistory(userDetails.getUsername(), pageable);
            log.debug("🔄 검색 히스토리 조회 완료 - 총 {}건", searchHistory.getHistory().size());
            
            // 4. 성공 결과 반환
            SearchHistoryFlowResult result = SearchHistoryFlowResult.success("검색 히스토리 조회 완료", searchHistory);
            log.debug("📤 검색 히스토리 조회 파이프라인 완료");
            
            return result;
            
        } catch (Exception e) {
            log.error("❌ 검색 히스토리 조회 파이프라인 실패: {}", e.getMessage());
            return SearchHistoryFlowResult.failure("검색 히스토리 조회 실패: " + e.getMessage(), e);
        }
    }
    
    /**
     * 최근 검색어 조회 파이프라인
     * User → Authentication → Service → Response
     */
    public SearchHistoryFlowResult getRecentQueriesFlow(UserDetails userDetails, int limit) {
        log.debug("🔍 최근 검색어 조회 파이프라인 시작 - user: {}, limit: {}", userDetails.getUsername(), limit);
        
        try {
            // 1. 인증 상태 검증
            validateAuthentication(userDetails);
            log.debug("✅ 사용자 인증 검증 완료: {}", userDetails.getUsername());
            
            // 2. 제한값 검증
            validateLimit(limit);
            log.debug("✅ 제한값 검증 완료");
            
            // 3. 최근 검색어 조회
            List<String> recentQueries = searchHistoryService.getRecentSearchQueries(userDetails.getUsername(), limit);
            log.debug("🔄 최근 검색어 조회 완료 - {}건", recentQueries.size());
            
            // 4. 성공 결과 반환
            SearchHistoryFlowResult result = SearchHistoryFlowResult.success("최근 검색어 조회 완료", recentQueries);
            log.debug("📤 최근 검색어 조회 파이프라인 완료");
            
            return result;
            
        } catch (Exception e) {
            log.error("❌ 최근 검색어 조회 파이프라인 실패: {}", e.getMessage());
            return SearchHistoryFlowResult.failure("최근 검색어 조회 실패: " + e.getMessage(), e);
        }
    }
    
    /**
     * 검색 히스토리 삭제 파이프라인
     * User → Authentication → Service → Response
     */
    public SearchHistoryFlowResult clearSearchHistoryFlow(UserDetails userDetails) {
        log.debug("🗑️ 검색 히스토리 삭제 파이프라인 시작 - user: {}", userDetails.getUsername());
        
        try {
            // 1. 인증 상태 검증
            validateAuthentication(userDetails);
            log.debug("✅ 사용자 인증 검증 완료: {}", userDetails.getUsername());
            
            // 2. 검색 히스토리 전체 삭제
            searchHistoryService.clearSearchHistory(userDetails.getUsername());
            log.debug("🔄 검색 히스토리 삭제 완료");
            
            // 3. 성공 결과 반환
            SearchHistoryFlowResult result = SearchHistoryFlowResult.successWithMessage("검색 히스토리가 삭제되었습니다.");
            log.debug("📤 검색 히스토리 삭제 파이프라인 완료");
            
            return result;
            
        } catch (Exception e) {
            log.error("❌ 검색 히스토리 삭제 파이프라인 실패: {}", e.getMessage());
            return SearchHistoryFlowResult.failure("검색 히스토리 삭제 실패: " + e.getMessage(), e);
        }
    }
    
    /**
     * 특정 검색 기록 삭제 파이프라인
     * User → Authentication → HistoryId → Service → Response
     */
    public SearchHistoryFlowResult deleteSearchHistoryFlow(UserDetails userDetails, Long historyId) {
        log.debug("🗑️ 검색 기록 삭제 파이프라인 시작 - user: {}, historyId: {}", userDetails.getUsername(), historyId);
        
        try {
            // 1. 인증 상태 검증
            validateAuthentication(userDetails);
            log.debug("✅ 사용자 인증 검증 완료: {}", userDetails.getUsername());
            
            // 2. 히스토리 ID 검증
            validateHistoryId(historyId);
            log.debug("✅ 히스토리 ID 검증 완료");
            
            // 3. 검색 기록 삭제
            searchHistoryService.deleteSearchHistory(userDetails.getUsername(), historyId);
            log.debug("🔄 검색 기록 삭제 완료");
            
            // 4. 성공 결과 반환
            SearchHistoryFlowResult result = SearchHistoryFlowResult.successWithMessage("검색 기록이 삭제되었습니다.");
            log.debug("📤 검색 기록 삭제 파이프라인 완료");
            
            return result;
            
        } catch (Exception e) {
            log.error("❌ 검색 기록 삭제 파이프라인 실패: {}", e.getMessage());
            return SearchHistoryFlowResult.failure("검색 기록 삭제 실패: " + e.getMessage(), e);
        }
    }
    
    // === Private Validation Methods ===
    
    private void validateAuthentication(UserDetails userDetails) {
        if (userDetails == null) {
            throw new CommonException(ResponseExceptionEnum.UNAUTHORIZED_ACCESS);
        }
    }
    
    private void validateSearchData(String query) {
        if (query == null || query.trim().isEmpty()) {
            throw new CommonException(ResponseExceptionEnum.PARAMETER_REQUIRED);
        }
        if (query.length() > 255) {
            throw new CommonException(ResponseExceptionEnum.PARAMETER_OUT_OF_RANGE);
        }
    }
    
    private void validatePagingParameters(Pageable pageable) {
        if (pageable.getPageNumber() < 0) {
            throw new CommonException(ResponseExceptionEnum.PARAMETER_OUT_OF_RANGE);
        }
        if (pageable.getPageSize() < 1 || pageable.getPageSize() > 100) {
            throw new CommonException(ResponseExceptionEnum.PARAMETER_OUT_OF_RANGE);
        }
    }
    
    private void validateLimit(int limit) {
        if (limit < 1 || limit > 50) {
            throw new CommonException(ResponseExceptionEnum.PARAMETER_OUT_OF_RANGE);
        }
    }
    
    private void validateHistoryId(Long historyId) {
        if (historyId == null || historyId <= 0) {
            throw new CommonException(ResponseExceptionEnum.PARAMETER_REQUIRED);
        }
    }
    
    /**
     * SearchHistory 도메인 데이터 흐름 결과 컨테이너
     */
    public static class SearchHistoryFlowResult {
        private final boolean success;
        private final String message;
        private final Object data;
        private final Exception error;
        
        private SearchHistoryFlowResult(boolean success, String message, Object data, Exception error) {
            this.success = success;
            this.message = message;
            this.data = data;
            this.error = error;
        }
        
        public static SearchHistoryFlowResult success(String message, Object data) {
            return new SearchHistoryFlowResult(true, message, data, null);
        }
        
        public static SearchHistoryFlowResult successWithMessage(String message) {
            return new SearchHistoryFlowResult(true, message, null, null);
        }
        
        public static SearchHistoryFlowResult failure(String message, Exception error) {
            return new SearchHistoryFlowResult(false, message, null, error);
        }
        
        // Getters
        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
        public Object getData() { return data; }
        public Exception getError() { return error; }
    }
}