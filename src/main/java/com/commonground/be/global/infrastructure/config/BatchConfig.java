package com.commonground.be.global.infrastructure.config;

import com.commonground.be.domain.news.service.crawling.CrawlingOrchestrationService;
import com.commonground.be.domain.news.dto.request.NaverCrawlingRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.*;
import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 🔄 Spring Batch 설정
 * 
 * 책임:
 * - 네이버 뉴스 정기 크롤링 배치 작업
 * - 스케줄 기반 뉴스 수집 자동화
 * - 언론사별/카테고리별 배치 처리
 */
@Slf4j
@Configuration
@EnableBatchProcessing
@RequiredArgsConstructor
public class BatchConfig {

    private final CrawlingOrchestrationService crawlingOrchestrationService;

    /**
     * 네이버 뉴스 정기 크롤링 Job
     */
    @Bean
    public Job naverNewsCrawlingJob(JobRepository jobRepository, Step naverNewsCrawlingStep) {
        return new JobBuilder("naverNewsCrawlingJob", jobRepository)
                .start(naverNewsCrawlingStep)
                .build();
    }

    /**
     * 네이버 뉴스 크롤링 Step
     */
    @Bean
    public Step naverNewsCrawlingStep(JobRepository jobRepository, 
                                     PlatformTransactionManager transactionManager) {
        return new StepBuilder("naverNewsCrawlingStep", jobRepository)
                .tasklet(naverNewsCrawlingTasklet(), transactionManager)
                .build();
    }

    /**
     * 네이버 뉴스 크롤링 Tasklet
     */
    @Bean
    public Tasklet naverNewsCrawlingTasklet() {
        return (contribution, chunkContext) -> {
            log.info("🔄 네이버 뉴스 정기 크롤링 배치 시작 - {}", LocalDateTime.now());
            
            try {
                // 주요 언론사별 크롤링 설정
                List<NaverCrawlingConfig> crawlingConfigs = getNaverCrawlingConfigs();
                
                int totalProcessed = 0;
                for (NaverCrawlingConfig config : crawlingConfigs) {
                    log.info("📰 크롤링 시작: {} - {}", config.getOfficeName(), config.getCategoryName());
                    
                    NaverCrawlingRequest request = NaverCrawlingRequest.builder()
                            .officeId(config.getOfficeId())
                            .categoryId(config.getCategoryId())
                            .maxArticles(config.getMaxArticles())
                            .maxScrollAttempts(config.getMaxScrollAttempts())
                            .sessionId("batch_" + System.currentTimeMillis())
                            .build();
                    
                    // 비동기 크롤링 실행
                    crawlingOrchestrationService.orchestrateCrawling(request)
                            .thenAccept(results -> {
                                log.info("✅ {} - {} 크롤링 완료: {}개 수집", 
                                    config.getOfficeName(), config.getCategoryName(), results.size());
                            })
                            .exceptionally(throwable -> {
                                log.error("❌ {} - {} 크롤링 실패: {}", 
                                    config.getOfficeName(), config.getCategoryName(), throwable.getMessage());
                                return null;
                            });
                    
                    totalProcessed++;
                    
                    // 크롤링 간격 (서버 부하 방지)
                    Thread.sleep(2000); // 2초 대기
                }
                
                log.info("✅ 네이버 뉴스 정기 크롤링 배치 완료 - 총 {}개 설정 처리", totalProcessed);
                
            } catch (Exception e) {
                log.error("❌ 네이버 뉴스 정기 크롤링 배치 실패", e);
                throw new RuntimeException("배치 작업 실패", e);
            }
            
            return RepeatStatus.FINISHED;
        };
    }

    /**
     * 네이버 뉴스 크롤링 설정 목록
     */
    private List<NaverCrawlingConfig> getNaverCrawlingConfigs() {
        return List.of(
            // 주요 언론사 - 정치 섹션
            new NaverCrawlingConfig("032", "100", "경향신문", "정치", 30, 10),
            new NaverCrawlingConfig("005", "100", "국민일보", "정치", 30, 10),
            new NaverCrawlingConfig("020", "100", "동아일보", "정치", 30, 10),
            new NaverCrawlingConfig("021", "100", "문화일보", "정치", 30, 10),
            new NaverCrawlingConfig("022", "100", "세계일보", "정치", 30, 10),
            new NaverCrawlingConfig("023", "100", "조선일보", "정치", 30, 10),
            new NaverCrawlingConfig("025", "100", "중앙일보", "정치", 30, 10),
            new NaverCrawlingConfig("028", "100", "한겨레", "정치", 30, 10),
            new NaverCrawlingConfig("469", "100", "한국일보", "정치", 30, 10),
            
            // 주요 언론사 - 경제 섹션
            new NaverCrawlingConfig("032", "101", "경향신문", "경제", 25, 8),
            new NaverCrawlingConfig("020", "101", "동아일보", "경제", 25, 8),
            new NaverCrawlingConfig("023", "101", "조선일보", "경제", 25, 8),
            new NaverCrawlingConfig("025", "101", "중앙일보", "경제", 25, 8),
            new NaverCrawlingConfig("009", "101", "매일경제", "경제", 25, 8),
            new NaverCrawlingConfig("008", "101", "머니투데이", "경제", 25, 8),
            new NaverCrawlingConfig("366", "101", "조선비즈", "경제", 25, 8),
            
            // 주요 언론사 - 사회 섹션
            new NaverCrawlingConfig("032", "102", "경향신문", "사회", 20, 8),
            new NaverCrawlingConfig("020", "102", "동아일보", "사회", 20, 8),
            new NaverCrawlingConfig("023", "102", "조선일보", "사회", 20, 8),
            new NaverCrawlingConfig("025", "102", "중앙일보", "사회", 20, 8),
            new NaverCrawlingConfig("028", "102", "한겨레", "사회", 20, 8),
            
            // 방송사 뉴스
            new NaverCrawlingConfig("055", "100", "SBS", "정치", 20, 8),
            new NaverCrawlingConfig("056", "100", "KBS", "정치", 20, 8),
            new NaverCrawlingConfig("057", "100", "MBC", "정치", 20, 8),
            new NaverCrawlingConfig("448", "100", "YTN", "정치", 20, 8),
            new NaverCrawlingConfig("421", "100", "뉴스1", "정치", 15, 6)
        );
    }

    /**
     * 네이버 크롤링 설정 정보
     */
    private static class NaverCrawlingConfig {
        private final String officeId;
        private final String categoryId;
        private final String officeName;
        private final String categoryName;
        private final Integer maxArticles;
        private final Integer maxScrollAttempts;

        public NaverCrawlingConfig(String officeId, String categoryId, String officeName, 
                                  String categoryName, Integer maxArticles, Integer maxScrollAttempts) {
            this.officeId = officeId;
            this.categoryId = categoryId;
            this.officeName = officeName;
            this.categoryName = categoryName;
            this.maxArticles = maxArticles;
            this.maxScrollAttempts = maxScrollAttempts;
        }

        public String getOfficeId() { return officeId; }
        public String getCategoryId() { return categoryId; }
        public String getOfficeName() { return officeName; }
        public String getCategoryName() { return categoryName; }
        public Integer getMaxArticles() { return maxArticles; }
        public Integer getMaxScrollAttempts() { return maxScrollAttempts; }
    }
}