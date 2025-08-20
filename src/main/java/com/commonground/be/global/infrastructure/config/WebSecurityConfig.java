package com.commonground.be.global.infrastructure.config;


import org.springframework.boot.autoconfigure.security.servlet.PathRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import com.commonground.be.global.infrastructure.security.admin.AdminTokenValidator;
import com.commonground.be.global.infrastructure.security.filter.JwtAuthorizationFilter;
import com.commonground.be.global.infrastructure.security.jwt.JwtProvider;
import com.commonground.be.global.infrastructure.security.jwt.TokenManager;
import com.commonground.be.global.infrastructure.security.oauth2.OAuth2FailureHandler;
import com.commonground.be.global.infrastructure.security.oauth2.OAuth2SuccessHandler;
import com.commonground.be.global.infrastructure.security.service.CustomOAuth2UserService;
import com.commonground.be.global.infrastructure.security.service.CustomUserDetailsService;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class WebSecurityConfig {

	private final TokenManager tokenManager;
	private final JwtProvider jwtProvider;
	private final AdminTokenValidator adminTokenValidator;
	private final CustomUserDetailsService userDetailsService;
	private final CustomOAuth2UserService customOAuth2UserService;
	private final OAuth2SuccessHandler oauth2SuccessHandler;
	private final OAuth2FailureHandler oauth2FailureHandler;

	@Bean
	public JwtAuthorizationFilter jwtAuthorizationFilter() {
		return new JwtAuthorizationFilter(jwtProvider, tokenManager,
				adminTokenValidator, userDetailsService);
	}

	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		// CSRF 설정
		http.csrf(AbstractHttpConfigurer::disable);
		http.httpBasic(AbstractHttpConfigurer::disable);
		http.formLogin(AbstractHttpConfigurer::disable);

		// 기본 설정인 Session 방식은 사용하지 않고 JWT 방식을 사용하기 위한 설정
		http.sessionManagement((sessionManagement) ->
				sessionManagement.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
		);

		// OAuth2 로그인 설정
		http.oauth2Login(oauth2 -> oauth2
				.successHandler(oauth2SuccessHandler)
				.failureHandler(oauth2FailureHandler)
				.userInfoEndpoint(userInfo -> userInfo
						.userService(this.customOAuth2UserService))
				.permitAll()
		);

		http.authorizeHttpRequests((authorizeHttpRequests) -> authorizeHttpRequests
				.requestMatchers(PathRequest.toStaticResources().atCommonLocations())
				.permitAll() // Public 엔드포인트

				// OAuth2 로그인 경로 (환경변수 리다이렉트 URI와 매칭)
				.requestMatchers("/oauth2/**", "/login/oauth2/**").permitAll() // Google: /oauth/google
				.requestMatchers("/oauth/**").permitAll() // Google 리다이렉트 URI용
				.requestMatchers("/api/v1/auth/social/**").permitAll() // Kakao: /api/v1/auth/social/kakao/callback

				// 공개 인증 API (OAuth2 URL 제공, 토큰 재발급)
				.requestMatchers("/api/v1/auth/oauth2/login", "/api/v1/auth/reissue").permitAll()
				.requestMatchers("/api/v1/auth/oauth2/**").permitAll()

				// 인증이 필요한 인증 API (현재 사용자 정보, 토큰 검증, 로그아웃, 탈퇴)
				.requestMatchers("/api/v1/auth/me", "/api/v1/auth/validate", "/api/v1/auth/logout", "/api/v1/auth/withdraw").authenticated()

				// 공개 뉴스 API
				.requestMatchers("/api/v1/news/recent", "/api/v1/news/trending").permitAll()
				.requestMatchers("/api/v1/news/categories/**").permitAll()
				.requestMatchers("/api/v1/news/search").permitAll()
				.requestMatchers("/api/v1/news/{id}").permitAll()
				.requestMatchers("/api/v1/news/statistics").permitAll()

				// WebSocket
				.requestMatchers("/ws/**").permitAll()

				// Swagger/Actuator
				.requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
				.requestMatchers("/actuator/**").hasRole("ADMIN")

				// Admin 전용
				.requestMatchers("/api/v1/news/crawl/**").hasRole("ADMIN")
				.requestMatchers(HttpMethod.POST, "/api/v1/news").hasRole("ADMIN")
				.requestMatchers(HttpMethod.PUT, "/api/v1/news/**").hasRole("ADMIN")
				.requestMatchers(HttpMethod.DELETE, "/api/v1/news/**").hasRole("ADMIN")

				// 헬스체크 및 공개 경로
				.requestMatchers("/health", "/", "/public/**").permitAll()

				// 관리자 전용 경로 (토큰 검증은 필터에서 처리)
				.requestMatchers("/api/v1/admin/**").permitAll()

				// 사용자 API (JWT 토큰 필요, 필터에서 검증)
				.requestMatchers("/api/v1/users/**").permitAll()

				// 기타 모든 요청은 인증 필요
				.anyRequest().authenticated()
		);

		// JWT 인가 필터 추가 (OAuth2 로그인 후 JWT 토큰 기반 인증)
		http.addFilterBefore(jwtAuthorizationFilter(), UsernamePasswordAuthenticationFilter.class);

		// 6. 예외 처리 설정 (가장 중요)
		// 인증되지 않은 사용자가 보호된 리소스에 접근 시, 리다이렉트 대신 401 Unauthorized 에러를 반환하도록 설정
		http.exceptionHandling(exceptions ->
				exceptions.authenticationEntryPoint((request, response, authException) ->
						response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized"))
		);
		return http.build();
	}
}
