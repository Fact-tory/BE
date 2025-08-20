package com.commonground.be.domain.news.controller;

import com.commonground.be.global.application.response.HttpResponseDto;
import com.commonground.be.global.application.response.ResponseCodeEnum;
import com.commonground.be.global.application.response.ResponseUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 🔄 배치 작업 제어 API
 * 
 * 책임:
 * - 네이버 뉴스 크롤링 배치 수동 실행
 * - 배치 작업 상태 조회
 * - 관리자 권한 배치 제어
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/batch")
@RequiredArgsConstructor
public class BatchController {

    private final JobLauncher jobLauncher;
    private final Job naverNewsCrawlingJob;

    /**
     * 네이버 뉴스 크롤링 배치 수동 실행
     */
    @PostMapping("/naver/crawling/start")
    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGER')")
    public ResponseEntity<HttpResponseDto> startNaverCrawlingBatch(
            @RequestParam(defaultValue = "manual") String type) {
        
        try {
            log.info("🔄 네이버 뉴스 크롤링 배치 수동 실행 요청 - type: {}", type);
            
            JobParameters jobParameters = new JobParametersBuilder()
                    .addLong("startTime", System.currentTimeMillis())
                    .addString("type", type)
                    .addString("timestamp", LocalDateTime.now().toString())
                    .addString("trigger", "manual")
                    .toJobParameters();
            
            // 비동기로 배치 실행
            new Thread(() -> {
                try {
                    jobLauncher.run(naverNewsCrawlingJob, jobParameters);
                    log.info("✅ 네이버 뉴스 크롤링 배치 수동 실행 완료");
                } catch (Exception e) {
                    log.error("❌ 네이버 뉴스 크롤링 배치 수동 실행 실패", e);
                }
            }).start();
            
            Map<String, Object> result = Map.of(
                "message", "네이버 뉴스 크롤링 배치가 시작되었습니다.",
                "type", type,
                "startTime", LocalDateTime.now(),
                "status", "STARTED"
            );
            
            return ResponseUtils.of(ResponseCodeEnum.SUCCESS, result);
            
        } catch (Exception e) {
            log.error("❌ 네이버 뉴스 크롤링 배치 수동 실행 실패", e);
            return ResponseUtils.of(ResponseCodeEnum.INTERNAL_SERVER_ERROR, 
                Map.of("error", "배치 실행 실패: " + e.getMessage()));
        }
    }

    /**
     * 배치 작업 상태 조회
     */
    @GetMapping("/status")
    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGER')")
    public ResponseEntity<HttpResponseDto> getBatchStatus() {
        
        try {
            Map<String, Object> status = Map.of(
                "scheduler", Map.of(
                    "enabled", true,
                    "nextScheduled", "매 2시간마다 (6-22시)",
                    "breakingNews", "매 30분마다 (7-23시)",
                    "nightly", "매일 새벽 3시",
                    "weekly", "매주 일요일 오전 1시"
                ),
                "lastUpdate", LocalDateTime.now(),
                "system", Map.of(
                    "status", "RUNNING",
                    "crawlingTargets", 27, // 설정된 크롤링 대상 수
                    "maxArticlesPerRun", 30
                )
            );
            
            return ResponseUtils.of(ResponseCodeEnum.SUCCESS, status);
            
        } catch (Exception e) {
            log.error("❌ 배치 상태 조회 실패", e);
            return ResponseUtils.of(ResponseCodeEnum.INTERNAL_SERVER_ERROR, 
                Map.of("error", "상태 조회 실패: " + e.getMessage()));
        }
    }

    /**
     * 배치 스케줄 설정 조회
     */
    @GetMapping("/schedule")
    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGER')")
    public ResponseEntity<HttpResponseDto> getBatchSchedule() {
        
        try {
            Map<String, Object> schedule = Map.of(
                "regular", Map.of(
                    "cron", "0 0 6,8,10,12,14,16,18,20,22 * * *",
                    "description", "매 2시간마다 실행 (오전 6시 ~ 오후 10시)",
                    "timezone", "Asia/Seoul"
                ),
                "breaking", Map.of(
                    "cron", "0 */30 7-23 * * *", 
                    "description", "매 30분마다 실행 (오전 7시 ~ 오후 11시)",
                    "timezone", "Asia/Seoul"
                ),
                "nightly", Map.of(
                    "cron", "0 0 3 * * *",
                    "description", "매일 새벽 3시 실행",
                    "timezone", "Asia/Seoul"
                ),
                "weekly", Map.of(
                    "cron", "0 0 1 * * SUN",
                    "description", "매주 일요일 오전 1시 실행",
                    "timezone", "Asia/Seoul"
                ),
                "targets", Map.of(
                    "총 언론사", 15,
                    "정치 섹션", 12,
                    "경제 섹션", 7, 
                    "사회 섹션", 5,
                    "방송사", 5
                )
            );
            
            return ResponseUtils.of(ResponseCodeEnum.SUCCESS, schedule);
            
        } catch (Exception e) {
            log.error("❌ 배치 스케줄 조회 실패", e);
            return ResponseUtils.of(ResponseCodeEnum.INTERNAL_SERVER_ERROR, 
                Map.of("error", "스케줄 조회 실패: " + e.getMessage()));
        }
    }

    /**
     * 배치 설정 정보 조회 (언론사 목록)
     */
    @GetMapping("/config")
    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGER')")
    public ResponseEntity<HttpResponseDto> getBatchConfig() {
        
        try {
            Map<String, Object> config = Map.of(
                "crawlingTargets", Map.of(
                    "majorNewspapers", Map.of(
                        "경향신문", Map.of("officeId", "032", "categories", List.of("정치", "경제", "사회")),
                        "동아일보", Map.of("officeId", "020", "categories", List.of("정치", "경제", "사회")),
                        "조선일보", Map.of("officeId", "023", "categories", List.of("정치", "경제", "사회")),
                        "중앙일보", Map.of("officeId", "025", "categories", List.of("정치", "경제", "사회")),
                        "한겨레", Map.of("officeId", "028", "categories", List.of("정치", "사회")),
                        "한국일보", Map.of("officeId", "469", "categories", List.of("정치"))
                    ),
                    "economicPapers", Map.of(
                        "매일경제", Map.of("officeId", "009", "categories", List.of("경제")),
                        "머니투데이", Map.of("officeId", "008", "categories", List.of("경제")),
                        "조선비즈", Map.of("officeId", "366", "categories", List.of("경제"))
                    ),
                    "broadcastNews", Map.of(
                        "SBS", Map.of("officeId", "055", "categories", List.of("정치")),
                        "KBS", Map.of("officeId", "056", "categories", List.of("정치")),
                        "MBC", Map.of("officeId", "057", "categories", List.of("정치")),
                        "YTN", Map.of("officeId", "448", "categories", List.of("정치")),
                        "뉴스1", Map.of("officeId", "421", "categories", List.of("정치"))
                    )
                ),
                "settings", Map.of(
                    "maxArticlesPerCategory", 30,
                    "maxScrollAttempts", 10,
                    "crawlingInterval", "2초",
                    "retryPolicy", "3회",
                    "timeout", "5분"
                )
            );
            
            return ResponseUtils.of(ResponseCodeEnum.SUCCESS, config);
            
        } catch (Exception e) {
            log.error("❌ 배치 설정 조회 실패", e);
            return ResponseUtils.of(ResponseCodeEnum.INTERNAL_SERVER_ERROR, 
                Map.of("error", "설정 조회 실패: " + e.getMessage()));
        }
    }
}