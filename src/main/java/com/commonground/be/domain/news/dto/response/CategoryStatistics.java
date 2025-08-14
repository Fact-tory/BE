package com.commonground.be.domain.news.dto.response;

import com.commonground.be.domain.news.enums.CategoryEnum;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CategoryStatistics {

	private Map<CategoryEnum, Long> categoryDistribution;
	private Map<CategoryEnum, Double> categoryPercentage;
	private CategoryEnum mostPopularCategory;
	private CategoryEnum leastPopularCategory;
}
