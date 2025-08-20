package com.commonground.be.domain.search.dto.response;

import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
public class SearchHistoryResponse {
    
    private final List<SearchHistoryItem> history;
    private final PageInfo pageInfo;

    public SearchHistoryResponse(List<SearchHistoryItem> history, PageInfo pageInfo) {
        this.history = history;
        this.pageInfo = pageInfo;
    }

    @Getter
    public static class SearchHistoryItem {
        private final String query;
        private final LocalDateTime searchedAt;
        private final int resultCount;
        private final String category;
        private final String filters;

        public SearchHistoryItem(String query, LocalDateTime searchedAt, int resultCount, 
                               String category, String filters) {
            this.query = query;
            this.searchedAt = searchedAt;
            this.resultCount = resultCount;
            this.category = category;
            this.filters = filters;
        }
    }

    @Getter
    public static class PageInfo {
        private final int currentPage;
        private final int totalPages;
        private final long totalElements;
        private final int size;
        private final boolean hasNext;
        private final boolean hasPrevious;

        public PageInfo(int currentPage, int totalPages, long totalElements, int size, 
                       boolean hasNext, boolean hasPrevious) {
            this.currentPage = currentPage;
            this.totalPages = totalPages;
            this.totalElements = totalElements;
            this.size = size;
            this.hasNext = hasNext;
            this.hasPrevious = hasPrevious;
        }
    }
}