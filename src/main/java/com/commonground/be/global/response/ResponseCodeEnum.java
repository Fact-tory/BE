package com.commonground.be.global.response;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ResponseCodeEnum {
	// 유저
	SUCCESS_LOGIN(HttpStatus.OK, "로그인을 완료했습니다."),
	USER_SIGNUP_SUCCESS(HttpStatus.OK, "님의 회원가입을 완료 했습니다."),
	SUCCESS_LOGOUT(HttpStatus.OK, "로그아웃을 완료했습니다."),
	USER_SUCCESS_GET(HttpStatus.OK, "유저 조회를 완료 했습니다."),
	USER_DELETE_SUCCESS(HttpStatus.OK, "회원 탈퇴를 완료했습니다."),
	USER_RESIGN_SUCCESS(HttpStatus.OK, "회원 복구를 완료했습니다."),
	USER_UPDATE_SUCCESS(HttpStatus.OK, "유저 정보 수정을 완료했습니다."),
	USER_SUCCESS_SIGNUP(HttpStatus.OK, "님의 회원가입을 완료 했습니다."),
	REISSUE_ACCESS_TOKEN(HttpStatus.OK, "액세스 토큰 재발급을 완료했습니다."),
	USER_SUCCESS_LIST(HttpStatus.OK, "유저 리스트 입니다."),
	SUCCESS_TEMPORARY_PASSWORD(HttpStatus.OK, "새로운 패스워드를 생성했습니다."),
	SUCCESS_CHANGE_PASSWORD(HttpStatus.OK, "비밀번호 변경을 완료 했습니다."),
	;


	private final HttpStatus httpStatus;
	private final String message;
}