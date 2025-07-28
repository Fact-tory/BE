package com.commonground.be.global.security.filters;


import static com.commonground.be.global.security.JwtProvider.AUTHORIZATION_HEADER;

import com.commonground.be.domain.user.utils.UserRole;
import com.commonground.be.global.response.HttpResponseDto;
import com.commonground.be.global.security.JwtProvider;
import com.commonground.be.global.security.UserDetailsServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

@Slf4j
@RequiredArgsConstructor
public class JwtAuthorizationFilter extends OncePerRequestFilter {

	private final JwtProvider jwtProvider;
	private final UserDetailsServiceImpl userDetailsService;
	private final RedisTemplate<String, String> redisTemplate;

	@Override
	protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res,
			FilterChain filterChain)
			throws ServletException, IOException {

		if (req.getRequestURI().equals("/v1/users/login") || req.getRequestURI()
				.startsWith("/v1/users/oauth/kakao")) {
			filterChain.doFilter(req, res);
			return;
		}

		String accessToken = jwtProvider.getAccessTokenFromHeader(req);
		String username = jwtProvider.getUsernameFromToken(accessToken);

		if (StringUtils.hasText(accessToken)) {
			if (jwtProvider.validateAccessToken(accessToken)) {
				log.info("액세스 토큰 검증 성공");
				updateToken(accessToken, username, res);
			} else if (jwtProvider.hasRefreshToken(username)) {
				String refreshToken = jwtProvider.substringToken(
						redisTemplate.opsForValue().get(username));
				updateToken(refreshToken, username, res);
				log.info("토큰 Refresh 성공");
			} else {
				jwtExceptionHandler(res, HttpStatus.UNAUTHORIZED, "유효하지 않은 토큰입니다.");
				return;
			}
		} else {
			log.error("토큰 없음");
		}
		filterChain.doFilter(req, res);
	}

	private void setAuthentication(String username) {
		SecurityContext context = SecurityContextHolder.createEmptyContext();
		context.setAuthentication(createAuthentication(username));
		SecurityContextHolder.setContext(context);
	}

	private Authentication createAuthentication(String username) {
		UserDetails userDetails = userDetailsService.loadUserByUsername(username);
		return new UsernamePasswordAuthenticationToken(userDetails, null,
				userDetails.getAuthorities());
	}

	private void jwtExceptionHandler(HttpServletResponse res, HttpStatus status, String msg) {
		int statusCode = status.value();
		res.setStatus(statusCode);
		res.setContentType("application/json");
		try {
			String json = new ObjectMapper().writeValueAsString(
					new HttpResponseDto(statusCode, msg));
			res.getWriter().write(json);
		} catch (Exception e) {
			log.error(e.getMessage());
		}
	}

	private void updateToken(String token, String username, HttpServletResponse res)
			throws IOException {
		UserRole role = jwtProvider.getRoleFromToken(token);
		String newAccessToken = jwtProvider.createAccessToken(username, role);

		res.setHeader(AUTHORIZATION_HEADER, newAccessToken);
		setAuthentication(jwtProvider.getUsernameFromToken(newAccessToken));

		String jsonResponse = new ObjectMapper().writeValueAsString(
				new HttpResponseDto(HttpStatus.OK.value(), "토큰 갱신 성공", newAccessToken)
		);
	}
}
