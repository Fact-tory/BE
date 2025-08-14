package com.commonground.be.global.infrastructure.security.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * TokenManager 단위 테스트 클래스
 * 
 * 이 테스트는 TokenManager의 토큰 버전 관리와 블랙리스트 기능을 검증합니다.
 * Redis를 활용한 토큰 무효화, 버전 관리, 개별 토큰 블랙리스트 등의
 * 핵심 보안 기능들을 테스트합니다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TokenManager 단위 테스트")
@ActiveProfiles("test")
class TokenManagerTest {

    @InjectMocks
    private TokenManager tokenManager;

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    // 테스트용 설정값
    private static final Long REFRESH_TOKEN_EXPIRATION = 86400L; // 24시간
    private String testUsername;
    private String testTokenId;

    /**
     * 각 테스트 실행 전 TokenManager 설정을 초기화합니다.
     */
    @BeforeEach
    void setUp() {
        testUsername = "testuser";
        testTokenId = "test-token-id-12345";

        // Redis 관련 Mock 설정
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        // 리플렉션을 통해 설정값 주입
        ReflectionTestUtils.setField(tokenManager, "refreshTokenExpiration", REFRESH_TOKEN_EXPIRATION);
    }

    @Nested
    @DisplayName("사용자 모든 토큰 무효화 테스트")
    class InvalidateAllUserTokensTest {

        @Test
        @DisplayName("사용자의 모든 토큰 무효화 성공")
        void invalidateAllUserTokens_WithValidUsername_ShouldIncrementTokenVersion() {
            // Given: 유효한 사용자명
            String expectedKey = "token_version:" + testUsername;
            when(valueOperations.increment(expectedKey)).thenReturn(1L);

            // When: 사용자의 모든 토큰을 무효화하면
            tokenManager.invalidateAllUserTokens(testUsername);

            // Then: Redis에서 토큰 버전이 증가하고 만료시간이 설정되어야 함
            verify(valueOperations).increment(expectedKey);
            verify(redisTemplate).expire(expectedKey, REFRESH_TOKEN_EXPIRATION, TimeUnit.SECONDS);
        }

        @Test
        @DisplayName("null 사용자명으로 토큰 무효화 시도")
        void invalidateAllUserTokens_WithNullUsername_ShouldHandleGracefully() {
            // Given: null 사용자명
            String nullUsername = null;
            String expectedKey = "token_version:" + nullUsername; // "token_version:null"

            // When: null 사용자명으로 토큰 무효화를 시도하면
            tokenManager.invalidateAllUserTokens(nullUsername);

            // Then: Redis 호출이 수행되어야 함 (null 처리는 Redis가 담당)
            verify(valueOperations).increment(expectedKey);
            verify(redisTemplate).expire(eq(expectedKey), eq(REFRESH_TOKEN_EXPIRATION), eq(TimeUnit.SECONDS));
        }

        @Test
        @DisplayName("빈 문자열 사용자명으로 토큰 무효화")
        void invalidateAllUserTokens_WithEmptyUsername_ShouldHandleGracefully() {
            // Given: 빈 문자열 사용자명
            String emptyUsername = "";
            String expectedKey = "token_version:" + emptyUsername; // "token_version:"

            // When: 빈 문자열 사용자명으로 토큰 무효화를 시도하면
            tokenManager.invalidateAllUserTokens(emptyUsername);

            // Then: Redis 호출이 수행되어야 함
            verify(valueOperations).increment(expectedKey);
            verify(redisTemplate).expire(eq(expectedKey), eq(REFRESH_TOKEN_EXPIRATION), eq(TimeUnit.SECONDS));
        }

        @Test
        @DisplayName("여러 번 토큰 무효화 시 버전이 계속 증가")
        void invalidateAllUserTokens_MultipleCalls_ShouldIncrementVersionEachTime() {
            // Given: 토큰 버전이 증가하는 시나리오
            String expectedKey = "token_version:" + testUsername;
            when(valueOperations.increment(expectedKey))
                    .thenReturn(1L)
                    .thenReturn(2L)
                    .thenReturn(3L);

            // When: 여러 번 토큰 무효화를 실행하면
            tokenManager.invalidateAllUserTokens(testUsername);
            tokenManager.invalidateAllUserTokens(testUsername);
            tokenManager.invalidateAllUserTokens(testUsername);

            // Then: 매번 버전이 증가해야 함
            verify(valueOperations, org.mockito.Mockito.times(3)).increment(expectedKey);
            verify(redisTemplate, org.mockito.Mockito.times(3))
                    .expire(expectedKey, REFRESH_TOKEN_EXPIRATION, TimeUnit.SECONDS);
        }
    }

