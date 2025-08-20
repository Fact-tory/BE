package com.commonground.be.domain.dashboard.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class CategoryNewsService {

    public Map<String, List<Object>> getCategoryNews(int limit) {
        log.info("카테고리별 뉴스 조회 - limit: {}", limit);

        Map<String, List<Object>> categoryNews = new HashMap<>();
        
        // 각 카테고리별로 Mock 뉴스 데이터 생성
        categoryNews.put("POLITICS", generateMockNews("정치", limit));
        categoryNews.put("ECONOMY", generateMockNews("경제", limit));
        categoryNews.put("SOCIETY", generateMockNews("사회", limit));
        categoryNews.put("CULTURE", generateMockNews("문화", limit));
        categoryNews.put("INTERNATIONAL", generateMockNews("국제", limit));
        
        return categoryNews;
    }

    private List<Object> generateMockNews(String category, int limit) {
        List<Object> newsList = new ArrayList<>();
        Random random = new Random();
        
        String[] titleTemplates = {
            category + " 분야 주요 소식",
            category + " 관련 정책 발표",
            category + " 부문 신규 동향",
            category + " 분야 전문가 의견",
            category + " 관련 주요 이슈"
        };
        
        for (int i = 0; i < Math.min(limit, 5); i++) {
            Map<String, Object> news = new HashMap<>();
            news.put("id", "news_" + category.toLowerCase() + "_" + (i + 1));
            news.put("title", titleTemplates[i % titleTemplates.length]);
            news.put("summary", category + " 분야의 최신 동향을 다룬 기사입니다.");
            news.put("category", category);
            news.put("publishedAt", LocalDateTime.now().minusHours(random.nextInt(24)));
            news.put("viewCount", 100 + random.nextInt(900));
            
            newsList.add(news);
        }
        
        return newsList;
    }
}