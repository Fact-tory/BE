package com.commonground.be.domain.news.service.communication;

import com.commonground.be.domain.news.dto.crawling.CrawlingProgress;
import java.time.LocalDateTime;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class WebSocketProgressService {
    
    private final SimpMessagingTemplate messagingTemplate;
    private final RedisTemplate<String, Object> redisTemplate;
    
    public void updateProgress(String sessionId, String step, int progress, String message) {
        updateProgress(sessionId, step, progress, message, 0, 0, 0, 0);
    }
    
    public void updateProgress(String sessionId, String step, int progress, String message,
                             int totalArticles, int processedArticles, int successCount, int failCount) {
        
        CrawlingProgress progressData = CrawlingProgress.builder()
            .sessionId(sessionId)
            .step(step)
            .progress(progress)
            .message(message)
            .totalArticles(totalArticles)
            .processedArticles(processedArticles)
            .successCount(successCount)
            .failCount(failCount)
            .timestamp(LocalDateTime.now())
            .build();
        
        try {
            // WebSocket으로 클라이언트에게 전송
            String destination = "/topic/crawling/" + sessionId;
            messagingTemplate.convertAndSend(destination, progressData);
            
            // Redis pub/sub으로 다른 서버 인스턴스에게 브로드캐스트
            redisTemplate.convertAndSend("crawling_progress", progressData);
            
            log.debug("진행상황 업데이트 전송: sessionId={}, step={}, progress={}%, 처리={}/{}, 성공={}, 실패={}", 
                sessionId, step, progress, processedArticles, totalArticles, successCount, failCount);
                
        } catch (Exception e) {
            log.error("진행상황 업데이트 전송 실패: sessionId={}", sessionId, e);
        }
    }
    
    public Optional<CrawlingProgress> getProgress(String sessionId) {
        try {
            String cacheKey = "crawling_progress:" + sessionId;
            CrawlingProgress progress = (CrawlingProgress) redisTemplate.opsForValue().get(cacheKey);
            return Optional.ofNullable(progress);
            
        } catch (Exception e) {
            log.error("진행상황 조회 실패: sessionId={}", sessionId, e);
            return Optional.empty();
        }
    }
}