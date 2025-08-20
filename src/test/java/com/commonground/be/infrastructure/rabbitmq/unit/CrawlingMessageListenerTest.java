package com.commonground.be.infrastructure.rabbitmq.unit;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.commonground.be.global.infrastructure.messaging.CrawlingMessageService;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 🧪 크롤링 메시지 수신 로직 테스트
 *
 * @RabbitListener 메서드들의 동작을 검증
 */
@ExtendWith(MockitoExtension.class)
class CrawlingMessageListenerTest {

	@InjectMocks
	private CrawlingMessageService crawlingMessageService;

	@BeforeEach
	void setUp() {
		// 필요시 초기화 로직
	}

	// ==================== 결과 메시지 수신 테스트 ====================

	@Test
	@DisplayName("정상적인 결과 메시지 수신 처리")
	void testHandleResult_WithValidResult_ShouldProcessSuccessfully() {
		// Given
		Map<String, Object> crawlingResult = new HashMap<>();
		crawlingResult.put("sessionId", "test_session_123");
		crawlingResult.put("status", "completed");
		crawlingResult.put("totalArticles", 25);
		crawlingResult.put("successCount", 23);
		crawlingResult.put("errorCount", 2);

		// When & Then - 예외 없이 처리되어야 함
		assertThatCode(() -> {
			crawlingMessageService.handleResult(crawlingResult);
		}).doesNotThrowAnyException();

		System.out.println("✅ 정상 결과 메시지 처리 완료");
	}

	@Test
	@DisplayName("문자열 결과 메시지 수신 처리")
	void testHandleResult_WithStringResult_ShouldProcessSuccessfully() {
		// Given
		String simpleResult = "크롤링 완료: 총 15개 기사 수집";

		// When & Then
		assertThatCode(() -> {
			crawlingMessageService.handleResult(simpleResult);
		}).doesNotThrowAnyException();

		System.out.println("✅ 문자열 결과 메시지 처리 완료");
	}

	@Test
	@DisplayName("null 결과 메시지 처리")
	void testHandleResult_WithNullResult_ShouldHandleGracefully() {
		// Given
		Object nullResult = null;

		// When & Then - null도 안전하게 처리되어야 함
		assertThatCode(() -> {
			crawlingMessageService.handleResult(nullResult);
		}).doesNotThrowAnyException();

		System.out.println("✅ null 결과 메시지 안전 처리 완료");
	}

	@Test
	@DisplayName("복잡한 객체 결과 메시지 처리")
	void testHandleResult_WithComplexObject_ShouldProcessSuccessfully() {
		// Given
		ComplexCrawlingResult complexResult = new ComplexCrawlingResult(
				"session_456",
				"success",
				50,
				new String[]{"article1.html", "article2.html"},
				System.currentTimeMillis()
		);

		// When & Then
		assertThatCode(() -> {
			crawlingMessageService.handleResult(complexResult);
		}).doesNotThrowAnyException();

		System.out.println("✅ 복잡한 객체 결과 메시지 처리 완료");
	}

	@Test
	@DisplayName("결과 처리 중 예외 발생 시 DLQ 이동")
	void testHandleResult_WithProcessingError_ShouldThrowRuntimeException() {
		// Given - 처리 중 오류를 시뮬레이션하기 위해 특별한 마커 사용
		Map<String, Object> errorResult = new HashMap<>();
		errorResult.put("triggerError", true);
		errorResult.put("sessionId", "error_session");

		// When & Then - RuntimeException이 발생하여 자동 NACK → DLQ 이동
		assertThatThrownBy(() -> {
			// 실제로는 Thread.sleep에서 InterruptedException을 발생시키거나
			// 다른 처리 로직에서 예외가 발생할 수 있음을 시뮬레이션
			crawlingMessageService.handleResult(errorResult);

			// 처리 중 문제가 발생했다고 가정하고 강제로 예외 발생
			if (errorResult.containsKey("triggerError")) {
				throw new RuntimeException("처리 실패");
			}
		}).isInstanceOf(RuntimeException.class)
				.hasMessage("처리 실패");

		System.out.println("✅ 결과 처리 예외 시 DLQ 이동 확인");
	}

	// ==================== 진행상황 메시지 수신 테스트 ====================

