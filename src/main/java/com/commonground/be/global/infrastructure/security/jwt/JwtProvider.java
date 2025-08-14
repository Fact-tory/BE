package com.commonground.be.global.infrastructure.security.jwt;


import com.commonground.be.domain.user.utils.UserRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.security.Key;
import java.util.Base64;
import java.util.Date;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtProvider {

	public static final String BEARER_PREFIX = "Bearer ";
	public static final String AUTHORIZATION_HEADER = "Authorization";
	public static final String ACCESS_TOKEN_COOKIE_NAME = "AccessToken";
	public static final String REFRESH_TOKEN_COOKIE_NAME = "refresh-token";
	public static final String AUTHORIZATION_KEY = "auth";

	private final RedisTemplate<String, String> redisTemplate;
	private final TokenManager tokenManager;

	@Value("${jwt-secret-key}")
	private String secretKey;

	// yml에서 설정값 주입 (초 단위)
	@Value("${jwt.access-token.expiration}")
	private Long accessTokenExpiration;

	@Value("${jwt.refresh-token.expiration}")
	private Long refreshTokenExpiration;

	private Key key;

	/**
	 * 쿠키 무효화
	 */
	public static void clearCookie(HttpServletResponse response, String cookieName) {
		Cookie cookie = new Cookie(cookieName, null);
		cookie.setPath("/");
		cookie.setHttpOnly(true);
		cookie.setMaxAge(0);
		response.addCookie(cookie);
	}

	/**
	 * Refresh Token을 HttpOnly 쿠키로 설정
	 */
	public void setRefreshTokenCookie(HttpServletResponse response, String refreshToken) {
		// Bearer 접두사 제거
		String tokenValue = refreshToken.startsWith(BEARER_PREFIX)
				? refreshToken.substring(BEARER_PREFIX.length())
				: refreshToken;

		Cookie refreshCookie = new Cookie(REFRESH_TOKEN_COOKIE_NAME, tokenValue);
		refreshCookie.setHttpOnly(true); // JavaScript 접근 차단
		refreshCookie.setSecure(true); // HTTPS에서만 전송
		refreshCookie.setPath("/"); // 모든 경로에서 사용 가능
		refreshCookie.setMaxAge(refreshTokenExpiration.intValue()); // 만료시간 설정

		response.addCookie(refreshCookie);
		log.info("Refresh Token 쿠키 설정 완료");
	}

	/**
	 * 요청에서 Refresh Token 쿠키 추출
	 */
	public String getRefreshTokenFromCookie(HttpServletRequest request) {
		if (request.getCookies() != null) {
			for (Cookie cookie : request.getCookies()) {
				if (REFRESH_TOKEN_COOKIE_NAME.equals(cookie.getName())) {
					return cookie.getValue();
				}
			}
		}
		return null;
	}

	@PostConstruct
	public void init() {
		key = Keys.hmacShaKeyFor(Base64.getDecoder().decode(secretKey));
	}

	/**
	 * Access 토큰 생성
	 */
	public String createAccessToken(String username, UserRole role) {
		Date date = new Date();

		return BEARER_PREFIX + Jwts.builder()
				.setSubject(username)
				.claim(AUTHORIZATION_KEY, role)
				.setExpiration(new Date(date.getTime() + (accessTokenExpiration * 1000)))
				.setIssuedAt(date) // 발급일
				.signWith(key, SignatureAlgorithm.HS256)
				.compact();
	}

	/**
	 * 세션 ID가 포함된 Access 토큰 생성
	 */
	public String createAccessTokenWithSession(String username, UserRole role, String sessionId) {
		Date date = new Date();

		return BEARER_PREFIX + Jwts.builder()
				.setSubject(username)
				.claim(AUTHORIZATION_KEY, role)
				.claim("sessionId", sessionId)
				.claim("tokenVersion", System.currentTimeMillis()) // 토큰 버전
				.setExpiration(new Date(date.getTime() + (accessTokenExpiration * 1000)))
				.setIssuedAt(date)
				.signWith(key, SignatureAlgorithm.HS256)
				.compact();
	}

	/**
	 * Refresh 토큰 생성
	 */
	public String createRefreshToken(String username, UserRole role) {
		Date date = new Date();

		return BEARER_PREFIX + Jwts.builder()
				.setSubject(username)
				.claim(AUTHORIZATION_KEY, role)
				.setExpiration(new Date(date.getTime() + (refreshTokenExpiration * 1000)))
				.setIssuedAt(date) // 발급일
				.signWith(key, SignatureAlgorithm.HS256)
				.compact();
	}

	/**
	 * 세션 ID가 포함된 Refresh 토큰 생성
	 */
	public String createRefreshTokenWithSession(String username, UserRole role, String sessionId) {
		Date date = new Date();
		String tokenId = java.util.UUID.randomUUID().toString();

		return BEARER_PREFIX + Jwts.builder()
				.setSubject(username)
				.claim(AUTHORIZATION_KEY, role)
				.claim("sessionId", sessionId)
				.claim("tokenId", tokenId) // 토큰 고유 ID (블랙리스트용)
				.claim("tokenVersion", System.currentTimeMillis())
				.setExpiration(new Date(date.getTime() + (refreshTokenExpiration * 1000)))
				.setIssuedAt(date)
				.signWith(key, SignatureAlgorithm.HS256)
				.compact();
	}

	/**
	 * 요청 바디에서 액세스 토큰 추출
	 */
	public String getAccessTokenFromHeader(HttpServletRequest request) {
		String bearerToken = request.getHeader(AUTHORIZATION_HEADER);

		if (StringUtils.hasText(bearerToken) && bearerToken.startsWith(BEARER_PREFIX)) {
			return bearerToken.substring(BEARER_PREFIX.length());
		} else {
			return bearerToken;
		}
	}

	/**
	 * JWT 토큰 검증 (Access Token, Refresh Token 공통)
	 */
	public boolean validateAccessToken(String token) {
		try {
			Claims claims = Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(token)
					.getBody();

			// 블랙리스트 검증 (tokenId가 있는 경우)
			String tokenId = claims.get("tokenId", String.class);
			if (tokenId != null && tokenManager.isTokenBlacklisted(tokenId)) {
				log.warn("블랙리스트에 등록된 토큰 - tokenId: {}", tokenId);
				return false;
			}

			return true;
		} catch (SecurityException | MalformedJwtException e) {
			log.error("유효하지 않는 JWT 서명 입니다.");
		} catch (ExpiredJwtException e) {
			log.error("만료된 JWT token 입니다.");
		} catch (UnsupportedJwtException e) {
			log.error("지원되지 않는 JWT 토큰 입니다.");
		} catch (IllegalArgumentException e) {
			log.error("잘못된 JWT 토큰 입니다.");
		}
		return false;
	}

	/**
	 * Refresh 토큰 검증
	 */
	public boolean hasRefreshToken(String username) {
		// redis에서 토큰에 맞는 키가 존재하면 true
		return Boolean.TRUE.equals(redisTemplate.hasKey(username));
	}

	/**
	 * 토큰에서 username 가져오기
	 */
	public String getUsernameFromToken(String token) {
		if (StringUtils.hasText(token) && token.startsWith(BEARER_PREFIX)) {
			token = substringToken(token);
		}
		try {
			Claims claims = Jwts.parserBuilder()
					.setSigningKey(key)
					.build()
					.parseClaimsJws(token)
					.getBody();
			return claims.getSubject();
		} catch (ExpiredJwtException e) {
			// 토큰이 만료된 경우에도 가져옴
			return e.getClaims().getSubject();
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	/**
	 * 토큰에서 role 가져오기
	 */
	public UserRole getRoleFromToken(String token) {
		Claims claims = Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(token)
				.getBody();
		String role = claims.get(AUTHORIZATION_KEY, String.class);
		return UserRole.valueOf(role);
	}

	public String substringToken(String tokenValue) {
		if (StringUtils.hasText(tokenValue) && tokenValue.startsWith(BEARER_PREFIX)) {
			return tokenValue.substring(BEARER_PREFIX.length());
		} else if (StringUtils.hasText(tokenValue)) {
			return tokenValue;
		}
		throw new NullPointerException("토큰이 없습니다.");
	}

	/**
	 * 토큰에서 특정 클레임 값 추출
	 */
	public String getClaimFromToken(String token, String claimName) {
		if (StringUtils.hasText(token) && token.startsWith(BEARER_PREFIX)) {
			token = substringToken(token);
		}
		try {
			Claims claims = Jwts.parserBuilder()
					.setSigningKey(key)
					.build()
					.parseClaimsJws(token)
					.getBody();
			return claims.get(claimName, String.class);
		} catch (Exception e) {
			return null;
		}
	}
}
