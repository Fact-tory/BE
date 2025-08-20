package com.commonground.be.infrastructure.rabbitmq;

import com.commonground.be.domain.news.dto.request.NaverCrawlingRequest;
import com.commonground.be.global.infrastructure.config.UnifiedRabbitMQConfig;
import com.commonground.be.global.infrastructure.messaging.CrawlingMessageService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.util.Properties;

/**
 * 🔍 RabbitMQ 메시지 가시성 테스트
 * 
 * 메시지가 큐에 실제로 들어가는지 확인하고, 
 * 소비자가 너무 빨리 처리해서 보이지 않는지 테스트
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
    "spring.rabbitmq.listener.simple.auto-startup=false"  // 컨슈머 자동 시작 비활성화
})
@EnabledIfEnvironmentVariable(named = "RABBITMQ_INTEGRATION_TEST", matches = "true")
class MessageVisibilityTest {
    
    private static final Logger log = LoggerFactory.getLogger(MessageVisibilityTest.class);

    @Autowired(required = false)
    private CrawlingMessageService crawlingMessageService;
    
    @Autowired(required = false)
    private RabbitTemplate rabbitTemplate;
    
    @Autowired(required = false)
    private RabbitAdmin rabbitAdmin;
    
    @Test
    @DisplayName("메시지를 큐에 전송하고 소비하지 않고 누적시키기")
    void testMessageAccumulation() throws InterruptedException {
        if (crawlingMessageService == null || rabbitTemplate == null) {
            log.info("⏭️ RabbitMQ 연결 없음 - 테스트 스킵");
            return;
        }
        
        log.info("🚀 메시지 대량 전송 시작 - 컨슈머 비활성화 상태");
        
        // 각 큐에 메시지 10개씩 전송
        for (int i = 1; i <= 10; i++) {
            // Light 큐 (5개 기사)
            NaverCrawlingRequest lightReq = createTestRequest("light_" + i, 5);
            crawlingMessageService.sendCrawlingRequest(lightReq);
            
            // Medium 큐 (25개 기사)
            NaverCrawlingRequest mediumReq = createTestRequest("medium_" + i, 25);
            crawlingMessageService.sendCrawlingRequest(mediumReq);
            
            // Heavy 큐 (80개 기사)
            NaverCrawlingRequest heavyReq = createTestRequest("heavy_" + i, 80);
            crawlingMessageService.sendCrawlingRequest(heavyReq);
            
            // 결과 메시지도 전송
            String resultMsg = String.format("크롤링 완료 %d: %d개 기사", i, i * 5);
            crawlingMessageService.sendResult(resultMsg);
            
            log.info("📤 배치 {} 전송 완료", i);
            Thread.sleep(100); // 약간의 지연
        }
        
        // 메시지 전송 완료 후 잠시 대기
        Thread.sleep(2000);
        
        // 큐 상태 확인
        checkQueueStatus();
        
        log.info("✅ 총 40개 메시지 전송 완료 (각 큐별 10개씩)");
        log.info("🔍 RabbitMQ Management UI (http://localhost:15672)에서 큐 상태를 확인하세요");
        log.info("📊 메시지가 큐에 누적되어 있어야 합니다 (컨슈머가 비활성화됨)");
        
        // 메시지가 보이도록 더 오래 대기
        Thread.sleep(10000);
    }
    
    @Test
    @DisplayName("개별 메시지 전송 후 즉시 확인")
    void testIndividualMessageVisibility() throws InterruptedException {
        if (crawlingMessageService == null || rabbitTemplate == null) {
            log.info("⏭️ RabbitMQ 연결 없음 - 테스트 스킵");
            return;
        }
        
        log.info("🎯 개별 메시지 전송 및 확인");
        
        // 하나의 메시지만 전송
        NaverCrawlingRequest request = createTestRequest("visibility_test", 15);
        
        log.info("📤 메시지 전송 중...");
        crawlingMessageService.sendCrawlingRequest(request);
        
        Thread.sleep(500);
        checkQueueStatus();
        
        log.info("⏰ 5초 대기 - 이 시간 동안 Management UI에서 확인하세요");
        Thread.sleep(5000);
        
        checkQueueStatus();
    }
    
    private NaverCrawlingRequest createTestRequest(String officeId, int maxArticles) {
        return NaverCrawlingRequest.builder()
            .officeId(officeId)
            .categoryId("100")
            .maxArticles(maxArticles)
            .sessionId("visibility_test_" + System.currentTimeMillis())
            .build();
    }
    
    private void checkQueueStatus() {
        if (rabbitAdmin == null) return;
        
        try {
            Properties lightProps = rabbitAdmin.getQueueProperties(UnifiedRabbitMQConfig.CRAWLING_LIGHT_QUEUE);
            Properties mediumProps = rabbitAdmin.getQueueProperties(UnifiedRabbitMQConfig.CRAWLING_MEDIUM_QUEUE);
            Properties heavyProps = rabbitAdmin.getQueueProperties(UnifiedRabbitMQConfig.CRAWLING_HEAVY_QUEUE);
            Properties resultProps = rabbitAdmin.getQueueProperties(UnifiedRabbitMQConfig.CRAWLING_RESULT_QUEUE);
            
            if (lightProps != null) {
                log.info("📊 Light Queue: {} 메시지", lightProps.get("QUEUE_MESSAGE_COUNT"));
            }
            if (mediumProps != null) {
                log.info("📊 Medium Queue: {} 메시지", mediumProps.get("QUEUE_MESSAGE_COUNT"));
            }
            if (heavyProps != null) {
                log.info("📊 Heavy Queue: {} 메시지", heavyProps.get("QUEUE_MESSAGE_COUNT"));
            }
            if (resultProps != null) {
                log.info("📊 Result Queue: {} 메시지", resultProps.get("QUEUE_MESSAGE_COUNT"));
            }
        } catch (Exception e) {
            log.warn("⚠️ 큐 상태 확인 실패: {}", e.getMessage());
        }
    }
}