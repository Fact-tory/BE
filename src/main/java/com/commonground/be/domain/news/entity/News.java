package com.commonground.be.domain.news.entity;

import com.commonground.be.domain.news.entity.metadata.CrawlingMetadata;
import com.commonground.be.domain.news.enums.CategoryEnum;
import com.commonground.be.domain.news.enums.CrawlingSourceEnum;
import com.commonground.be.domain.news.enums.NewsStatusEnum;
import jakarta.validation.constraints.NotBlank;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "news")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class News {

	@Id
	private String id;

	// 기본 정보
	@NotBlank
	@Indexed
	private String title;

	@NotBlank
	private String content;

	@NotBlank
	@Indexed(unique = true)
	private String url;

	// 발행 정보
	@NotBlank
	private String authorName;

	@Indexed
	private LocalDateTime publishedAt;

	@Indexed
	private LocalDateTime crawledAt;

	// 분류 정보
	@Indexed
	private CategoryEnum category;

	@Indexed
	private String mediaOutletId;

	private String journalistId;

	// AI 분석 준비 정보
	private String summary;
	private List<String> keywords;

	// 메타 정보
	@Builder.Default
	private Long viewCount = 0L;

	@Indexed
	private CrawlingSourceEnum crawlingSource;

	private String crawlingPlatform;

	@Indexed
	@Builder.Default
	private NewsStatusEnum status = NewsStatusEnum.PUBLISHED;

	// 크롤링 원본 정보 (디버깅용)
	private CrawlingMetadata crawlingMetadata;

	@CreatedDate
	private LocalDateTime createdAt;

	@LastModifiedDate
	private LocalDateTime updatedAt;

	private LocalDateTime deletedAt;

	// 도메인 메서드
	public void incrementViewCount() {
		this.viewCount++;
	}

	public void markAsDeleted() {
		this.deletedAt = LocalDateTime.now();
		this.status = NewsStatusEnum.DELETED;
	}

	public boolean isValid() {
		// 기본 필수 필드 검증
		if (title == null || title.trim().isEmpty()) {
			return false;
		}
		
		if (content == null || content.trim().length() < 20) { // 50자에서 20자로 완화
			return false;
		}
		
		if (url == null || url.trim().isEmpty()) {
			return false;
		}
		
		// URL 유효성 검증 - 블랙리스트 방식
		String urlLower = url.toLowerCase();
		
		// 제외할 도메인들 (스팸, 광고 등)
		String[] blacklistDomains = {
			"youtube.com", "youtu.be",
			"facebook.com", "fb.com",
			"instagram.com", "insta.com",
			"twitter.com", "x.com",
			"tiktok.com",
			"ad.naver.com",
			"shopping.naver.com"
		};
		
		for (String blackDomain : blacklistDomains) {
			if (urlLower.contains(blackDomain)) {
				return false;
			}
		}
		
		// 기본적인 URL 형식 검증
		return urlLower.startsWith("http://") || urlLower.startsWith("https://");
	}

	public void updateSummary(String summary, List<String> keywords) {
		this.summary = summary;
		this.keywords = keywords;
	}
	
	public void updateContent(String title, String content, String summary, List<String> keywords) {
		if (title != null) {
			this.title = title;
		}
		if (content != null) {
			this.content = content;
		}
		if (summary != null) {
			this.summary = summary;
		}
		if (keywords != null) {
			this.keywords = keywords;
		}
		this.updatedAt = LocalDateTime.now();
	}
	
	public void updateStatus(NewsStatusEnum status) {
		this.status = status;
		this.updatedAt = LocalDateTime.now();
	}
	
	public void assignId(String id) {
		this.id = id;
	}
}