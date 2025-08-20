package com.commonground.be.infrastructure.rabbitmq;

import com.commonground.be.global.infrastructure.config.UnifiedRabbitMQConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * 🧪 RabbitMQ 설정 단위 테스트
 * 
 * Spring 컨텍스트 없이 순수 로직만 테스트
 */
class RabbitMQConfigTest {

    @Test
    @DisplayName("요청 크기별 자동 라우팅 로직 테스트")
    void testAutomaticRoutingBySize() {
        // Given & When & Then
        // Light 큐 (1-10개)
        assertThat(UnifiedRabbitMQConfig.getRoutingKeyBySize(1))
            .isEqualTo(UnifiedRabbitMQConfig.LIGHT_ROUTING_KEY);
        assertThat(UnifiedRabbitMQConfig.getRoutingKeyBySize(5))
            .isEqualTo(UnifiedRabbitMQConfig.LIGHT_ROUTING_KEY);
        assertThat(UnifiedRabbitMQConfig.getRoutingKeyBySize(10))
            .isEqualTo(UnifiedRabbitMQConfig.LIGHT_ROUTING_KEY);
        
        // Medium 큐 (11-50개)
        assertThat(UnifiedRabbitMQConfig.getRoutingKeyBySize(11))
            .isEqualTo(UnifiedRabbitMQConfig.MEDIUM_ROUTING_KEY);
        assertThat(UnifiedRabbitMQConfig.getRoutingKeyBySize(30))
            .isEqualTo(UnifiedRabbitMQConfig.MEDIUM_ROUTING_KEY);
        assertThat(UnifiedRabbitMQConfig.getRoutingKeyBySize(50))
            .isEqualTo(UnifiedRabbitMQConfig.MEDIUM_ROUTING_KEY);
        
        // Heavy 큐 (51개 이상)
        assertThat(UnifiedRabbitMQConfig.getRoutingKeyBySize(51))
            .isEqualTo(UnifiedRabbitMQConfig.HEAVY_ROUTING_KEY);
        assertThat(UnifiedRabbitMQConfig.getRoutingKeyBySize(100))
            .isEqualTo(UnifiedRabbitMQConfig.HEAVY_ROUTING_KEY);
        assertThat(UnifiedRabbitMQConfig.getRoutingKeyBySize(1000))
            .isEqualTo(UnifiedRabbitMQConfig.HEAVY_ROUTING_KEY);
        
        System.out.println("✅ 자동 라우팅 로직 검증 완료");
    }
    
    @Test
    @DisplayName("큐 이름 상수 값 검증")
    void testQueueNames() {
        // Given & When & Then
        assertThat(UnifiedRabbitMQConfig.CRAWLING_LIGHT_QUEUE).isEqualTo("crawling.light.queue");
        assertThat(UnifiedRabbitMQConfig.CRAWLING_MEDIUM_QUEUE).isEqualTo("crawling.medium.queue");
        assertThat(UnifiedRabbitMQConfig.CRAWLING_HEAVY_QUEUE).isEqualTo("crawling.heavy.queue");
        assertThat(UnifiedRabbitMQConfig.CRAWLING_RESULT_QUEUE).isEqualTo("crawling.result.queue");
        assertThat(UnifiedRabbitMQConfig.CRAWLING_PROGRESS_QUEUE).isEqualTo("crawling.progress.queue");
        assertThat(UnifiedRabbitMQConfig.CRAWLING_DLQ).isEqualTo("crawling.dlq");
        
        System.out.println("✅ 큐 이름 상수 검증 완료");
    }
    
    @Test
    @DisplayName("라우팅 키 상수 값 검증")
    void testRoutingKeys() {
        // Given & When & Then
        assertThat(UnifiedRabbitMQConfig.LIGHT_ROUTING_KEY).isEqualTo("crawling.light");
        assertThat(UnifiedRabbitMQConfig.MEDIUM_ROUTING_KEY).isEqualTo("crawling.medium");
        assertThat(UnifiedRabbitMQConfig.HEAVY_ROUTING_KEY).isEqualTo("crawling.heavy");
        assertThat(UnifiedRabbitMQConfig.RESULT_ROUTING_KEY).isEqualTo("crawling.result");
        assertThat(UnifiedRabbitMQConfig.PROGRESS_ROUTING_KEY).isEqualTo("crawling.progress");
        
        System.out.println("✅ 라우팅 키 상수 검증 완료");
    }
    
    @Test
    @DisplayName("Exchange 이름 상수 값 검증")
    void testExchangeName() {
        // Given & When & Then
        assertThat(UnifiedRabbitMQConfig.CRAWLING_EXCHANGE).isEqualTo("crawling.exchange");
        
        System.out.println("✅ Exchange 이름 상수 검증 완료");
    }
    
    @Test
    @DisplayName("경계값 테스트")
    void testBoundaryValues() {
        // Given & When & Then
        // Light/Medium 경계 (10 vs 11)
        assertThat(UnifiedRabbitMQConfig.getRoutingKeyBySize(10))
            .isEqualTo(UnifiedRabbitMQConfig.LIGHT_ROUTING_KEY);
        assertThat(UnifiedRabbitMQConfig.getRoutingKeyBySize(11))
            .isEqualTo(UnifiedRabbitMQConfig.MEDIUM_ROUTING_KEY);
        
        // Medium/Heavy 경계 (50 vs 51)
        assertThat(UnifiedRabbitMQConfig.getRoutingKeyBySize(50))
            .isEqualTo(UnifiedRabbitMQConfig.MEDIUM_ROUTING_KEY);
        assertThat(UnifiedRabbitMQConfig.getRoutingKeyBySize(51))
            .isEqualTo(UnifiedRabbitMQConfig.HEAVY_ROUTING_KEY);
        
        // 최소값 테스트
        assertThat(UnifiedRabbitMQConfig.getRoutingKeyBySize(0))
            .isEqualTo(UnifiedRabbitMQConfig.LIGHT_ROUTING_KEY);
        assertThat(UnifiedRabbitMQConfig.getRoutingKeyBySize(-1))
            .isEqualTo(UnifiedRabbitMQConfig.LIGHT_ROUTING_KEY);
        
        System.out.println("✅ 경계값 테스트 완료");
    }
}