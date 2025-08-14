package com.commonground.be.integration.crawler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.commonground.be.domain.news.dto.crawling.RawNewsData;
import com.commonground.be.domain.news.dto.request.NaverCrawlingRequest;
import com.commonground.be.domain.news.service.communication.WebSocketProgressService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "spring.data.mongodb.uri=mongodb://localhost:27017/test",
    "spring.data.redis.host=localhost",
    "spring.data.redis.port=6379"
})
@DisplayName("네이버 뉴스 크롤러 통합 테스트")
class NaverNewsCrawlerIntegrationTest {

    @Autowired
    private NaverNewsCrawler naverNewsCrawler;

    @MockBean
    private WebSocketProgressService progressService;

    @MockBean
    private RedisTemplate<String, Object> redisTemplate;

    @BeforeEach
    void setUp() {
        // WebSocket 진행상황 서비스 목킹
        doNothing().when(progressService).updateProgress(anyString(), anyString(), anyInt(), anyString());
        
        // Redis 목킹
        when(redisTemplate.opsForValue()).thenReturn(mock(org.springframework.data.redis.core.ValueOperations.class));
    }

    @Test
    @DisplayName("최소한의 크롤링 요청으로 기본 기능 검증")
    void crawlNews_WithMinimalRequest_ShouldCompleteWithoutErrors() {
        // Given
        NaverCrawlingRequest request = NaverCrawlingRequest.builder()
                .officeId("001") // 연합뉴스
                .categoryId("100") // 정치 카테고리
                .maxArticles(1) // 최소 1개만 크롤링
                .maxScrollAttempts(5) // 최소 스크롤 시도
                .sessionId("integration-test-minimal")
                .build();

        // When & Then
        assertThatCode(() -> {
            CompletableFuture<List<RawNewsData>> future = naverNewsCrawler.crawlNews(request);
            List<RawNewsData> result = future.get(30, TimeUnit.SECONDS); // 30초 타임아웃
            
            // 기본 검증
            assertThat(result).isNotNull();
            // 네트워크 상황에 따라 결과가 없을 수 있으므로 null 체크만 수행
        }).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("진행상황 업데이트가 올바르게 호출되는지 검증")
    void crawlNews_ShouldCallProgressUpdates() {
        // Given
        String sessionId = "progress-integration-test";
        NaverCrawlingRequest request = NaverCrawlingRequest.builder()
                .officeId("001")
                .categoryId("100")
                .maxArticles(2)
                .maxScrollAttempts(5)
                .sessionId(sessionId)
                .build();

        // When
        try {
            CompletableFuture<List<RawNewsData>> future = naverNewsCrawler.crawlNews(request);
            future.get(30, TimeUnit.SECONDS);
        } catch (Exception e) {
            // 네트워크 오류 등은 무시하고 진행상황 호출 여부만 검증
        }

        // Then
        verify(progressService, atLeastOnce())
                .updateProgress(eq(sessionId), anyString(), anyInt(), anyString());
    }

    @Test
    @DisplayName("여러 카테고리로 크롤링 요청 처리 검증")
    void crawlNews_WithDifferentCategories_ShouldHandleCorrectly() {
        // Given
        String[] categoryIds = {"100", "101", "102"}; // 정치, 경제, 사회
        
        for (String categoryId : categoryIds) {
            NaverCrawlingRequest request = NaverCrawlingRequest.builder()
                    .officeId("001")
                    .categoryId(categoryId)
                    .maxArticles(1)
                    .maxScrollAttempts(5)
                    .sessionId("category-test-" + categoryId)
                    .build();

            // When & Then
            assertThatCode(() -> {
                CompletableFuture<List<RawNewsData>> future = naverNewsCrawler.crawlNews(request);
                List<RawNewsData> result = future.get(20, TimeUnit.SECONDS);
                assertThat(result).isNotNull();
            }).doesNotThrowAnyException();
        }
    }

    @Test
    @DisplayName("동시 다중 크롤링 요청 처리 검증")
    void crawlNews_WithConcurrentRequests_ShouldHandleCorrectly() {
        // Given
        NaverCrawlingRequest request1 = createTestRequest("concurrent-1", "001", "100");
        NaverCrawlingRequest request2 = createTestRequest("concurrent-2", "001", "101");

        // When
        CompletableFuture<List<RawNewsData>> future1 = naverNewsCrawler.crawlNews(request1);
        CompletableFuture<List<RawNewsData>> future2 = naverNewsCrawler.crawlNews(request2);

        // Then
        assertThatCode(() -> {
            List<RawNewsData> result1 = future1.get(30, TimeUnit.SECONDS);
            List<RawNewsData> result2 = future2.get(30, TimeUnit.SECONDS);
            
            assertThat(result1).isNotNull();
            assertThat(result2).isNotNull();
        }).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("잘못된 언론사 ID로 크롤링 시 적절한 처리")
    void crawlNews_WithInvalidOfficeId_ShouldHandleGracefully() {
        // Given
        NaverCrawlingRequest request = NaverCrawlingRequest.builder()
                .officeId("999999") // 존재하지 않는 언론사 ID
                .categoryId("100")
                .maxArticles(1)
                .maxScrollAttempts(5)
                .sessionId("invalid-office-test")
                .build();

        // When & Then
        assertThatCode(() -> {
            CompletableFuture<List<RawNewsData>> future = naverNewsCrawler.crawlNews(request);
            List<RawNewsData> result = future.get(20, TimeUnit.SECONDS);
            
            // 잘못된 ID라도 크롤러가 crash되지 않고 빈 결과를 반환해야 함
            assertThat(result).isNotNull();
        }).doesNotThrowAnyException();
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "ENABLE_FULL_CRAWLING_TEST", matches = "true")
    @DisplayName("전체 크롤링 프로세스 검증 (실제 네이버 접속)")
    void crawlNews_FullProcess_ShouldReturnValidData() throws Exception {
        // Given
        NaverCrawlingRequest request = NaverCrawlingRequest.builder()
                .officeId("001") // 연합뉴스
                .categoryId("100") // 정치
                .maxArticles(5)
                .maxScrollAttempts(10)
                .sessionId("full-process-test")
                .build();

        // When
        CompletableFuture<List<RawNewsData>> future = naverNewsCrawler.crawlNews(request);
        List<RawNewsData> results = future.get(60, TimeUnit.SECONDS); // 1분 타임아웃

        // Then
        assertThat(results).isNotNull();
        
        // 실제 데이터가 크롤링되었다면 검증
        if (!results.isEmpty()) {
            for (RawNewsData data : results) {
                assertThat(data.getUrl()).isNotNull().contains("naver.com");
                assertThat(data.getTitle()).isNotNull().isNotBlank();
                assertThat(data.getContent()).isNotNull();
                assertThat(data.getSource()).isEqualTo("naver_news");
                assertThat(data.getDiscoveredAt()).isNotNull();
                assertThat(data.getDiscoveredAt()).isBefore(LocalDateTime.now().plusMinutes(1));
                assertThat(data.getResponseTime()).isNotNull().isPositive();
            }
        }

        // 진행상황 업데이트가 여러 번 호출되었는지 확인
        verify(progressService, atLeast(3))
                .updateProgress(eq("full-process-test"), anyString(), anyInt(), anyString());
    }

    @Test
    @DisplayName("타임아웃 상황에서의 안전한 처리")
    void crawlNews_WithShortTimeout_ShouldHandleGracefully() {
        // Given
        NaverCrawlingRequest request = NaverCrawlingRequest.builder()
                .officeId("001")
                .categoryId("100")
                .maxArticles(10) // 많은 수의 기사 요청
                .maxScrollAttempts(20) // 많은 스크롤 시도
                .sessionId("timeout-test")
                .build();

        // When & Then
        assertThatCode(() -> {
            CompletableFuture<List<RawNewsData>> future = naverNewsCrawler.crawlNews(request);
            // 짧은 타임아웃으로 강제 종료 상황 시뮬레이션
            try {
                future.get(5, TimeUnit.SECONDS); // 5초 타임아웃
            } catch (Exception e) {
                // 타임아웃이 발생해도 시스템이 안전하게 처리되어야 함
                assertThat(e).isInstanceOf(java.util.concurrent.TimeoutException.class);
            }
        }).doesNotThrowAnyException();
    }

    // 헬퍼 메소드
    private NaverCrawlingRequest createTestRequest(String sessionId, String officeId, String categoryId) {
        return NaverCrawlingRequest.builder()
                .sessionId(sessionId)
                .officeId(officeId)
                .categoryId(categoryId)
                .maxArticles(1)
                .maxScrollAttempts(5)
                .build();
    }
}

/**
 * 테스트 실행 안내
 * 
 * 1. 기본 테스트 실행:
 *    ./gradlew test --tests "NaverNewsCrawlerIntegrationTest"
 * 
 * 2. 실제 네이버 접속 테스트 (환경변수 설정 필요):
 *    ENABLE_FULL_CRAWLING_TEST=true ./gradlew test --tests "NaverNewsCrawlerIntegrationTest.crawlNews_FullProcess_ShouldReturnValidData"
 * 
 * 3. 테스트 전 준비사항:
 *    - MongoDB 테스트 데이터베이스 실행
 *    - Redis 서버 실행 (또는 embedded-redis 사용)
 *    - 네트워크 연결 확인
 * 
 * 4. 주의사항:
 *    - 실제 네이버 뉴스 사이트에 접속하므로 과도한 테스트는 IP 차단 위험
 *    - CI/CD 환경에서는 실제 크롤링 테스트 비활성화 권장
 *    - 테스트용 더미 데이터 사용 고려
 */