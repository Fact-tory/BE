package com.commonground.be.domain.dashboard.dto.response;

import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Getter
public class MainDashboardResponse {
    
    private final RealtimeNewsSection realtime;
    private final TrendingNewsSection trending;
    private final Map<String, List<Object>> categories;
    private final List<String> popularSearches;
    private final LocalDateTime lastUpdated;

    public MainDashboardResponse(
            List<Object> realtimeNews,
            List<Object> trendingNews,
            Map<String, List<Object>> categoryNews,
            List<String> popularSearches
    ) {
        this.realtime = new RealtimeNewsSection(realtimeNews);
        this.trending = new TrendingNewsSection(trendingNews);
        this.categories = categoryNews;
        this.popularSearches = popularSearches;
        this.lastUpdated = LocalDateTime.now();
    }

    @Getter
    public static class RealtimeNewsSection {
        private final List<Object> articles;
        private final LocalDateTime lastUpdated;

        public RealtimeNewsSection(List<Object> articles) {
            this.articles = articles;
            this.lastUpdated = LocalDateTime.now();
        }
    }

    @Getter
    public static class TrendingNewsSection {
        private final List<Object> articles;
        private final LocalDateTime lastUpdated;

        public TrendingNewsSection(List<Object> articles) {
            this.articles = articles;
            this.lastUpdated = LocalDateTime.now();
        }
    }
}