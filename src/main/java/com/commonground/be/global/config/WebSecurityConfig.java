package com.commonground.be.global.config;


import com.commonground.be.domain.session.service.SessionService;
import com.commonground.be.global.security.admin.AdminTokenValidator;
import com.commonground.be.global.security.details.CustomUserDetailsService;
import com.commonground.be.global.security.JwtProvider;
import com.commonground.be.global.security.TokenManager;
import com.commonground.be.global.security.filters.JwtAuthorizationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.security.servlet.PathRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class WebSecurityConfig {

	private final TokenManager tokenManager;
	private final JwtProvider jwtProvider;
	private final SessionService sessionService;
	private final AdminTokenValidator adminTokenValidator;
	private final CustomUserDetailsService userDetailsService;

	@Bean
	public JwtAuthorizationFilter jwtAuthorizationFilter() {
		return new JwtAuthorizationFilter(jwtProvider, sessionService, tokenManager, adminTokenValidator, userDetailsService);
	}

	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		// CSRF 설정
		http.csrf(AbstractHttpConfigurer::disable);

		// 기본 설정인 Session 방식은 사용하지 않고 JWT 방식을 사용하기 위한 설정
		http.sessionManagement((sessionManagement) ->
				sessionManagement.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
		);

		http.authorizeHttpRequests((authorizeHttpRequests) -> authorizeHttpRequests
				.requestMatchers(PathRequest.toStaticResources().atCommonLocations()).permitAll()
				
				// OAuth2 소셜 로그인 관련 경로 (인증 불필요)
				.requestMatchers("/api/v1/auth/social/**").permitAll()
				.requestMatchers("/api/v1/auth/reissue").permitAll()
				
				// 헬스체크 및 공개 경로
				.requestMatchers("/health", "/", "/public/**").permitAll()
				
				// 레거시 OAuth 경로 지원 (하위 호환성)  
				.requestMatchers("/v1/users/oauth/**").permitAll()
				
				// 관리자 전용 경로 (토큰 검증은 필터에서 처리)
				.requestMatchers("/api/v1/admin/**").permitAll()
				
				// 사용자 API (JWT 토큰 필요, 필터에서 검증)
				.requestMatchers("/api/v1/users/**").permitAll()
				
				// 기타 모든 요청은 인증 필요
				.anyRequest().authenticated()
		);

		// JWT 인가 필터만 추가 (OAuth2이므로 인증 필터는 불필요)
		http.addFilterBefore(jwtAuthorizationFilter(), UsernamePasswordAuthenticationFilter.class);

		return http.build();
	}
}
