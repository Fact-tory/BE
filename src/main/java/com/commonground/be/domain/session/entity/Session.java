package com.commonground.be.domain.session.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.redis.core.RedisHash;
import org.springframework.data.redis.core.index.Indexed;

import java.time.LocalDateTime;

/**
 * 세션 엔티티 - Redis 저장용 엔티티
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@RedisHash(value = "session") // TTL은 동적으로 설정
public class Session {
    
    @Id
    private String sessionId;
    
    @Indexed
    private String userId;
    private String userAgent;
    private LocalDateTime createdAt;
    private LocalDateTime lastAccessTime;
    private Long expirationSeconds;
    private boolean active;

    // 비즈니스 로직
    public boolean isExpired() {
        if (expirationSeconds == null) return false;
        return createdAt.plusSeconds(expirationSeconds).isBefore(LocalDateTime.now());
    }

    public boolean isValid() {
        return active && !isExpired();
    }

    public void updateAccess() {
        this.lastAccessTime = LocalDateTime.now();
    }

    public void deactivate() {
        this.active = false;
    }

    // 팩토리 메서드
    public static Session create(String userId, String userAgent, Long expirationSeconds) {
        String sessionId = java.util.UUID.randomUUID().toString().replace("-", "");
        LocalDateTime now = LocalDateTime.now();
        
        return Session.builder()
                .sessionId(sessionId)
                .userId(userId)
                .userAgent(userAgent)
                .createdAt(now)
                .lastAccessTime(now)
                .expirationSeconds(expirationSeconds)
                .active(true)
                .build();
    }
}