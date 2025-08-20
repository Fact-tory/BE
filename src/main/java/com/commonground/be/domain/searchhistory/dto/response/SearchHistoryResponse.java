package com.commonground.be.domain.searchhistory.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SearchHistoryResponse {
    
    private List<SearchHistoryItem> history;
    private PageInfo pageInfo;
    private SearchStats stats;
    
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SearchHistoryItem {
        private Long id;
        private String query;
        private LocalDateTime searchedAt;
        private Integer resultCount;
        private String category;
        private String filters;
        
        public SearchHistoryItem(String query, LocalDateTime searchedAt, Integer resultCount, String category, String filters) {
            this.query = query;
            this.searchedAt = searchedAt;
            this.resultCount = resultCount;
            this.category = category;
            this.filters = filters;
        }
    }
    
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PageInfo {
        private int currentPage;
        private int totalPages;
        private int totalElements;
        private int size;
        private boolean hasNext;
        private boolean hasPrevious;
    }
    
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SearchStats {
        private long totalSearches;
        private long todaySearches;
        private long thisWeekSearches;
        private long thisMonthSearches;
        private List<String> topQueries;
        private List<String> topCategories;
    }
    
    public static SearchHistoryResponse of(List<SearchHistoryItem> history, PageInfo pageInfo, SearchStats stats) {
        return SearchHistoryResponse.builder()
                .history(history)
                .pageInfo(pageInfo)
                .stats(stats)
                .build();
    }
}