    @Nested
    @DisplayName("토큰 버전 검증 테스트")
    class TokenVersionValidationTest {

        @Test
        @DisplayName("초기 상태에서 토큰 버전 검증 (Redis에 버전 없음)")
        void isTokenVersionValid_WithNoVersionInRedis_ShouldReturnTrue() {
            // Given: Redis에 토큰 버전이 없는 초기 상태
            String versionKey = "token_version:" + testUsername;
            when(valueOperations.get(versionKey)).thenReturn(null);

            // When: 토큰 버전을 검증하면
            boolean isValid = tokenManager.isTokenVersionValid(testUsername, 1L);

            // Then: 초기 상태이므로 true를 반환해야 함
            assertThat(isValid).isTrue();
            verify(valueOperations).get(versionKey);
        }

        @Test
        @DisplayName("유효한 토큰 버전 검증 성공")
        void isTokenVersionValid_WithValidVersion_ShouldReturnTrue() {
            // Given: Redis에 현재 버전이 2이고, 검증할 토큰 버전이 2인 경우
            String versionKey = "token_version:" + testUsername;
            when(valueOperations.get(versionKey)).thenReturn("2");

            // When: 같은 버전의 토큰을 검증하면
            boolean isValid = tokenManager.isTokenVersionValid(testUsername, 2L);

            // Then: 유효하다고 판단되어야 함
            assertThat(isValid).isTrue();
            verify(valueOperations).get(versionKey);
        }

        @Test
        @DisplayName("더 높은 버전의 토큰 검증 성공")
        void isTokenVersionValid_WithHigherVersion_ShouldReturnTrue() {
            // Given: Redis에 현재 버전이 2이고, 검증할 토큰 버전이 3인 경우
            String versionKey = "token_version:" + testUsername;
            when(valueOperations.get(versionKey)).thenReturn("2");

            // When: 더 높은 버전의 토큰을 검증하면
            boolean isValid = tokenManager.isTokenVersionValid(testUsername, 3L);

            // Then: 유효하다고 판단되어야 함
            assertThat(isValid).isTrue();
            verify(valueOperations).get(versionKey);
        }

        @Test
        @DisplayName("낮은 버전의 토큰 검증 실패")
        void isTokenVersionValid_WithLowerVersion_ShouldReturnFalse() {
            // Given: Redis에 현재 버전이 3이고, 검증할 토큰 버전이 2인 경우
            String versionKey = "token_version:" + testUsername;
            when(valueOperations.get(versionKey)).thenReturn("3");

            // When: 낮은 버전의 토큰을 검증하면
            boolean isValid = tokenManager.isTokenVersionValid(testUsername, 2L);

            // Then: 유효하지 않다고 판단되어야 함
            assertThat(isValid).isFalse();
            verify(valueOperations).get(versionKey);
        }

        @Test
        @DisplayName("null 토큰 버전 검증 실패")
        void isTokenVersionValid_WithNullTokenVersion_ShouldReturnFalse() {
            // Given: Redis에 현재 버전이 있고, 검증할 토큰 버전이 null인 경우
            String versionKey = "token_version:" + testUsername;
            when(valueOperations.get(versionKey)).thenReturn("1");

            // When: null 토큰 버전을 검증하면
            boolean isValid = tokenManager.isTokenVersionValid(testUsername, null);

            // Then: 유효하지 않다고 판단되어야 함
            assertThat(isValid).isFalse();
            verify(valueOperations).get(versionKey);
        }

        @Test
        @DisplayName("Redis에서 숫자가 아닌 값이 반환될 때 예외 처리")
        void isTokenVersionValid_WithInvalidRedisValue_ShouldThrowException() {
            // Given: Redis에서 숫자가 아닌 값이 반환되는 경우
            String versionKey = "token_version:" + testUsername;
            when(valueOperations.get(versionKey)).thenReturn("invalid-number");

            // When & Then: NumberFormatException이 발생해야 함
            org.junit.jupiter.api.Assertions.assertThrows(
                    NumberFormatException.class,
                    () -> tokenManager.isTokenVersionValid(testUsername, 1L)
            );
        }
    }

