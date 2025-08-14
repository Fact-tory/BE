package com.commonground.be.global.infrastructure.concurrency;

/**
 * 동시성 제어 관련 예외
 */
public class ConcurrencyException extends RuntimeException {

	public ConcurrencyException(String message) {
		super(message);
	}

	public ConcurrencyException(String message, Throwable cause) {
		super(message, cause);
	}
}