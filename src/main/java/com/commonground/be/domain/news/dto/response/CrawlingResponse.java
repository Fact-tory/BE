package com.commonground.be.domain.news.dto.response;

import java.time.Duration;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CrawlingResponse {

	private Integer totalCrawled;
	private Integer successCount;
	private Integer failCount;
	private List<NewsResponse> successfulNews;
	private List<String> errors;
	private Duration crawlingDuration;
	private String sessionId;
}