package com.commonground.be.domain.news.service.crawling;

import com.commonground.be.domain.news.dto.crawling.RawNewsData;
import com.commonground.be.domain.news.dto.request.NaverCrawlingRequest;
import com.commonground.be.domain.news.service.communication.CrawlingQueueService;
import com.commonground.be.domain.news.service.communication.WebSocketProgressService;
import com.commonground.be.domain.news.service.management.NewsDataProcessingService;
import com.commonground.be.global.application.aop.LogExecutionTime;
import com.commonground.be.global.infrastructure.concurrency.RedisLock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * 🎯 크롤링 오케스트레이션 서비스
 * 
 * 책임:
 * - 크롤링 요청 조율 및 관리
 * - Python 크롤러와의 통신 관리
 * - 크롤링 결과를 뉴스 데이터 처리 파이프라인으로 전달
 * - 동시성 제어 (Redis Lock)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CrawlingOrchestrationService {

    private final CrawlingQueueService crawlingQueueService;
    private final NewsDataProcessingService newsDataProcessingService;
    private final WebSocketProgressService progressService;

    /**
     * 네이버 뉴스 크롤링 요청 처리 (메시지 큐 기반)
     */
    @Async("crawlingTaskExecutor")
    @RedisLock(
        key = "'naver_crawling:' + #request.officeId + ':' + #request.categoryId",
        waitTime = 5,
        leaseTime = 300,  // 5분
        timeoutMessage = "해당 언론사의 크롤링이 이미 진행 중입니다. 잠시 후 다시 시도해주세요."
    )
    @LogExecutionTime
    public CompletableFuture<List<RawNewsData>> orchestrateCrawling(NaverCrawlingRequest request) {
        
        String sessionId = request.getSessionId() != null ? 
            request.getSessionId() : UUID.randomUUID().toString();
        request.setSessionId(sessionId);
        
        log.info("크롤링 오케스트레이션 시작: sessionId={}, officeId={}, categoryId={}", 
            sessionId, request.getOfficeId(), request.getCategoryId());
        
        try {
            // 진행상황 초기화
            progressService.updateProgress(
                sessionId, "started", 0, 
                "Python 크롤링 워커로 요청 전송 중...", 0, 0, 0, 0
            );
            
            // Python 크롤링 워커로 요청 전송
            CompletableFuture<List<RawNewsData>> crawlingResult = 
                crawlingQueueService.submitCrawlingRequest(request);
            
            // 크롤링 완료 후 데이터 처리
            return crawlingResult.thenCompose(rawDataList -> {
                log.info("Python 크롤링 완료: sessionId={}, 수집된 기사 수={}", 
                    sessionId, rawDataList.size());
                
                // 진행상황 업데이트
                progressService.updateProgress(
                    sessionId, "processing", 50, 
                    "크롤링 완료, 데이터 처리 시작...", 
                    rawDataList.size(), 0, 0, 0
                );
                
                // 데이터 처리 (비동기)
                return processRawDataAsync(sessionId, rawDataList);
            })
            .whenComplete((result, throwable) -> {
                if (throwable != null) {
                    log.error("크롤링 오케스트레이션 실패: sessionId={}", sessionId, throwable);
                    progressService.updateProgress(
                        sessionId, "failed", 0,
                        "크롤링 실패: " + throwable.getMessage(), 0, 0, 0, 0
                    );
                } else {
                    log.info("크롤링 오케스트레이션 완료: sessionId={}, 처리된 기사 수={}", 
                        sessionId, result != null ? result.size() : 0);
                    progressService.updateProgress(
                        sessionId, "completed", 100, 
                        "크롤링 및 데이터 처리 완료", 
                        result != null ? result.size() : 0, 
                        result != null ? result.size() : 0, 
                        result != null ? result.size() : 0, 0
                    );
                }
            });
            
        } catch (Exception e) {
            log.error("크롤링 오케스트레이션 초기화 실패: sessionId={}", sessionId, e);
            
            progressService.updateProgress(
                sessionId, "failed", 0,
                "크롤링 초기화 실패: " + e.getMessage(), 0, 0, 0, 0
            );
            
            CompletableFuture<List<RawNewsData>> errorFuture = new CompletableFuture<>();
            errorFuture.completeExceptionally(e);
            return errorFuture;
        }
    }
    
    /**
     * 원시 데이터 비동기 처리
     */
    @Async("crawlingTaskExecutor")
    private CompletableFuture<List<RawNewsData>> processRawDataAsync(String sessionId, List<RawNewsData> rawDataList) {
        return CompletableFuture.supplyAsync(() -> {
            int processed = 0;
            int success = 0;
            int failed = 0;
            
            for (RawNewsData rawData : rawDataList) {
                try {
                    // 개별 뉴스 데이터 처리
                    var processedNews = newsDataProcessingService.processRawNewsData(rawData);
                    
                    if (processedNews != null) {
                        success++;
                    } else {
                        failed++; // 중복 등의 이유로 처리하지 않음
                    }
                    
                } catch (Exception e) {
                    log.warn("개별 뉴스 처리 실패: title={}, error={}", 
                        rawData.getTitle(), e.getMessage());
                    failed++;
                }
                
                processed++;
                
                // 진행상황 업데이트 (10개마다)
                if (processed % 10 == 0) {
                    int progressPercent = 50 + (processed * 50 / rawDataList.size());
                    progressService.updateProgress(
                        sessionId, "processing", progressPercent,
                        String.format("데이터 처리 중... (%d/%d)", processed, rawDataList.size()),
                        rawDataList.size(), processed, success, failed
                    );
                }
            }
            
            log.info("원시 데이터 처리 완료: sessionId={}, 전체={}, 성공={}, 실패={}", 
                sessionId, rawDataList.size(), success, failed);
            
            return rawDataList; // 원본 데이터 반환 (통계용)
        });
    }
    
    /**
     * 기존 NaverNewsCrawler 호환성을 위한 래퍼 메서드
     */
    public CompletableFuture<List<RawNewsData>> crawlNews(NaverCrawlingRequest request) {
        return orchestrateCrawling(request);
    }
}