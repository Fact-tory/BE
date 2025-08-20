package com.commonground.be.global.infrastructure.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.RabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 🐰 통합 RabbitMQ 설정
 * <p>
 * 큐 구조: - crawling.light.queue (가벼운 작업: ~10개 기사) - crawling.medium.queue (중간 작업: ~50개 기사) -
 * crawling.heavy.queue (무거운 작업: ~100개+ 기사) - crawling.result.queue (크롤링 결과) -
 * crawling.progress.queue (진행상황) - crawling.dlq (실패한 메시지들)
 */
@Configuration
@Slf4j
public class UnifiedRabbitMQConfig {

	// ==================== 큐 & Exchange 상수 ====================

	public static final String CRAWLING_EXCHANGE = "crawling.exchange";

	// 요청 큐들 (우선순위별)
	public static final String CRAWLING_LIGHT_QUEUE = "crawling.light.queue";
	public static final String CRAWLING_MEDIUM_QUEUE = "crawling.medium.queue";
	public static final String CRAWLING_HEAVY_QUEUE = "crawling.heavy.queue";

	// 응답 큐들
	public static final String CRAWLING_RESULT_QUEUE = "crawling.result.queue";
	public static final String CRAWLING_PROGRESS_QUEUE = "crawling.progress.queue";
	
	// URL 크롤링 큐들
	public static final String URL_CRAWLING_QUEUE = "crawling.url.queue";
	public static final String URL_RESULT_QUEUE = "crawling.url.result.queue";

	// DLQ
	public static final String CRAWLING_DLQ = "crawling.dlq";
	public static final String CRAWLING_DLX = "crawling.dlx";

	// 라우팅 키
	public static final String LIGHT_ROUTING_KEY = "crawling.light";
	public static final String MEDIUM_ROUTING_KEY = "crawling.medium";
	public static final String HEAVY_ROUTING_KEY = "crawling.heavy";
	public static final String RESULT_ROUTING_KEY = "crawling.result";
	public static final String PROGRESS_ROUTING_KEY = "crawling.progress";
	
	// URL 크롤링 관련 라우팅 키
	public static final String URL_CRAWLING_ROUTING_KEY = "crawling.url";
	public static final String URL_RESULT_ROUTING_KEY = "crawling.url.result";

	// ==================== Exchange 설정 ====================

	@Bean
	public TopicExchange crawlingExchange() {
		return new TopicExchange(CRAWLING_EXCHANGE, true, false);
	}

	@Bean
	public DirectExchange deadLetterExchange() {
		return new DirectExchange(CRAWLING_DLX, true, false);
	}

	// ==================== 큐 설정 ====================

	@Bean
	public Queue crawlingLightQueue() {
		return QueueBuilder.durable(CRAWLING_LIGHT_QUEUE)
				.withArgument("x-dead-letter-exchange", CRAWLING_DLX)
				.withArgument("x-dead-letter-routing-key", "light.failed")
				.withArgument("x-message-ttl", 300000) // 5분 TTL
				.build();
	}

	@Bean
	public Queue crawlingMediumQueue() {
		return QueueBuilder.durable(CRAWLING_MEDIUM_QUEUE)
				.withArgument("x-dead-letter-exchange", CRAWLING_DLX)
				.withArgument("x-dead-letter-routing-key", "medium.failed")
				.withArgument("x-message-ttl", 600000) // 10분 TTL
				.build();
	}

	@Bean
	public Queue crawlingHeavyQueue() {
		return QueueBuilder.durable(CRAWLING_HEAVY_QUEUE)
				.withArgument("x-dead-letter-exchange", CRAWLING_DLX)
				.withArgument("x-dead-letter-routing-key", "heavy.failed")
				.withArgument("x-message-ttl", 1800000) // 30분 TTL
				.build();
	}

	@Bean
	public Queue crawlingResultQueue() {
		return QueueBuilder.durable(CRAWLING_RESULT_QUEUE)
				.withArgument("x-dead-letter-exchange", CRAWLING_DLX)
				.withArgument("x-dead-letter-routing-key", "result.failed")
				.build();
	}

	@Bean
	public Queue crawlingProgressQueue() {
		return QueueBuilder.durable(CRAWLING_PROGRESS_QUEUE)
				.withArgument("x-dead-letter-exchange", CRAWLING_DLX)
				.withArgument("x-dead-letter-routing-key", "progress.failed")
				.build();
	}

	@Bean
	public Queue crawlingDeadLetterQueue() {
		return QueueBuilder.durable(CRAWLING_DLQ).build();
	}
	
	@Bean
	public Queue urlCrawlingQueue() {
		return QueueBuilder.durable(URL_CRAWLING_QUEUE)
				.withArgument("x-dead-letter-exchange", CRAWLING_DLX)
				.withArgument("x-dead-letter-routing-key", "url.failed")
				.withArgument("x-message-ttl", 300000) // 5분 TTL
				.build();
	}
	
