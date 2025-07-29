package com.commonground.be.domain.session.service;

import com.commonground.be.domain.session.dto.SessionCreateRequest;
import com.commonground.be.domain.session.dto.SessionResponse;
import com.commonground.be.domain.session.entity.Session;
import com.commonground.be.domain.session.repository.SessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * 일반 사용자 세션 서비스 - KakaoService와 동일한 패턴
 * 확장 예시: AdminSessionService, GuestSessionService 등
 */
@Slf4j
@Service("sessionService")
@RequiredArgsConstructor
public class UserSessionService implements SessionService {

    private final SessionRepository sessionRepository;

    @Value("${redis.expiration.user-session}")
    private Long sessionExpiration;

    @Value("${session.max-concurrent:3}")
    private Integer maxConcurrentSessions;

    @Override
    public SessionResponse createSession(SessionCreateRequest request) {
        // 비즈니스 규칙: 최대 세션 수 제한
        if (sessionRepository.countByUserId(request.getUserId()) >= maxConcurrentSessions) {
            sessionRepository.deleteAllByUserId(request.getUserId());
            log.info("최대 세션 수 초과로 기존 세션 정리: {}", request.getUserId());
        }

        // 세션 생성
        Session session = Session.create(
            request.getUserId(),
            request.getUserAgent(),
            sessionExpiration
        );
        
        sessionRepository.save(session);
        
        log.info("새 세션 생성: {} for user: {}", session.getSessionId(), request.getUserId());
        return SessionResponse.from(session);
    }

    @Override
    public Optional<SessionResponse> getSession(String sessionId) {
        return sessionRepository.findById(sessionId)
                .map(SessionResponse::from);
    }

    @Override
    public void updateSessionAccess(String sessionId) {
        sessionRepository.findById(sessionId)
                .ifPresent(session -> {
                    session.updateAccess();
                    sessionRepository.save(session);
                });
    }

    @Override
    public boolean validateSession(String sessionId) {
        return sessionRepository.findById(sessionId)
                .map(Session::isValid)
                .orElse(false);
    }

    @Override
    public void invalidateSession(String sessionId) {
        sessionRepository.findById(sessionId)
                .ifPresent(session -> {
                    session.deactivate();
                    sessionRepository.deleteById(sessionId);
                    log.info("세션 무효화: {}", sessionId);
                });
    }

    @Override
    public void invalidateAllUserSessions(String userId) {
        sessionRepository.deleteAllByUserId(userId);
        log.info("사용자 {} 모든 세션 무효화", userId);
    }

    @Override
    public long getActiveSessionCount(String userId) {
        return sessionRepository.countByUserId(userId);
    }
}