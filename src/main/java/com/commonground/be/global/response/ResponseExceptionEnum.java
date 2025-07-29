package com.commonground.be.global.response;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ResponseExceptionEnum {
	// 유저
	REFRESH_TOKEN_UNAVAILABLE(HttpStatus.BAD_REQUEST, "유효하지 않은 리프레쉬토큰 입니다."),
	USER_FAIL_SIGNUP(HttpStatus.BAD_REQUEST, "회원가입에 실패했습니다."),
	USER_ALREADY_EXIST(HttpStatus.BAD_REQUEST, "중복된 아이디 입니다."),
	USER_NOT_FOUND(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다"),
	USER_DELETED(HttpStatus.UNAUTHORIZED, "탈퇴한 사용자입니다"),
	USER_NOT_DELETED(HttpStatus.BAD_REQUEST, "탈퇴되지 않은 사용자입니다."),
	NOT_FOUND_AUTHENTICATION_INFO(HttpStatus.BAD_REQUEST, "사용자 정보가 일치하지 않습니다. 다시 시도해 주세요 :)"),
	INVALID_REFRESHTOKEN(HttpStatus.NOT_FOUND, "유효하지 않은 리프레쉬 토큰입니다."),
	FAIL_TO_CHANGE_ROLE(HttpStatus.BAD_REQUEST, "Role 변경을 실패했습니다."),
	UNAUTHORIZED_ACCESS(HttpStatus.UNAUTHORIZED, "인증되지 않은 사용자입니다."),
	USER_ERROR(HttpStatus.BAD_REQUEST, "유저 오류 발생"),
	USER_NOT_MANAGER(HttpStatus.BAD_REQUEST, "권한이 없습니다."),
	
	// 소셜 로그인
	INVALID_SOCIAL_PROVIDER(HttpStatus.BAD_REQUEST, "지원하지 않는 소셜 로그인 프로바이더입니다."),
	SOCIAL_TOKEN_REQUEST_FAILED(HttpStatus.BAD_REQUEST, "소셜 토큰 요청에 실패했습니다."),
	SOCIAL_USER_INFO_REQUEST_FAILED(HttpStatus.BAD_REQUEST, "소셜 사용자 정보 요청에 실패했습니다."),
	INVALID_SOCIAL_TOKEN_RESPONSE(HttpStatus.BAD_REQUEST, "소셜 토큰 응답이 올바르지 않습니다."),
	INVALID_SOCIAL_USER_INFO_RESPONSE(HttpStatus.BAD_REQUEST, "소셜 사용자 정보 응답이 올바르지 않습니다."),
	;

	private final HttpStatus httpStatus;
	private final String message;
}
