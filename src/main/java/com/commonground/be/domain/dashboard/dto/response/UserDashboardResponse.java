package com.commonground.be.domain.dashboard.dto.response;

import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
public class UserDashboardResponse {
    
    private final List<AnalysisItem> myAnalyses;
    private final List<SearchHistoryItem> recentSearches;
    private final UserStats stats;
    private final LocalDateTime lastUpdated;

    public UserDashboardResponse(
            List<AnalysisItem> myAnalyses,
            List<SearchHistoryItem> recentSearches,
            UserStats stats
    ) {
        this.myAnalyses = myAnalyses;
        this.recentSearches = recentSearches;
        this.stats = stats;
        this.lastUpdated = LocalDateTime.now();
    }

    @Getter
    public static class AnalysisItem {
        private final Long analysisId;
        private final String title;
        private final String status;
        private final LocalDateTime createdAt;

        public AnalysisItem(Long analysisId, String title, String status, LocalDateTime createdAt) {
            this.analysisId = analysisId;
            this.title = title;
            this.status = status;
            this.createdAt = createdAt;
        }
    }


    @Getter
    public static class SearchHistoryItem {
        private final String query;
        private final LocalDateTime searchedAt;
        private final int resultCount;

        public SearchHistoryItem(String query, LocalDateTime searchedAt, int resultCount) {
            this.query = query;
            this.searchedAt = searchedAt;
            this.resultCount = resultCount;
        }
    }

    @Getter
    public static class UserStats {
        private final int totalAnalyses;
        private final int completedAnalyses;
        private final int totalSearches;

        public UserStats(int totalAnalyses, int completedAnalyses, int totalSearches) {
            this.totalAnalyses = totalAnalyses;
            this.completedAnalyses = completedAnalyses;
            this.totalSearches = totalSearches;
        }
    }
}