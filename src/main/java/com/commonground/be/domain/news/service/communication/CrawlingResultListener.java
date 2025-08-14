package com.commonground.be.domain.news.service.communication;

import com.commonground.be.domain.news.dto.crawling.CrawlingProgress;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.listener.adapter.MessageListenerAdapter;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * 🔔 크롤링 결과 큐 리스너 (RabbitMQ)
 * 
 * 책임:
 * - Python 크롤러로부터 결과 메시지 수신
 * - CrawlingQueueService로 결과 전달
 * - WebSocket으로 진행상황 브로드캐스트
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CrawlingResultListener {
    
    private final CrawlingQueueService crawlingQueueService;
    private final WebSocketProgressService progressService;
    
    /**
     * 크롤링 결과 메시지 수신
     */
    @RabbitListener(
        queues = "#{T(com.commonground.be.global.infrastructure.config.RabbitMQConfig).CRAWLING_RESULT_QUEUE}",
        containerFactory = "crawlingRabbitListenerContainerFactory"
    )
    public void handleCrawlingResult(
            CrawlingQueueService.CrawlingResultMessage result,
            Channel channel,
            @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) {
        
        try {
            log.debug("크롤링 결과 메시지 수신: requestId={}, success={}", 
                result.getRequestId(), result.isSuccess());
            
            crawlingQueueService.handleCrawlingResult(result);
            
            // 수동 ACK
            channel.basicAck(deliveryTag, false);
            
        } catch (Exception e) {
            log.error("크롤링 결과 처리 실패: requestId={}", result.getRequestId(), e);
            try {
                // NACK - 메시지를 재큐잉하지 않고 DLQ로 보냄
                channel.basicNack(deliveryTag, false, false);
            } catch (Exception nackException) {
                log.error("NACK 처리 실패", nackException);
            }
        }
    }
    
    /**
     * 크롤링 진행상황 메시지 수신
     */
    @RabbitListener(
        queues = "#{T(com.commonground.be.global.infrastructure.config.RabbitMQConfig).CRAWLING_PROGRESS_QUEUE}",
        containerFactory = "crawlingRabbitListenerContainerFactory"
    )
    public void handleCrawlingProgress(
            CrawlingProgress progress,
            Channel channel,
            @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) {
        
        try {
            log.debug("크롤링 진행상황 수신: sessionId={}, progress={}%", 
                progress.getSessionId(), progress.getProgress());
            
            // WebSocket으로 진행상황 브로드캐스트
            progressService.updateProgress(
                progress.getSessionId(),
                progress.getStatus(),
                progress.getProgress(),
                progress.getMessage(),
                progress.getTotalArticles(),
                progress.getProcessedArticles(),
                progress.getSuccessCount(),
                progress.getFailCount()
            );
            
            // 수동 ACK
            channel.basicAck(deliveryTag, false);
            
        } catch (Exception e) {
            log.error("크롤링 진행상황 처리 실패: sessionId={}", progress.getSessionId(), e);
            try {
                channel.basicAck(deliveryTag, false); // 진행상황은 중요하지 않으므로 그냥 ACK
            } catch (Exception ackException) {
                log.error("ACK 처리 실패", ackException);
            }
        }
    }
}