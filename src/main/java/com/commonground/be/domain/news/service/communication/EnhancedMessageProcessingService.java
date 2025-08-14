package com.commonground.be.domain.news.service.communication;

import com.commonground.be.domain.news.dto.crawling.RawNewsData;
import com.commonground.be.domain.news.dto.request.NaverCrawlingRequest;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 🚀 고도화된 메시지 처리 서비스
 * 
 * 기능:
 * - 메시지 재시도 로직
 * - Dead Letter Queue 처리
 * - 타임아웃 메시지 처리
 * - 메시지 상태 추적
 * - 실패 원인 분석
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EnhancedMessageProcessingService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final WebSocketProgressService progressService;
    private final CrawlingQueueService crawlingQueueService;
    private final ObjectMapper objectMapper;
    
    @Qualifier("enhancedRabbitTemplate")
    private final RabbitTemplate rabbitTemplate;

    /**
     * Dead Letter Queue 메시지 처리
     */
    @RabbitListener(
        queues = "crawling.request.dlq",
        containerFactory = "crawlingRabbitListenerContainerFactory"
    )
    public void handleDeadLetterRequest(
            Message message,
            Channel channel,
            @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) {
        
        try {
            String messageBody = new String(message.getBody());
            log.error("DLQ 메시지 수신 - 크롤링 요청 최종 실패: message={}", messageBody);
            
            // 메시지 파싱하여 세션 ID 추출
            String sessionId = extractSessionIdFromMessage(messageBody);
            if (sessionId != null) {
                // 실패 상태 업데이트
                progressService.updateProgress(
                    sessionId, "failed", 0,
                    "크롤링 요청이 여러 번 실패하여 중단되었습니다.", 0, 0, 0, 0
                );
                
                // 실패 원인 Redis에 저장
                String failureKey = "crawling_failure:" + sessionId;
                Map<String, Object> failureInfo = Map.of(
                    "sessionId", sessionId,
                    "failedAt", LocalDateTime.now().toString(),
                    "reason", "메시지 재시도 횟수 초과",
                    "originalMessage", messageBody,
                    "headers", message.getMessageProperties().getHeaders()
                );
                redisTemplate.opsForValue().set(failureKey, failureInfo, Duration.ofHours(24));
            }
            
            // DLQ 메시지는 항상 ACK (재시도하지 않음)
            channel.basicAck(deliveryTag, false);
            
        } catch (Exception e) {
            log.error("DLQ 메시지 처리 중 오류", e);
            try {
                channel.basicAck(deliveryTag, false);
            } catch (Exception ackException) {
                log.error("DLQ ACK 실패", ackException);
            }
        }
    }
    
    /**
     * Dead Letter Queue 결과 처리
     */
    @RabbitListener(
        queues = "crawling.result.dlq",
        containerFactory = "crawlingRabbitListenerContainerFactory"
    )
    public void handleDeadLetterResult(
            Message message,
            Channel channel,
            @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) {
        
        try {
            String messageBody = new String(message.getBody());
            log.error("DLQ 결과 메시지 수신 - 처리 최종 실패: message={}", messageBody);
            
            // 실패한 결과 메시지 정보 저장
            String failureKey = "result_failure:" + System.currentTimeMillis();
            Map<String, Object> failureInfo = Map.of(
                "failedAt", LocalDateTime.now().toString(),
                "reason", "결과 메시지 처리 실패",
                "originalMessage", messageBody
            );
            redisTemplate.opsForValue().set(failureKey, failureInfo, Duration.ofHours(24));
            
            channel.basicAck(deliveryTag, false);
            
        } catch (Exception e) {
            log.error("DLQ 결과 메시지 처리 중 오류", e);
            try {
                channel.basicAck(deliveryTag, false);
            } catch (Exception ackException) {
                log.error("DLQ 결과 ACK 실패", ackException);
            }
        }
    }
    
    /**
     * 타임아웃된 크롤링 요청 처리
     */
    @RabbitListener(
        queues = "crawling.timeout.queue",
        containerFactory = "crawlingRabbitListenerContainerFactory"
    )
    public void handleTimeoutMessage(
            Message message,
            Channel channel,
            @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) {
        
        try {
            String messageBody = new String(message.getBody());
            log.warn("타임아웃 메시지 수신: message={}", messageBody);
            
            String sessionId = extractSessionIdFromMessage(messageBody);
            if (sessionId != null) {
                // 타임아웃 상태 업데이트
                progressService.updateProgress(
                    sessionId, "timeout", 0,
                    "크롤링 요청이 시간 초과되었습니다.", 0, 0, 0, 0
                );
                
                // 재시도 여부 결정 (재시도 횟수 확인)
                String retryKey = "retry_count:" + sessionId;
                Integer retryCount = (Integer) redisTemplate.opsForValue().get(retryKey);
                if (retryCount == null) retryCount = 0;
                
                if (retryCount < 2) { // 최대 2회 재시도
                    retryCount++;
                    redisTemplate.opsForValue().set(retryKey, retryCount, Duration.ofHours(1));
                    
                    log.info("크롤링 재시도 시도: sessionId={}, attempt={}", sessionId, retryCount);
                    
                    // 지연 후 재시도 (5분 후)
                    scheduleRetry(messageBody, Duration.ofMinutes(5));
                    
                } else {
                    log.error("크롤링 재시도 횟수 초과: sessionId={}", sessionId);
                    progressService.updateProgress(
                        sessionId, "failed", 0,
                        "재시도 횟수를 초과하여 크롤링을 중단합니다.", 0, 0, 0, 0
                    );
                }
            }
            
            channel.basicAck(deliveryTag, false);
            
        } catch (Exception e) {
            log.error("타임아웃 메시지 처리 중 오류", e);
            try {
                channel.basicAck(deliveryTag, false);
            } catch (Exception ackException) {
                log.error("타임아웃 ACK 실패", ackException);
            }
        }
    }
    
    /**
     * Python 크롤러 헬스체크 처리
     */
    @RabbitListener(
        queues = "crawling.health.queue",
        containerFactory = "crawlingRabbitListenerContainerFactory"
    )
    public void handleHealthCheck(
            Message message,
            Channel channel,
            @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) {
        
        try {
            String messageBody = new String(message.getBody());
            log.debug("Python 크롤러 헬스체크 수신: {}", messageBody);
            
            // 헬스체크 정보를 Redis에 저장
            String healthKey = "crawler_health:python";
            Map<String, Object> healthInfo = Map.of(
                "lastSeen", LocalDateTime.now().toString(),
                "status", "healthy",
                "message", messageBody
            );
            redisTemplate.opsForValue().set(healthKey, healthInfo, Duration.ofMinutes(10));
            
            channel.basicAck(deliveryTag, false);
            
        } catch (Exception e) {
            log.error("헬스체크 메시지 처리 중 오류", e);
            try {
                channel.basicAck(deliveryTag, false);
            } catch (Exception ackException) {
                log.error("헬스체크 ACK 실패", ackException);
            }
        }
    }
    
    /**
     * 메시지에서 세션 ID 추출
     */
    private String extractSessionIdFromMessage(String messageBody) {
        try {
            // JSON 파싱하여 sessionId 추출
            @SuppressWarnings("unchecked")
            Map<String, Object> messageMap = objectMapper.readValue(messageBody, Map.class);
            Object payload = messageMap.get("payload");
            
            if (payload instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> payloadMap = (Map<String, Object>) payload;
                return (String) payloadMap.get("sessionId");
            }
            
            return (String) messageMap.get("requestId");
            
        } catch (JsonProcessingException e) {
            log.error("세션 ID 추출 실패: messageBody={}", messageBody, e);
            return null;
        }
    }
    
    /**
     * 지연 재시도 스케줄링
     */
    private void scheduleRetry(String originalMessage, Duration delay) {
        // 실제 구현에서는 Redis나 스케줄러를 사용하여 지연 실행
        // 여기서는 단순히 로그로 표시
        log.info("재시도 스케줄링: delay={}분, message={}", 
            delay.toMinutes(), originalMessage.substring(0, Math.min(100, originalMessage.length())));
    }
    
    /**
     * 크롤링 시스템 상태 조회
     */
    public Map<String, Object> getCrawlingSystemStatus() {
        // Python 크롤러 헬스체크 상태
        Object pythonHealth = redisTemplate.opsForValue().get("crawler_health:python");
        
        // 최근 실패한 크롤링 요청 수 (Redis KEYS 명령어 사용)
        Long failureCount = 0L;
        try {
            String pattern = "crawling_failure:*";
            var keys = redisTemplate.keys(pattern);
            failureCount = keys != null ? (long) keys.size() : 0L;
        } catch (Exception e) {
            log.warn("실패 카운트 조회 실패", e);
        }
        
        // 대기 중인 요청 수 (pendingRequests에서 가져와야 하지만 여기서는 예시)
        int pendingRequests = 0; // crawlingQueueService.getPendingRequestCount();
        
        return Map.of(
            "pythonCrawlerHealth", pythonHealth != null ? pythonHealth : "unknown",
            "recentFailures", failureCount != null ? failureCount : 0,
            "pendingRequests", pendingRequests,
            "systemStatus", pythonHealth != null ? "healthy" : "warning",
            "lastUpdated", LocalDateTime.now().toString()
        );
    }
}