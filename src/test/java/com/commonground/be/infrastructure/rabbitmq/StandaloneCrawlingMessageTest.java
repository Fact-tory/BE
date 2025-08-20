package com.commonground.be.infrastructure.rabbitmq;

import com.commonground.be.domain.news.dto.request.NaverCrawlingRequest;
import com.commonground.be.global.infrastructure.config.UnifiedRabbitMQConfig;
import com.commonground.be.global.infrastructure.messaging.CrawlingMessageService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.util.Properties;

import static org.assertj.core.api.Assertions.*;

/**
 * 🚀 실제 메시지 전송/수신 테스트
 * 
 * RabbitMQ 서버가 실행 중일 때 실제 큐에 메시지를 전송하고 확인하는 테스트
 * 
 * 사용법:
 * 1. docker-compose -f docker-compose.dev.yml up rabbitmq -d
 * 2. RABBITMQ_INTEGRATION_TEST=true ./gradlew test --tests="*StandaloneCrawlingMessageTest"
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
    "spring.rabbitmq.publisher-returns=true"
})
@EnabledIfEnvironmentVariable(named = "RABBITMQ_INTEGRATION_TEST", matches = "true")
class StandaloneCrawlingMessageTest {
    
    private static final Logger log = LoggerFactory.getLogger(StandaloneCrawlingMessageTest.class);

    @Autowired(required = false)
    private CrawlingMessageService crawlingMessageService;
    
    @Autowired(required = false)
    private RabbitTemplate rabbitTemplate;
    
    @Autowired(required = false)
    private RabbitAdmin rabbitAdmin;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @BeforeEach
    void setUp() {
        // RabbitMQ가 실행 중이지 않으면 테스트 스킵
        if (rabbitTemplate == null || rabbitAdmin == null || crawlingMessageService == null) {
            log.warn("⚠️ RabbitMQ가 실행되지 않음. 테스트 스킵");
            return;
        }
        
        // 테스트 전 큐 정리
        purgeAllQueues();
        log.info("🧹 테스트 큐 정리 완료");
    }
    
    @Test
    @DisplayName("실제 크롤링 요청 메시지 전송 테스트")
    void testSendCrawlingRequestMessage() {
        if (crawlingMessageService == null) {
            log.info("⏭️ RabbitMQ 연결 없음 - 테스트 스킵");
            return;
        }
        
        // Given
        NaverCrawlingRequest lightRequest = createTestRequest("light_test", 5);
        NaverCrawlingRequest mediumRequest = createTestRequest("medium_test", 25);
        NaverCrawlingRequest heavyRequest = createTestRequest("heavy_test", 80);
        
        // When - 각 유형별 메시지 전송
        crawlingMessageService.sendCrawlingRequest(lightRequest);
        crawlingMessageService.sendCrawlingRequest(mediumRequest);
        crawlingMessageService.sendCrawlingRequest(heavyRequest);
        
        // 메시지 전송 후 잠시 대기
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // Then - 각 큐에서 메시지 확인
        Object lightReceived = rabbitTemplate.receiveAndConvert(UnifiedRabbitMQConfig.CRAWLING_LIGHT_QUEUE, 3000);
        Object mediumReceived = rabbitTemplate.receiveAndConvert(UnifiedRabbitMQConfig.CRAWLING_MEDIUM_QUEUE, 3000);
        Object heavyReceived = rabbitTemplate.receiveAndConvert(UnifiedRabbitMQConfig.CRAWLING_HEAVY_QUEUE, 3000);
        
        assertThat(lightReceived).isNotNull();
        assertThat(mediumReceived).isNotNull();
        assertThat(heavyReceived).isNotNull();
        
        NaverCrawlingRequest lightReq = (NaverCrawlingRequest) lightReceived;
        NaverCrawlingRequest mediumReq = (NaverCrawlingRequest) mediumReceived;
        NaverCrawlingRequest heavyReq = (NaverCrawlingRequest) heavyReceived;
        
        assertThat(lightReq.getOfficeId()).isEqualTo("light_test");
        assertThat(mediumReq.getOfficeId()).isEqualTo("medium_test");
        assertThat(heavyReq.getOfficeId()).isEqualTo("heavy_test");
        
        log.info("✅ 모든 크롤링 요청 메시지 전송/수신 완료");
        log.info("   - Light: {} ({}개 기사)", lightReq.getOfficeId(), lightReq.getMaxArticles());
        log.info("   - Medium: {} ({}개 기사)", mediumReq.getOfficeId(), mediumReq.getMaxArticles());
        log.info("   - Heavy: {} ({}개 기사)", heavyReq.getOfficeId(), heavyReq.getMaxArticles());
    }
    
    @Test
    @DisplayName("큐 상태 및 연결 상태 확인")
    void testQueueStatusAndConnection() {
        if (rabbitAdmin == null) {
            log.info("⏭️ RabbitMQ 연결 없음 - 테스트 스킵");
            return;
        }
        
        // Given & When & Then
        Properties lightProps = rabbitAdmin.getQueueProperties(UnifiedRabbitMQConfig.CRAWLING_LIGHT_QUEUE);
        Properties mediumProps = rabbitAdmin.getQueueProperties(UnifiedRabbitMQConfig.CRAWLING_MEDIUM_QUEUE);
        Properties heavyProps = rabbitAdmin.getQueueProperties(UnifiedRabbitMQConfig.CRAWLING_HEAVY_QUEUE);
        Properties resultProps = rabbitAdmin.getQueueProperties(UnifiedRabbitMQConfig.CRAWLING_RESULT_QUEUE);
        
        assertThat(lightProps).isNotNull();
        assertThat(mediumProps).isNotNull();
        assertThat(heavyProps).isNotNull();
        assertThat(resultProps).isNotNull();
        
        log.info("✅ 모든 큐가 정상적으로 생성되어 있음");
        log.info("   - Light Queue: {} 메시지", lightProps.get("QUEUE_MESSAGE_COUNT"));
        log.info("   - Medium Queue: {} 메시지", mediumProps.get("QUEUE_MESSAGE_COUNT"));
        log.info("   - Heavy Queue: {} 메시지", heavyProps.get("QUEUE_MESSAGE_COUNT"));
        log.info("   - Result Queue: {} 메시지", resultProps.get("QUEUE_MESSAGE_COUNT"));
    }
    
    @Test
    @DisplayName("결과 메시지 전송 테스트")
    void testSendResultMessage() {
        if (crawlingMessageService == null) {
            log.info("⏭️ RabbitMQ 연결 없음 - 테스트 스킵");
            return;
        }
        
        // Given
        String testResult = "크롤링 완료: 42개 기사 수집";
        
        // When
        crawlingMessageService.sendResult(testResult);
        
        // 메시지 전송 후 잠시 대기
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // Then
        Object received = rabbitTemplate.receiveAndConvert(UnifiedRabbitMQConfig.CRAWLING_RESULT_QUEUE, 3000);
        
        assertThat(received).isNotNull();
        assertThat(received.toString()).isEqualTo(testResult);
        
        log.info("✅ 결과 메시지 전송/수신 완료: {}", received);
    }
    
    @Test
    @DisplayName("메시지 라우팅 키 자동 선택 확인")
    void testAutomaticRoutingKeySelection() {
        // Given & When & Then
        String lightKey = UnifiedRabbitMQConfig.getRoutingKeyBySize(5);
        String mediumKey = UnifiedRabbitMQConfig.getRoutingKeyBySize(25);
        String heavyKey = UnifiedRabbitMQConfig.getRoutingKeyBySize(80);
        
        assertThat(lightKey).isEqualTo(UnifiedRabbitMQConfig.LIGHT_ROUTING_KEY);
        assertThat(mediumKey).isEqualTo(UnifiedRabbitMQConfig.MEDIUM_ROUTING_KEY);
        assertThat(heavyKey).isEqualTo(UnifiedRabbitMQConfig.HEAVY_ROUTING_KEY);
        
        log.info("✅ 자동 라우팅 키 선택 확인 완료");
        log.info("   - 5개 기사 -> {}", lightKey);
        log.info("   - 25개 기사 -> {}", mediumKey);
        log.info("   - 80개 기사 -> {}", heavyKey);
    }
    
    // ==================== 헬퍼 메서드 ====================
    
    private NaverCrawlingRequest createTestRequest(String officeId, int maxArticles) {
        return NaverCrawlingRequest.builder()
            .officeId(officeId)
            .categoryId("100")
            .maxArticles(maxArticles)
            .sessionId("standalone_test_" + System.currentTimeMillis())
            .build();
    }
    
    private void purgeAllQueues() {
        if (rabbitAdmin == null) return;
        
        try {
            rabbitAdmin.purgeQueue(UnifiedRabbitMQConfig.CRAWLING_LIGHT_QUEUE);
            rabbitAdmin.purgeQueue(UnifiedRabbitMQConfig.CRAWLING_MEDIUM_QUEUE);
            rabbitAdmin.purgeQueue(UnifiedRabbitMQConfig.CRAWLING_HEAVY_QUEUE);
            rabbitAdmin.purgeQueue(UnifiedRabbitMQConfig.CRAWLING_RESULT_QUEUE);
            rabbitAdmin.purgeQueue(UnifiedRabbitMQConfig.CRAWLING_PROGRESS_QUEUE);
        } catch (Exception e) {
            log.warn("⚠️ 큐 정리 중 에러 (무시): {}", e.getMessage());
        }
    }
}