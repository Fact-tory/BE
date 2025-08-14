package com.commonground.be.global.application.exception;

public class NewsExceptions {
    
    public static class DuplicateNewsException extends RuntimeException {
        public DuplicateNewsException(String message) {
            super(message);
        }
    }
    
    public static class InvalidNewsException extends RuntimeException {
        public InvalidNewsException(String message) {
            super(message);
        }
    }
    
    public static class NewsNotFoundException extends RuntimeException {
        public NewsNotFoundException(String message) {
            super(message);
        }
    }
    
    public static class CrawlingException extends RuntimeException {
        public CrawlingException(String message) {
            super(message);
        }
        
        public CrawlingException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}