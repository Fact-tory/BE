package com.commonground.be.domain.user.service;

import com.commonground.be.domain.user.dto.UserResponseDto;
import com.commonground.be.domain.user.entity.User;
import com.commonground.be.domain.user.repository.UserAdapter;
import com.commonground.be.global.infrastructure.security.jwt.TokenManager;
import jakarta.transaction.Transactional;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 소셜 로그인 특화 User Service 구현체 불필요한 비밀번호 관련 기능 제거
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserSocialService implements SocialUserServiceInterface {

	private final UserAdapter userAdapter;
	private final TokenManager tokenManager;

	@Override
	public UserResponseDto getUser(Long userId) {
		User user = userAdapter.findById(userId);
		userAdapter.validateNotDeleted(user.getUsername());
		return new UserResponseDto(user);
	}

	@Override
	public User findByUsername(String username) {
		return userAdapter.findByUsername(username);
	}

	@Override
	public User findByEmail(String email) {
		return userAdapter.findByEmail(email);
	}

	@Override
	public User save(User user){
		return userAdapter.save(user);
	}

	@Override
	public List<UserResponseDto> getAllUsers() {
		return userAdapter.findAll().stream()
				.map(UserResponseDto::new)
				.collect(Collectors.toList());
	}

	@Override
	@Transactional
	public void promoteToManager(Long userId) {
		User user = userAdapter.findById(userId);
		userAdapter.validateNotDeleted(user.getUsername());
		user.promoteToManager();
		userAdapter.save(user);
		log.info("사용자 관리자 승격: {}", user.getUsername());
	}

	@Override
	@Transactional
	public void demoteToUser(Long userId) {
		User user = userAdapter.findById(userId);
		userAdapter.validateNotDeleted(user.getUsername());
		user.demoteToUser();
		userAdapter.save(user);
		log.info("사용자 관리자 권한 해제: {}", user.getUsername());
	}

	@Override
	@Transactional
	public void disconnectSocialAccount(User user) {
		userAdapter.validateNotDeleted(user.getUsername());

		// 소셜 계정 연결 해제 (소프트 삭제)
		user.softDelete();
		userAdapter.save(user);

		// 계정 연결 해제 시 모든 토큰 무효화
		tokenManager.invalidateAllUserTokens(user.getUsername());
		log.info("소셜 계정 연결 해제 완료: {}", user.getUsername());
	}

	@Override
	public boolean isUserActive(String username) {
		User user = userAdapter.findByUsername(username);
		return user.isActive();
	}

	@Override
	public long getUserAccountAge(String username) {
		User user = userAdapter.findByUsername(username);
		return user.getDaysSinceCreated();
	}
}