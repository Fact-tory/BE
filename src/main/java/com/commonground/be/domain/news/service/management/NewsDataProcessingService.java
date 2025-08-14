package com.commonground.be.domain.news.service.management;

import com.commonground.be.domain.news.dto.crawling.RawNewsData;
import com.commonground.be.domain.news.entity.News;
import com.commonground.be.domain.news.enums.CategoryEnum;
import com.commonground.be.domain.news.enums.CrawlingSourceEnum;
import com.commonground.be.domain.news.repository.NewsRepository;
import com.commonground.be.global.application.aop.LogExecutionTime;
import com.commonground.be.global.application.aop.LogMethodCall;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 📝 뉴스 데이터 변환 서비스
 * 
 * 책임:
 * - Python 크롤러로부터 받은 정제된 RawNewsData를 News 엔티티로 변환
 * - 중복 뉴스 체크 (DB 레벨)
 * - 카테고리 매핑
 * - 소스 타입 매핑
 * 
 * 주의: Python 크롤러에서 이미 모든 데이터 정제가 완료되므로 추가 정제 없음
 */
@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class NewsDataProcessingService {

    private final NewsRepository newsRepository;
    private final NewsManagementService newsManagementService;

    // ==================== 메인 처리 메서드 ====================

    /**
     * Python 크롤러로부터 받은 정제된 RawNewsData를 News 엔티티로 변환
     * Python에서 이미 모든 데이터 정제가 완료되므로 단순 변환만 수행
     */
    @LogExecutionTime
    public News processRawNewsData(RawNewsData rawData) {
        log.debug("Python 크롤러 결과 변환 시작: title={}", 
                rawData.getTitle() != null ? 
                        rawData.getTitle().substring(0, Math.min(50, rawData.getTitle().length())) + "..." 
                        : "null");

        // Python에서 이미 정제된 데이터이므로 기본 유효성만 체크
        if (rawData.getTitle() == null || rawData.getTitle().trim().isEmpty()) {
            log.warn("필수 데이터 누락으로 처리 중단: title missing");
            return null;
        }

        // 중복 체크 (DB 레벨)
        if (isDuplicateNews(rawData)) {
            log.info("중복 뉴스 발견, 처리 중단: title={}", rawData.getTitle());
            return null;
        }

        // MediaOutlet 및 Journalist 생성/조회 (Python에서 정제된 데이터로)
        var mediaOutlet = newsManagementService.findOrCreateMediaOutlet(rawData);
        var journalist = newsManagementService.findOrCreateJournalist(rawData, mediaOutlet);

        // Python에서 정제된 데이터를 News 엔티티로 직접 변환
        News news = News.builder()
                .title(rawData.getTitle())  // Python에서 이미 정제됨
                .content(rawData.getContent())  // Python에서 이미 정제됨
                .url(rawData.getUrl())
                .authorName(rawData.getAuthorName())  // Python에서 이미 추출됨
                .publishedAt(rawData.getPublishedAt())
                .crawledAt(rawData.getDiscoveredAt())
                .category(mapNaverCategoryToEnum(rawData.getCategoryId()))
                .mediaOutletId(mediaOutlet.getId())
                .journalistId(journalist.getId())
                .crawlingSource(mapSourceToCrawlingSourceEnum(rawData.getSource()))
                .crawlingPlatform(extractMediaNameFromRawData(rawData))
                .viewCount(0L)
                .build();

        News savedNews = newsRepository.save(news);
        
        // 후처리 작업 (비동기)
        newsManagementService.processNewsAfterSave(savedNews);

        log.info("Python 크롤러 결과 변환 완료: id={}, title={}", 
                savedNews.getId(), 
                savedNews.getTitle().substring(0, Math.min(50, savedNews.getTitle().length())) + "...");
        
        return savedNews;
    }

    // ==================== 중복 체크 ====================

    /**
     * 중복 뉴스 체크 (원문 링크 > 제목+기자+카테고리 조합으로 비교)
     * 중요한 필드가 누락된 경우에는 중복 체크를 건너뛰어 오버랩 허용
     */
    public boolean isDuplicateNews(RawNewsData rawData) {
        // 기본 필수 필드 검증 - 하나라도 없으면 중복 체크 건너뛰기 (데이터 품질 우선)
        if (rawData.getTitle() == null || rawData.getTitle().trim().isEmpty()) {
            log.debug("⚠️ 제목 누락으로 중복 체크 건너뛰기: url={}", rawData.getUrl());
            return false; // 중복이 아닌 것으로 처리하여 저장 허용
        }
        
        // 1차: 원문 링크로 중복 체크 (가장 정확한 방법)
        if (rawData.getOriginalUrl() != null && !rawData.getOriginalUrl().trim().isEmpty()) {
            boolean existsByOriginalUrl = newsRepository.existsByOriginalUrl(rawData.getOriginalUrl());
            if (existsByOriginalUrl) {
                log.debug("중복 감지 (원문 URL): {}", rawData.getOriginalUrl());
                return true;
            }
        }
        
        // 2차: 일반 URL로 중복 체크 (fallback)
        if (rawData.getUrl() != null && !rawData.getUrl().trim().isEmpty()) {
            boolean existsByUrl = newsRepository.existsByUrl(rawData.getUrl());
            if (existsByUrl) {
                log.debug("중복 감지 (일반 URL): {}", rawData.getUrl());
                return true;
            }
        }

        // 3차: 제목 + 기자 + 카테고리 조합으로 중복 체크 (모든 필드가 있는 경우에만)
        if (rawData.getAuthorName() != null && !rawData.getAuthorName().trim().isEmpty() &&
            rawData.getCategoryId() != null) {
            
            CategoryEnum category = mapNaverCategoryToEnum(rawData.getCategoryId());
            boolean existsByTitleAuthorCategory = newsRepository.existsByTitleAndAuthorNameAndCategory(
                    rawData.getTitle().trim(), 
                    rawData.getAuthorName().trim(), 
                    category);
            
            if (existsByTitleAuthorCategory) {
                log.debug("중복 감지 (제목+기자+카테고리): title={}, author={}, category={}", 
                        rawData.getTitle().substring(0, Math.min(30, rawData.getTitle().length())) + "...",
                        rawData.getAuthorName(),
                        category);
                return true;
            }
        } else {
            log.debug("⚠️ 기자명 또는 카테고리 누락으로 3차 중복 체크 건너뛰기: title={}, author={}, categoryId={}", 
                    rawData.getTitle() != null ? rawData.getTitle().substring(0, Math.min(30, rawData.getTitle().length())) + "..." : "null",
                    rawData.getAuthorName(), 
                    rawData.getCategoryId());
        }

        return false;
    }

    // ==================== 데이터 보완 ====================


    // ==================== 카테고리 매핑 ====================

    /**
     * 네이버 카테고리 ID를 시스템 CategoryEnum으로 변환
     */
    public CategoryEnum mapNaverCategoryToEnum(String naverCategoryId) {
        if (naverCategoryId == null) {
            log.warn("카테고리 ID가 null입니다. 기본값 SOCIETY로 설정합니다.");
            return CategoryEnum.SOCIETY;
        }

        return switch (naverCategoryId) {
            case "100" -> CategoryEnum.POLITICS;
            case "101" -> CategoryEnum.ECONOMY;
            case "102" -> CategoryEnum.SOCIETY;
            case "103" -> CategoryEnum.CULTURE;
            default -> {
                log.warn("알 수 없는 카테고리 ID: {}. 기본값 SOCIETY로 설정합니다.", naverCategoryId);
                yield CategoryEnum.SOCIETY;
            }
        };
    }

    /**
     * RawNewsData의 source를 CrawlingSourceEnum으로 변환
     */
    public CrawlingSourceEnum mapSourceToCrawlingSourceEnum(String source) {
        if (source == null) {
            log.warn("크롤링 소스가 null입니다. 기본값 SCRAPING으로 설정합니다.");
            return CrawlingSourceEnum.SCRAPING;
        }

        return switch (source.toLowerCase()) {
            case "naver_crawler", "naver_news" -> CrawlingSourceEnum.NAVER_NEWS;
            case "api" -> CrawlingSourceEnum.API;
            case "rss" -> CrawlingSourceEnum.RSS;
            case "sitemap" -> CrawlingSourceEnum.SITEMAP;
            case "manual" -> CrawlingSourceEnum.MANUAL;
            default -> {
                log.debug("알 수 없는 크롤링 소스: {}. 기본값 SCRAPING으로 설정합니다.", source);
                yield CrawlingSourceEnum.SCRAPING;
            }
        };
    }

    /**
     * RawNewsData의 metadata에서 언론사명(mediaName) 추출
     */
    private String extractMediaNameFromRawData(RawNewsData rawData) {
        if (rawData.getMetadata() == null) {
            log.debug("RawNewsData metadata가 null입니다.");
            return null;
        }

        Object mediaNameObj = rawData.getMetadata().get("mediaName");
        if (mediaNameObj instanceof String mediaName) {
            if (mediaName != null && !mediaName.trim().isEmpty()) {
                log.debug("언론사명 추출 성공: {}", mediaName);
                return mediaName.trim();
            }
        }

        log.debug("metadata에서 언론사명을 찾을 수 없습니다: {}", rawData.getMetadata());
        return null;
    }
}