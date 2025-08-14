package com.commonground.be.domain.news.dto.crawling;

import java.time.LocalDateTime;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RawNewsData {

	private String url;
	private String title;
	private String content;
	private String authorName;
	private LocalDateTime publishedAt;
	private String officeId;        // 네이버 언론사 ID
	private String categoryId;      // 네이버 카테고리 ID
	private String articleId;       // 네이버 기사 ID
	private String originalUrl;     // 원문 링크
	private LocalDateTime discoveredAt;
	private Integer responseTime;
	@Builder.Default
	private String source = "naver_news";
	private Map<String, Object> metadata;
}
