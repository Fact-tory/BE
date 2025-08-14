package com.commonground.be.global.infrastructure.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.amqp.rabbit.listener.adapter.MessageListenerAdapter;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.commonground.be.domain.news.service.communication.CrawlingResultListener;

/**
 * 🐰 RabbitMQ 설정
 * 
 * 큐 구조:
 * - crawling.request.queue: Java → Python (크롤링 요청)
 * - crawling.result.queue: Python → Java (크롤링 결과)
 * - crawling.progress.queue: Python → Java (진행상황)
 * - crawling.dlq: 실패한 메시지들
 */
// @Configuration  // RabbitMQAdvancedConfig로 대체됨
@Slf4j
public class RabbitMQConfig {

    // ==================== 큐 및 Exchange 상수 ====================
    
    public static final String CRAWLING_EXCHANGE = "crawling.exchange";
    
    // 요청/응답 큐
    public static final String CRAWLING_REQUEST_QUEUE = "crawling.request.queue";
    public static final String CRAWLING_RESULT_QUEUE = "crawling.result.queue";
    public static final String CRAWLING_PROGRESS_QUEUE = "crawling.progress.queue";
    
    // 라우팅 키
    public static final String REQUEST_ROUTING_KEY = "crawling.request";
    public static final String RESULT_ROUTING_KEY = "crawling.result";
    public static final String PROGRESS_ROUTING_KEY = "crawling.progress";
    
    // Dead Letter Queue
    public static final String CRAWLING_DLQ = "crawling.dlq";
    public static final String CRAWLING_DLX = "crawling.dlx";
    
    // ==================== Exchange 설정 ====================
    
    @Bean
    public TopicExchange crawlingExchange() {
        return new TopicExchange(CRAWLING_EXCHANGE, true, false);
    }
    
    @Bean
    public TopicExchange deadLetterExchange() {
        return new TopicExchange(CRAWLING_DLX, true, false);
    }
    
    // ==================== 큐 설정 ====================
    
    /**
     * 크롤링 요청 큐 (Java → Python)
     */
    @Bean
    public Queue crawlingRequestQueue() {
        return QueueBuilder.durable(CRAWLING_REQUEST_QUEUE)
            .withArgument("x-dead-letter-exchange", CRAWLING_DLX)
            .withArgument("x-dead-letter-routing-key", "request.dlq")
            .withArgument("x-message-ttl", 600000) // 10분 TTL
            .build();
    }
    
    /**
     * 크롤링 결과 큐 (Python → Java)
     */
    @Bean
    public Queue crawlingResultQueue() {
        return QueueBuilder.durable(CRAWLING_RESULT_QUEUE)
            .withArgument("x-dead-letter-exchange", CRAWLING_DLX)
            .withArgument("x-dead-letter-routing-key", "result.dlq")
            .build();
    }
    
    /**
     * 크롤링 진행상황 큐 (Python → Java)
     */
    @Bean
    public Queue crawlingProgressQueue() {
        return QueueBuilder.durable(CRAWLING_PROGRESS_QUEUE)
            .withArgument("x-dead-letter-exchange", CRAWLING_DLX)
            .withArgument("x-dead-letter-routing-key", "progress.dlq")
            .build();
    }
    
    /**
     * Dead Letter Queue
     */
    @Bean
    public Queue deadLetterQueue() {
        return QueueBuilder.durable(CRAWLING_DLQ).build();
    }
    
    // ==================== 바인딩 설정 ====================
    
    @Bean
    public Binding crawlingRequestBinding() {
        return BindingBuilder
            .bind(crawlingRequestQueue())
            .to(crawlingExchange())
            .with(REQUEST_ROUTING_KEY);
    }
    
    @Bean
    public Binding crawlingResultBinding() {
        return BindingBuilder
            .bind(crawlingResultQueue())
            .to(crawlingExchange())
            .with(RESULT_ROUTING_KEY);
    }
    
    @Bean
    public Binding crawlingProgressBinding() {
        return BindingBuilder
            .bind(crawlingProgressQueue())
            .to(crawlingExchange())
            .with(PROGRESS_ROUTING_KEY);
    }
    
    @Bean
    public Binding deadLetterBinding() {
        return BindingBuilder
            .bind(deadLetterQueue())
            .to(deadLetterExchange())
            .with("failed.*");
    }
    
    // ==================== 메시지 컨버터 ====================
    
    @Bean
    public MessageConverter messageConverter() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        return new Jackson2JsonMessageConverter(objectMapper);
    }
    
    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, MessageConverter messageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter);
        
        // Publisher Confirms 활성화 (메시지 전달 확인)
        template.setConfirmCallback((correlationData, ack, cause) -> {
            if (ack) {
                log.debug("메시지 전달 확인: {}", correlationData);
            } else {
                log.error("메시지 전달 실패: {}, 원인: {}", correlationData, cause);
            }
        });
        
        // Return Callback (라우팅 실패시)
        template.setReturnsCallback(returnCallback -> {
            log.error("메시지 라우팅 실패: {}, 원인: {}", 
                returnCallback.getMessage(), returnCallback.getReplyText());
        });
        
        return template;
    }
    
    // ==================== 메시지 리스너 어댑터 ====================
    
    @Bean
    public MessageListenerAdapter resultListenerAdapter(CrawlingResultListener listener) {
        return new MessageListenerAdapter(listener, "handleCrawlingResult");
    }
    
    @Bean  
    public MessageListenerAdapter progressListenerAdapter(CrawlingResultListener listener) {
        return new MessageListenerAdapter(listener, "handleCrawlingProgress");
    }
    
    // ==================== 리스너 컨테이너 ====================
    
    @Bean
    public SimpleMessageListenerContainer resultListenerContainer(
            ConnectionFactory connectionFactory,
            MessageListenerAdapter resultListenerAdapter) {
        
        SimpleMessageListenerContainer container = new SimpleMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.setQueueNames(CRAWLING_RESULT_QUEUE);
        container.setMessageListener(resultListenerAdapter);
        container.setConcurrentConsumers(3); // 동시 처리 수
        container.setMaxConcurrentConsumers(10);
        
        // 수동 ACK 모드
        container.setAcknowledgeMode(AcknowledgeMode.MANUAL);
        
        log.info("크롤링 결과 리스너 컨테이너 초기화 완료");
        return container;
    }
    
    @Bean
    public SimpleMessageListenerContainer progressListenerContainer(
            ConnectionFactory connectionFactory,
            MessageListenerAdapter progressListenerAdapter) {
        
        SimpleMessageListenerContainer container = new SimpleMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.setQueueNames(CRAWLING_PROGRESS_QUEUE);
        container.setMessageListener(progressListenerAdapter);
        container.setConcurrentConsumers(2);
        
        log.info("크롤링 진행상황 리스너 컨테이너 초기화 완료");
        return container;
    }
}