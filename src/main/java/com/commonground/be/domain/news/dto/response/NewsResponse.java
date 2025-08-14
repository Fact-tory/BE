package com.commonground.be.domain.news.dto.response;

import com.commonground.be.domain.news.entity.News;
import com.commonground.be.domain.news.enums.CategoryEnum;
import com.commonground.be.domain.news.enums.CrawlingSourceEnum;
import com.commonground.be.domain.news.enums.NewsStatusEnum;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class NewsResponse {

	private String id;
	private String title;
	private String content;
	private String url;
	private String authorName;
	private LocalDateTime publishedAt;
	private LocalDateTime crawledAt;
	private CategoryEnum category;
	private String categoryName;
	private String mediaOutletId;
	private String mediaOutletName;
	private String journalistId;
	private String journalistName;
	private String summary;
	private List<String> keywords;
	private Long viewCount;
	private CrawlingSourceEnum crawlingSource;
	private String crawlingPlatform;
	private NewsStatusEnum status;
	private LocalDateTime createdAt;
	private LocalDateTime updatedAt;

	public static NewsResponse from(News news) {
		return NewsResponse.builder()
				.id(news.getId())
				.title(news.getTitle())
				.content(news.getContent())
				.url(news.getUrl())
				.authorName(news.getAuthorName())
				.publishedAt(news.getPublishedAt())
				.crawledAt(news.getCrawledAt())
				.category(news.getCategory())
				.categoryName(news.getCategory().getKoreanName())
				.mediaOutletId(news.getMediaOutletId())
				.journalistId(news.getJournalistId())
				.summary(news.getSummary())
				.keywords(news.getKeywords())
				.viewCount(news.getViewCount())
				.crawlingSource(news.getCrawlingSource())
				.crawlingPlatform(news.getCrawlingPlatform())
				.status(news.getStatus())
				.createdAt(news.getCreatedAt())
				.updatedAt(news.getUpdatedAt())
				.build();
	}

	public static NewsResponse summary(News news) {
		return NewsResponse.builder()
				.id(news.getId())
				.title(news.getTitle())
				.url(news.getUrl())
				.authorName(news.getAuthorName())
				.publishedAt(news.getPublishedAt())
				.category(news.getCategory())
				.categoryName(news.getCategory().getKoreanName())
				.summary(news.getSummary())
				.viewCount(news.getViewCount())
				.build();
	}
}