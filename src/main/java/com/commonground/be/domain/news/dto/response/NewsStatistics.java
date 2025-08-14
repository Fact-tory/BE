package com.commonground.be.domain.news.dto.response;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NewsStatistics {

	private Long totalNews;
	private Long politicsCount;
	private Long economyCount;
	private Long societyCount;
	private Long cultureCount;
	private LocalDateTime lastCrawledAt;
	private LocalDateTime lastUpdated;
}