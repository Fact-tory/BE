package com.commonground.be.global.infrastructure.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.RabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.amqp.rabbit.retry.MessageRecoverer;
import org.springframework.amqp.rabbit.retry.RepublishMessageRecoverer;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;

import java.util.HashMap;
import java.util.Map;

/**
 * RabbitMQ 고급 설정 - 재시도, DLQ, 타임아웃 처리
 * Python 크롤러와의 안정적인 통신을 위한 고도화된 메시지 처리
 */
@Configuration
public class RabbitMQAdvancedConfig {

    @Value("${spring.rabbitmq.listener.simple.retry.max-attempts:3}")
    private int maxRetryAttempts;

    @Value("${spring.rabbitmq.listener.simple.retry.initial-interval:5000}")
    private long initialRetryInterval;

    @Value("${spring.rabbitmq.message.ttl:600000}")  // 10분 (기존 큐와 일치)
    private long messageTtl;

    // ==================== Exchange 설정 ====================

    @Bean
    public TopicExchange crawlingExchange() {
        return ExchangeBuilder
                .topicExchange(RabbitMQConfig.CRAWLING_EXCHANGE)
                .durable(true)
                .build();
    }

    @Bean
    public TopicExchange crawlingDeadLetterExchange() {
        return ExchangeBuilder
                .topicExchange("crawling.dlx")
                .durable(true)
                .build();
    }

    // ==================== Dead Letter Queue 설정 ====================

    @Bean
    public Queue crawlingRequestDeadLetterQueue() {
        return QueueBuilder
                .durable("crawling.request.dlq")
                .withArgument("x-message-ttl", 86400000) // 24시간 후 자동 삭제
                .build();
    }

    @Bean
    public Queue crawlingResultDeadLetterQueue() {
        return QueueBuilder
                .durable("crawling.result.dlq")
                .withArgument("x-message-ttl", 86400000)
                .build();
    }

    @Bean
    public Binding crawlingRequestDlqBinding() {
        return BindingBuilder
                .bind(crawlingRequestDeadLetterQueue())
                .to(crawlingDeadLetterExchange())
                .with("request.dlq");
    }

    @Bean
    public Binding crawlingResultDlqBinding() {
        return BindingBuilder
                .bind(crawlingResultDeadLetterQueue())
                .to(crawlingDeadLetterExchange())
                .with("result.dlq");
    }

    // ==================== 메인 큐 재설정 (DLX 포함) ====================

    @Bean
    public Queue enhancedCrawlingRequestQueue() {
        return QueueBuilder
                .durable(RabbitMQConfig.CRAWLING_REQUEST_QUEUE)
                .withArgument("x-dead-letter-exchange", "crawling.dlx")
                .withArgument("x-dead-letter-routing-key", "request.dlq")
                .withArgument("x-message-ttl", messageTtl)
                .build();
    }

    @Bean
    public Queue enhancedCrawlingResultQueue() {
        return QueueBuilder
                .durable(RabbitMQConfig.CRAWLING_RESULT_QUEUE)
                .withArgument("x-dead-letter-exchange", "crawling.dlx")
                .withArgument("x-dead-letter-routing-key", "result.dlq")
                .withArgument("x-message-ttl", messageTtl)
                .build();
    }

    @Bean
    public Queue enhancedCrawlingProgressQueue() {
        return QueueBuilder
                .durable(RabbitMQConfig.CRAWLING_PROGRESS_QUEUE)
                .withArgument("x-message-ttl", 60000) // 1분 (진행상황은 짧게)
                .build();
    }

    // ==================== 메인 큐 바인딩 ====================

    @Bean
    public Binding enhancedCrawlingRequestBinding() {
        return BindingBuilder
                .bind(enhancedCrawlingRequestQueue())
                .to(crawlingExchange())
                .with(RabbitMQConfig.REQUEST_ROUTING_KEY);
    }

    @Bean
    public Binding enhancedCrawlingResultBinding() {
        return BindingBuilder
                .bind(enhancedCrawlingResultQueue())
                .to(crawlingExchange())
                .with(RabbitMQConfig.RESULT_ROUTING_KEY);
    }

