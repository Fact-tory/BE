package com.commonground.be.domain.news.service;

import com.commonground.be.domain.news.dto.request.CreateNewsRequest;
import com.commonground.be.domain.news.dto.request.NaverCrawlingRequest;
import com.commonground.be.domain.news.dto.request.UpdateNewsRequest;
import com.commonground.be.domain.news.dto.response.CategoryStatistics;
import com.commonground.be.domain.news.dto.response.NewsStatistics;
import com.commonground.be.domain.news.dto.crawling.RawNewsData;
import com.commonground.be.domain.news.entity.News;
import com.commonground.be.domain.news.enums.CategoryEnum;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public interface NewsService {
    
    // 뉴스 CRUD
    News createNews(CreateNewsRequest request);
    Optional<News> findNewsById(String id);
    News updateNews(String id, UpdateNewsRequest request);
    void deleteNews(String id);
    
    // 뉴스 조회
    List<News> findNewsByCategory(CategoryEnum category, int page, int limit);
    List<News> findRecentNews(int limit);
    List<News> findTrendingNews(int limit);
    List<News> searchNews(String keyword, int page, int limit);
    
    // 크롤링 관련
    CompletableFuture<List<News>> collectFromNaverApi(NaverCrawlingRequest request);
    CompletableFuture<List<News>> crawlNaverNews(NaverCrawlingRequest request);
    News processRawNewsData(RawNewsData rawData);
    
    // 통계
    NewsStatistics getNewsStatistics();
    CategoryStatistics getCategoryStatistics();
    
    // 뷰 카운트
    void incrementViewCount(String newsId);
}