    @Nested
    @DisplayName("개별 토큰 무효화 테스트")
    class IndividualTokenInvalidationTest {

        @Test
        @DisplayName("유효한 토큰 ID로 개별 토큰 무효화 성공")
        void invalidateToken_WithValidTokenId_ShouldAddToBlacklist() {
            // Given: 유효한 토큰 ID
            String expectedKey = "token_blacklist:" + testTokenId;

            // When: 개별 토큰을 무효화하면
            tokenManager.invalidateToken(testTokenId);

            // Then: Redis 블랙리스트에 추가되어야 함
            verify(valueOperations).set(
                    eq(expectedKey),
                    eq("revoked"),
                    eq(REFRESH_TOKEN_EXPIRATION),
                    eq(TimeUnit.SECONDS)
            );
        }

        @Test
        @DisplayName("null 토큰 ID로 개별 토큰 무효화 시도")
        void invalidateToken_WithNullTokenId_ShouldNotCallRedis() {
            // When: null 토큰 ID로 무효화를 시도하면
            tokenManager.invalidateToken(null);

            // Then: Redis 호출이 발생하지 않아야 함
            verify(valueOperations, org.mockito.Mockito.never()).set(anyString(), anyString(), anyLong(), any(TimeUnit.class));
        }

        @Test
        @DisplayName("빈 문자열 토큰 ID로 개별 토큰 무효화 시도")
        void invalidateToken_WithEmptyTokenId_ShouldNotCallRedis() {
            // When: 빈 문자열 토큰 ID로 무효화를 시도하면
            tokenManager.invalidateToken("");

            // Then: Redis 호출이 발생하지 않아야 함
            verify(valueOperations, org.mockito.Mockito.never()).set(anyString(), anyString(), anyLong(), any(TimeUnit.class));
        }

        @Test
        @DisplayName("공백만 있는 토큰 ID로 개별 토큰 무효화 시도")
        void invalidateToken_WithWhitespaceTokenId_ShouldNotCallRedis() {
            // When: 공백만 있는 토큰 ID로 무효화를 시도하면
            tokenManager.invalidateToken("   ");

            // Then: Redis 호출이 발생하지 않아야 함
            verify(valueOperations, org.mockito.Mockito.never()).set(anyString(), anyString(), anyLong(), any(TimeUnit.class));
        }
    }

    @Nested
    @DisplayName("토큰 블랙리스트 확인 테스트")
    class TokenBlacklistCheckTest {

        @Test
        @DisplayName("블랙리스트에 있는 토큰 확인")
        void isTokenBlacklisted_WithBlacklistedToken_ShouldReturnTrue() {
            // Given: 블랙리스트에 있는 토큰
            String blacklistKey = "token_blacklist:" + testTokenId;
            when(redisTemplate.hasKey(blacklistKey)).thenReturn(true);

            // When: 토큰이 블랙리스트에 있는지 확인하면
            boolean isBlacklisted = tokenManager.isTokenBlacklisted(testTokenId);

            // Then: true를 반환해야 함
            assertThat(isBlacklisted).isTrue();
            verify(redisTemplate).hasKey(blacklistKey);
        }

        @Test
        @DisplayName("블랙리스트에 없는 토큰 확인")
        void isTokenBlacklisted_WithNonBlacklistedToken_ShouldReturnFalse() {
            // Given: 블랙리스트에 없는 토큰
            String blacklistKey = "token_blacklist:" + testTokenId;
            when(redisTemplate.hasKey(blacklistKey)).thenReturn(false);

            // When: 토큰이 블랙리스트에 있는지 확인하면
            boolean isBlacklisted = tokenManager.isTokenBlacklisted(testTokenId);

            // Then: false를 반환해야 함
            assertThat(isBlacklisted).isFalse();
            verify(redisTemplate).hasKey(blacklistKey);
        }

        @Test
        @DisplayName("null 토큰 ID로 블랙리스트 확인")
        void isTokenBlacklisted_WithNullTokenId_ShouldReturnFalse() {
            // When: null 토큰 ID로 블랙리스트를 확인하면
            boolean isBlacklisted = tokenManager.isTokenBlacklisted(null);

            // Then: false를 반환하고 Redis 호출하지 않아야 함
            assertThat(isBlacklisted).isFalse();
            verify(redisTemplate, org.mockito.Mockito.never()).hasKey(anyString());
        }