	@Bean
	public Queue urlResultQueue() {
		return QueueBuilder.durable(URL_RESULT_QUEUE)
				.withArgument("x-dead-letter-exchange", CRAWLING_DLX)
				.withArgument("x-dead-letter-routing-key", "url.result.failed")
				.build();
	}

	// ==================== 바인딩 설정 ====================

	@Bean
	public Binding lightBinding() {
		return BindingBuilder.bind(crawlingLightQueue())
				.to(crawlingExchange()).with(LIGHT_ROUTING_KEY);
	}

	@Bean
	public Binding mediumBinding() {
		return BindingBuilder.bind(crawlingMediumQueue())
				.to(crawlingExchange()).with(MEDIUM_ROUTING_KEY);
	}

	@Bean
	public Binding heavyBinding() {
		return BindingBuilder.bind(crawlingHeavyQueue())
				.to(crawlingExchange()).with(HEAVY_ROUTING_KEY);
	}

	@Bean
	public Binding resultBinding() {
		return BindingBuilder.bind(crawlingResultQueue())
				.to(crawlingExchange()).with(RESULT_ROUTING_KEY);
	}

	@Bean
	public Binding progressBinding() {
		return BindingBuilder.bind(crawlingProgressQueue())
				.to(crawlingExchange()).with(PROGRESS_ROUTING_KEY);
	}

	@Bean
	public Binding dlqBinding() {
		return BindingBuilder.bind(crawlingDeadLetterQueue())
				.to(deadLetterExchange()).with("*.failed");
	}
	
	@Bean
	public Binding urlCrawlingBinding() {
		return BindingBuilder.bind(urlCrawlingQueue())
				.to(crawlingExchange()).with(URL_CRAWLING_ROUTING_KEY);
	}
	
	@Bean
	public Binding urlResultBinding() {
		return BindingBuilder.bind(urlResultQueue())
				.to(crawlingExchange()).with(URL_RESULT_ROUTING_KEY);
	}

	// ==================== 메시지 컨버터 ====================

	@Bean
	public MessageConverter messageConverter() {
		ObjectMapper objectMapper = new ObjectMapper();
		objectMapper.registerModule(new JavaTimeModule());
		return new Jackson2JsonMessageConverter(objectMapper);
	}

	@Bean
	public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
		RabbitTemplate template = new RabbitTemplate(connectionFactory);
		template.setMessageConverter(messageConverter());

		// 간단한 에러 핸들링
		template.setConfirmCallback((correlationData, ack, cause) -> {
            if (!ack) {
                log.error("메시지 전송 실패: {}", cause);
            }
		});

		return template;
	}

	@Bean
	public RabbitListenerContainerFactory<SimpleMessageListenerContainer>
	rabbitListenerContainerFactory(ConnectionFactory connectionFactory) {

		SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
		factory.setConnectionFactory(connectionFactory);
		factory.setMessageConverter(messageConverter());

		// 기본 설정
		factory.setConcurrentConsumers(1);
		factory.setMaxConcurrentConsumers(3);
		factory.setAcknowledgeMode(AcknowledgeMode.AUTO); // 자동 ACK/NACK
		factory.setDefaultRequeueRejected(false); // 실패시 DLQ 이동

		return factory;
	}

	// ==================== 유틸리티 메서드 ====================

	/**
	 * 요청 크기에 따른 라우팅 키 결정
	 */
	public static String getRoutingKeyBySize(int maxArticles) {
		if (maxArticles <= 10) {
			return LIGHT_ROUTING_KEY;
		} else if (maxArticles <= 50) {
			return MEDIUM_ROUTING_KEY;
		} else {
			return HEAVY_ROUTING_KEY;
		}
	}

	/**
	 * 큐 사이즈 조회를 위한 관리 인터페이스
	 */
	public static class QueueInfo {

		public final String name;
		public final String routingKey;
		public final int ttlMinutes;

		public QueueInfo(String name, String routingKey, int ttlMinutes) {
			this.name = name;
			this.routingKey = routingKey;
			this.ttlMinutes = ttlMinutes;
		}
	}

	public static QueueInfo[] getAllQueues() {
		return new QueueInfo[]{
				new QueueInfo(CRAWLING_LIGHT_QUEUE, LIGHT_ROUTING_KEY, 5),
				new QueueInfo(CRAWLING_MEDIUM_QUEUE, MEDIUM_ROUTING_KEY, 10),
				new QueueInfo(CRAWLING_HEAVY_QUEUE, HEAVY_ROUTING_KEY, 30),
				new QueueInfo(CRAWLING_RESULT_QUEUE, RESULT_ROUTING_KEY, 0),
				new QueueInfo(CRAWLING_PROGRESS_QUEUE, PROGRESS_ROUTING_KEY, 0)
		};
	}
}