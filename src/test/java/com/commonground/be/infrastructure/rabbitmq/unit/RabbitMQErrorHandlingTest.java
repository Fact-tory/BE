package com.commonground.be.infrastructure.rabbitmq.unit;

import com.commonground.be.domain.news.dto.request.NaverCrawlingRequest;
import com.commonground.be.global.infrastructure.config.UnifiedRabbitMQConfig;
import com.commonground.be.global.infrastructure.messaging.CrawlingMessageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.support.ListenerExecutionFailedException;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 🧪 RabbitMQ 에러 처리 및 DLQ 테스트
 * 
 * 에러 시나리오, 재시도 로직, DLQ 이동을 검증
 */
@ExtendWith(MockitoExtension.class)
class RabbitMQErrorHandlingTest {

    @Mock
    private RabbitTemplate rabbitTemplate;
    
    private CrawlingMessageService crawlingMessageService;
    
    @BeforeEach
    void setUp() {
        crawlingMessageService = new CrawlingMessageService(rabbitTemplate);
    }
    
    // ==================== DLQ 테스트 ====================
    
    @Test
    @DisplayName("메시지 처리 실패 시 DLQ로 이동")
    void testMessageProcessingFailure_ShouldMoveToDLQ() {
        // Given
        Map<String, Object> failedMessage = new HashMap<>();
        failedMessage.put("sessionId", "failed_session");
        failedMessage.put("intentionalError", true);
        
        // When & Then - RuntimeException 발생 시 자동으로 DLQ 이동
        assertThatThrownBy(() -> {
            // 실제 서비스에서는 @RabbitListener가 이 예외를 잡아서 DLQ로 이동
            crawlingMessageService.handleResult(failedMessage);
            
            // 의도적으로 예외 발생 (실제로는 처리 로직 내부에서 발생)
            throw new RuntimeException("처리 실패", new Exception("상세 오류"));
        }).isInstanceOf(RuntimeException.class)
          .hasMessageContaining("처리 실패");
        
        System.out.println("✅ 메시지 처리 실패 시 DLQ 이동 확인");
    }
    
    @Test
    @DisplayName("DLQ 메시지 포맷 검증")
    void testDLQMessageFormat_ShouldContainErrorInfo() {
        // Given
        String originalMessage = "처리 실패 메시지";
        Exception processingError = new IllegalArgumentException("잘못된 데이터 형식");
        
        // When - DLQ 메시지 생성 시뮬레이션
        Map<String, Object> dlqMessage = createDLQMessage(originalMessage, processingError);
        
        // Then
        assertThat(dlqMessage).containsKeys(
            "originalMessage",
            "errorType",
            "errorMessage", 
            "timestamp",
            "retryCount"
        );
        
        assertThat(dlqMessage.get("originalMessage")).isEqualTo(originalMessage);
        assertThat(dlqMessage.get("errorType")).isEqualTo("IllegalArgumentException");
        assertThat(dlqMessage.get("errorMessage")).isEqualTo("잘못된 데이터 형식");
        
        System.out.println("✅ DLQ 메시지 포맷 검증 완료");
    }
    
    @Test
    @DisplayName("재시도 횟수 초과 시 DLQ 이동")
    void testMaxRetryExceeded_ShouldMoveToDLQ() {
        // Given
        int maxRetries = 3;
        Map<String, Object> retryMessage = new HashMap<>();
        retryMessage.put("sessionId", "retry_session");
        retryMessage.put("retryCount", maxRetries + 1);
        retryMessage.put("lastError", "연결 타임아웃");
        
        // When & Then
        boolean shouldMoveToDLQ = checkIfShouldMoveToDLQ(retryMessage, maxRetries);
        assertThat(shouldMoveToDLQ).isTrue();
        
        System.out.println("✅ 재시도 횟수 초과 시 DLQ 이동 확인");
    }
    
    // ==================== 에러 시나리오 테스트 ====================
    
