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

	// 공통 응답 코드
	SUCCESS(HttpStatus.OK, "요청이 성공적으로 처리되었습니다."),
	BAD_REQUEST(HttpStatus.BAD_REQUEST, "잘못된 요청입니다."),
	UNAUTHORIZED_ACCESS(HttpStatus.UNAUTHORIZED, "인증이 필요합니다."),
	FORBIDDEN_ACCESS(HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),
	NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 리소스를 찾을 수 없습니다."),
	INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류가 발생했습니다."),
	SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "서비스를 사용할 수 없습니다."),

	// 분석 관련
	ANALYSIS_REQUEST_SUCCESS(HttpStatus.OK, "분석 요청이 성공적으로 처리되었습니다."),
	ANALYSIS_GET_SUCCESS(HttpStatus.OK, "분석 결과를 조회했습니다."),
	ANALYSIS_LIST_SUCCESS(HttpStatus.OK, "분석 목록을 조회했습니다."),
	ANALYSIS_DELETE_SUCCESS(HttpStatus.OK, "분석을 삭제했습니다."),
	ANALYSIS_LIMIT_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "일일 분석 요청 한도를 초과했습니다."),
	ANALYSIS_NOT_FOUND(HttpStatus.NOT_FOUND, "분석 결과를 찾을 수 없습니다."),
	ANALYSIS_REQUEST_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "분석 요청 처리 중 오류가 발생했습니다."),

	// 검색 관련
	SEARCH_SUCCESS(HttpStatus.OK, "검색이 완료되었습니다."),
	SEARCH_AUTOCOMPLETE_SUCCESS(HttpStatus.OK, "자동완성 조회가 완료되었습니다."),
	SEARCH_STATISTICS_SUCCESS(HttpStatus.OK, "검색 통계를 조회했습니다."),
	SEARCH_HISTORY_SUCCESS(HttpStatus.OK, "검색 히스토리를 조회했습니다."),

	// 대시보드 관련
	DASHBOARD_SUCCESS(HttpStatus.OK, "대시보드 정보를 조회했습니다."),
	DASHBOARD_USER_SUCCESS(HttpStatus.OK, "사용자 대시보드 정보를 조회했습니다."),
	;


	private final HttpStatus httpStatus;
	private final String message;
}