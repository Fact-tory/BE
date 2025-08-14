package com.commonground.be.global.application.response;

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

	// 소셜 로그인
	SOCIAL_AUTH_URL_SUCCESS(HttpStatus.OK, "소셜 로그인 URL을 생성했습니다."),
	SOCIAL_LOGIN_SUCCESS(HttpStatus.OK, "소셜 로그인을 완료했습니다."),

	// 뉴스 관리
	NEWS_CREATE_SUCCESS(HttpStatus.CREATED, "뉴스를 생성했습니다."),
	NEWS_GET_SUCCESS(HttpStatus.OK, "뉴스를 조회했습니다."),
	NEWS_UPDATE_SUCCESS(HttpStatus.OK, "뉴스를 수정했습니다."),
	NEWS_DELETE_SUCCESS(HttpStatus.OK, "뉴스를 삭제했습니다."),
	NEWS_LIST_SUCCESS(HttpStatus.OK, "뉴스 목록을 조회했습니다."),
	NEWS_SEARCH_SUCCESS(HttpStatus.OK, "뉴스 검색을 완료했습니다."),
	NEWS_STATISTICS_SUCCESS(HttpStatus.OK, "뉴스 통계를 조회했습니다."),
	
	// 크롤링 관리
	CRAWLING_SOURCE_CREATE_SUCCESS(HttpStatus.OK, "크롤링 소스를 생성했습니다."),
	CRAWLING_SOURCE_LIST_SUCCESS(HttpStatus.OK, "크롤링 소스 목록을 조회했습니다."),
	CRAWLING_SOURCE_DETAIL_SUCCESS(HttpStatus.OK, "크롤링 소스 상세 정보를 조회했습니다."),
	CRAWLING_SOURCE_UPDATE_SUCCESS(HttpStatus.OK, "크롤링 소스를 수정했습니다."),
	CRAWLING_SOURCE_DELETE_SUCCESS(HttpStatus.OK, "크롤링 소스를 삭제했습니다."),
	CRAWLING_EXECUTE_SUCCESS(HttpStatus.OK, "크롤링을 시작했습니다."),
	CRAWLING_RUN_LIST_SUCCESS(HttpStatus.OK, "크롤링 실행 기록을 조회했습니다."),
	CRAWLING_URL_LIST_SUCCESS(HttpStatus.OK, "크롤링 URL 목록을 조회했습니다."),
	CRAWLING_QUICK_TEST_SUCCESS(HttpStatus.OK, "빠른 테스트 크롤링을 완료했습니다."),
	;


	private final HttpStatus httpStatus;
	private final String message;
}