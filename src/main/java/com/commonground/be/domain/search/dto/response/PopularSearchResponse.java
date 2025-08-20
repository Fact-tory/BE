package com.commonground.be.domain.search.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PopularSearchResponse {
    private List<SearchRankItem> searches;
    private int totalCount;
    private int limit;
    private LocalDateTime lastUpdated;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SearchRankItem {
        private int rank;
        private String term;
        private long count;
        private String trend; // "up", "down", "same"
        private Integer change; // 순위 변화량 (실시간 검색어용)
    }
    
    public static PopularSearchResponse of(List<SearchRankItem> searches, int limit) {
        return PopularSearchResponse.builder()
                .searches(searches)
                .totalCount(searches.size())
                .limit(limit)
                .lastUpdated(LocalDateTime.now())
                .build();
    }
}