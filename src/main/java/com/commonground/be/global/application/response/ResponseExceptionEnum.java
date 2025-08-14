package com.commonground.be.global.application.response;

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

	// 크롤링
	CRAWLING_SOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "크롤링 소스를 찾을 수 없습니다."),
	CRAWLING_SOURCE_CREATION_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "크롤링 소스 생성에 실패했습니다."),
	CRAWLING_SOURCE_INACTIVE(HttpStatus.BAD_REQUEST, "비활성화된 크롤링 소스입니다."),
	CRAWLING_EXECUTION_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "크롤링 실행에 실패했습니다."),
	CRAWLING_TIMEOUT(HttpStatus.REQUEST_TIMEOUT, "크롤링이 시간 초과되었습니다."),
	CRAWLING_QUICK_TEST_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "빠른 테스트 크롤링에 실패했습니다."),
	
	// 뉴스 관리
	NEWS_NOT_FOUND(HttpStatus.NOT_FOUND, "뉴스를 찾을 수 없습니다."),
	DUPLICATE_NEWS(HttpStatus.CONFLICT, "이미 존재하는 뉴스입니다."),
	INVALID_NEWS_DATA(HttpStatus.BAD_REQUEST, "유효하지 않은 뉴스 데이터입니다."),
	NEWS_COLLECTION_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "뉴스 수집에 실패했습니다."),
	DATA_PROCESSING_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "뉴스 데이터 처리에 실패했습니다."),
	NEWS_SEARCH_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "뉴스 검색에 실패했습니다."),
	NEWS_INDEXING_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "뉴스 인덱싱에 실패했습니다."),
	
	// 네이버 API/크롤링 전용
	NAVER_API_FAILED(HttpStatus.SERVICE_UNAVAILABLE, "네이버 API 호출에 실패했습니다."),
	CRAWLING_INITIALIZATION_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "크롤링 초기화에 실패했습니다."),
	BROWSER_LAUNCH_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "브라우저 실행에 실패했습니다."),
	PAGE_NAVIGATION_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "페이지 이동에 실패했습니다."),
	ARTICLE_EXTRACTION_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "기사 추출에 실패했습니다."),
	AUTHOR_EXTRACTION_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "기자명 추출에 실패했습니다."),
	CONTENT_EXTRACTION_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "본문 추출에 실패했습니다."),
	INVALID_ARTICLE_URL(HttpStatus.BAD_REQUEST, "유효하지 않은 기사 URL입니다."),
	URL_FILTERING_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "URL 필터링에 실패했습니다."),
	LINK_DISCOVERY_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "링크 발견에 실패했습니다."),
	CRAWLING_SESSION_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "크롤링 세션 처리에 실패했습니다."),
	PROGRESS_UPDATE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "진행상황 업데이트에 실패했습니다."),
	CRAWLING_TIMEOUT_EXCEEDED(HttpStatus.REQUEST_TIMEOUT, "크롤링 시간이 초과되었습니다."),
	NAVER_MEDIA_PAGE_LOAD_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "네이버 언론사 페이지 로드에 실패했습니다."),
	NAVER_ARTICLE_PARSING_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "네이버 기사 파싱에 실패했습니다."),
	NAVER_SCROLLING_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "네이버 페이지 스크롤에 실패했습니다."),
	NAVER_CATEGORY_MAPPING_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "네이버 카테고리 매핑에 실패했습니다."),
	
	;

	private final HttpStatus httpStatus;
	private final String message;
}
