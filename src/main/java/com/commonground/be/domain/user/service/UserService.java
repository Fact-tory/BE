package com.commonground.be.domain.user.service;


import static com.commonground.be.domain.user.utils.UserRole.MANAGER;
import static com.commonground.be.domain.user.utils.UserRole.USER;

import com.commonground.be.domain.user.dto.ProfileUpdateRequestDto;
import com.commonground.be.domain.user.dto.UserResponseDto;
import com.commonground.be.domain.user.entity.User;
import com.commonground.be.domain.user.repository.UserAdapter;
import com.commonground.be.domain.user.repository.UserRepository;
import com.commonground.be.global.exception.UserExceptions;
import com.commonground.be.global.security.TokenManager;
import jakarta.transaction.Transactional;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

	private final UserAdapter adapter;
	private final UserRepository userRepository;
	private final UserAdapter userAdapter;
	private final RedisTemplate<String, String> redisTemplate;
	private final TokenManager tokenManager;


	@Transactional
	public void logout(String username) {
		redisTemplate.delete(username);
	}

	public UserResponseDto getUser(Long userId) {
		User user = userAdapter.findById(userId);
		userAdapter.isDeleted(user.getUsername());
		return new UserResponseDto(user);
	}

	@Transactional
	public void withdraw(User user) {
		userAdapter.isDeleted(user.getUsername());

		// SoftDeleteTimeStamp 편의 메서드 활용
		user.markAsDeleted();
		userAdapter.save(user);

		// 계정 탈퇴 시 모든 토큰 무효화 (TokenManager 활용)
		tokenManager.invalidateAllUserTokens(user.getUsername());
		log.info("계정 탈퇴로 인한 토큰 무효화: {}", user.getUsername());
	}

	@Transactional
	public void resign(Long userId) {
		User user = userAdapter.findById(userId);
		userAdapter.isDeleted(user.getUsername());

		// SoftDeleteTimeStamp 편의 메서드 활용 - 복원
		user.restore();
		userAdapter.save(user);
		log.info("사용자 복원 완료: {}", user.getUsername());
	}

	@Transactional
	public void update(User user, ProfileUpdateRequestDto requestDto) {
		user.updateProfile(requestDto);
		userAdapter.save(user);
	}

	public List<UserResponseDto> getUserAllList(User user) {
		checkUserRole(user);
		return userAdapter.findAll().stream()  // 모든 사용자 목록을 스트림으로 변환
				.map(UserResponseDto::new)  // 각 User 객체를 UserResponseDto로 변환
				.collect(Collectors.toList());  // 결과를 리스트로 수집
	}

	/**
	 * 관리자 전용: 권한 체크 없이 모든 사용자 조회 (삭제된 사용자 포함)
	 */
	public List<UserResponseDto> getAllUsersForAdmin() {
		return userRepository.findAll().stream() // 삭제된 사용자도 포함
				.map(UserResponseDto::new)
				.collect(Collectors.toList());
	}

	/**
	 * 관리자 전용: 특정 사용자 강제 탈퇴
	 */
	@Transactional
	public void forceWithdrawUser(Long userId) {
		User user = userAdapter.findById(userId);
		user.markAsDeleted();
		userAdapter.save(user);
		
		// 해당 사용자의 모든 토큰 무효화
		tokenManager.invalidateAllUserTokens(user.getUsername());
		log.info("관리자에 의한 강제 탈퇴: {}", user.getUsername());
	}

	/**
	 * 관리자 전용: 활성 사용자 수 조회
	 */
	public long getActiveUserCount() {
		return userRepository.findAll().stream()
				.filter(User::isActive)
				.count();
	}



	public void checkUserRole(User user) {
		if (!user.getUserRole().equals(MANAGER)) {
			throw UserExceptions.notManager();
		}
	}


	/**
	 * TimeStamp 활용 예시들 - stamps 패키지 편의 메서드 활용
	 */

	// 신규 가입자 조회 (7일 이내)
	public List<UserResponseDto> getRecentUsers(int days) {
		return userRepository.findAll().stream()
				.filter(user -> user.isActive() && user.isRecentlyCreated(days))
				.map(UserResponseDto::new)
				.collect(Collectors.toList());
	}

	// 최근 활동 사용자 조회 (프로필 수정 기준)
	public List<UserResponseDto> getRecentlyActiveUsers(int days) {
		return userRepository.findAll().stream()
				.filter(user -> user.isActive() && user.isRecentlyUpdated(days))
				.map(UserResponseDto::new)
				.collect(Collectors.toList());
	}

	// 휴면 계정 처리 (90일 이상 업데이트 없음)
	@Transactional
	public int processDormantAccounts() {
		List<User> dormantUsers = userRepository.findAll().stream()
				.filter(user -> user.isActive() && user.getDaysSinceUpdated() >= 90)
				.toList();

		dormantUsers.forEach(user -> {
			log.info("휴면 계정 처리: {} ({}일 비활성)", user.getUsername(), user.getDaysSinceUpdated());
			// 필요시 알림 전송 또는 추가 처리
		});

		return dormantUsers.size();
	}

	/**
	 * username으로 사용자 조회
	 */
	public User findByUsername(String username) {
		return userAdapter.findByUsername(username);
	}

	// 사용자 상태 확인
	public boolean isUserActive(String username) {
		User user = userAdapter.findByUsername(username);
		return user.isActive();
	}

	// 사용자 가입 경과일 조회
	public long getUserAccountAge(String username) {
		User user = userAdapter.findByUsername(username);
		return user.getDaysSinceCreated();
	}
}
