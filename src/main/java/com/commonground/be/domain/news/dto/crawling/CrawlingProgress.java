package com.commonground.be.domain.news.dto.crawling;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CrawlingProgress {

	private String sessionId;
	private String step;
	private Integer progress;      // 0-100
	private String message;
	private Integer totalArticles;
	private Integer processedArticles;
	private Integer successCount;
	private Integer failCount;
	private LocalDateTime timestamp;
	
	// 기존 필드명과 호환성을 위한 getter 메서드들
	public String getStatus() {
		return step;
	}
	
	public Integer getTotalUrls() {
		return totalArticles;
	}
	
	public Integer getCrawledUrls() {
		return processedArticles;
	}
	
	public Integer getSuccessUrls() {
		return successCount;
	}
	
	public Integer getFailedUrls() {
		return failCount;
	}
}