package com.commonground.be.domain.search.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SearchRequest {
    private String query;
    @Builder.Default
    private int page = 1;
    @Builder.Default
    private int size = 20;
    private String category;
    private String bias;
    private String sort;
    
    public static SearchRequest of(String query, int page, int size, String category, String bias, String sort) {
        return SearchRequest.builder()
                .query(query)
                .page(page)
                .size(size)
                .category(category)
                .bias(bias)
                .sort(sort)
                .build();
    }
}