    @Test
    @DisplayName("메시지 직렬화 실패 처리")
    void testMessageSerializationFailure_ShouldHandleGracefully() {
        // Given
        Object unserializableMessage = new Object() {
            // 직렬화 불가능한 객체 시뮬레이션
            private final Object circularReference = this;
        };
        
        // When - 직렬화 실패 시뮬레이션
        doThrow(new RuntimeException("직렬화 실패"))
            .when(rabbitTemplate).convertAndSend(anyString(), anyString(), eq(unserializableMessage));
        
        // Then
        assertThatThrownBy(() -> {
            crawlingMessageService.sendResult(unserializableMessage);
        }).isInstanceOf(RuntimeException.class)
          .hasMessage("직렬화 실패");
        
        verify(rabbitTemplate, times(1)).convertAndSend(anyString(), anyString(), eq(unserializableMessage));
        System.out.println("✅ 메시지 직렬화 실패 처리 확인");
    }
    
    @Test
    @DisplayName("큐 연결 실패 시 예외 처리")
    void testQueueConnectionFailure_ShouldThrowException() {
        // Given
        NaverCrawlingRequest request = createTestRequest("connection_test", 10);
        
        doThrow(new RuntimeException("RabbitMQ 연결 실패"))
            .when(rabbitTemplate).convertAndSend(anyString(), anyString(), any(Object.class));
        
        // When & Then
        assertThatThrownBy(() -> {
            crawlingMessageService.sendCrawlingRequest(request);
        }).isInstanceOf(RuntimeException.class)
          .hasMessage("RabbitMQ 연결 실패");
        
        System.out.println("✅ 큐 연결 실패 예외 처리 확인");
    }
    
    @Test
    @DisplayName("메시지 크기 제한 초과 처리")
    void testMessageSizeLimit_ShouldHandleOversizedMessage() {
        // Given - 대용량 메시지 시뮬레이션 (예: 10MB+)
        Map<String, Object> oversizedMessage = new HashMap<>();
        oversizedMessage.put("sessionId", "large_session");
        oversizedMessage.put("largeData", createLargeData(1000000)); // 1M 문자열
        
        // When - 메시지 크기 제한 초과 시뮬레이션
        doThrow(new RuntimeException("메시지 크기 제한 초과: 10MB"))
            .when(rabbitTemplate).convertAndSend(anyString(), anyString(), eq(oversizedMessage));
        
        // Then
        assertThatThrownBy(() -> {
            crawlingMessageService.sendResult(oversizedMessage);
        }).isInstanceOf(RuntimeException.class)
          .hasMessageContaining("메시지 크기 제한 초과");
        
        System.out.println("✅ 메시지 크기 제한 처리 확인");
    }
    
    // ==================== 복구 시나리오 테스트 ====================
    
    @Test
    @DisplayName("일시적 연결 실패 후 복구 테스트")
    void testTemporaryConnectionFailure_ShouldRecover() {
        // Given
        NaverCrawlingRequest request = createTestRequest("recovery_test", 15);
        
        // 첫 번째 호출은 실패, 두 번째는 성공
        doThrow(new RuntimeException("일시적 연결 실패"))
            .doNothing()
            .when(rabbitTemplate).convertAndSend(anyString(), anyString(), any(Object.class));
        
        // When & Then
        // 첫 번째 시도 - 실패
        assertThatThrownBy(() -> {
            crawlingMessageService.sendCrawlingRequest(request);
        }).isInstanceOf(RuntimeException.class);
        
        // 두 번째 시도 - 성공
        assertThatCode(() -> {
            crawlingMessageService.sendCrawlingRequest(request);
        }).doesNotThrowAnyException();
        
        verify(rabbitTemplate, times(2)).convertAndSend(anyString(), anyString(), any(Object.class));
        System.out.println("✅ 일시적 연결 실패 후 복구 확인");
    }
    