    @Bean
    public Binding enhancedCrawlingProgressBinding() {
        return BindingBuilder
                .bind(enhancedCrawlingProgressQueue())
                .to(crawlingExchange())
                .with(RabbitMQConfig.PROGRESS_ROUTING_KEY);
    }

    // ==================== Retry Template 설정 ====================

    @Bean
    public RetryTemplate crawlingRetryTemplate() {
        RetryTemplate retryTemplate = new RetryTemplate();

        // 재시도 정책
        SimpleRetryPolicy retryPolicy = new SimpleRetryPolicy();
        retryPolicy.setMaxAttempts(maxRetryAttempts);
        retryTemplate.setRetryPolicy(retryPolicy);

        // 백오프 정책 (지수적 증가)
        ExponentialBackOffPolicy backOffPolicy = new ExponentialBackOffPolicy();
        backOffPolicy.setInitialInterval(initialRetryInterval);
        backOffPolicy.setMultiplier(2.0);
        backOffPolicy.setMaxInterval(60000); // 최대 1분
        retryTemplate.setBackOffPolicy(backOffPolicy);

        return retryTemplate;
    }

    // ==================== Message Recoverer 설정 ====================

    @Bean
    public MessageRecoverer crawlingMessageRecoverer(RabbitTemplate rabbitTemplate) {
        // 재시도 실패 시 DLQ로 전송
        return new RepublishMessageRecoverer(
                rabbitTemplate,
                "crawling.dlx",
                "request.dlq"
        );
    }

    // ==================== 리스너 컨테이너 팩토리 설정 ====================

    @Bean
    public RabbitListenerContainerFactory<SimpleMessageListenerContainer> 
           crawlingRabbitListenerContainerFactory(ConnectionFactory connectionFactory) {
        
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        
        // 메시지 컨버터
        factory.setMessageConverter(new Jackson2JsonMessageConverter());
        
        // 동시성 설정
        factory.setConcurrentConsumers(1);
        factory.setMaxConcurrentConsumers(5);
        factory.setPrefetchCount(1);
        
        // 재시도 설정
        factory.setRetryTemplate(crawlingRetryTemplate());
        
        // 확인 모드 (수동 확인)
        factory.setAcknowledgeMode(AcknowledgeMode.AUTO);
        
        return factory;
    }

    // ==================== RabbitTemplate 고급 설정 ====================

    @Bean
    public RabbitTemplate enhancedRabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        
        // JSON 메시지 컨버터
        template.setMessageConverter(new Jackson2JsonMessageConverter());
        
        // 확인 콜백 설정
        template.setConfirmCallback((correlationData, ack, cause) -> {
            if (ack) {
                System.out.println("Message sent successfully: " + correlationData);
            } else {
                System.err.println("Message send failed: " + cause);
            }
        });
        
        // 반환 콜백 설정 (라우팅 실패 시)
        template.setReturnsCallback(returned -> {
            System.err.println("Message returned: " + returned.getMessage());
            System.err.println("Reply Code: " + returned.getReplyCode());
            System.err.println("Reply Text: " + returned.getReplyText());
        });
        
        // 필수 확인 설정
        template.setMandatory(true);
        
        return template;
    }

    // ==================== 메시지 타임아웃 처리 ====================

    @Bean
    public Queue crawlingTimeoutQueue() {
        return QueueBuilder
                .durable("crawling.timeout.queue")
                .withArgument("x-message-ttl", 86400000) // 24시간
                .build();
    }

    @Bean
    public Binding crawlingTimeoutBinding() {
        return BindingBuilder
                .bind(crawlingTimeoutQueue())
                .to(crawlingExchange())
                .with("timeout");
    }

    // ==================== 헬스체크 큐 ====================

    @Bean
    public Queue crawlingHealthQueue() {
        return QueueBuilder
                .durable("crawling.health.queue")
                .withArgument("x-message-ttl", 300000) // 5분
                .build();
    }

    @Bean
    public Binding crawlingHealthBinding() {
        return BindingBuilder
                .bind(crawlingHealthQueue())
                .to(crawlingExchange())
                .with("health");
    }
}