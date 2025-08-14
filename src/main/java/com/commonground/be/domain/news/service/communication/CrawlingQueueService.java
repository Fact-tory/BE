package com.commonground.be.domain.news.service.communication;

import com.commonground.be.domain.news.dto.crawling.RawNewsData;
import com.commonground.be.domain.news.dto.request.NaverCrawlingRequest;
import com.commonground.be.global.infrastructure.config.RabbitMQConfig;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/**
 * 📨 크롤링 요청/응답 큐 관리 서비스 (RabbitMQ)
 * <p>
 * 책임: - Python 크롤러로 크롤링 요청 전송 - Python 크롤러로부터 결과 수신 - 비동기 크롤링 작업 관리
 */
@Service
@Slf4j
public class CrawlingQueueService {

	private final RabbitTemplate rabbitTemplate;
	
	public CrawlingQueueService(@Qualifier("enhancedRabbitTemplate") RabbitTemplate rabbitTemplate) {
		this.rabbitTemplate = rabbitTemplate;
	}

	// 크롤링 요청에 대한 CompletableFuture 저장소
	private final ConcurrentHashMap<String, CompletableFuture<List<RawNewsData>>> pendingRequests = new ConcurrentHashMap<>();

	// ==================== 크롤링 요청 전송 ====================

	/**
	 * 크롤링 요청을 Python 워커로 전송 (RabbitMQ)
	 */
	public CompletableFuture<List<RawNewsData>> submitCrawlingRequest(
			NaverCrawlingRequest request) {
		String requestId = request.getSessionId() != null ?
				request.getSessionId() : UUID.randomUUID().toString();

		request.setSessionId(requestId);

		try {
			// 크롤링 요청 메시지 생성
			CrawlingRequestMessage message = CrawlingRequestMessage.builder()
					.requestId(requestId)
					.requestType("NAVER_NEWS")
					.payload(request)
					.timestamp(System.currentTimeMillis())
					.build();

			// 결과를 기다릴 CompletableFuture 생성
			CompletableFuture<List<RawNewsData>> resultFuture = new CompletableFuture<>();
			pendingRequests.put(requestId, resultFuture);

			// RabbitMQ로 요청 전송
			rabbitTemplate.convertAndSend(
					RabbitMQConfig.CRAWLING_EXCHANGE,
					RabbitMQConfig.REQUEST_ROUTING_KEY,
					message
			);

			log.info("크롤링 요청 전송 완료: requestId={}, officeId={}, categoryId={}",
					requestId, request.getOfficeId(), request.getCategoryId());

			// 타임아웃 설정 (10분)
			resultFuture.orTimeout(10, TimeUnit.MINUTES)
					.whenComplete((result, throwable) -> {
						if (throwable != null) {
							log.warn("크롤링 요청 타임아웃: requestId={}", requestId);
							pendingRequests.remove(requestId);
						}
					});

			return resultFuture;

		} catch (Exception e) {
			log.error("크롤링 요청 전송 실패: requestId={}", requestId, e);
			CompletableFuture<List<RawNewsData>> errorFuture = new CompletableFuture<>();
			errorFuture.completeExceptionally(e);
			return errorFuture;
		}
	}

	// ==================== 크롤링 결과 수신 ====================

	/**
	 * Python 워커로부터 크롤링 결과 수신 처리
	 */
	public void handleCrawlingResult(CrawlingResultMessage result) {
		String requestId = result.getRequestId();

		CompletableFuture<List<RawNewsData>> pendingRequest = pendingRequests.get(requestId);
		if (pendingRequest == null) {
			log.warn("해당하는 대기 중인 크롤링 요청을 찾을 수 없음: requestId={}", requestId);
			return;
		}

		if (result.isSuccess()) {
			List<RawNewsData> newsData = result.getData();
			pendingRequest.complete(newsData);
			log.info("크롤링 결과 수신 완료: requestId={}, count={}", requestId, newsData.size());
		} else {
			Exception error = new RuntimeException("크롤링 실패: " + result.getErrorMessage());
			pendingRequest.completeExceptionally(error);
			log.error("크롤링 실패 결과 수신: requestId={}, error={}", requestId, result.getErrorMessage());
		}

		pendingRequests.remove(requestId);
	}

	/**
	 * 크롤링 에러 메시지 수신 처리
	 */
	public void handleCrawlingError(CrawlingErrorMessage error) {
		String requestId = error.getRequestId();

		CompletableFuture<List<RawNewsData>> pendingRequest = pendingRequests.get(requestId);
		if (pendingRequest != null) {
			Exception exception = new RuntimeException("크롤링 에러: " + error.getErrorMessage());
			pendingRequest.completeExceptionally(exception);
			pendingRequests.remove(requestId);

			log.error("크롤링 에러 수신: requestId={}, error={}", requestId, error.getErrorMessage());
		}
	}

	// ==================== 내부 메시지 클래스들 ====================

	@lombok.Builder
	@lombok.Data
	@lombok.NoArgsConstructor
	@lombok.AllArgsConstructor
	public static class CrawlingRequestMessage {

		private String requestId;
		private String requestType;
		private Object payload;
		private long timestamp;
	}

	@lombok.Data
	@lombok.NoArgsConstructor
	@lombok.AllArgsConstructor
	public static class CrawlingResultMessage {

		private String requestId;
		private boolean success;
		private List<RawNewsData> data;
		private String errorMessage;
		private long timestamp;
	}

	@lombok.Data
	@lombok.NoArgsConstructor
	@lombok.AllArgsConstructor
	public static class CrawlingErrorMessage {

		private String requestId;
		private String errorMessage;
		private String errorType;
		private long timestamp;
	}
}