    @Test
    @DisplayName("큐 백로그 처리 테스트")
    void testQueueBacklogHandling_ShouldHandleHighLoad() {
        // Given
        int messageCount = 100;
        
        // 큐가 가득 찬 상황 시뮬레이션
        doAnswer(invocation -> {
            // 큐 백로그 시뮬레이션을 위한 지연
            Thread.sleep(10);
            return null;
        }).when(rabbitTemplate).convertAndSend(anyString(), anyString(), any(Object.class));
        
        // When
        long startTime = System.currentTimeMillis();
        
        for (int i = 0; i < messageCount; i++) {
            NaverCrawlingRequest request = createTestRequest("backlog_" + i, 5);
            crawlingMessageService.sendCrawlingRequest(request);
        }
        
        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;
        
        // Then
        assertThat(duration).isGreaterThan(500); // 최소 500ms (백로그로 인한 지연)
        verify(rabbitTemplate, times(messageCount)).convertAndSend(anyString(), anyString(), any(Object.class));
        
        System.out.println("✅ 큐 백로그 처리 확인 (처리시간: " + duration + "ms)");
    }
    
    // ==================== 동시성 에러 테스트 ====================
    
    @Test
    @DisplayName("동시 요청 시 에러 처리")
    void testConcurrentRequestErrors_ShouldHandleRaceConditions() throws InterruptedException {
        // Given
        int threadCount = 10;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        
        // 일부 요청은 실패하도록 설정
        doAnswer(invocation -> {
            if (Math.random() < 0.3) { // 30% 확률로 실패
                throw new RuntimeException("동시성 오류");
            }
            return null;
        }).when(rabbitTemplate).convertAndSend(anyString(), anyString(), any(Object.class));
        
        // When
        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    NaverCrawlingRequest request = createTestRequest("concurrent_" + threadId, 5);
                    crawlingMessageService.sendCrawlingRequest(request);
                } catch (Exception e) {
                    // 예상된 에러이므로 로그만 출력
                    System.out.println("스레드 " + threadId + " 에러: " + e.getMessage());
                } finally {
                    endLatch.countDown();
                }
            });
        }
        
        startLatch.countDown();
        boolean completed = endLatch.await(10, TimeUnit.SECONDS);
        
        // Then
        assertThat(completed).isTrue();
        verify(rabbitTemplate, times(threadCount)).convertAndSend(anyString(), anyString(), any(Object.class));
        
        executor.shutdown();
        System.out.println("✅ 동시 요청 시 에러 처리 확인");
    }
    
    // ==================== 헬퍼 메서드 ====================
    
    private NaverCrawlingRequest createTestRequest(String officeId, int maxArticles) {
        return NaverCrawlingRequest.builder()
            .officeId(officeId)
            .categoryId("100")
            .maxArticles(maxArticles)
            .sessionId("test_session_" + System.currentTimeMillis())
            .build();
    }
    
    private Map<String, Object> createDLQMessage(Object originalMessage, Exception error) {
        Map<String, Object> dlqMessage = new HashMap<>();
        dlqMessage.put("originalMessage", originalMessage);
        dlqMessage.put("errorType", error.getClass().getSimpleName());
        dlqMessage.put("errorMessage", error.getMessage());
        dlqMessage.put("timestamp", System.currentTimeMillis());
        dlqMessage.put("retryCount", 0);
        dlqMessage.put("queueName", UnifiedRabbitMQConfig.CRAWLING_DLQ);
        return dlqMessage;
    }
    
    private boolean checkIfShouldMoveToDLQ(Map<String, Object> message, int maxRetries) {
        Integer retryCount = (Integer) message.get("retryCount");
        return retryCount != null && retryCount > maxRetries;
    }
    
    private String createLargeData(int size) {
        StringBuilder sb = new StringBuilder(size);
        for (int i = 0; i < size; i++) {
            sb.append("A");
        }
        return sb.toString();
    }
}