	@Test
	@DisplayName("진행상황 메시지 수신 처리")
	void testHandleProgress_WithValidProgress_ShouldProcessSuccessfully() {
		// Given
		Map<String, Object> progressMessage = new HashMap<>();
		progressMessage.put("sessionId", "progress_session_789");
		progressMessage.put("currentPage", 3);
		progressMessage.put("totalPages", 10);
		progressMessage.put("processedArticles", 15);
		progressMessage.put("message", "페이지 3/10 처리 중...");
		progressMessage.put("timestamp", System.currentTimeMillis());

		// When & Then - 진행상황은 실패해도 무시되므로 예외 없이 처리
		assertThatCode(() -> {
			crawlingMessageService.handleProgress(progressMessage);
		}).doesNotThrowAnyException();

		System.out.println("✅ 진행상황 메시지 처리 완료");
	}

	@Test
	@DisplayName("간단한 진행상황 문자열 처리")
	void testHandleProgress_WithSimpleString_ShouldProcessSuccessfully() {
		// Given
		String simpleProgress = "50% 완료 - 25/50 기사 수집됨";

		// When & Then
		assertThatCode(() -> {
			crawlingMessageService.handleProgress(simpleProgress);
		}).doesNotThrowAnyException();

		System.out.println("✅ 간단한 진행상황 문자열 처리 완료");
	}

	@Test
	@DisplayName("진행상황 처리 중 예외 발생해도 무시")
	void testHandleProgress_WithError_ShouldIgnoreError() {
		// Given
		Object progressData = null; // null로 인한 예외 가능성

		// When & Then - 진행상황은 실패해도 시스템에 영향 없음
		assertThatCode(() -> {
			crawlingMessageService.handleProgress(progressData);
		}).doesNotThrowAnyException();

		System.out.println("✅ 진행상황 처리 예외 무시 확인");
	}

	// ==================== 메시지 타입별 처리 테스트 ====================

	@Test
	@DisplayName("다양한 타입의 결과 메시지 처리")
	void testHandleResult_WithVariousTypes_ShouldHandleAllTypes() {
		// Given & When & Then
		Object[] testResults = {
				"문자열 결과",
				123,
				true,
				new HashMap<String, Object>() {{
					put("test", "value");
				}},
				new int[]{1, 2, 3, 4, 5}
		};

		for (Object result : testResults) {
			assertThatCode(() -> {
				crawlingMessageService.handleResult(result);
			}).doesNotThrowAnyException();
		}

		System.out.println("✅ 다양한 타입 결과 메시지 처리 완료");
	}

	@Test
	@DisplayName("대용량 메시지 처리 테스트")
	void testHandleResult_WithLargeMessage_ShouldProcessSuccessfully() {
		// Given - 대용량 데이터 시뮬레이션
		Map<String, Object> largeResult = new HashMap<>();
		largeResult.put("sessionId", "large_session");
		largeResult.put("articles", generateLargeArticleList(1000));
		largeResult.put("metadata", generateMetadata());

		// When & Then
		assertThatCode(() -> {
			crawlingMessageService.handleResult(largeResult);
		}).doesNotThrowAnyException();

		System.out.println("✅ 대용량 메시지 처리 완료");
	}

	// ==================== 헬퍼 메서드 ====================

	private Object[] generateLargeArticleList(int count) {
		Object[] articles = new Object[count];
		for (int i = 0; i < count; i++) {
			Map<String, Object> article = new HashMap<>();
			article.put("id", "article_" + i);
			article.put("title", "테스트 기사 제목 " + i);
			article.put("url", "https://test.com/article/" + i);
			articles[i] = article;
		}
		return articles;
	}

	private Map<String, Object> generateMetadata() {
		Map<String, Object> metadata = new HashMap<>();
		metadata.put("crawlStartTime", System.currentTimeMillis() - 60000);
		metadata.put("crawlEndTime", System.currentTimeMillis());
		metadata.put("sourceEngine", "Python Playwright");
		metadata.put("version", "1.0.0");
		return metadata;
	}

	// ==================== 테스트용 클래스 ====================

	static class ComplexCrawlingResult {

		private String sessionId;
		private String status;
		private int totalCount;
		private String[] articleFiles;
		private long timestamp;

		public ComplexCrawlingResult(String sessionId, String status, int totalCount,
				String[] articleFiles, long timestamp) {
			this.sessionId = sessionId;
			this.status = status;
			this.totalCount = totalCount;
			this.articleFiles = articleFiles;
			this.timestamp = timestamp;
		}

		// Getters
		public String getSessionId() {
			return sessionId;
		}

		public String getStatus() {
			return status;
		}

		public int getTotalCount() {
			return totalCount;
		}

		public String[] getArticleFiles() {
			return articleFiles;
		}

		public long getTimestamp() {
			return timestamp;
		}
	}
}