package com.commonground.be.global.application.exception;

import com.commonground.be.global.application.response.ResponseExceptionEnum;

/**
 * 뉴스 서비스 관련 예외 처리
 * 기존 exception 방식을 따라 static 메서드로 CommonException 반환
 */
public class NewsServiceExceptions {

    // ==================== 뉴스 관리 관련 예외 ====================
    
    public static CommonException newsNotFound(String newsId) {
        return new CommonException(ResponseExceptionEnum.NEWS_NOT_FOUND);
    }
    
    public static CommonException duplicateNews(String url) {
        return new CommonException(ResponseExceptionEnum.DUPLICATE_NEWS);
    }
    
    public static CommonException invalidNewsData(String reason) {
        return new CommonException(ResponseExceptionEnum.INVALID_NEWS_DATA);
    }
    
    // ==================== 뉴스 수집 관련 예외 ====================
    
    public static CommonException newsCollectionFailed(String source) {
        return new CommonException(ResponseExceptionEnum.NEWS_COLLECTION_FAILED);
    }
    
    public static CommonException naverApiFailed() {
        return new CommonException(ResponseExceptionEnum.NAVER_API_FAILED);
    }
    
    public static CommonException dataProcessingFailed(String url) {
        return new CommonException(ResponseExceptionEnum.DATA_PROCESSING_FAILED);
    }
    
    // ==================== 뉴스 검색 관련 예외 ====================
    
    public static CommonException searchFailed(String keyword) {
        return new CommonException(ResponseExceptionEnum.NEWS_SEARCH_FAILED);
    }
    
    public static CommonException indexingFailed(String newsId) {
        return new CommonException(ResponseExceptionEnum.NEWS_INDEXING_FAILED);
    }
}