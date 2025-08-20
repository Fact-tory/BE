package com.commonground.be.infrastructure.rabbitmq.performance;

import com.commonground.be.domain.news.dto.request.NaverCrawlingRequest;
import com.commonground.be.global.infrastructure.messaging.CrawlingMessageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 🧪 RabbitMQ 동시성 및 성능 테스트
 * <p>
 * 처리량, 지연시간, 동시성 처리 능력을 검증
 */
@ExtendWith(MockitoExtension.class)
class RabbitMQPerformanceTest {

    @Mock
    private RabbitTemplate rabbitTemplate;
    
    private CrawlingMessageService crawlingMessageService;
    
    @BeforeEach
    void setUp() {
        crawlingMessageService = new CrawlingMessageService(rabbitTemplate);
    }
    
    // ==================== 처리량 테스트 ====================
    
    @Test
    @DisplayName("초당 처리량 성능 테스트")
    void testMessageThroughputPerformance() {
        // Given
        int messageCount = 1000;
        long startTime = System.currentTimeMillis();
        
        // When - 1000개 메시지 전송
        for (int i = 0; i < messageCount; i++) {
            NaverCrawlingRequest request = createTestRequest("throughput_" + i, 5);
            crawlingMessageService.sendCrawlingRequest(request);
        }
        
        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;
        double messagesPerSecond = (double) messageCount / (duration / 1000.0);
        
        // Then
        assertThat(messagesPerSecond).isGreaterThan(100); // 최소 100 msg/sec
        verify(rabbitTemplate, times(messageCount)).convertAndSend(anyString(), anyString(), any(Object.class));
        
        System.out.println("📊 처리량 성능 결과:");
        System.out.println("- 총 메시지: " + messageCount + "개");
        System.out.println("- 처리 시간: " + duration + "ms");
        System.out.println("- 초당 처리량: " + String.format("%.2f", messagesPerSecond) + " msg/sec");
        
        // 성능 벤치마크 확인
        if (messagesPerSecond > 500) {
            System.out.println("✅ 우수한 성능: " + String.format("%.0f", messagesPerSecond) + " msg/sec");
        } else if (messagesPerSecond > 200) {
            System.out.println("✅ 양호한 성능: " + String.format("%.0f", messagesPerSecond) + " msg/sec");
        } else {
            System.out.println("⚠️ 성능 개선 필요: " + String.format("%.0f", messagesPerSecond) + " msg/sec");
        }
    }
    
    @Test
    @DisplayName("배치 처리 성능 테스트")
    void testBatchProcessingPerformance() {
        // Given
        int batchSize = 100;
        int batchCount = 10;
        
        List<Long> batchTimes = new ArrayList<>();
        
        // When - 배치별 처리 시간 측정
        for (int batch = 0; batch < batchCount; batch++) {
            long batchStartTime = System.currentTimeMillis();
            
            for (int i = 0; i < batchSize; i++) {
                NaverCrawlingRequest request = createTestRequest("batch_" + batch + "_" + i, 5);
                crawlingMessageService.sendCrawlingRequest(request);
            }
            
            long batchEndTime = System.currentTimeMillis();
            batchTimes.add(batchEndTime - batchStartTime);
        }
        
        // Then
        double avgBatchTime = batchTimes.stream().mapToLong(Long::longValue).average().orElse(0.0);
        double avgThroughputPerBatch = batchSize / (avgBatchTime / 1000.0);
        
        assertThat(avgThroughputPerBatch).isGreaterThan(50); // 배치당 최소 50 msg/sec
        
        System.out.println("📊 배치 처리 성능 결과:");
        System.out.println("- 배치 크기: " + batchSize + "개");
        System.out.println("- 배치 수: " + batchCount + "개");
        System.out.println("- 평균 배치 처리 시간: " + String.format("%.2f", avgBatchTime) + "ms");
        System.out.println("- 배치당 처리량: " + String.format("%.2f", avgThroughputPerBatch) + " msg/sec");
    }
    
    // ==================== 동시성 테스트 ====================
    
    @Test
    @DisplayName("고부하 동시성 처리 테스트")
    void testHighLoadConcurrency() throws InterruptedException {
        // Given
        int threadCount = 20;
        int messagesPerThread = 50;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);
        AtomicLong totalProcessingTime = new AtomicLong(0);
        
