package com.commonground.be.global.application.exception;

import static com.commonground.be.global.application.response.ResponseUtils.of;

import com.commonground.be.global.application.response.HttpResponseDto;
import com.commonground.be.global.application.response.ResponseCodeEnum;
import com.commonground.be.global.application.response.ResponseExceptionEnum;
import com.commonground.be.global.infrastructure.concurrency.ConcurrencyException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@ControllerAdvice
public class GlobalExceptionAdvice {

	/**
	 * 공통 예외 처리
	 */
	@ExceptionHandler(CommonException.class)
	public ResponseEntity<HttpResponseDto> handleCommonException(CommonException e) {
		log.error("🚨 CommonException 발생: {}", e.getResponseExceptionEnum().getMessage(), e);
		return of(e.getResponseExceptionEnum());
	}
	
	/**
	 * 동시성 예외 처리 (Redis Lock)
	 */
	@ExceptionHandler(ConcurrencyException.class)
	public ResponseEntity<HttpResponseDto> handleConcurrencyException(ConcurrencyException e) {
		log.warn("⚠️ 동시성 제어: {}", e.getMessage());
		return of(ResponseExceptionEnum.CONCURRENCY_CONFLICT);
	}
	
	/**
	 * 유효성 검증 실패 처리 (@Valid)
	 */
	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<HttpResponseDto> handleValidationException(MethodArgumentNotValidException e) {
		Map<String, String> errors = new HashMap<>();
		e.getBindingResult().getAllErrors().forEach((error) -> {
			String fieldName = ((FieldError) error).getField();
			String errorMessage = error.getDefaultMessage();
			errors.put(fieldName, errorMessage);
		});
		
		log.warn("📝 유효성 검증 실패: {}", errors);
		return of(ResponseCodeEnum.BAD_REQUEST, errors);
	}
	
	/**
	 * 누락된 요청 파라미터 처리 (@RequestParam)
	 */
	@ExceptionHandler(MissingServletRequestParameterException.class)
	public ResponseEntity<HttpResponseDto> handleMissingParameterException(MissingServletRequestParameterException e) {
		log.warn("🚫 필수 파라미터 누락: {}", e.getParameterName());
		return of(ResponseCodeEnum.BAD_REQUEST, Map.of("error", "필수 파라미터가 누락되었습니다: " + e.getParameterName()));
	}
	
	/**
	 * 파라미터 타입 불일치 처리
	 */
	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	public ResponseEntity<HttpResponseDto> handleTypeMismatchException(MethodArgumentTypeMismatchException e) {
		log.warn("🔢 파라미터 타입 불일치: {} - {}", e.getName(), e.getValue());
		return of(ResponseExceptionEnum.INVALID_PARAMETER);
	}
	
	/**
	 * 정적 리소스 없음 예외 처리 (무시)
	 */
	@ExceptionHandler(NoResourceFoundException.class)
	public ResponseEntity<HttpResponseDto> handleNoResourceFound(NoResourceFoundException e) {
		// favicon.ico, auth/success 등 정적 리소스 요청 무시 (로그 없이)
		return ResponseEntity.notFound().build();
	}
	
	/**
	 * 일반적인 런타임 예외 처리
	 */
	@ExceptionHandler(RuntimeException.class)
	public ResponseEntity<HttpResponseDto> handleRuntimeException(RuntimeException e) {
		log.error("💥 예상치 못한 RuntimeException 발생", e);
		return of(ResponseCodeEnum.INTERNAL_SERVER_ERROR);
	}
	
	/**
	 * 최상위 예외 처리
	 */
	@ExceptionHandler(Exception.class)
	public ResponseEntity<HttpResponseDto> handleException(Exception e) {
		log.error("🔥 예상치 못한 Exception 발생", e);
		return of(ResponseCodeEnum.INTERNAL_SERVER_ERROR);
	}
}