package com.commonground.be.global.application.exception;

import com.commonground.be.global.application.response.ResponseExceptionEnum;

public class SearchHistoryExceptions {
    
    public static CommonException searchHistoryNotFound() {
        return new CommonException(ResponseExceptionEnum.SEARCH_HISTORY_NOT_FOUND);
    }
    
    public static CommonException searchHistoryDeleteFailed() {
        return new CommonException(ResponseExceptionEnum.SEARCH_HISTORY_DELETE_FAILED);
    }
    
    public static CommonException searchHistorySaveFailed() {
        return new CommonException(ResponseExceptionEnum.SEARCH_HISTORY_SAVE_FAILED);
    }
    
    public static CommonException searchHistoryFailed() {
        return new CommonException(ResponseExceptionEnum.SEARCH_HISTORY_FAILED);
    }
}