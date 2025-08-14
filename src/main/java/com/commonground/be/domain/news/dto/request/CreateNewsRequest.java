package com.commonground.be.domain.news.dto.request;

import com.commonground.be.domain.news.enums.CategoryEnum;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.validator.constraints.URL;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateNewsRequest {

	@NotBlank(message = "제목은 필수입니다")
	private String title;

	@NotBlank(message = "내용은 필수입니다")
	private String content;

	@NotBlank(message = "URL은 필수입니다")
	@URL(message = "올바른 URL 형식이 아닙니다")
	private String url;

	@NotBlank(message = "작성자명은 필수입니다")
	private String authorName;

	@NotNull(message = "발행일시는 필수입니다")
	private LocalDateTime publishedAt;

	@NotNull(message = "카테고리는 필수입니다")
	private CategoryEnum category;

	private String mediaOutletId;
	private String journalistId;
	private String summary;
	private List<String> keywords;
}