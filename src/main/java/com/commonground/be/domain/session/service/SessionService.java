package com.commonground.be.domain.session.service;

import com.commonground.be.domain.session.dto.SessionCreateRequest;
import com.commonground.be.domain.session.dto.SessionResponse;

import java.util.Optional;

/**
 * 세션 서비스 인터페이스 - SocialAuthService와 동일한 패턴
 * 확장성: UserSessionService, AdminSessionService 등 다양한 구현체 가능
 */
public interface SessionService {
    
    SessionResponse createSession(SessionCreateRequest request);
    Optional<SessionResponse> getSession(String sessionId);
    void updateSessionAccess(String sessionId);
    boolean validateSession(String sessionId);
    void invalidateSession(String sessionId);
    void invalidateAllUserSessions(String userId);
    long getActiveSessionCount(String userId);
}