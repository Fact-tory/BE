package com.commonground.be.domain.session.repository;

import com.commonground.be.domain.session.entity.Session;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Redis 기반 세션 레포지토리 구현체 - UserAdapter와 동일한 패턴
 * UserAdapter가 UserRepository 구현하듯이 SessionAdapter가 SessionRepository 구현
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class SessionAdapter implements SessionRepository {

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${redis.expiration.user-session}")
    private Long sessionExpiration;

    @Override
    public void save(Session session) {
        try {
            String sessionJson = objectMapper.writeValueAsString(session);
            
            // 세션 데이터 저장
            redisTemplate.opsForValue().set(
                "session:" + session.getSessionId(),
                sessionJson,
                sessionExpiration,
                TimeUnit.SECONDS
            );
            
            // 사용자별 세션 목록에 추가
            redisTemplate.opsForSet().add("user_sessions:" + session.getUserId(), session.getSessionId());
            redisTemplate.expire("user_sessions:" + session.getUserId(), sessionExpiration, TimeUnit.SECONDS);
            
        } catch (JsonProcessingException e) {
            log.error("세션 저장 실패: {}", e.getMessage());
            throw new RuntimeException("세션 저장 실패", e);
        }
    }

    @Override
    public Optional<Session> findById(String sessionId) {
        try {
            String sessionJson = redisTemplate.opsForValue().get("session:" + sessionId);
            if (sessionJson == null) {
                return Optional.empty();
            }
            
            Session session = objectMapper.readValue(sessionJson, Session.class);
            return Optional.of(session);
            
        } catch (JsonProcessingException e) {
            log.error("세션 조회 실패: {}", e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public void deleteById(String sessionId) {
        Optional<Session> session = findById(sessionId);
        if (session.isPresent()) {
            redisTemplate.delete("session:" + sessionId);
            redisTemplate.opsForSet().remove("user_sessions:" + session.get().getUserId(), sessionId);
        }
    }

    @Override
    public void deleteAllByUserId(String userId) {
        Set<String> sessionIds = redisTemplate.opsForSet().members("user_sessions:" + userId);
        if (sessionIds != null) {
            sessionIds.forEach(sessionId -> redisTemplate.delete("session:" + sessionId));
            redisTemplate.delete("user_sessions:" + userId);
        }
    }

    @Override
    public long countByUserId(String userId) {
        Long count = redisTemplate.opsForSet().size("user_sessions:" + userId);
        return count != null ? count : 0L;
    }
}