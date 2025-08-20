package com.commonground.be.global.infrastructure.messaging;

import com.commonground.be.domain.news.dto.request.NaverCrawlingRequest;
import com.commonground.be.domain.news.dto.request.UrlCrawlingRequest;
import com.commonground.be.global.infrastructure.config.UnifiedRabbitMQConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

/**
 * 🚀 통합 크롤링 메시지 서비스
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CrawlingMessageService {

	private final RabbitTemplate rabbitTemplate;

	// ==================== 메시지 전송 ====================

	/**
	 * 크롤링 요청 전송 (자동 라우팅)
	 */
	public void sendCrawlingRequest(NaverCrawlingRequest request) {
		String routingKey = UnifiedRabbitMQConfig.getRoutingKeyBySize(request.getMaxArticles());

		rabbitTemplate.convertAndSend(
				UnifiedRabbitMQConfig.CRAWLING_EXCHANGE,
				routingKey,
				request
		);

		log.info("크롤링 요청 전송: office={}, category={}, size={}, route={}",
				request.getOfficeId(), request.getCategoryId(), request.getMaxArticles(),
				routingKey);
	}

	/**
	 * URL 크롤링 요청 전송
	 */
	public void sendUrlCrawlingRequest(UrlCrawlingRequest request) {
		rabbitTemplate.convertAndSend(
				UnifiedRabbitMQConfig.CRAWLING_EXCHANGE,
				UnifiedRabbitMQConfig.URL_CRAWLING_ROUTING_KEY,
				request
		);

		log.info("URL 크롤링 요청 전송: url={}, priority={}, session={}",
				request.getUrl(), request.getPriority(), request.getSessionId());
	}

	/**
	 * 크롤링 결과 전송
	 */
	public void sendResult(Object result) {
		rabbitTemplate.convertAndSend(
				UnifiedRabbitMQConfig.CRAWLING_EXCHANGE,
				UnifiedRabbitMQConfig.RESULT_ROUTING_KEY,
				result
		);
		log.info("크롤링 결과 전송");
	}

	// ==================== 메시지 수신 ====================

	@RabbitListener(queues = UnifiedRabbitMQConfig.CRAWLING_RESULT_QUEUE)
	public void handleResult(Object result) {
		try {
			log.info("📥 결과 수신: {}", result.getClass().getSimpleName());

			// 실제 처리 로직 (뉴스 저장, 인덱싱 등)
			Thread.sleep(100); // 처리 시뮬레이션

			log.info("✅ 결과 처리 완료");

		} catch (Exception e) {
			log.error("❌ 결과 처리 실패: {}", e.getMessage());
			throw new RuntimeException("처리 실패", e); // 자동 NACK → DLQ
		}
	}

	@RabbitListener(queues = UnifiedRabbitMQConfig.CRAWLING_PROGRESS_QUEUE)
	public void handleProgress(Object progress) {
		log.debug("📊 진행상황: {}", progress);
		// 진행상황은 실패해도 무시
	}

	@RabbitListener(queues = UnifiedRabbitMQConfig.URL_RESULT_QUEUE)
	public void handleUrlCrawlingResult(Object result) {
		try {
			log.info("📥 URL 크롤링 결과 수신: {}", result.getClass().getSimpleName());

			// 실제 처리 로직 (URL 크롤링 결과 저장, 인덱싱 등)
			Thread.sleep(100); // 처리 시뮬레이션

			log.info("✅ URL 크롤링 결과 처리 완료");

		} catch (Exception e) {
			log.error("❌ URL 크롤링 결과 처리 실패: {}", e.getMessage());
			throw new RuntimeException("URL 크롤링 처리 실패", e); // 자동 NACK → DLQ
		}
	}
}