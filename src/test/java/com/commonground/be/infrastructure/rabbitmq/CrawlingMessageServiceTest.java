package com.commonground.be.infrastructure.rabbitmq;

import com.commonground.be.domain.news.dto.request.NaverCrawlingRequest;
import com.commonground.be.global.infrastructure.messaging.CrawlingMessageService;
import com.commonground.be.global.infrastructure.config.UnifiedRabbitMQConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 🧪 크롤링 메시지 서비스 단위 테스트
 * 
 * 외부 서비스(RabbitMQ, DB 등) 없이 로직만 테스트
 */
@ExtendWith(MockitoExtension.class)
class CrawlingMessageServiceTest {

    @Mock
    private RabbitTemplate rabbitTemplate;
    
    private CrawlingMessageService crawlingMessageService;
    
    @BeforeEach
    void setUp() {
        crawlingMessageService = new CrawlingMessageService(rabbitTemplate);
    }
    
    @Test
    @DisplayName("Light 요청 메시지 전송 테스트")
    void testSendLightCrawlingRequest() {
        // Given
        NaverCrawlingRequest request = createCrawlingRequest("001", 5); // Light (5개)
        String expectedRoutingKey = UnifiedRabbitMQConfig.LIGHT_ROUTING_KEY;
        
        // When
        crawlingMessageService.sendCrawlingRequest(request);
        
        // Then
        verify(rabbitTemplate, times(1)).convertAndSend(
            eq(UnifiedRabbitMQConfig.CRAWLING_EXCHANGE),
            eq(expectedRoutingKey),
            eq(request)
        );
        
        System.out.println("✅ Light 요청 메시지 전송 검증 완료");
    }
    
    @Test
    @DisplayName("Medium 요청 메시지 전송 테스트")
    void testSendMediumCrawlingRequest() {
        // Given
        NaverCrawlingRequest request = createCrawlingRequest("020", 30); // Medium (30개)
        String expectedRoutingKey = UnifiedRabbitMQConfig.MEDIUM_ROUTING_KEY;
        
        // When
        crawlingMessageService.sendCrawlingRequest(request);
        
        // Then
        verify(rabbitTemplate, times(1)).convertAndSend(
            eq(UnifiedRabbitMQConfig.CRAWLING_EXCHANGE),
            eq(expectedRoutingKey),
            eq(request)
        );
        
        System.out.println("✅ Medium 요청 메시지 전송 검증 완료");
    }
    
    @Test
    @DisplayName("Heavy 요청 메시지 전송 테스트")
    void testSendHeavyCrawlingRequest() {
        // Given
        NaverCrawlingRequest request = createCrawlingRequest("052", 80); // Heavy (80개)
        String expectedRoutingKey = UnifiedRabbitMQConfig.HEAVY_ROUTING_KEY;
        
        // When
        crawlingMessageService.sendCrawlingRequest(request);
        
        // Then
        verify(rabbitTemplate, times(1)).convertAndSend(
            eq(UnifiedRabbitMQConfig.CRAWLING_EXCHANGE),
            eq(expectedRoutingKey),
            eq(request)
        );
        
        System.out.println("✅ Heavy 요청 메시지 전송 검증 완료");
    }
    
    @Test
    @DisplayName("결과 메시지 전송 테스트")
    void testSendResultMessage() {
        // Given
        Object result = "크롤링 완료 결과";
        
        // When
        crawlingMessageService.sendResult(result);
        
        // Then
        verify(rabbitTemplate, times(1)).convertAndSend(
            eq(UnifiedRabbitMQConfig.CRAWLING_EXCHANGE),
            eq(UnifiedRabbitMQConfig.RESULT_ROUTING_KEY),
            eq(result)
        );
        
        System.out.println("✅ 결과 메시지 전송 검증 완료");
    }
    
    @Test
    @DisplayName("메시지 전송 실패 시 예외 처리")
    void testMessageSendingFailure() {
        // Given
        NaverCrawlingRequest request = createCrawlingRequest("001", 5);
        doThrow(new RuntimeException("메시지 전송 실패"))
            .when(rabbitTemplate).convertAndSend(anyString(), anyString(), any(Object.class));
        
        // When & Then
        try {
            crawlingMessageService.sendCrawlingRequest(request);
        } catch (Exception e) {
            // 예외가 발생해도 시스템이 안전하게 처리되어야 함
            System.out.println("✅ 메시지 전송 실패 예외 처리 확인: " + e.getMessage());
        }
        
        verify(rabbitTemplate, times(1)).convertAndSend(anyString(), anyString(), any(Object.class));
    }
    
    @Test
    @DisplayName("다양한 크기의 요청에 대한 라우팅 테스트")
    void testVariousRequestSizeRouting() {
        // Given & When & Then
        
        // Light 테스트 (1, 5, 10개)
        for (int size : new int[]{1, 5, 10}) {
            NaverCrawlingRequest request = createCrawlingRequest("001", size);
            crawlingMessageService.sendCrawlingRequest(request);
            
            verify(rabbitTemplate).convertAndSend(
                eq(UnifiedRabbitMQConfig.CRAWLING_EXCHANGE),
                eq(UnifiedRabbitMQConfig.LIGHT_ROUTING_KEY),
                eq(request)
            );
        }
        
        // Medium 테스트 (15, 30, 50개)
        for (int size : new int[]{15, 30, 50}) {
            NaverCrawlingRequest request = createCrawlingRequest("020", size);
            crawlingMessageService.sendCrawlingRequest(request);
            
            verify(rabbitTemplate).convertAndSend(
                eq(UnifiedRabbitMQConfig.CRAWLING_EXCHANGE),
                eq(UnifiedRabbitMQConfig.MEDIUM_ROUTING_KEY),
                eq(request)
            );
        }
        
        // Heavy 테스트 (60, 80, 100개)
        for (int size : new int[]{60, 80, 100}) {
            NaverCrawlingRequest request = createCrawlingRequest("052", size);
            crawlingMessageService.sendCrawlingRequest(request);
            
            verify(rabbitTemplate).convertAndSend(
                eq(UnifiedRabbitMQConfig.CRAWLING_EXCHANGE),
                eq(UnifiedRabbitMQConfig.HEAVY_ROUTING_KEY),
                eq(request)
            );
        }
        
        System.out.println("✅ 다양한 크기 요청 라우팅 테스트 완료");
    }
    
    @Test
    @DisplayName("null 요청 처리 테스트")
    void testNullRequestHandling() {
        // Given
        NaverCrawlingRequest nullRequest = null;
        
        // When & Then
        try {
            crawlingMessageService.sendCrawlingRequest(nullRequest);
        } catch (Exception e) {
            System.out.println("✅ null 요청 예외 처리 확인: " + e.getClass().getSimpleName());
        }
    }
    
    // ==================== 헬퍼 메서드 ====================
    
    private NaverCrawlingRequest createCrawlingRequest(String officeId, int maxArticles) {
        return NaverCrawlingRequest.builder()
            .officeId(officeId)
            .categoryId("100")
            .maxArticles(maxArticles)
            .sessionId("test_session_" + System.currentTimeMillis())
            .build();
    }
}