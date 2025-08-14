package com.commonground.be.global.infrastructure.concurrency;

import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class RedisLockAspect {

	private final RedissonClient redissonClient;
	private final ExpressionParser expressionParser = new SpelExpressionParser();

	@Around("@annotation(redisLock)")
	public Object around(ProceedingJoinPoint joinPoint, RedisLock redisLock) throws Throwable {
		// SpEL로 락 키 생성
		String lockKey = generateLockKey(redisLock.key(), joinPoint);

		RLock lock = redissonClient.getLock(lockKey);
		boolean acquired = false;

		try {
			// 락 획득 시도
			acquired = lock.tryLock(redisLock.waitTime(), redisLock.leaseTime(), TimeUnit.SECONDS);

			if (!acquired) {
				log.warn("락 획득 실패: {}", lockKey);
				throw new ConcurrencyException(redisLock.timeoutMessage());
			}

			log.debug("락 획득 성공: {}", lockKey);
			return joinPoint.proceed();

		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new ConcurrencyException("락 획득 중 인터럽트 발생");
		} finally {
			if (acquired && lock.isHeldByCurrentThread()) {
				lock.unlock();
				log.debug("락 해제: {}", lockKey);
			}
		}
	}

	/**
	 * SpEL을 사용하여 동적으로 락 키 생성
	 */
	private String generateLockKey(String keyExpression, ProceedingJoinPoint joinPoint) {
		MethodSignature signature = (MethodSignature) joinPoint.getSignature();
		Method method = signature.getMethod();
		String[] parameterNames = signature.getParameterNames();
		Object[] args = joinPoint.getArgs();

		StandardEvaluationContext context = new StandardEvaluationContext();

		// 메서드 파라미터를 SpEL 컨텍스트에 추가
		for (int i = 0; i < parameterNames.length; i++) {
			context.setVariable(parameterNames[i], args[i]);
		}

		return expressionParser.parseExpression(keyExpression).getValue(context, String.class);
	}
}