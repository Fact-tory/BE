package com.commonground.be.global.infrastructure.concurrency;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Redis 분산 락을 적용하는 어노테이션
 * <p>
 * 사용 예시:
 *
 * @RedisLock(key = "'user_session:' + #username", waitTime = 3, leaseTime = 10) public String
 * socialLogin(String username, ...) { ... }
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RedisLock {

	/**
	 * 락 키 (SpEL 표현식 지원) 예: "'user_session:' + #username"
	 */
	String key();

	/**
	 * 락 획득 대기 시간 (초) 기본값: 3초
	 */
	long waitTime() default 3L;

	/**
	 * 락 보유 시간 (초) 기본값: 10초
	 */
	long leaseTime() default 10L;

	/**
	 * 락 획득 실패 시 예외 메시지
	 */
	String timeoutMessage() default "동시 요청 처리 중입니다. 잠시 후 다시 시도해주세요.";
}