package com.commonground.be.global.infrastructure.security.jwt;

import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 토큰 관리자 - 실제로 사용하는 기능만 포함
 * <p>
 * 현재 사용 중인 기능: - 사용자 모든 토큰 무효화 (로그아웃 시)
 * <p>
 * 향후 필요 시 추가할 기능: - 개별 토큰 블랙리스트 (보안 위협 시) - 토큰 버전 관리 (비밀번호 변경 시)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TokenManager {

	private final RedisTemplate<String, String> redisTemplate;

	@Value("${jwt.refresh-token.expiration}")
	private Long refreshTokenExpiration;

	/**
	 * 사용자의 모든 토큰 무효화 사용 시점: 로그아웃, 계정 탈퇴, 보안 위협 감지
	 */
	public void invalidateAllUserTokens(String username) {
		String versionKey = "token_version:" + username;
		redisTemplate.opsForValue().increment(versionKey);
		redisTemplate.expire(versionKey, refreshTokenExpiration, TimeUnit.SECONDS);
		log.info("사용자 {} 모든 토큰 무효화", username);
	}

	/**
	 * 토큰 버전 검증 - JWT에 포함된 버전과 Redis의 현재 버전 비교
	 */
	public boolean isTokenVersionValid(String username, Long tokenVersion) {
		String versionKey = "token_version:" + username;
		String currentVersion = redisTemplate.opsForValue().get(versionKey);

		if (currentVersion == null) {
			return true; // 초기 상태
		}

		return tokenVersion != null && tokenVersion >= Long.parseLong(currentVersion);
	}

	/**
	 * 개별 토큰 무효화 (Refresh Token 로테이션용) Refresh Token의 tokenId를 블랙리스트에 추가
	 */
	public void invalidateToken(String tokenId) {
		if (tokenId == null || tokenId.trim().isEmpty()) {
			return;
		}

		String blacklistKey = "token_blacklist:" + tokenId;
		redisTemplate.opsForValue()
				.set(blacklistKey, "revoked", refreshTokenExpiration, TimeUnit.SECONDS);
		log.debug("토큰 개별 무효화 - tokenId: {}", tokenId);
	}

	/**
	 * 토큰이 블랙리스트에 있는지 확인
	 */
	public boolean isTokenBlacklisted(String tokenId) {
		if (tokenId == null || tokenId.trim().isEmpty()) {
			return false;
		}

		String blacklistKey = "token_blacklist:" + tokenId;
		return Boolean.TRUE.equals(redisTemplate.hasKey(blacklistKey));
	}
}