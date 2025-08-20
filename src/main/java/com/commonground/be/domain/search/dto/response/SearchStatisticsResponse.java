package com.commonground.be.domain.search.dto.response;

import lombok.Getter;

import java.time.LocalDateTime;
import java.util.Map;

@Getter
public class SearchStatisticsResponse {
    
    private final GeneralStats general;
    private final Map<String, Long> categoryStats;
    private final Map<String, Long> timeRangeStats;
    private final LocalDateTime generatedAt;

    public SearchStatisticsResponse(
            GeneralStats general,
            Map<String, Long> categoryStats,
            Map<String, Long> timeRangeStats
    ) {
        this.general = general;
        this.categoryStats = categoryStats;
        this.timeRangeStats = timeRangeStats;
        this.generatedAt = LocalDateTime.now();
    }

    @Getter
    public static class GeneralStats {
        private final long totalSearches;
        private final long uniqueQueries;
        private final long todaySearches;
        private final long thisWeekSearches;
        private final double averageResultsPerSearch;

        public GeneralStats(long totalSearches, long uniqueQueries, long todaySearches, 
                           long thisWeekSearches, double averageResultsPerSearch) {
            this.totalSearches = totalSearches;
            this.uniqueQueries = uniqueQueries;
            this.todaySearches = todaySearches;
            this.thisWeekSearches = thisWeekSearches;
            this.averageResultsPerSearch = averageResultsPerSearch;
        }
    }
}