package com.commonground.be.domain.news.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NaverCrawlingRequest {

	@NotBlank(message = "언론사 ID는 필수입니다")
	private String officeId;

	@NotBlank(message = "카테고리 ID는 필수입니다")
	private String categoryId;

	@Min(value = 1, message = "최소 1개 이상 크롤링해야 합니다")
	@Max(value = 100, message = "최대 100개까지 크롤링 가능합니다")
	private final Integer maxArticles = 50;

	@Min(value = 5, message = "최소 5회 이상 스크롤해야 합니다")
	@Max(value = 50, message = "최대 50회까지 스크롤 가능합니다")
	private final Integer maxScrollAttempts = 20;

	private String sessionId;
}