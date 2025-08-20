package com.commonground.be.global.infrastructure.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 📅 크롤링 스케줄러
 * 
 * 책임:
 * - 네이버 뉴스 정기 크롤링 스케줄 관리
 * - 시간대별 크롤링 작업 자동 실행
 * - 배치 작업 실행 및 모니터링
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.scheduler.enabled", havingValue = "true", matchIfMissing = true)
public class CrawlingScheduler {

    private final JobLauncher jobLauncher;
    private final Job naverNewsCrawlingJob;

    /**
     * 네이버 뉴스 정기 크롤링 - 매 2시간마다 실행
     * 오전 6시부터 오후 10시까지 (6, 8, 10, 12, 14, 16, 18, 20, 22시)
     */
    @Scheduled(cron = "0 0 6,8,10,12,14,16,18,20,22 * * *", zone = "Asia/Seoul")
    public void scheduleNaverNewsCrawling() {
        try {
            log.info("🔄 정기 네이버 뉴스 크롤링 스케줄 시작 - {}", LocalDateTime.now());
            
            JobParameters jobParameters = new JobParametersBuilder()
                    .addLong("startTime", System.currentTimeMillis())
                    .addString("type", "scheduled")
                    .addString("timestamp", LocalDateTime.now().toString())
                    .toJobParameters();
            
            jobLauncher.run(naverNewsCrawlingJob, jobParameters);
            
            log.info("✅ 정기 네이버 뉴스 크롤링 스케줄 완료");
            
        } catch (Exception e) {
            log.error("❌ 정기 네이버 뉴스 크롤링 스케줄 실패", e);
        }
    }

    /**
     * 네이버 뉴스 속보 크롤링 - 매 30분마다 실행 (주요 시간대)
     * 오전 7시부터 오후 11시까지 30분 간격
     */
    @Scheduled(cron = "0 */30 7-23 * * *", zone = "Asia/Seoul")
    public void scheduleBreakingNewsCrawling() {
        try {
            log.info("⚡ 네이버 속보 뉴스 크롤링 스케줄 시작 - {}", LocalDateTime.now());
            
            JobParameters jobParameters = new JobParametersBuilder()
                    .addLong("startTime", System.currentTimeMillis())
                    .addString("type", "breaking")
                    .addString("timestamp", LocalDateTime.now().toString())
                    .toJobParameters();
            
            jobLauncher.run(naverNewsCrawlingJob, jobParameters);
            
            log.info("✅ 네이버 속보 뉴스 크롤링 스케줄 완료");
            
        } catch (Exception e) {
            log.error("❌ 네이버 속보 뉴스 크롤링 스케줄 실패", e);
        }
    }

    /**
     * 새벽 시간대 크롤링 - 매일 새벽 3시 (야간 뉴스 수집)
     */
    @Scheduled(cron = "0 0 3 * * *", zone = "Asia/Seoul")
    public void scheduleNightlyCrawling() {
        try {
            log.info("🌙 새벽 네이버 뉴스 크롤링 스케줄 시작 - {}", LocalDateTime.now());
            
            JobParameters jobParameters = new JobParametersBuilder()
                    .addLong("startTime", System.currentTimeMillis())
                    .addString("type", "nightly")
                    .addString("timestamp", LocalDateTime.now().toString())
                    .toJobParameters();
            
            jobLauncher.run(naverNewsCrawlingJob, jobParameters);
            
            log.info("✅ 새벽 네이버 뉴스 크롤링 스케줄 완료");
            
        } catch (Exception e) {
            log.error("❌ 새벽 네이버 뉴스 크롤링 스케줄 실패", e);
        }
    }

    /**
     * 주간 종합 크롤링 - 매주 일요일 오전 1시 (전체 언론사 대상)
     */
    @Scheduled(cron = "0 0 1 * * SUN", zone = "Asia/Seoul")
    public void scheduleWeeklyCrawling() {
        try {
            log.info("📅 주간 네이버 뉴스 종합 크롤링 스케줄 시작 - {}", LocalDateTime.now());
            
            JobParameters jobParameters = new JobParametersBuilder()
                    .addLong("startTime", System.currentTimeMillis())
                    .addString("type", "weekly")
                    .addString("timestamp", LocalDateTime.now().toString())
                    .toJobParameters();
            
            jobLauncher.run(naverNewsCrawlingJob, jobParameters);
            
            log.info("✅ 주간 네이버 뉴스 종합 크롤링 스케줄 완료");
            
        } catch (Exception e) {
            log.error("❌ 주간 네이버 뉴스 종합 크롤링 스케줄 실패", e);
        }
    }
}