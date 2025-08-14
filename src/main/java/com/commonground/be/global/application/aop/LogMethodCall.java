package com.commonground.be.global.application.aop;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 메서드 호출 상세 정보(파라미터 포함)를 로깅하는 어노테이션
 * 
 * 사용 예:
 * @LogMethodCall
 * public String processData(String input) { ... }
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface LogMethodCall {
}