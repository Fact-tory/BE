package com.commonground.be.global.application.response;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class HttpResponseDto {

	private Integer statusCode;
	private String message;
	private Object data;
	private String redirectUrl;

	public HttpResponseDto(Integer statusCode, String message) {
		this.statusCode = statusCode;
		this.message = message;
	}

	public HttpResponseDto(Integer statusCode, String message, Object data) {
		this.statusCode = statusCode;
		this.message = message;
		this.data = data;
	}

	public HttpResponseDto(Integer statusCode, String message, Object data, String redirectUrl) {
		this.statusCode = statusCode;
		this.message = message;
		this.data = data;
		this.redirectUrl = redirectUrl;
	}

	public HttpResponseDto(
			ResponseExceptionEnum responseExceptionEnum) {
		this.statusCode = responseExceptionEnum.getHttpStatus().value();
		this.message = responseExceptionEnum.getMessage();
	}
}
