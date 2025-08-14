package com.commonground.be.global.application.exception;

import com.commonground.be.global.application.response.ResponseExceptionEnum;

/**
 * 뉴스 크롤링 관련 예외 처리
 * 기존 exception 방식을 따라 static 메서드로 CommonException 반환
 */
public class NewsCrawlingExceptions {

    // ==================== 웹 크롤링 예외 ====================
    
    public static CommonException crawlingInitializationFailed() {
        return new CommonException(ResponseExceptionEnum.CRAWLING_INITIALIZATION_FAILED);
    }
    
    public static CommonException browserLaunchFailed() {
        return new CommonException(ResponseExceptionEnum.BROWSER_LAUNCH_FAILED);
    }
    
    public static CommonException pageNavigationFailed(String url) {
        return new CommonException(ResponseExceptionEnum.PAGE_NAVIGATION_FAILED);
    }
    
    public static CommonException articleExtractionFailed(String url) {
        return new CommonException(ResponseExceptionEnum.ARTICLE_EXTRACTION_FAILED);
    }
    
    public static CommonException authorExtractionFailed(String url) {
        return new CommonException(ResponseExceptionEnum.AUTHOR_EXTRACTION_FAILED);
    }
    
    public static CommonException contentExtractionFailed(String url) {
        return new CommonException(ResponseExceptionEnum.CONTENT_EXTRACTION_FAILED);
    }
    
    // ==================== URL 및 링크 처리 예외 ====================
    
    public static CommonException invalidArticleUrl(String url) {
        return new CommonException(ResponseExceptionEnum.INVALID_ARTICLE_URL);
    }
    
    public static CommonException urlFilteringFailed(String url) {
        return new CommonException(ResponseExceptionEnum.URL_FILTERING_FAILED);
    }
    
    public static CommonException linkDiscoveryFailed(String baseUrl) {
        return new CommonException(ResponseExceptionEnum.LINK_DISCOVERY_FAILED);
    }
    
    // ==================== 크롤링 세션 및 진행상황 예외 ====================
    
    public static CommonException crawlingSessionFailed(String sessionId) {
        return new CommonException(ResponseExceptionEnum.CRAWLING_SESSION_FAILED);
    }
    
    public static CommonException progressUpdateFailed(String sessionId) {
        return new CommonException(ResponseExceptionEnum.PROGRESS_UPDATE_FAILED);
    }
    
    public static CommonException crawlingTimeoutExceeded(int timeoutMinutes) {
        return new CommonException(ResponseExceptionEnum.CRAWLING_TIMEOUT_EXCEEDED);
    }
    
    // ==================== 네이버 전용 크롤링 예외 ====================
    
    public static CommonException naverMediaPageLoadFailed(String officeId) {
        return new CommonException(ResponseExceptionEnum.NAVER_MEDIA_PAGE_LOAD_FAILED);
    }
    
    public static CommonException naverArticleParsingFailed(String articleId) {
        return new CommonException(ResponseExceptionEnum.NAVER_ARTICLE_PARSING_FAILED);
    }
    
    public static CommonException naverScrollingFailed(int attemptCount) {
        return new CommonException(ResponseExceptionEnum.NAVER_SCROLLING_FAILED);
    }
    
    public static CommonException naverCategoryMappingFailed(String categoryId) {
        return new CommonException(ResponseExceptionEnum.NAVER_CATEGORY_MAPPING_FAILED);
    }
    
    // ==================== 일반 크롤링 예외 ====================
    
    public static CommonException failToCrawlNews() {
        return new CommonException(ResponseExceptionEnum.CRAWLING_QUICK_TEST_FAILED);
    }
}