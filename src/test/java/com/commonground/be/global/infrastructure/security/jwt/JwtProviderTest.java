package com.commonground.be.global.infrastructure.security.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.commonground.be.domain.user.utils.UserRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.security.Key;
import java.util.Base64;
import java.util.Date;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.context.ActiveProfiles;

/**
 * JwtProvider 단위 테스트 클래스
 * 
 * 이 테스트는 JwtProvider의 JWT 토큰 생성, 검증, 파싱 등의 핵심 기능을 검증합니다.
 * Spring Security와 연동되는 JWT 기반 인증 시스템의 핵심 로직을 테스트하며,
 * 토큰의 생성부터 만료, 검증까지의 전체 라이프사이클을 다룹니다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("JwtProvider 단위 테스트")
@ActiveProfiles("test")
class JwtProviderTest {

    @InjectMocks
    private JwtProvider jwtProvider;

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private TokenManager tokenManager;

    // 테스트용 JWT 설정값들
    private static final String TEST_SECRET_KEY = "dGVzdC1zZWNyZXQta2V5LWZvci1qd3QtdGVzdGluZy0xMjM0NTY3ODkw"; // Base64 인코딩된 시크릿 키
    private static final Long ACCESS_TOKEN_EXPIRATION = 1800L; // 30분 (초 단위)
    private static final Long REFRESH_TOKEN_EXPIRATION = 86400L; // 24시간 (초 단위)

    private Key testKey;
    private String testUsername;
    private UserRole testRole;
    private String testSessionId;

    /**
     * 각 테스트 실행 전 JwtProvider 설정을 초기화합니다.
     * 실제 운영 환경과 동일한 조건으로 테스트하기 위해 리플렉션을 통해 설정값을 주입합니다.
     */
    @BeforeEach
    void setUp() {
        // 테스트용 데이터 설정
        testUsername = "testuser";
        testRole = UserRole.USER;
        testSessionId = "session-123";

        // 리플렉션을 통해 설정값 주입
        ReflectionTestUtils.setField(jwtProvider, "secretKey", TEST_SECRET_KEY);
        ReflectionTestUtils.setField(jwtProvider, "accessTokenExpiration", ACCESS_TOKEN_EXPIRATION);
        ReflectionTestUtils.setField(jwtProvider, "refreshTokenExpiration", REFRESH_TOKEN_EXPIRATION);

        // JWT 키 초기화
        testKey = Keys.hmacShaKeyFor(Base64.getDecoder().decode(TEST_SECRET_KEY));
        ReflectionTestUtils.setField(jwtProvider, "key", testKey);

        // JwtProvider 초기화
        jwtProvider.init();
    }

    @Nested
    @DisplayName("Access Token 생성 테스트")
    class AccessTokenCreationTest {

        @Test
        @DisplayName("기본 Access Token 생성 성공")
        void createAccessToken_WithValidUserInfo_ShouldReturnValidToken() {
            // When: Access Token을 생성하면
            String accessToken = jwtProvider.createAccessToken(testUsername, testRole);

            // Then: 올바른 형식의 토큰이 생성되어야 함
            assertThat(accessToken).isNotNull();
            assertThat(accessToken).startsWith(JwtProvider.BEARER_PREFIX);
            
            // 토큰에서 정보 추출 검증
            String tokenWithoutPrefix = accessToken.substring(JwtProvider.BEARER_PREFIX.length());
            assertThat(jwtProvider.validateAccessToken(tokenWithoutPrefix)).isTrue();
            assertThat(jwtProvider.getUsernameFromToken(accessToken)).isEqualTo(testUsername);
            assertThat(jwtProvider.getRoleFromToken(tokenWithoutPrefix)).isEqualTo(testRole);
        }

        @Test
        @DisplayName("세션 ID가 포함된 Access Token 생성 성공")
        void createAccessTokenWithSession_WithValidInfo_ShouldReturnTokenWithSession() {
            // When: 세션 ID가 포함된 Access Token을 생성하면
            String accessToken = jwtProvider.createAccessTokenWithSession(testUsername, testRole, testSessionId);

            // Then: 세션 ID가 포함된 토큰이 생성되어야 함
            assertThat(accessToken).isNotNull();
            assertThat(accessToken).startsWith(JwtProvider.BEARER_PREFIX);
            
            String sessionIdFromToken = jwtProvider.getClaimFromToken(accessToken, "sessionId");
            assertThat(sessionIdFromToken).isEqualTo(testSessionId);
            
            // 토큰 버전도 포함되어야 함
            String tokenVersion = jwtProvider.getClaimFromToken(accessToken, "tokenVersion");
            assertThat(tokenVersion).isNotNull();
        }

        @Test
        @DisplayName("Access Token 만료시간 검증")
        void createAccessToken_ShouldHaveCorrectExpiration() {
            // When: Access Token을 생성하면
            String accessToken = jwtProvider.createAccessToken(testUsername, testRole);
            String tokenWithoutPrefix = accessToken.substring(JwtProvider.BEARER_PREFIX.length());

            // Then: 토큰의 만료시간이 올바르게 설정되어야 함
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(testKey)
                    .build()
                    .parseClaimsJws(tokenWithoutPrefix)
                    .getBody();

            Date issuedAt = claims.getIssuedAt();
            Date expiration = claims.getExpiration();
            
            // 만료시간이 발급시간 + 설정된 만료시간과 일치하는지 확인 (±1초 허용)
            long expectedExpirationTime = issuedAt.getTime() + (ACCESS_TOKEN_EXPIRATION * 1000);
            long actualExpirationTime = expiration.getTime();
            
            assertThat(Math.abs(actualExpirationTime - expectedExpirationTime)).isLessThan(1000);
        }
    }

    @Nested
    @DisplayName("Refresh Token 생성 테스트")
    class RefreshTokenCreationTest {

        @Test
        @DisplayName("기본 Refresh Token 생성 성공")
        void createRefreshToken_WithValidUserInfo_ShouldReturnValidToken() {
            // When: Refresh Token을 생성하면
            String refreshToken = jwtProvider.createRefreshToken(testUsername, testRole);

            // Then: 올바른 형식의 토큰이 생성되어야 함
            assertThat(refreshToken).isNotNull();
            assertThat(refreshToken).startsWith(JwtProvider.BEARER_PREFIX);
            
            // 토큰 검증
            String tokenWithoutPrefix = refreshToken.substring(JwtProvider.BEARER_PREFIX.length());
            assertThat(jwtProvider.validateAccessToken(tokenWithoutPrefix)).isTrue();
            assertThat(jwtProvider.getUsernameFromToken(refreshToken)).isEqualTo(testUsername);
            assertThat(jwtProvider.getRoleFromToken(tokenWithoutPrefix)).isEqualTo(testRole);
        }

        @Test
        @DisplayName("세션 ID가 포함된 Refresh Token 생성 성공")
        void createRefreshTokenWithSession_WithValidInfo_ShouldReturnTokenWithSessionAndId() {
            // When: 세션 ID가 포함된 Refresh Token을 생성하면
            String refreshToken = jwtProvider.createRefreshTokenWithSession(testUsername, testRole, testSessionId);

            // Then: 세션 ID와 토큰 ID가 포함된 토큰이 생성되어야 함
            assertThat(refreshToken).isNotNull();
            assertThat(refreshToken).startsWith(JwtProvider.BEARER_PREFIX);
            
            String sessionIdFromToken = jwtProvider.getClaimFromToken(refreshToken, "sessionId");
            String tokenIdFromToken = jwtProvider.getClaimFromToken(refreshToken, "tokenId");
            String tokenVersionFromToken = jwtProvider.getClaimFromToken(refreshToken, "tokenVersion");
            
            assertThat(sessionIdFromToken).isEqualTo(testSessionId);
            assertThat(tokenIdFromToken).isNotNull();
            assertThat(tokenVersionFromToken).isNotNull();
        }

        @Test
        @DisplayName("Refresh Token 만료시간 검증")
        void createRefreshToken_ShouldHaveCorrectExpiration() {
            // When: Refresh Token을 생성하면
            String refreshToken = jwtProvider.createRefreshToken(testUsername, testRole);
            String tokenWithoutPrefix = refreshToken.substring(JwtProvider.BEARER_PREFIX.length());

            // Then: 토큰의 만료시간이 올바르게 설정되어야 함
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(testKey)
                    .build()
                    .parseClaimsJws(tokenWithoutPrefix)
                    .getBody();

            Date issuedAt = claims.getIssuedAt();
            Date expiration = claims.getExpiration();
            
            // Refresh Token의 만료시간이 Access Token보다 길어야 함
            long expectedExpirationTime = issuedAt.getTime() + (REFRESH_TOKEN_EXPIRATION * 1000);
            long actualExpirationTime = expiration.getTime();
            
            assertThat(Math.abs(actualExpirationTime - expectedExpirationTime)).isLessThan(1000);
            assertThat(REFRESH_TOKEN_EXPIRATION).isGreaterThan(ACCESS_TOKEN_EXPIRATION);
        }
    }

    @Nested
    @DisplayName("토큰 검증 테스트")
    class TokenValidationTest {

        @Test
        @DisplayName("유효한 토큰 검증 성공")
        void validateAccessToken_WithValidToken_ShouldReturnTrue() {
            // Given: 유효한 토큰 생성
            String accessToken = jwtProvider.createAccessToken(testUsername, testRole);
            String tokenWithoutPrefix = accessToken.substring(JwtProvider.BEARER_PREFIX.length());

            // When: 토큰을 검증하면
            boolean isValid = jwtProvider.validateAccessToken(tokenWithoutPrefix);

            // Then: 유효하다고 판단되어야 함
            assertThat(isValid).isTrue();
        }

        @Test
        @DisplayName("만료된 토큰 검증 실패")
        void validateAccessToken_WithExpiredToken_ShouldReturnFalse() {
            // Given: 이미 만료된 토큰 생성 (만료시간을 과거로 설정)
            Date pastDate = new Date(System.currentTimeMillis() - 1000); // 1초 전
            String expiredToken = Jwts.builder()
                    .setSubject(testUsername)
                    .claim(JwtProvider.AUTHORIZATION_KEY, testRole)
                    .setExpiration(pastDate)
                    .setIssuedAt(new Date(pastDate.getTime() - 1000))
                    .signWith(testKey)
                    .compact();

            // When: 만료된 토큰을 검증하면
            boolean isValid = jwtProvider.validateAccessToken(expiredToken);

            // Then: 유효하지 않다고 판단되어야 함
            assertThat(isValid).isFalse();
        }

        @Test
        @DisplayName("잘못된 서명의 토큰 검증 실패")
        void validateAccessToken_WithInvalidSignature_ShouldReturnFalse() {
            // Given: 다른 키로 서명된 토큰
            Key wrongKey = Keys.hmacShaKeyFor("wrong-secret-key-for-testing-1234567890".getBytes());
            String invalidToken = Jwts.builder()
                    .setSubject(testUsername)
                    .claim(JwtProvider.AUTHORIZATION_KEY, testRole)
                    .setExpiration(new Date(System.currentTimeMillis() + 1000000))
                    .setIssuedAt(new Date())
                    .signWith(wrongKey)
                    .compact();

            // When: 잘못된 서명의 토큰을 검증하면
            boolean isValid = jwtProvider.validateAccessToken(invalidToken);

            // Then: 유효하지 않다고 판단되어야 함
            assertThat(isValid).isFalse();
        }

        @Test
        @DisplayName("블랙리스트 토큰 검증 실패")
        void validateAccessToken_WithBlacklistedToken_ShouldReturnFalse() {
            // Given: 토큰 ID가 포함된 토큰과 블랙리스트 설정
            String tokenWithSession = jwtProvider.createRefreshTokenWithSession(testUsername, testRole, testSessionId);
            String tokenId = jwtProvider.getClaimFromToken(tokenWithSession, "tokenId");
            String tokenWithoutPrefix = tokenWithSession.substring(JwtProvider.BEARER_PREFIX.length());
            
            when(tokenManager.isTokenBlacklisted(tokenId)).thenReturn(true);

            // When: 블랙리스트에 등록된 토큰을 검증하면
            boolean isValid = jwtProvider.validateAccessToken(tokenWithoutPrefix);

            // Then: 유효하지 않다고 판단되어야 함
            assertThat(isValid).isFalse();
        }

        @Test
        @DisplayName("null 토큰 검증 실패")
        void validateAccessToken_WithNullToken_ShouldReturnFalse() {
            // When & Then: null 토큰 검증 시 false 반환
            assertThat(jwtProvider.validateAccessToken(null)).isFalse();
        }

        @Test
        @DisplayName("빈 문자열 토큰 검증 실패")
        void validateAccessToken_WithEmptyToken_ShouldReturnFalse() {
            // When & Then: 빈 문자열 토큰 검증 시 false 반환
            assertThat(jwtProvider.validateAccessToken("")).isFalse();
        }

        @Test
        @DisplayName("잘못된 형식의 토큰 검증 실패")
        void validateAccessToken_WithMalformedToken_ShouldReturnFalse() {
            // When & Then: 잘못된 형식의 토큰 검증 시 false 반환
            assertThat(jwtProvider.validateAccessToken("invalid.token.format")).isFalse();
        }
    }

    @Nested
    @DisplayName("토큰 정보 추출 테스트")
    class TokenInformationExtractionTest {

        @Test
        @DisplayName("토큰에서 사용자명 추출 성공")
        void getUsernameFromToken_WithValidToken_ShouldReturnUsername() {
            // Given: 유효한 토큰 생성
            String accessToken = jwtProvider.createAccessToken(testUsername, testRole);

            // When: 토큰에서 사용자명을 추출하면
            String extractedUsername = jwtProvider.getUsernameFromToken(accessToken);

            // Then: 올바른 사용자명이 반환되어야 함
            assertThat(extractedUsername).isEqualTo(testUsername);
        }

        @Test
        @DisplayName("Bearer 접두사 없는 토큰에서 사용자명 추출")
        void getUsernameFromToken_WithoutBearerPrefix_ShouldReturnUsername() {
            // Given: Bearer 접두사가 없는 토큰
            String accessToken = jwtProvider.createAccessToken(testUsername, testRole);
            String tokenWithoutPrefix = accessToken.substring(JwtProvider.BEARER_PREFIX.length());

            // When: 토큰에서 사용자명을 추출하면
            String extractedUsername = jwtProvider.getUsernameFromToken(tokenWithoutPrefix);

            // Then: 올바른 사용자명이 반환되어야 함
            assertThat(extractedUsername).isEqualTo(testUsername);
        }

        @Test
        @DisplayName("만료된 토큰에서도 사용자명 추출 가능")
        void getUsernameFromToken_WithExpiredToken_ShouldReturnUsername() {
            // Given: 만료된 토큰 생성
            Date pastDate = new Date(System.currentTimeMillis() - 1000);
            String expiredToken = JwtProvider.BEARER_PREFIX + Jwts.builder()
                    .setSubject(testUsername)
                    .claim(JwtProvider.AUTHORIZATION_KEY, testRole)
                    .setExpiration(pastDate)
                    .setIssuedAt(new Date(pastDate.getTime() - 1000))
                    .signWith(testKey)
                    .compact();

            // When: 만료된 토큰에서 사용자명을 추출하면
            String extractedUsername = jwtProvider.getUsernameFromToken(expiredToken);

            // Then: 만료되었지만 사용자명은 추출되어야 함
            assertThat(extractedUsername).isEqualTo(testUsername);
        }

        @Test
        @DisplayName("토큰에서 권한 추출 성공")
        void getRoleFromToken_WithValidToken_ShouldReturnRole() {
            // Given: 유효한 토큰 생성
            String accessToken = jwtProvider.createAccessToken(testUsername, testRole);
            String tokenWithoutPrefix = accessToken.substring(JwtProvider.BEARER_PREFIX.length());

            // When: 토큰에서 권한을 추출하면
            UserRole extractedRole = jwtProvider.getRoleFromToken(tokenWithoutPrefix);

            // Then: 올바른 권한이 반환되어야 함
            assertThat(extractedRole).isEqualTo(testRole);
        }

        @Test
        @DisplayName("토큰에서 특정 클레임 추출 성공")
        void getClaimFromToken_WithValidClaim_ShouldReturnClaimValue() {
            // Given: 세션 ID가 포함된 토큰 생성
            String tokenWithSession = jwtProvider.createAccessTokenWithSession(testUsername, testRole, testSessionId);

            // When: 토큰에서 세션 ID 클레임을 추출하면
            String extractedSessionId = jwtProvider.getClaimFromToken(tokenWithSession, "sessionId");

            // Then: 올바른 세션 ID가 반환되어야 함
            assertThat(extractedSessionId).isEqualTo(testSessionId);
        }

        @Test
        @DisplayName("존재하지 않는 클레임 추출 시 null 반환")
        void getClaimFromToken_WithNonExistentClaim_ShouldReturnNull() {
            // Given: 기본 토큰 생성
            String accessToken = jwtProvider.createAccessToken(testUsername, testRole);

            // When: 존재하지 않는 클레임을 추출하면
            String nonExistentClaim = jwtProvider.getClaimFromToken(accessToken, "nonExistentClaim");

            // Then: null이 반환되어야 함
            assertThat(nonExistentClaim).isNull();
        }

        @Test
        @DisplayName("잘못된 토큰에서 정보 추출 시 null 반환")
        void getUsernameFromToken_WithInvalidToken_ShouldReturnNull() {
            // When & Then: 잘못된 토큰에서 사용자명 추출 시 null 반환
            assertThat(jwtProvider.getUsernameFromToken("invalid.token")).isNull();
        }
    }

    @Nested
    @DisplayName("쿠키 관리 테스트")
    class CookieManagementTest {

        @Test
        @DisplayName("Refresh Token 쿠키 설정 성공")
        void setRefreshTokenCookie_WithValidToken_ShouldSetCookie() {
            // Given: 유효한 Refresh Token과 MockHttpServletResponse
            String refreshToken = jwtProvider.createRefreshToken(testUsername, testRole);
            MockHttpServletResponse response = new MockHttpServletResponse();

            // When: Refresh Token을 쿠키로 설정하면
            jwtProvider.setRefreshTokenCookie(response, refreshToken);

            // Then: 올바른 쿠키가 설정되어야 함
            Cookie cookie = response.getCookie(JwtProvider.REFRESH_TOKEN_COOKIE_NAME);
            assertThat(cookie).isNotNull();
            assertThat(cookie.getName()).isEqualTo(JwtProvider.REFRESH_TOKEN_COOKIE_NAME);
            assertThat(cookie.getValue()).isEqualTo(refreshToken.substring(JwtProvider.BEARER_PREFIX.length()));
            assertThat(cookie.isHttpOnly()).isTrue();
            assertThat(cookie.getSecure()).isTrue();
            assertThat(cookie.getPath()).isEqualTo("/");
            assertThat(cookie.getMaxAge()).isEqualTo(REFRESH_TOKEN_EXPIRATION.intValue());
        }

        @Test
        @DisplayName("Bearer 접두사가 포함된 토큰의 쿠키 설정")
        void setRefreshTokenCookie_WithBearerPrefix_ShouldSetCookieWithoutPrefix() {
            // Given: Bearer 접두사가 포함된 Refresh Token
            String refreshToken = jwtProvider.createRefreshToken(testUsername, testRole);
            MockHttpServletResponse response = new MockHttpServletResponse();

            // When: Bearer 접두사가 포함된 토큰을 쿠키로 설정하면
            jwtProvider.setRefreshTokenCookie(response, refreshToken);

            // Then: 쿠키 값에는 Bearer 접두사가 제거되어야 함
            Cookie cookie = response.getCookie(JwtProvider.REFRESH_TOKEN_COOKIE_NAME);
            assertThat(cookie.getValue()).doesNotContain(JwtProvider.BEARER_PREFIX);
        }

        @Test
        @DisplayName("요청에서 Refresh Token 쿠키 추출 성공")
        void getRefreshTokenFromCookie_WithValidCookie_ShouldReturnToken() {
            // Given: Refresh Token이 포함된 쿠키가 있는 요청
            MockHttpServletRequest request = new MockHttpServletRequest();
            String tokenValue = "valid.refresh.token";
            Cookie refreshCookie = new Cookie(JwtProvider.REFRESH_TOKEN_COOKIE_NAME, tokenValue);
            request.setCookies(refreshCookie);

            // When: 요청에서 Refresh Token을 추출하면
            String extractedToken = jwtProvider.getRefreshTokenFromCookie(request);

            // Then: 올바른 토큰이 반환되어야 함
            assertThat(extractedToken).isEqualTo(tokenValue);
        }

        @Test
        @DisplayName("쿠키가 없는 요청에서 null 반환")
        void getRefreshTokenFromCookie_WithNoCookies_ShouldReturnNull() {
            // Given: 쿠키가 없는 요청
            MockHttpServletRequest request = new MockHttpServletRequest();

            // When: 요청에서 Refresh Token을 추출하면
            String extractedToken = jwtProvider.getRefreshTokenFromCookie(request);

            // Then: null이 반환되어야 함
            assertThat(extractedToken).isNull();
        }

        @Test
        @DisplayName("다른 이름의 쿠키만 있는 경우 null 반환")
        void getRefreshTokenFromCookie_WithDifferentCookieName_ShouldReturnNull() {
            // Given: 다른 이름의 쿠키가 있는 요청
            MockHttpServletRequest request = new MockHttpServletRequest();
            Cookie otherCookie = new Cookie("other-cookie", "other-value");
            request.setCookies(otherCookie);

            // When: 요청에서 Refresh Token을 추출하면
            String extractedToken = jwtProvider.getRefreshTokenFromCookie(request);

            // Then: null이 반환되어야 함
            assertThat(extractedToken).isNull();
        }

        @Test
        @DisplayName("쿠키 무효화 성공")
        void clearCookie_ShouldCreateExpiredCookie() {
            // Given: MockHttpServletResponse
            MockHttpServletResponse response = new MockHttpServletResponse();
            String cookieName = "test-cookie";

            // When: 쿠키를 무효화하면
            JwtProvider.clearCookie(response, cookieName);

            // Then: 만료된 쿠키가 설정되어야 함
            Cookie cookie = response.getCookie(cookieName);
            assertThat(cookie).isNotNull();
            assertThat(cookie.getName()).isEqualTo(cookieName);
            assertThat(cookie.getValue()).isNull();
            assertThat(cookie.getMaxAge()).isEqualTo(0);
            assertThat(cookie.getPath()).isEqualTo("/");
            assertThat(cookie.isHttpOnly()).isTrue();
        }
    }

    @Nested
    @DisplayName("토큰 문자열 처리 테스트")
    class TokenStringProcessingTest {

        @Test
        @DisplayName("Bearer 접두사 제거 성공")
        void substringToken_WithBearerPrefix_ShouldRemovePrefix() {
            // Given: Bearer 접두사가 포함된 토큰
            String tokenWithPrefix = JwtProvider.BEARER_PREFIX + "actual.token.value";

            // When: Bearer 접두사를 제거하면
            String tokenWithoutPrefix = jwtProvider.substringToken(tokenWithPrefix);

            // Then: 접두사가 제거된 토큰이 반환되어야 함
            assertThat(tokenWithoutPrefix).isEqualTo("actual.token.value");
        }

        @Test
        @DisplayName("Bearer 접두사가 없는 토큰 그대로 반환")
        void substringToken_WithoutBearerPrefix_ShouldReturnAsIs() {
            // Given: Bearer 접두사가 없는 토큰
            String tokenWithoutPrefix = "actual.token.value";

            // When: substringToken을 호출하면
            String result = jwtProvider.substringToken(tokenWithoutPrefix);

            // Then: 원본 토큰이 그대로 반환되어야 함
            assertThat(result).isEqualTo(tokenWithoutPrefix);
        }

        @Test
        @DisplayName("null 토큰 처리 시 예외 발생")
        void substringToken_WithNullToken_ShouldThrowException() {
            // When & Then: null 토큰 처리 시 NullPointerException 발생
            assertThatThrownBy(() -> jwtProvider.substringToken(null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("토큰이 없습니다.");
        }

        @Test
        @DisplayName("빈 문자열 토큰 처리 시 예외 발생")
        void substringToken_WithEmptyToken_ShouldThrowException() {
            // When & Then: 빈 문자열 토큰 처리 시 NullPointerException 발생
            assertThatThrownBy(() -> jwtProvider.substringToken(""))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("토큰이 없습니다.");
        }

        @Test
        @DisplayName("Header에서 Access Token 추출 성공")
        void getAccessTokenFromHeader_WithValidHeader_ShouldReturnToken() {
            // Given: Authorization 헤더가 있는 요청
            MockHttpServletRequest request = new MockHttpServletRequest();
            String tokenValue = "valid.access.token";
            request.addHeader(JwtProvider.AUTHORIZATION_HEADER, JwtProvider.BEARER_PREFIX + tokenValue);

            // When: 헤더에서 Access Token을 추출하면
            String extractedToken = jwtProvider.getAccessTokenFromHeader(request);

            // Then: Bearer 접두사가 제거된 토큰이 반환되어야 함
            assertThat(extractedToken).isEqualTo(tokenValue);
        }

        @Test
        @DisplayName("Bearer 접두사 없는 헤더에서 토큰 추출")
        void getAccessTokenFromHeader_WithoutBearerPrefix_ShouldReturnAsIs() {
            // Given: Bearer 접두사가 없는 Authorization 헤더
            MockHttpServletRequest request = new MockHttpServletRequest();
            String tokenValue = "token.without.bearer";
            request.addHeader(JwtProvider.AUTHORIZATION_HEADER, tokenValue);

            // When: 헤더에서 Access Token을 추출하면
            String extractedToken = jwtProvider.getAccessTokenFromHeader(request);

            // Then: 원본 값이 그대로 반환되어야 함
            assertThat(extractedToken).isEqualTo(tokenValue);
        }

        @Test
        @DisplayName("Authorization 헤더가 없는 경우 null 반환")
        void getAccessTokenFromHeader_WithNoHeader_ShouldReturnNull() {
            // Given: Authorization 헤더가 없는 요청
            MockHttpServletRequest request = new MockHttpServletRequest();

            // When: 헤더에서 Access Token을 추출하면
            String extractedToken = jwtProvider.getAccessTokenFromHeader(request);

            // Then: null이 반환되어야 함
            assertThat(extractedToken).isNull();
        }
    }

    @Nested
    @DisplayName("Redis 연동 테스트")
    class RedisIntegrationTest {

        @Test
        @DisplayName("Refresh Token 존재 여부 확인 - 존재하는 경우")
        void hasRefreshToken_WithExistingToken_ShouldReturnTrue() {
            // Given: Redis에 토큰이 존재하는 상황
            when(redisTemplate.hasKey(testUsername)).thenReturn(true);

            // When: Refresh Token 존재 여부를 확인하면
            boolean hasToken = jwtProvider.hasRefreshToken(testUsername);

            // Then: true를 반환해야 함
            assertThat(hasToken).isTrue();
        }

        @Test
        @DisplayName("Refresh Token 존재 여부 확인 - 존재하지 않는 경우")
        void hasRefreshToken_WithNonExistingToken_ShouldReturnFalse() {
            // Given: Redis에 토큰이 존재하지 않는 상황
            when(redisTemplate.hasKey(testUsername)).thenReturn(false);

            // When: Refresh Token 존재 여부를 확인하면
            boolean hasToken = jwtProvider.hasRefreshToken(testUsername);

            // Then: false를 반환해야 함
            assertThat(hasToken).isFalse();
        }

        @Test
        @DisplayName("Redis에서 null 반환 시 false 처리")
        void hasRefreshToken_WithNullResponse_ShouldReturnFalse() {
            // Given: Redis에서 null을 반환하는 상황
            when(redisTemplate.hasKey(testUsername)).thenReturn(null);

            // When: Refresh Token 존재 여부를 확인하면
            boolean hasToken = jwtProvider.hasRefreshToken(testUsername);

            // Then: false를 반환해야 함
            assertThat(hasToken).isFalse();
        }
    }
}