        @Test
        @DisplayName("빈 문자열 토큰 ID로 블랙리스트 확인")
        void isTokenBlacklisted_WithEmptyTokenId_ShouldReturnFalse() {
            // When: 빈 문자열 토큰 ID로 블랙리스트를 확인하면
            boolean isBlacklisted = tokenManager.isTokenBlacklisted("");

            // Then: false를 반환하고 Redis 호출하지 않아야 함
            assertThat(isBlacklisted).isFalse();
            verify(redisTemplate, org.mockito.Mockito.never()).hasKey(anyString());
        }

        @Test
        @DisplayName("공백만 있는 토큰 ID로 블랙리스트 확인")
        void isTokenBlacklisted_WithWhitespaceTokenId_ShouldReturnFalse() {
            // When: 공백만 있는 토큰 ID로 블랙리스트를 확인하면
            boolean isBlacklisted = tokenManager.isTokenBlacklisted("   ");

            // Then: false를 반환하고 Redis 호출하지 않아야 함
            assertThat(isBlacklisted).isFalse();
            verify(redisTemplate, org.mockito.Mockito.never()).hasKey(anyString());
        }

        @Test
        @DisplayName("Redis에서 null 반환 시 false 처리")
        void isTokenBlacklisted_WithNullRedisResponse_ShouldReturnFalse() {
            // Given: Redis에서 null을 반환하는 경우
            String blacklistKey = "token_blacklist:" + testTokenId;
            when(redisTemplate.hasKey(blacklistKey)).thenReturn(null);

            // When: 토큰이 블랙리스트에 있는지 확인하면
            boolean isBlacklisted = tokenManager.isTokenBlacklisted(testTokenId);

            // Then: false를 반환해야 함
            assertThat(isBlacklisted).isFalse();
            verify(redisTemplate).hasKey(blacklistKey);
        }
    }

    @Nested
    @DisplayName("통합 시나리오 테스트")
    class IntegrationScenarioTest {

        @Test
        @DisplayName("토큰 무효화 후 블랙리스트 확인 시나리오")
        void tokenInvalidationAndBlacklistCheck_ShouldWorkTogether() {
            // Given: 토큰을 무효화한 상황
            String blacklistKey = "token_blacklist:" + testTokenId;
            
            // When: 토큰을 무효화하고
            tokenManager.invalidateToken(testTokenId);
            
            // Redis 응답 설정
            when(redisTemplate.hasKey(blacklistKey)).thenReturn(true);
            
            // 블랙리스트 확인하면
            boolean isBlacklisted = tokenManager.isTokenBlacklisted(testTokenId);

            // Then: 무효화되었다고 판단되어야 함
            assertThat(isBlacklisted).isTrue();
            verify(valueOperations).set(
                    eq(blacklistKey),
                    eq("revoked"),
                    eq(REFRESH_TOKEN_EXPIRATION),
                    eq(TimeUnit.SECONDS)
            );
            verify(redisTemplate).hasKey(blacklistKey);
        }

        @Test
        @DisplayName("사용자 모든 토큰 무효화 후 버전 검증 시나리오")
        void userTokenInvalidationAndVersionCheck_ShouldWorkTogether() {
            // Given: 사용자의 모든 토큰을 무효화한 상황
            String versionKey = "token_version:" + testUsername;
            when(valueOperations.increment(versionKey)).thenReturn(2L);
            when(valueOperations.get(versionKey)).thenReturn("2");

            // When: 모든 토큰을 무효화하고
            tokenManager.invalidateAllUserTokens(testUsername);
            
            // 이전 버전의 토큰을 검증하면
            boolean isOldTokenValid = tokenManager.isTokenVersionValid(testUsername, 1L);
            // 새 버전의 토큰을 검증하면
            boolean isNewTokenValid = tokenManager.isTokenVersionValid(testUsername, 2L);

            // Then: 이전 버전은 무효, 새 버전은 유효해야 함
            assertThat(isOldTokenValid).isFalse();
            assertThat(isNewTokenValid).isTrue();
            
            verify(valueOperations).increment(versionKey);
            verify(redisTemplate).expire(versionKey, REFRESH_TOKEN_EXPIRATION, TimeUnit.SECONDS);
        }
    }
}