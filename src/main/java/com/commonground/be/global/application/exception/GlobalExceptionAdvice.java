package com.commonground.be.global.application.exception;


import static com.commonground.be.global.application.response.ResponseUtils.of;

import com.commonground.be.global.application.response.HttpResponseDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@Slf4j
@ControllerAdvice
public class GlobalExceptionAdvice {

	@ExceptionHandler(CommonException.class)
	public ResponseEntity<HttpResponseDto> handleCommonException(CommonException e) {
		log.error("에러 메세지: ", e);
		return of(e.getResponseExceptionEnum());
	}
}