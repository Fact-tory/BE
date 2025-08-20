package com.commonground.be.domain.news.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 🔗 URL 크롤링 결과 응답 DTO
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UrlCrawlingResponse {
    
    /**
     * 세션 ID
     */
    private String sessionId;
    
    /**
     * 원본 URL
     */
    private String originalUrl;
    
    /**
     * 실제 접근된 URL (리다이렉트 고려)
     */
    private String finalUrl;
    
    /**
     * 추출된 제목
     */
    private String title;
    
    /**
     * 추출된 본문
     */
    private String content;
    
    /**
     * 요약 (선택사항)
     */
    private String summary;
    
    /**
     * 메타데이터
     */
    private UrlMetadata metadata;
    
    /**
     * 처리 상태
     */
    private ProcessingStatus status;
    
    /**
     * 오류 메시지 (실패시)
     */
    private String errorMessage;
    
    /**
     * 처리 시간 (밀리초)
     */
    private long processingTimeMs;
    
    /**
     * 추출 완료 시각
     */
    private LocalDateTime extractedAt;
    
    /**
     * URL 메타데이터
     */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UrlMetadata {
        /**
         * 웹사이트 도메인
         */
        private String domain;
        
        /**
         * 페이지 설명
         */
        private String description;
        
        /**
         * 대표 이미지 URL
         */
        private String imageUrl;
        
        /**
         * 작성자
         */
        private String author;
        
        /**
         * 게시일
         */
        private LocalDateTime publishedAt;
        
        /**
         * 언어
         */
        private String language;
        
        /**
         * 추가 메타 태그들
         */
        private Map<String, String> additionalMeta;
        
        /**
         * 본문 길이
         */
        private int contentLength;
        
        /**
         * 이미지 개수
         */
        private int imageCount;
        
        /**
         * 링크 개수
         */
        private int linkCount;
    }
    
    /**
     * 처리 상태
     */
    public enum ProcessingStatus {
        SUCCESS,        // 성공적으로 추출됨
        PARTIAL_SUCCESS, // 일부만 추출됨
        FAILED,         // 추출 실패
        TIMEOUT,        // 타임아웃
        BLOCKED,        // 접근 차단됨
        INVALID_URL     // 잘못된 URL
    }
    
    /**
     * 성공 여부 확인
     */
    public boolean isSuccess() {
        return status == ProcessingStatus.SUCCESS || status == ProcessingStatus.PARTIAL_SUCCESS;
    }
    
    /**
     * 유효한 내용 확인
     */
    public boolean hasValidContent() {
        return title != null && !title.trim().isEmpty() && 
               content != null && content.length() >= 50;
    }
}