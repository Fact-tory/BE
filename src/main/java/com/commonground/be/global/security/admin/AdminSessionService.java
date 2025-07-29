package com.commonground.be.global.security.admin;

import com.commonground.be.domain.session.dto.SessionCreateRequest;
import com.commonground.be.domain.session.dto.SessionResponse;
import com.commonground.be.domain.session.entity.Session;
import com.commonground.be.domain.session.repository.SessionRepository;
import com.commonground.be.domain.session.service.SessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * 관리자 세션 서비스 - 확장성 예시
 * UserSessionService와 동일한 인터페이스지만 다른 정책 적용
 */
@Slf4j
@Service("adminSessionService")
@RequiredArgsConstructor
public class AdminSessionService implements SessionService {

    private final SessionRepository sessionRepository;

    @Value("${admin.session.expiration:1800}") // 30분 (더 짧은 세션)
    private Long adminSessionExpiration;

    @Value("${admin.session.max-concurrent:1}") // 관리자는 1개 세션만
    private Integer maxConcurrentSessions;

    @Override
    public SessionResponse createSession(SessionCreateRequest request) {
        // 관리자는 기존 세션 무조건 무효화 (단일 세션 정책)
        sessionRepository.deleteAllByUserId(request.getUserId());
        log.info("관리자 기존 세션 정리: {}", request.getUserId());

        Session session = Session.create(
            request.getUserId(),
            request.getUserAgent(),
            adminSessionExpiration // 더 짧은 만료시간
        );
        
        sessionRepository.save(session);
        
        log.info("관리자 세션 생성: {} for admin: {}", session.getSessionId(), request.getUserId());
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
                    log.info("관리자 세션 무효화: {}", sessionId);
                });
    }

    @Override
    public void invalidateAllUserSessions(String userId) {
        sessionRepository.deleteAllByUserId(userId);
        log.info("관리자 {} 모든 세션 무효화", userId);
    }

    @Override
    public long getActiveSessionCount(String userId) {
        return sessionRepository.countByUserId(userId);
    }
}