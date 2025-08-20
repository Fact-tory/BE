package com.commonground.be.domain.search.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SearchResponse {
    private List<Object> articles;
    private long totalHits;
    private int page;
    private int size;
    private boolean hasNext;
    private String query;
    private String category;
    private String bias;
    private String sort;
    private Map<String, Object> metadata;
    
    public static SearchResponse of(List<Object> articles, long totalHits, int page, int size, 
                                   boolean hasNext, String query, String category, String bias, 
                                   String sort, Map<String, Object> metadata) {
        return SearchResponse.builder()
                .articles(articles)
                .totalHits(totalHits)
                .page(page)
                .size(size)
                .hasNext(hasNext)
                .query(query)
                .category(category)
                .bias(bias)
                .sort(sort)
                .metadata(metadata)
                .build();
    }
}