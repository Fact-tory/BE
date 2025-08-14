package com.commonground.be.global.application.aop;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;

/**
 * 전역 메서드 실행 로깅을 위한 AOP
 * 
 * 사용법:
 * 1. @LogExecutionTime - 메서드 실행 시간 로깅
 * 2. @LogMethodCall - 메서드 호출 상세 로깅  
 * 3. 특정 패키지/클래스 지정 - execution 포인트컷 사용
 */
@Aspect
@Component
@Slf4j
public class MethodLoggingAspect {

    /**
     * @LogExecutionTime 어노테이션이 붙은 메서드의 실행 시간 로깅
     */
    @Around("@annotation(com.commonground.be.global.application.aop.LogExecutionTime)")
    public Object logExecutionTime(ProceedingJoinPoint joinPoint) throws Throwable {
        Instant start = Instant.now();
        String className = joinPoint.getTarget().getClass().getSimpleName();
        String methodName = joinPoint.getSignature().getName();
        
        log.debug("⏱️ 메서드 실행 시작: {}.{}", className, methodName);
        
        try {
            Object result = joinPoint.proceed();
            
            Duration duration = Duration.between(start, Instant.now());
            log.info("✅ 메서드 실행 완료: {}.{}, 소요시간={}ms", 
                    className, methodName, duration.toMillis());
            
            return result;
        } catch (Exception e) {
            Duration duration = Duration.between(start, Instant.now());
            log.error("❌ 메서드 실행 실패: {}.{}, 소요시간={}ms, error={}", 
                    className, methodName, duration.toMillis(), e.getMessage(), e);
            throw e;
        }
    }

    /**
     * @LogMethodCall 어노테이션이 붙은 메서드의 상세 호출 로깅 (파라미터 포함)
     */
    @Around("@annotation(com.commonground.be.global.application.aop.LogMethodCall)")
    public Object logMethodCall(ProceedingJoinPoint joinPoint) throws Throwable {
        Instant start = Instant.now();
        String className = joinPoint.getTarget().getClass().getSimpleName();
        String methodName = joinPoint.getSignature().getName();
        Object[] args = joinPoint.getArgs();
        
        log.info("🚀 메서드 호출: {}.{}, args={}", className, methodName, Arrays.toString(args));
        
        try {
            Object result = joinPoint.proceed();
            
            Duration duration = Duration.between(start, Instant.now());
            log.info("✅ 메서드 완료: {}.{}, 소요시간={}ms", 
                    className, methodName, duration.toMillis());
            
            return result;
        } catch (Exception e) {
            Duration duration = Duration.between(start, Instant.now());
            log.error("❌ 메서드 예외: {}.{}, 소요시간={}ms, error={}", 
                    className, methodName, duration.toMillis(), e.getMessage(), e);
            throw e;
        }
    }

    /**
     * 크롤링 관련 메서드 로깅 (기존 CrawlingLoggingAspect 기능 유지)
     */
    @Around("execution(* com.commonground.be.domain.news.service.NaverNewsCrawler.crawlNews(..))")
    public Object logCrawlingExecution(ProceedingJoinPoint joinPoint) throws Throwable {
        Instant start = Instant.now();
        String methodName = joinPoint.getSignature().getName();
        Object[] args = joinPoint.getArgs();
        
        log.info("🚀 크롤링 시작: method={}, args={}", methodName, Arrays.toString(args));
        
        try {
            Object result = joinPoint.proceed();
            
            Duration duration = Duration.between(start, Instant.now());
            log.info("✅ 크롤링 성공: method={}, 소요시간={}ms", methodName, duration.toMillis());
            
            return result;
        } catch (Exception e) {
            Duration duration = Duration.between(start, Instant.now());
            log.error("❌ 크롤링 실패: method={}, 소요시간={}ms, error={}", 
                    methodName, duration.toMillis(), e.getMessage(), e);
            throw e;
        }
    }

    /**
     * 개별 기사 크롤링 로깅 (기존 기능 유지)
     */
    @Around("execution(* com.commonground.be.domain.news.service.NaverNewsCrawler.crawlSingleArticle(..))")
    public Object logSingleArticleCrawling(ProceedingJoinPoint joinPoint) throws Throwable {
        Instant start = Instant.now();
        Object[] args = joinPoint.getArgs();
        String articleUrl = args.length > 0 ? args[0].toString() : "unknown";
        
        log.debug("📰 개별 기사 크롤링 시작: url={}", articleUrl);
        
        try {
            Object result = joinPoint.proceed();
            
            Duration duration = Duration.between(start, Instant.now());
            
            if (result != null) {
                log.debug("✅ 개별 기사 크롤링 성공: url={}, 소요시간={}ms", articleUrl, duration.toMillis());
            } else {
                log.warn("⚠️ 개별 기사 크롤링 실패 (null 반환): url={}, 소요시간={}ms", articleUrl, duration.toMillis());
            }
            
            return result;
        } catch (Exception e) {
            Duration duration = Duration.between(start, Instant.now());
            log.error("❌ 개별 기사 크롤링 예외: url={}, 소요시간={}ms, error={}", 
                    articleUrl, duration.toMillis(), e.getMessage());
            throw e;
        }
    }
}