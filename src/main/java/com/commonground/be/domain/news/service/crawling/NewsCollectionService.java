package com.commonground.be.domain.news.service.crawling;

import com.commonground.be.domain.news.dto.crawling.RawNewsData;
import com.commonground.be.domain.news.dto.request.NaverCrawlingRequest;
import com.commonground.be.domain.news.entity.News;
import com.commonground.be.domain.news.service.management.NewsDataProcessingService;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * 🔄 뉴스 수집 서비스
 * 
 * 책임:
 * - 네이버 뉴스 수집 (API + 웹크롤링)
 * - 수집된 데이터 통합 및 변환
 * - 크롤링 전략 관리
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NewsCollectionService {

    private final NaverNewsApiService naverNewsApiService;
    private final CrawlingOrchestrationService crawlingOrchestrationService;
    private final NewsDataProcessingService newsDataProcessingService;

    // ==================== 네이버 뉴스 API 수집 ====================

    /**
     * 네이버 뉴스 API 전용 데이터 수집 메서드
     */
    @Async
    public CompletableFuture<List<News>> collectFromNaverApi(NaverCrawlingRequest request) {
        log.info("📡 네이버 API 데이터 수집 시작: officeId={}, categoryId={}", 
                request.getOfficeId(), request.getCategoryId());

        try {
            List<RawNewsData> rawDataList = collectFromNewsApi(request);
            
            List<News> newsList = rawDataList.stream()
                    .map(newsDataProcessingService::processRawNewsData)
                    .filter(news -> news != null)
                    .toList();

            log.info("✅ 네이버 API 데이터 수집 완료: {}개 -> {}개 저장", rawDataList.size(), newsList.size());
            return CompletableFuture.completedFuture(newsList);

        } catch (Exception e) {
            log.error("❌ 네이버 API 데이터 수집 실패", e);
            return CompletableFuture.completedFuture(new ArrayList<>());
        }
    }

    // ==================== 웹 크롤링 수집 ====================

    /**
     * 하이브리드 크롤러 (Python) 전용 뉴스 수집 메서드
     */
    @Async
    public CompletableFuture<List<News>> crawlNaverNews(NaverCrawlingRequest request) {
        log.info("🐍 Python 하이브리드 크롤링 시작: officeId={}, categoryId={}", 
                request.getOfficeId(), request.getCategoryId());

        try {
            // CrawlingOrchestrationService를 통한 Python 크롤링
            CompletableFuture<List<RawNewsData>> crawlingFuture = crawlingOrchestrationService.orchestrateCrawling(request);
            
            return crawlingFuture.thenApply(rawDataList -> {
                List<News> newsList = rawDataList.stream()
                        .map(newsDataProcessingService::processRawNewsData)
                        .filter(news -> news != null)
                        .toList();

                log.info("✅ Python 하이브리드 크롤링 완료: {}개 -> {}개 저장", rawDataList.size(), newsList.size());
                return newsList;
            });

        } catch (Exception e) {
            log.error("❌ Python 하이브리드 크롤링 실패", e);
            return CompletableFuture.completedFuture(new ArrayList<>());
        }
    }

    // ==================== Private 메서드 ====================

    /**
     * 네이버 News API로부터 RSS 데이터 수집
     */
    private List<RawNewsData> collectFromNewsApi(NaverCrawlingRequest request) {
        // 언론사 ID로부터 검색 키워드 생성
        String searchQuery = generateSearchQuery(request.getOfficeId(), request.getCategoryId());

        if (searchQuery != null && naverNewsApiService.canUseApi(request.getMaxArticles())) {
            log.info("📡 네이버 API 사용: query={}", searchQuery);
            List<RawNewsData> apiData = naverNewsApiService.searchNewsFromApi(searchQuery,
                    request.getMaxArticles(), 1);

            // API 데이터 후처리: categoryId, officeId 설정 및 네이버 URL에서 기자명 추출
            int authorNameCount = 0;
            for (RawNewsData data : apiData) {
                data.setCategoryId(request.getCategoryId());
                data.setOfficeId(request.getOfficeId());

                // API 데이터의 경우 기자명이 누락될 수 있음 (Python 크롤러에서 처리됨)
                if ((data.getAuthorName() == null || data.getAuthorName().trim().isEmpty())
                        && data.getUrl() != null && data.getUrl().contains("n.news.naver.com")) {

                    log.debug("⚠️ API 기사에서 기자명 누락: url={} (Python 크롤링 사용 권장)", data.getUrl());
                    // 개별 URL 기자명 추출은 Python 크롤러에서 처리 - API만으로는 한계가 있음
                }

                if (data.getAuthorName() != null && !data.getAuthorName().trim().isEmpty()) {
                    authorNameCount++;
                    log.debug("📝 API 기사에서 기자명 확인: title={}, authorName={}",
                            data.getTitle() != null ? data.getTitle()
                                    .substring(0, Math.min(50, data.getTitle().length())) + "..."
                                    : "null",
                            data.getAuthorName());
                } else {
                    log.debug("⚠️ API 기사에서 기자명 없음: title={}, url={}",
                            data.getTitle() != null ? data.getTitle()
                                    .substring(0, Math.min(50, data.getTitle().length())) + "..."
                                    : "null",
                            data.getUrl());
                }
            }

            log.info("📊 API 데이터 분석: categoryId={}, 기사 수={}, 기자명 있음={}/{}개 ({}%)",
                    request.getCategoryId(), apiData.size(), authorNameCount, apiData.size(),
                    apiData.size() > 0 ? Math.round((double) authorNameCount / apiData.size() * 100)
                            : 0);

            return apiData;
        } else {
            log.info("⚠️ 네이버 API 건너뜀 (설정 없음 또는 제한 초과)");
            return new ArrayList<>();
        }
    }

    /**
     * 언론사 ID로부터 검색 키워드 생성
     */
    private String generateSearchQuery(String officeId, String categoryId) {
        // 언론사 ID에서 언론사명 매핑
        Map<String, String> mediaNames = Map.of(
                "023", "조선일보",
                "025", "중앙일보",
                "028", "한겨레",
                "047", "오마이뉴스",
                "469", "한국일보"
        );

        return mediaNames.get(officeId);
    }
}