package com.commonground.be.global.security.admin;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;

/**
 * 관리자 권한 체크를 위한 AOP(Aspect-Oriented Programming) 클래스
 * 
 * <p>이 클래스는 {@link AdminRequired} 어노테이션이 적용된 메서드들에 대해
 * 실행 전에 관리자 권한을 자동으로 검증하는 횡단 관심사(Cross-cutting Concern)를 처리합니다.</p>
 * 
 * <h3>주요 기능:</h3>
 * <ul>
 *   <li>메서드 실행 전 관리자 토큰 검증</li>
 *   <li>다중 HTTP 헤더 지원 (Authorization, X-Admin-Token)</li>
 *   <li>클라이언트 IP 추적 및 보안 로깅</li>
 *   <li>권한 체크 실패 시 상세한 보안 감사 로그</li>
 *   <li>커스터마이징 가능한 에러 메시지</li>
 * </ul>
 * 
 * <h3>동작 과정:</h3>
 * <ol>
 *   <li>@AdminRequired 어노테이션이 적용된 메서드 호출 감지</li>
 *   <li>HTTP 요청에서 관리자 토큰 추출</li>
 *   <li>AdminTokenValidator를 통한 토큰 유효성 검증</li>
 *   <li>유효한 경우 원본 메서드 실행, 무효한 경우 SecurityException 발생</li>
 *   <li>모든 과정에 대한 보안 감사 로그 기록</li>
 * </ol>
 * 
 * <h3>지원하는 토큰 전달 방식:</h3>
 * <ul>
 *   <li>우선순위 1: X-Admin-Token 헤더</li>
 *   <li>우선순위 2: Authorization 헤더 (Bearer 토큰)</li>
 * </ul>
 * 
 * @author CommonGround Development Team
 * @since 1.0
 * @see AdminRequired
 * @see AdminTokenValidator
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class AdminAuthAspect {

    /**
     * 관리자 토큰 검증을 수행하는 컴포넌트
     */
    private final AdminTokenValidator adminTokenValidator;

    /**
     * @AdminRequired 어노테이션이 적용된 메서드 실행 전에 관리자 권한을 검증합니다.
     * 
     * <p>이 메서드는 AOP Around Advice로 동작하여, 대상 메서드 실행 전후로
     * 권한 검증과 로깅을 수행합니다.</p>
     * 
     * <h3>검증 단계:</h3>
     * <ol>
     *   <li>HTTP 요청 컨텍스트 유효성 확인</li>
     *   <li>어노테이션 활성화 상태 확인</li>
     *   <li>HTTP 헤더에서 관리자 토큰 추출</li>
     *   <li>토큰 유효성 검증</li>
     *   <li>권한 확인 완료 후 원본 메서드 실행</li>
     * </ol>
     * 
     * @param joinPoint AOP 조인 포인트 - 실행될 메서드의 정보
     * @param adminRequired 메서드에 적용된 @AdminRequired 어노테이션
     * @return 원본 메서드의 실행 결과
     * 
     * @throws Throwable 다음과 같은 경우 예외 발생:
     *                   <ul>
     *                     <li>HTTP 요청 컨텍스트를 찾을 수 없는 경우</li>
     *                     <li>관리자 토큰이 유효하지 않은 경우</li>
     *                     <li>원본 메서드 실행 중 발생한 예외</li>
     *                   </ul>
     * 
     * @apiNote 이 메서드는 Spring AOP에 의해 자동으로 호출되며, 
     *          직접 호출하지 않습니다.
     */
    @Around("@annotation(adminRequired)")
    public Object checkAdminAuth(ProceedingJoinPoint joinPoint, AdminRequired adminRequired) throws Throwable {
        
        // 메서드 정보 추출 (로깅용)
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        String methodName = method.getDeclaringClass().getSimpleName() + "." + method.getName();
        
        // 어노테이션 활성화 상태 확인
        if (!adminRequired.enabled()) {
            logSecurityEvent(AdminRequired.LogLevel.DEBUG, adminRequired.logLevel(), 
                "관리자 권한 체크 비활성화됨 - 메서드: {}", methodName);
            return joinPoint.proceed();
        }
        
        // HTTP 요청 컨텍스트 추출
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            log.error("HTTP 요청 컨텍스트를 찾을 수 없음 - AOP 실행 환경 오류");
            throw new SecurityException("HTTP 요청 컨텍스트를 찾을 수 없습니다");
        }

        HttpServletRequest request = attributes.getRequest();
        String requestUri = request.getRequestURI();
        String clientIp = getClientIpAddress(request);
        
        logSecurityEvent(AdminRequired.LogLevel.DEBUG, adminRequired.logLevel(), 
            "관리자 권한 체크 시작 - URI: {}, Method: {}, IP: {}", 
            requestUri, methodName, clientIp);
        
        // 관리자 토큰 추출 (우선순위: X-Admin-Token → Authorization)
        String adminToken = extractAdminToken(request);
        
        // 토큰 존재 여부 확인
        if (adminToken == null || adminToken.trim().isEmpty()) {
            logSecurityFailure(requestUri, methodName, clientIp, "관리자 토큰이 제공되지 않음");
            throw new SecurityException(adminRequired.message());
        }

        // 관리자 토큰 유효성 검증
        if (!adminTokenValidator.isValidAdminToken(adminToken)) {
            logSecurityFailure(requestUri, methodName, clientIp, "유효하지 않은 관리자 토큰");
            throw new SecurityException(adminRequired.message());
        }

        // 권한 검증 성공 로깅
        logSecurityEvent(AdminRequired.LogLevel.INFO, adminRequired.logLevel(), 
            "관리자 권한 확인 완료 - URI: {}, Method: {}, IP: {}", 
            requestUri, methodName, clientIp);
        
        try {
            // 관리자 권한 확인 완료, 원본 메서드 실행
            Object result = joinPoint.proceed();
            
            logSecurityEvent(AdminRequired.LogLevel.DEBUG, adminRequired.logLevel(), 
                "관리자 메서드 실행 완료 - Method: {}", methodName);
            
            return result;
            
        } catch (Exception e) {
            // 원본 메서드 실행 중 예외 발생
            log.error("관리자 메서드 실행 중 예외 발생 - Method: {}, Error: {}", 
                methodName, e.getMessage());
            throw e;
        }
    }

    /**
     * HTTP 요청에서 관리자 토큰을 추출합니다.
     * 
     * <p>다음 순서로 토큰을 찾습니다:
     * <ol>
     *   <li>X-Admin-Token 헤더 (우선순위 높음)</li>
     *   <li>Authorization 헤더 (fallback)</li>
     * </ol>
     * </p>
     * 
     * @param request HTTP 요청 객체
     * @return 추출된 관리자 토큰, 없으면 null
     * 
     * @apiNote Authorization 헤더의 경우 Bearer 접두사는 
     *          AdminTokenValidator에서 처리됩니다.
     */
    private String extractAdminToken(HttpServletRequest request) {
        // 1순위: X-Admin-Token 헤더 확인
        String adminToken = request.getHeader(AdminTokenValidator.ADMIN_TOKEN_HEADER);
        
        if (adminToken != null && !adminToken.trim().isEmpty()) {
            log.debug("X-Admin-Token 헤더에서 관리자 토큰 추출");
            return adminToken;
        }
        
        // 2순위: Authorization 헤더 확인 (fallback)
        adminToken = request.getHeader("Authorization");
        
        if (adminToken != null && !adminToken.trim().isEmpty()) {
            log.debug("Authorization 헤더에서 관리자 토큰 추출");
            return adminToken;
        }
        
        return null;
    }

    /**
     * 클라이언트의 실제 IP 주소를 추출합니다.
     * 
     * <p>프록시나 로드 밸런서를 고려하여 다음 헤더들을 순서대로 확인합니다:
     * <ol>
     *   <li>X-Forwarded-For</li>
     *   <li>X-Real-IP</li>
     *   <li>X-Original-Forwarded-For</li>
     *   <li>RemoteAddr (최종 fallback)</li>
     * </ol>
     * </p>
     * 
     * @param request HTTP 요청 객체
     * @return 클라이언트의 실제 IP 주소
     * 
     * @apiNote 보안 감사 로그에 사용되므로 정확한 IP 추출이 중요합니다.
     */
    private String getClientIpAddress(HttpServletRequest request) {
        String[] ipHeaderNames = {
            "X-Forwarded-For", 
            "X-Real-IP", 
            "X-Original-Forwarded-For"
        };
        
        // 프록시 헤더들을 순서대로 확인
        for (String headerName : ipHeaderNames) {
            String ip = request.getHeader(headerName);
            if (isValidIpAddress(ip)) {
                // X-Forwarded-For의 경우 첫 번째 IP만 사용 (클라이언트 실제 IP)
                return ip.split(",")[0].trim();
            }
        }
        
        // 모든 프록시 헤더가 없으면 직접 연결 IP 사용
        return request.getRemoteAddr();
    }

    /**
     * IP 주소의 유효성을 검사합니다.
     * 
     * @param ip 검사할 IP 주소 문자열
     * @return 유효한 IP 주소인 경우 true
     */
    private boolean isValidIpAddress(String ip) {
        return ip != null 
            && !ip.trim().isEmpty() 
            && !"unknown".equalsIgnoreCase(ip.trim());
    }

    /**
     * 보안 관련 이벤트를 로깅합니다.
     * 
     * <p>@AdminRequired 어노테이션의 logLevel 설정에 따라 
     * 로깅 여부가 결정됩니다.</p>
     * 
     * @param eventLevel 이벤트의 기본 레벨
     * @param configuredLevel 어노테이션에서 설정된 로깅 레벨
     * @param message 로그 메시지 (포맷 문자열)
     * @param args 로그 메시지 인자들
     */
    private void logSecurityEvent(AdminRequired.LogLevel eventLevel, 
                                 AdminRequired.LogLevel configuredLevel,
                                 String message, Object... args) {
        
        // 설정된 로깅 레벨 확인
        if (configuredLevel == AdminRequired.LogLevel.NONE) {
            return; // 로깅 비활성화
        }
        
        // 이벤트 레벨이 설정된 레벨보다 높은 경우에만 로깅
        if (shouldLog(eventLevel, configuredLevel)) {
            if (eventLevel == AdminRequired.LogLevel.DEBUG) {
                log.debug(message, args);
            } else {
                log.info(message, args);
            }
        }
    }

    /**
     * 로깅 여부를 결정합니다.
     * 
     * @param eventLevel 이벤트 레벨
     * @param configuredLevel 설정된 로깅 레벨
     * @return 로깅해야 하는 경우 true
     */
    private boolean shouldLog(AdminRequired.LogLevel eventLevel, AdminRequired.LogLevel configuredLevel) {
        if (configuredLevel == AdminRequired.LogLevel.DEBUG) {
            return true; // DEBUG 설정 시 모든 로그 출력
        }
        
        if (configuredLevel == AdminRequired.LogLevel.INFO) {
            return eventLevel == AdminRequired.LogLevel.INFO; // INFO 레벨만 출력
        }
        
        return false; // NONE인 경우
    }

    /**
     * 보안 실패 이벤트를 로깅합니다.
     * 
     * <p>관리자 권한 체크 실패는 보안상 중요한 이벤트이므로
     * 항상 WARNING 레벨로 로깅됩니다.</p>
     * 
     * @param requestUri 요청 URI
     * @param methodName 메서드 이름
     * @param clientIp 클라이언트 IP
     * @param reason 실패 사유
     */
    private void logSecurityFailure(String requestUri, String methodName, String clientIp, String reason) {
        log.warn("관리자 권한 체크 실패 - URI: {}, Method: {}, IP: {}, Reason: {}", 
                requestUri, methodName, clientIp, reason);
    }
}