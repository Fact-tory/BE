package com.commonground.be.domain.session.dto;

import com.commonground.be.domain.session.entity.Session;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 세션 응답 DTO
 */
@Data
@Builder
public class SessionResponse {
    private String sessionId;
    private String userId;
    private String userAgent;
    private LocalDateTime createdAt;
    private LocalDateTime lastAccessTime;
    private boolean active;
    private boolean expired;

    public static SessionResponse from(Session session) {
        return SessionResponse.builder()
                .sessionId(session.getSessionId())
                .userId(session.getUserId())
                .userAgent(session.getUserAgent())
                .createdAt(session.getCreatedAt())
                .lastAccessTime(session.getLastAccessTime())
                .active(session.isActive())
                .expired(session.isExpired())
                .build();
    }
}