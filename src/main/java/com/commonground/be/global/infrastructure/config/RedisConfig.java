package com.commonground.be.global.infrastructure.config;

import com.commonground.be.domain.news.dto.crawling.CrawlingProgress;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.repository.configuration.EnableRedisRepositories;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Configuration
@EnableRedisRepositories
@RequiredArgsConstructor
@Slf4j
public class RedisConfig {

	@Value("${spring.data.redis.host}")
	public String host;

	@Value("${spring.data.redis.port}")
	public int port;

	@Bean
	public RedisConnectionFactory redisConnectionFactory() {
		return new LettuceConnectionFactory(host, port);
	}

	@Bean
	@Primary
	public RedisTemplate<String, Object> redisTemplate() {
		RedisTemplate<String, Object> template = new RedisTemplate<>();
		template.setConnectionFactory(redisConnectionFactory());
		
		// String 직렬화
		template.setKeySerializer(new StringRedisSerializer());
		template.setHashKeySerializer(new StringRedisSerializer());
		
		// JSON 직렬화 (Java 8 시간 타입 지원)
		ObjectMapper objectMapper = new ObjectMapper();
		objectMapper.registerModule(new JavaTimeModule());
		GenericJackson2JsonRedisSerializer serializer = new GenericJackson2JsonRedisSerializer(objectMapper);
		
		template.setValueSerializer(serializer);
		template.setHashValueSerializer(serializer);
		
		template.afterPropertiesSet();
		return template;
	}

	@Bean
	public RedisMessageListenerContainer redisMessageListenerContainer(
			RedisConnectionFactory connectionFactory,
			CrawlingProgressMessageListener messageListener) {

		RedisMessageListenerContainer container = new RedisMessageListenerContainer();
		container.setConnectionFactory(connectionFactory);

		// 크롤링 진행상황 채널 구독
		container.addMessageListener(messageListener, new ChannelTopic("crawling_progress"));

		log.info("Redis Message Listener Container 초기화 완료");
		return container;
	}

	@Component
	@RequiredArgsConstructor
	@Slf4j
	public static class CrawlingProgressMessageListener implements MessageListener {

		private final SimpMessagingTemplate messagingTemplate;
		private final ObjectMapper objectMapper;

		@Override
		public void onMessage(Message message, byte[] pattern) {
			try {
				String messageBody = new String(message.getBody());
				CrawlingProgress progress = objectMapper.readValue(messageBody,
						CrawlingProgress.class);

				// WebSocket으로 클라이언트에게 브로드캐스트
				String destination = "/topic/crawling/" + progress.getSessionId();
				messagingTemplate.convertAndSend(destination, progress);

				log.debug("크롤링 진행상황 브로드캐스트: sessionId={}, progress={}%",
						progress.getSessionId(), progress.getProgress());

			} catch (Exception e) {
				log.error("Redis 메시지 처리 실패", e);
			}
		}
	}
}