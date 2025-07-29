package com.commonground.be.domain.session.repository;

import com.commonground.be.domain.session.entity.Session;

import java.util.Optional;

/**
 * 세션 레포지토리 인터페이스 - UserRepository와 동일한 패턴
 * 확장성: Redis, DB, Memory 등 다양한 구현체 가능
 */
public interface SessionRepository {
    
    void save(Session session);
    Optional<Session> findById(String sessionId);
    void deleteById(String sessionId);
    void deleteAllByUserId(String userId);
    long countByUserId(String userId);
}