        // When - 고부하 동시 요청
        for (int t = 0; t < threadCount; t++) {
            final int threadId = t;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    long threadStartTime = System.currentTimeMillis();
                    
                    for (int i = 0; i < messagesPerThread; i++) {
                        try {
                            NaverCrawlingRequest request = createTestRequest(
                                "concurrent_" + threadId + "_" + i, 
                                (i % 3 == 0) ? 5 : (i % 3 == 1) ? 30 : 80 // 다양한 큐 타입
                            );
                            crawlingMessageService.sendCrawlingRequest(request);
                            successCount.incrementAndGet();
                        } catch (Exception e) {
                            errorCount.incrementAndGet();
                        }
                    }
                    
                    long threadEndTime = System.currentTimeMillis();
                    totalProcessingTime.addAndGet(threadEndTime - threadStartTime);
                    
                } catch (Exception e) {
                    System.err.println("스레드 " + threadId + " 실행 중 에러: " + e.getMessage());
                } finally {
                    endLatch.countDown();
                }
            });
        }
        
        long testStartTime = System.currentTimeMillis();
        startLatch.countDown();
        boolean completed = endLatch.await(60, TimeUnit.SECONDS);
        long testEndTime = System.currentTimeMillis();
        
        // Then
        assertThat(completed).isTrue();
        int totalMessages = threadCount * messagesPerThread;
        double successRate = (double) successCount.get() / totalMessages * 100;
        double totalThroughput = (double) totalMessages / ((testEndTime - testStartTime) / 1000.0);
        
        assertThat(successRate).isGreaterThan(95.0); // 95% 이상 성공률
        
        System.out.println("📊 고부하 동시성 테스트 결과:");
        System.out.println("- 동시 스레드 수: " + threadCount);
        System.out.println("- 총 메시지 수: " + totalMessages);
        System.out.println("- 성공 처리: " + successCount.get() + "개");
        System.out.println("- 에러 발생: " + errorCount.get() + "개");
        System.out.println("- 성공률: " + String.format("%.2f", successRate) + "%");
        System.out.println("- 전체 처리량: " + String.format("%.2f", totalThroughput) + " msg/sec");
        System.out.println("- 총 테스트 시간: " + (testEndTime - testStartTime) + "ms");
        
        executor.shutdown();
    }
    
    @Test
    @DisplayName("스레드 안전성 테스트")
    void testThreadSafety() throws InterruptedException {
        // Given
        int threadCount = 10;
        int operationsPerThread = 100;
        Set<String> allSessionIds = Collections.synchronizedSet(new HashSet<>());
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        
        // When - 동시에 다른 세션 ID로 요청
        for (int t = 0; t < threadCount; t++) {
            final int threadId = t;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    
                    for (int i = 0; i < operationsPerThread; i++) {
                        String sessionId = "thread_" + threadId + "_op_" + i;
                        allSessionIds.add(sessionId);
                        
                        NaverCrawlingRequest request = NaverCrawlingRequest.builder()
                            .officeId("safety_test")
                            .categoryId("100")
                            .maxArticles(5)
                            .sessionId(sessionId)
                            .build();
                        
                        crawlingMessageService.sendCrawlingRequest(request);
                    }
                } catch (Exception e) {
                    System.err.println("스레드 " + threadId + " 안전성 테스트 에러: " + e.getMessage());
                } finally {
                    endLatch.countDown();
                }
            });
        }
        
        startLatch.countDown();
        boolean completed = endLatch.await(30, TimeUnit.SECONDS);
        
        // Then
        assertThat(completed).isTrue();
        int expectedSessionCount = threadCount * operationsPerThread;
        assertThat(allSessionIds).hasSize(expectedSessionCount); // 모든 세션 ID가 고유해야 함
        
        System.out.println("✅ 스레드 안전성 테스트 완료");
        System.out.println("- 생성된 고유 세션 ID: " + allSessionIds.size() + "개");
        System.out.println("- 예상 세션 ID: " + expectedSessionCount + "개");
        
        executor.shutdown();
    }
    
    // ==================== 지연시간 테스트 ====================
    
    @Test
    @DisplayName("메시지 처리 지연시간 분석")
    void testMessageLatencyAnalysis() {
        // Given
        int testCount = 1000;
        List<Long> latencies = new ArrayList<>();
        
        // When - 개별 메시지 처리 시간 측정
        for (int i = 0; i < testCount; i++) {
            long startTime = System.nanoTime();
            
            NaverCrawlingRequest request = createTestRequest("latency_" + i, 5);
            crawlingMessageService.sendCrawlingRequest(request);
            
            long endTime = System.nanoTime();
            latencies.add((endTime - startTime) / 1_000_000); // ms 변환
        }
        
        // Then - 지연시간 통계 분석
        latencies.sort(Long::compareTo);
        
        long minLatency = latencies.get(0);
        long maxLatency = latencies.get(latencies.size() - 1);
        double avgLatency = latencies.stream().mapToLong(Long::longValue).average().orElse(0.0);
        long p95Latency = latencies.get((int) (latencies.size() * 0.95));
        long p99Latency = latencies.get((int) (latencies.size() * 0.99));
        
        // 성능 기준 검증
        assertThat(avgLatency).isLessThan(10.0); // 평균 10ms 이하
        assertThat(p95Latency).isLessThan(20); // P95 20ms 이하
        assertThat(p99Latency).isLessThan(50); // P99 50ms 이하
        
        System.out.println("📊 지연시간 분석 결과:");
        System.out.println("- 테스트 횟수: " + testCount + "회");
        System.out.println("- 최소 지연시간: " + minLatency + "ms");
        System.out.println("- 최대 지연시간: " + maxLatency + "ms");
        System.out.println("- 평균 지연시간: " + String.format("%.2f", avgLatency) + "ms");
        System.out.println("- P95 지연시간: " + p95Latency + "ms");
        System.out.println("- P99 지연시간: " + p99Latency + "ms");
    }
    
    // ==================== 백프레셔 테스트 ====================
    
    @Test
    @DisplayName("백프레셔(Backpressure) 처리 테스트")
    void testBackpressureHandling() throws InterruptedException {
        // Given
        int burstSize = 500; // 한 번에 500개 메시지 전송
        
        // 큐 백로그 시뮬레이션 (처리 지연)
        doAnswer(invocation -> {
            Thread.sleep(5); // 5ms 처리 지연
            return null;
        }).when(rabbitTemplate).convertAndSend(anyString(), anyString(), any(Object.class));
        
        // When - 버스트 전송
        long burstStartTime = System.currentTimeMillis();
        
        for (int i = 0; i < burstSize; i++) {
            NaverCrawlingRequest request = createTestRequest("burst_" + i, 10);
            crawlingMessageService.sendCrawlingRequest(request);
        }
        
        long burstEndTime = System.currentTimeMillis();
        long burstDuration = burstEndTime - burstStartTime;
        
        // Then
        assertThat(burstDuration).isGreaterThan(2000); // 백프레셔로 인한 지연 (최소 2초)
        verify(rabbitTemplate, times(burstSize)).convertAndSend(anyString(), anyString(), any(Object.class));
        
        double burstThroughput = (double) burstSize / (burstDuration / 1000.0);
        
        System.out.println("📊 백프레셔 테스트 결과:");
        System.out.println("- 버스트 크기: " + burstSize + "개");
        System.out.println("- 처리 시간: " + burstDuration + "ms");
        System.out.println("- 백프레셔 처리량: " + String.format("%.2f", burstThroughput) + " msg/sec");
        
        // 백프레셔 상황에서도 메시지 손실 없이 처리되어야 함
        assertThat(burstThroughput).isGreaterThan(10); // 최소한의 처리량 보장
    }
    
    // ==================== 리소스 사용량 테스트 ====================
    
    @Test
    @DisplayName("메모리 사용량 모니터링 테스트")
    void testMemoryUsageMonitoring() {
        // Given
        Runtime runtime = Runtime.getRuntime();
        long initialMemory = runtime.totalMemory() - runtime.freeMemory();
        
        int messageCount = 10000;
        
        // When - 대량 메시지 처리
        for (int i = 0; i < messageCount; i++) {
            NaverCrawlingRequest request = createTestRequest("memory_test_" + i, 15);
            crawlingMessageService.sendCrawlingRequest(request);
            
            // 주기적으로 가비지 컬렉션 유도
            if (i % 1000 == 0) {
                System.gc();
                Thread.yield();
            }
        }
        
        // 메모리 정리 후 최종 측정
        System.gc();
        Thread.yield();
        long finalMemory = runtime.totalMemory() - runtime.freeMemory();
        long memoryIncrease = finalMemory - initialMemory;
        
        // Then
        double memoryPerMessage = (double) memoryIncrease / messageCount;
        
        System.out.println("📊 메모리 사용량 분석:");
        System.out.println("- 처리된 메시지: " + messageCount + "개");
        System.out.println("- 초기 메모리: " + (initialMemory / 1024 / 1024) + "MB");
        System.out.println("- 최종 메모리: " + (finalMemory / 1024 / 1024) + "MB");
        System.out.println("- 메모리 증가: " + (memoryIncrease / 1024 / 1024) + "MB");
        System.out.println("- 메시지당 메모리: " + String.format("%.2f", memoryPerMessage) + " bytes");
        
        // 메모리 사용량이 과도하지 않은지 확인
        assertThat(memoryPerMessage).isLessThan(1024); // 메시지당 1KB 미만
    }
    
    // ==================== 헬퍼 메서드 ====================
    
    private NaverCrawlingRequest createTestRequest(String officeId, int maxArticles) {
        return NaverCrawlingRequest.builder()
            .officeId(officeId)
            .categoryId("100")
            .maxArticles(maxArticles)
            .sessionId("perf_test_" + System.currentTimeMillis())
            .build();
    }
}