package com.commonground.be.domain.news.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * 🔗 URL 기반 본문 추출 요청 DTO
 * 
 * 특정 URL의 웹페이지에서 본문 내용을 추출하는 요청
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UrlCrawlingRequest {
    
    /**
     * 세션 ID (자동 생성)
     */
    @Builder.Default
    private String sessionId = UUID.randomUUID().toString();
    
    /**
     * 추출할 대상 URL
     */
    @NotBlank(message = "URL은 필수입니다")
    @Pattern(regexp = "^https?://.*", message = "올바른 URL 형식이어야 합니다")
    private String url;
    
    /**
     * 사용자 정의 제목 (선택사항)
     */
    private String customTitle;
    
    /**
     * 본문 포함 여부
     */
    @Builder.Default
    private boolean includeContent = true;
    
    /**
     * 메타데이터 포함 여부 (이미지, 설명 등)
     */
    @Builder.Default
    private boolean includeMetadata = true;
    
    /**
     * 최소 본문 길이
     */
    @Builder.Default
    private int minContentLength = 100;
    
    /**
     * 타임아웃 (초)
     */
    @Builder.Default
    private int timeoutSeconds = 30;
    
    /**
     * 요청 우선순위 (기본: NORMAL)
     */
    @Builder.Default
    private CrawlingPriority priority = CrawlingPriority.NORMAL;
    
    /**
     * 크롤링 우선순위
     */
    public enum CrawlingPriority {
        LOW,    // 낮은 우선순위 (배치 처리)
        NORMAL, // 일반 우선순위 
        HIGH,   // 높은 우선순위 (실시간 처리)
        URGENT  // 긴급 우선순위 (즉시 처리)
    }
    
    /**
     * 도메인 추출
     */
    public String extractDomain() {
        if (url == null) return null;
        try {
            return url.replaceAll("^https?://([^/]+).*", "$1");
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * 요청 설명
     */
    public String getDescription() {
        String domain = extractDomain();
        return String.format("URL 크롤링: %s (%s)", 
            customTitle != null ? customTitle : domain, 
            priority.name());
    }
}