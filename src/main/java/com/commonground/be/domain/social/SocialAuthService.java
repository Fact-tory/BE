package com.commonground.be.domain.social;


import com.fasterxml.jackson.core.JsonProcessingException;
import jakarta.servlet.http.HttpServletResponse;

public interface SocialAuthService {

	/**
	 * 소셜 로그인 처리
	 *
	 * @param code 인가 코드
	 * @param res  HTTP 응답
	 * @return 액세스 토큰
	 */
	String socialLogin(String code, HttpServletResponse res) throws JsonProcessingException;

	/**
	 * 소셜 로그인 URL 생성
	 *
	 * @param redirectUri 리다이렉트 URI
	 * @return 로그인 URL
	 */
	String getAuthUrl(String redirectUri);

	/**
	 * 프로바이더 이름 반환
	 *
	 * @return 프로바이더 이름 (kakao, google)
	 */
	String getProviderName();
}