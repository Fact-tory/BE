package com.commonground.be.infrastructure.rabbitmq.integration;

import com.commonground.be.domain.news.dto.request.NaverCrawlingRequest;
import com.commonground.be.global.infrastructure.config.UnifiedRabbitMQConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.*;

/**
 * 🧪 RabbitMQ 실제 연동 통합 테스트
 * 
 * 실제 RabbitMQ 서버와 연동하여 End-to-End 테스트 수행
 * 
 * 주의: 이 테스트는 실제 RabbitMQ 서버가 실행 중일 때만 동작합니다.
 * 환경변수 RABBITMQ_INTEGRATION_TEST=true로 활성화
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "spring.rabbitmq.host=localhost",
    "spring.rabbitmq.port=5672", 
    "spring.rabbitmq.username=guest",
    "spring.rabbitmq.password=guest",
    "spring.rabbitmq.virtual-host=/",
    "spring.rabbitmq.publisher-confirm-type=correlated",
    "spring.rabbitmq.publisher-returns=true",
    "spring.rabbitmq.listener.simple.acknowledge-mode=manual",
    "spring.rabbitmq.listener.simple.prefetch=1",
    "spring.rabbitmq.listener.simple.retry.enabled=true",
    "spring.rabbitmq.listener.simple.retry.max-attempts=3"
})
@EnabledIfEnvironmentVariable(named = "RABBITMQ_INTEGRATION_TEST", matches = "true")
class RabbitMQIntegrationTest {

    @Autowired(required = false)
    private RabbitTemplate rabbitTemplate;
    
    @Autowired(required = false)
    private RabbitAdmin rabbitAdmin;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @BeforeEach
    void setUp() {
        // RabbitMQ가 실행 중이지 않으면 테스트 스킵
        if (rabbitTemplate == null || rabbitAdmin == null) {
            System.out.println("⚠️ RabbitMQ가 실행되지 않음. 통합 테스트 스킵");
            return;
        }
        
        // 테스트 전 큐 정리
        purgeAllQueues();
        System.out.println("🧹 테스트 큐 정리 완료");
    }
    
    // ==================== 기본 연결 테스트 ====================
    
    @Test
    @DisplayName("RabbitMQ 서버 연결 확인")
    void testRabbitMQConnection() {
        // Given & When & Then
        if (rabbitTemplate == null) {
            System.out.println("⏭️ RabbitMQ 연결 없음 - 테스트 스킵");
            return;
        }
        
        assertThat(rabbitTemplate).isNotNull();
        assertThat(rabbitAdmin).isNotNull();
        
        // 큐 존재 확인
        Properties queueProps = rabbitAdmin.getQueueProperties(UnifiedRabbitMQConfig.CRAWLING_LIGHT_QUEUE);
        assertThat(queueProps).isNotNull();
        
        System.out.println("✅ RabbitMQ 서버 연결 확인 완료");
    }
    
    // ==================== 실제 메시지 송수신 테스트 ====================
    
    @Test
    @DisplayName("실제 메시지 송수신 E2E 테스트")
    void testEndToEndMessageFlow() throws Exception {
        if (rabbitTemplate == null) {
            System.out.println("⏭️ RabbitMQ 연결 없음 - 테스트 스킵");
            return;
        }
        
        // Given
        NaverCrawlingRequest request = createTestRequest("e2e_test", 25);
        String routingKey = UnifiedRabbitMQConfig.getRoutingKeyBySize(25); // Medium
        String expectedQueue = UnifiedRabbitMQConfig.CRAWLING_MEDIUM_QUEUE;
        
        // When - 메시지 전송
        rabbitTemplate.convertAndSend(
            UnifiedRabbitMQConfig.CRAWLING_EXCHANGE,
            routingKey,
            request
        );
        
        // Then - 메시지 수신
        Object received = rabbitTemplate.receiveAndConvert(expectedQueue, 5000);
        
        assertThat(received).isNotNull();
        assertThat(received).isInstanceOf(NaverCrawlingRequest.class);
        
        NaverCrawlingRequest receivedRequest = (NaverCrawlingRequest) received;
        assertThat(receivedRequest.getOfficeId()).isEqualTo("e2e_test");
        assertThat(receivedRequest.getMaxArticles()).isEqualTo(25);
        
        System.out.println("✅ E2E 메시지 송수신 완료");
    }
    
    @Test
    @DisplayName("다중 큐 라우팅 실제 테스트")
    void testMultiQueueRoutingReal() throws Exception {
        if (rabbitTemplate == null) {
            System.out.println("⏭️ RabbitMQ 연결 없음 - 테스트 스킵");
            return;
        }
        
        // Given
        Map<String, Integer> testCases = new HashMap<>();
        testCases.put("light_case", 5);   // Light queue
        testCases.put("medium_case", 30); // Medium queue  
        testCases.put("heavy_case", 80);  // Heavy queue
        
        // When & Then
        for (Map.Entry<String, Integer> testCase : testCases.entrySet()) {
            String officeId = testCase.getKey();
            int maxArticles = testCase.getValue();
            
            NaverCrawlingRequest request = createTestRequest(officeId, maxArticles);
            String routingKey = UnifiedRabbitMQConfig.getRoutingKeyBySize(maxArticles);
            
            // 메시지 전송
            rabbitTemplate.convertAndSend(
                UnifiedRabbitMQConfig.CRAWLING_EXCHANGE,
                routingKey,
                request
            );
            
            // 해당 큐에서 메시지 수신 확인
            String expectedQueue = getExpectedQueueBySize(maxArticles);
            Object received = rabbitTemplate.receiveAndConvert(expectedQueue, 3000);
            
            assertThat(received).isNotNull();
            NaverCrawlingRequest receivedReq = (NaverCrawlingRequest) received;
            assertThat(receivedReq.getOfficeId()).isEqualTo(officeId);
            
            System.out.println("✅ " + officeId + " -> " + expectedQueue + " 라우팅 완료");
        }
    }
    
    // ==================== 대용량 메시지 테스트 ====================
    
    @Test
    @DisplayName("대용량 메시지 처리 실제 테스트")
    void testHighVolumeMessagesReal() throws Exception {
        if (rabbitTemplate == null) {
            System.out.println("⏭️ RabbitMQ 연결 없음 - 테스트 스킵");
            return;
        }
        
        // Given
        int messageCount = 50;
        List<String> sentIds = new ArrayList<>();
        
        // When - 대량 메시지 전송
        for (int i = 0; i < messageCount; i++) {
            String officeId = "bulk_test_" + i;
            NaverCrawlingRequest request = createTestRequest(officeId, 5); // Light queue
            sentIds.add(officeId);
            
            rabbitTemplate.convertAndSend(
                UnifiedRabbitMQConfig.CRAWLING_EXCHANGE,
                UnifiedRabbitMQConfig.LIGHT_ROUTING_KEY,
                request
            );
        }
        
        // Then - 모든 메시지 수신 확인
        List<String> receivedIds = new ArrayList<>();
        for (int i = 0; i < messageCount; i++) {
            Object received = rabbitTemplate.receiveAndConvert(
                UnifiedRabbitMQConfig.CRAWLING_LIGHT_QUEUE, 
                10000 // 10초 대기
            );
            
            assertThat(received).isNotNull();
            NaverCrawlingRequest req = (NaverCrawlingRequest) received;
            receivedIds.add(req.getOfficeId());
        }
        
        assertThat(receivedIds).hasSize(messageCount);
        assertThat(receivedIds).containsExactlyInAnyOrderElementsOf(sentIds);
        
        System.out.println("✅ 대용량 메시지 처리 완료: " + messageCount + "개");
    }
    
    // ==================== 메시지 지속성 테스트 ====================
    
    @Test
    @DisplayName("메시지 지속성 및 복구 테스트")
    void testMessagePersistenceAndRecovery() throws Exception {
        if (rabbitTemplate == null) {
            System.out.println("⏭️ RabbitMQ 연결 없음 - 테스트 스킵");
            return;
        }
        
        // Given
        NaverCrawlingRequest persistentRequest = createTestRequest("persistent_test", 15);
        
        // When - 지속성 메시지 전송
        Message message = createPersistentMessage(persistentRequest);
        rabbitTemplate.send(
            UnifiedRabbitMQConfig.CRAWLING_EXCHANGE,
            UnifiedRabbitMQConfig.MEDIUM_ROUTING_KEY,
            message
        );
        
        // 잠시 대기 후 메시지 확인
        Thread.sleep(1000);
        
        // Then - 메시지가 큐에 지속되어 있는지 확인
        Properties queueProps = rabbitAdmin.getQueueProperties(UnifiedRabbitMQConfig.CRAWLING_MEDIUM_QUEUE);
        assertThat(queueProps).isNotNull();
        
        Object received = rabbitTemplate.receiveAndConvert(
            UnifiedRabbitMQConfig.CRAWLING_MEDIUM_QUEUE, 
            5000
        );
        
        assertThat(received).isNotNull();
        System.out.println("✅ 메시지 지속성 확인 완료");
    }
    
    // ==================== 동시성 실제 테스트 ====================
    
    @Test
    @DisplayName("실제 동시성 처리 테스트")
    void testRealConcurrentProcessing() throws Exception {
        if (rabbitTemplate == null) {
            System.out.println("⏭️ RabbitMQ 연결 없음 - 테스트 스킵");
            return;
        }
        
        // Given
        int threadCount = 5;
        int messagesPerThread = 10;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        
        List<String> allSentIds = Collections.synchronizedList(new ArrayList<>());
        
        // When - 동시 메시지 전송
        for (int t = 0; t < threadCount; t++) {
            final int threadId = t;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    
                    for (int i = 0; i < messagesPerThread; i++) {
                        String officeId = "concurrent_" + threadId + "_" + i;
                        NaverCrawlingRequest request = createTestRequest(officeId, 5);
                        allSentIds.add(officeId);
                        
                        rabbitTemplate.convertAndSend(
                            UnifiedRabbitMQConfig.CRAWLING_EXCHANGE,
                            UnifiedRabbitMQConfig.LIGHT_ROUTING_KEY,
                            request
                        );
                    }
                } catch (Exception e) {
                    System.err.println("스레드 " + threadId + " 에러: " + e.getMessage());
                } finally {
                    endLatch.countDown();
                }
            });
        }
        
        startLatch.countDown();
        boolean completed = endLatch.await(30, TimeUnit.SECONDS);
        assertThat(completed).isTrue();
        
        // Then - 모든 메시지 수신 확인
        List<String> receivedIds = new ArrayList<>();
        int totalMessages = threadCount * messagesPerThread;
        
        for (int i = 0; i < totalMessages; i++) {
            Object received = rabbitTemplate.receiveAndConvert(
                UnifiedRabbitMQConfig.CRAWLING_LIGHT_QUEUE,
                15000 // 15초 대기
            );
            
            if (received != null) {
                NaverCrawlingRequest req = (NaverCrawlingRequest) received;
                receivedIds.add(req.getOfficeId());
            }
        }
        
        assertThat(receivedIds).hasSize(totalMessages);
        System.out.println("✅ 동시성 처리 완료: " + receivedIds.size() + "개 메시지");
        
        executor.shutdown();
    }
    
    // ==================== 결과 메시지 처리 테스트 ====================
    
    @Test
    @DisplayName("크롤링 결과 메시지 실제 처리")
    void testCrawlingResultProcessingReal() throws Exception {
        if (rabbitTemplate == null) {
            System.out.println("⏭️ RabbitMQ 연결 없음 - 테스트 스킵");
            return;
        }
        
        // Given
        Map<String, Object> crawlingResult = new HashMap<>();
        crawlingResult.put("sessionId", "real_session_123");
        crawlingResult.put("status", "completed");
        crawlingResult.put("totalArticles", 42);
        crawlingResult.put("articles", Arrays.asList(
            createArticleData("article1", "제목1"),
            createArticleData("article2", "제목2")
        ));
        
        // When - 결과 메시지 전송
        rabbitTemplate.convertAndSend(
            UnifiedRabbitMQConfig.CRAWLING_EXCHANGE,
            UnifiedRabbitMQConfig.RESULT_ROUTING_KEY,
            crawlingResult
        );
        
        // Then - 결과 큐에서 메시지 수신
        Object received = rabbitTemplate.receiveAndConvert(
            UnifiedRabbitMQConfig.CRAWLING_RESULT_QUEUE,
            5000
        );
        
        assertThat(received).isNotNull();
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) received;
        assertThat(result.get("sessionId")).isEqualTo("real_session_123");
        assertThat(result.get("totalArticles")).isEqualTo(42);
        
        System.out.println("✅ 크롤링 결과 메시지 처리 완료");
    }
    
    // ==================== 헬퍼 메서드 ====================
    
    private NaverCrawlingRequest createTestRequest(String officeId, int maxArticles) {
        return NaverCrawlingRequest.builder()
            .officeId(officeId)
            .categoryId("100")
            .maxArticles(maxArticles)
            .sessionId("integration_test_" + System.currentTimeMillis())
            .build();
    }
    
    private String getExpectedQueueBySize(int maxArticles) {
        if (maxArticles <= 10) {
            return UnifiedRabbitMQConfig.CRAWLING_LIGHT_QUEUE;
        } else if (maxArticles <= 50) {
            return UnifiedRabbitMQConfig.CRAWLING_MEDIUM_QUEUE;
        } else {
            return UnifiedRabbitMQConfig.CRAWLING_HEAVY_QUEUE;
        }
    }
    
    private Message createPersistentMessage(NaverCrawlingRequest request) throws Exception {
        MessageProperties properties = new MessageProperties();
        properties.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
        properties.setContentType("application/json");
        
        String json = objectMapper.writeValueAsString(request);
        return MessageBuilder
            .withBody(json.getBytes(StandardCharsets.UTF_8))
            .andProperties(properties)
            .build();
    }
    
    private Map<String, Object> createArticleData(String id, String title) {
        Map<String, Object> article = new HashMap<>();
        article.put("id", id);
        article.put("title", title);
        article.put("url", "https://test.com/" + id);
        article.put("timestamp", System.currentTimeMillis());
        return article;
    }
    
    private void purgeAllQueues() {
        if (rabbitAdmin == null) return;
        
        try {
            rabbitAdmin.purgeQueue(UnifiedRabbitMQConfig.CRAWLING_LIGHT_QUEUE);
            rabbitAdmin.purgeQueue(UnifiedRabbitMQConfig.CRAWLING_MEDIUM_QUEUE);
            rabbitAdmin.purgeQueue(UnifiedRabbitMQConfig.CRAWLING_HEAVY_QUEUE);
            rabbitAdmin.purgeQueue(UnifiedRabbitMQConfig.CRAWLING_RESULT_QUEUE);
            rabbitAdmin.purgeQueue(UnifiedRabbitMQConfig.CRAWLING_PROGRESS_QUEUE);
            rabbitAdmin.purgeQueue(UnifiedRabbitMQConfig.CRAWLING_DLQ);
        } catch (Exception e) {
            System.out.println("⚠️ 큐 정리 중 에러 (무시): " + e.getMessage());
        }
    }
}