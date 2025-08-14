package com.commonground.be.domain.news.dto.request;

import com.commonground.be.domain.news.enums.NewsStatusEnum;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateNewsRequest {

	private String title;
	private String content;
	private String summary;
	private List<String> keywords;
	private NewsStatusEnum status;
}
