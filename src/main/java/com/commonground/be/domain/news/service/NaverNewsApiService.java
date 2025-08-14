package com.commonground.be.domain.news.service;

import com.commonground.be.domain.news.dto.crawling.RawNewsData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 네이버 뉴스 API를 통한 RSS 기반 뉴스 수집
 * 월 제한을 고려한 효율적 API 사용
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NaverNewsApiService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${naver.api.client-id:}")
    private String naverClientId;

    @Value("${naver.api.client-secret:}")
    private String naverClientSecret;

    @Value("${naver.api.daily-limit:1000}")
    private int dailyLimit;

    /**
     * 네이버 뉴스 검색 API를 통한 RSS 데이터 수집
     * 
     * @param query 검색 키워드 (언론사명 등)
     * @param display 가져올 뉴스 개수 (최대 100)
     * @param start 시작 위치 (1~1000)
     * @return RSS 기반 뉴스 데이터
     */
    public List<RawNewsData> searchNewsFromApi(String query, int display, int start) {
        
        if (!isApiConfigured()) {
            log.warn("네이버 API 설정이 없습니다. 크롤링만 사용됩니다.");
            return new ArrayList<>();
        }

        try {
            String url = String.format(
                "https://openapi.naver.com/v1/search/news.json?query=%s&display=%d&start=%d&sort=date",
                query, Math.min(display, 100), start);

            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Naver-Client-Id", naverClientId);
            headers.set("X-Naver-Client-Secret", naverClientSecret);

            HttpEntity<String> entity = new HttpEntity<>(headers);
            
            log.info("📡 네이버 뉴스 API 호출: query={}, display={}, start={}", query, display, start);
            
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            
            return parseApiResponse(response.getBody());
            
        } catch (Exception e) {
            log.error("네이버 뉴스 API 호출 실패: query={}", query, e);
            return new ArrayList<>();
        }
    }

    /**
     * API 응답 파싱
     */
    private List<RawNewsData> parseApiResponse(String responseBody) {
        List<RawNewsData> newsDataList = new ArrayList<>();
        
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode items = root.get("items");
            
            if (items != null && items.isArray()) {
                for (JsonNode item : items) {
                    String naverUrl = item.get("link").asText();
                    String originalUrl = item.get("originallink").asText();
                    
                    log.debug("📝 API 응답 URL 확인: naverUrl={}, originalUrl={}", naverUrl, originalUrl);
                    
                    RawNewsData newsData = RawNewsData.builder()
                            .title(cleanHtmlTags(item.get("title").asText()))
                            .content(cleanHtmlTags(item.get("description").asText()))
                            .url(naverUrl)  // 네이버 뉴스 URL을 메인 URL로 사용
                            .originalUrl(originalUrl)  // 원문 URL은 별도 보관
                            .publishedAt(parsePublishDate(item.get("pubDate").asText()))
                            .discoveredAt(LocalDateTime.now())
                            .source("naver_api")
                            .build();
                    
                    newsDataList.add(newsData);
                }
            }
            
            log.info("📊 네이버 API에서 {}개 뉴스 수집 완료", newsDataList.size());
            
        } catch (Exception e) {
            log.error("네이버 API 응답 파싱 실패", e);
        }
        
        return newsDataList;
    }

    /**
     * HTML 태그 제거
     */
    private String cleanHtmlTags(String text) {
        if (text == null) return null;
        return text.replaceAll("<[^>]*>", "").trim();
    }

    /**
     * 발행일 파싱 (RFC 2822 format)
     */
    private LocalDateTime parsePublishDate(String pubDateStr) {
        try {
            // "Tue, 19 May 2020 08:50:00 +0900" 형태
            DateTimeFormatter formatter = DateTimeFormatter.RFC_1123_DATE_TIME;
            return LocalDateTime.parse(pubDateStr, formatter);
        } catch (Exception e) {
            log.debug("발행일 파싱 실패: {}", pubDateStr);
            return LocalDateTime.now();
        }
    }

    /**
     * API 설정 여부 확인
     */
    private boolean isApiConfigured() {
        return naverClientId != null && !naverClientId.isEmpty() &&
               naverClientSecret != null && !naverClientSecret.isEmpty();
    }

    /**
     * 일일 제한량 대비 사용량 체크 (Redis로 카운팅)
     */
    public boolean canUseApi(int requestCount) {
        // TODO: Redis를 통한 일일 사용량 추적
        return requestCount <= dailyLimit;
    }
}