package com.commonground.be.global.response;

import org.springframework.http.ResponseEntity;

public class ResponseUtils {

	public static ResponseEntity<HttpResponseDto> of(ResponseExceptionEnum responseExceptionEnum) {
		return ResponseEntity.status(responseExceptionEnum.getHttpStatus())
				.body(new HttpResponseDto(responseExceptionEnum.getHttpStatus().value(),
						responseExceptionEnum.getMessage()));
	}

	public static ResponseEntity<HttpResponseDto> of(ResponseCodeEnum responseCodeEnum) {
		return ResponseEntity.status(responseCodeEnum.getHttpStatus())
				.body(new HttpResponseDto(responseCodeEnum.getHttpStatus().value(),
						responseCodeEnum.getMessage()));
	}

	public static ResponseEntity<HttpResponseDto> of(ResponseCodeEnum responseCodeEnum,
			Object data) {
		return ResponseEntity.status(responseCodeEnum.getHttpStatus())
				.body(new HttpResponseDto(responseCodeEnum.getHttpStatus().value(),
						responseCodeEnum.getMessage(), data));
	}

	public static ResponseEntity<HttpResponseDto> of(ResponseCodeEnum responseCodeEnum,
			Object data, String redirectUrl) {
		return ResponseEntity.status(responseCodeEnum.getHttpStatus())
				.body(new HttpResponseDto(responseCodeEnum.getHttpStatus().value(),
						responseCodeEnum.getMessage(), data, redirectUrl));
	}
}
