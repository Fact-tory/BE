package com.commonground.be.domain.redis;

import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RedisService {

	private final RedisTemplate<String, String> redisTemplate;

	// yml에서 설정값 주입 (초 단위)
	@Value("${redis.expiration.refresh-token}")
	private Long refreshTokenExpiration;

	@Value("${redis.expiration.kakao-tid}")
	private Long kakaoTidExpiration;

	@Value("${redis.expiration.user-session}")
	private Long userSessionExpiration;

	@Value("${redis.expiration.verification-code}")
	private Long verificationCodeExpiration;

	@Transactional
	public void saveRefreshToken(String username, String refreshToken) {
		redisTemplate.opsForValue()
				.set(username, refreshToken, refreshTokenExpiration, TimeUnit.SECONDS);
	}

	@Transactional
	public void saveKakaoTid(String username, String tid) {
		redisTemplate.opsForValue()
				.set(username + ":tid", tid, kakaoTidExpiration, TimeUnit.SECONDS);
	}

	@Transactional
	public void saveUserSession(String sessionKey, String sessionData) {
		redisTemplate.opsForValue()
				.set("session:" + sessionKey, sessionData, userSessionExpiration, TimeUnit.SECONDS);
	}

	@Transactional
	public void saveVerificationCode(String phoneNumber, String code) {
		redisTemplate.opsForValue()
				.set("verify:" + phoneNumber, code, verificationCodeExpiration, TimeUnit.SECONDS);
	}

	public String getValue(String key, String prefix) {
		return redisTemplate.opsForValue().get(key + prefix);
	}

	public String getSessionData(String sessionKey) {
		return redisTemplate.opsForValue().get("session:" + sessionKey);
	}

	public String getVerificationCode(String phoneNumber) {
		return redisTemplate.opsForValue().get("verify:" + phoneNumber);
	}

	@Transactional
	public void deleteKey(String key) {
		redisTemplate.delete(key);
	}

	@Transactional
	public void deleteSession(String sessionKey) {
		redisTemplate.delete("session:" + sessionKey);
	}
}
