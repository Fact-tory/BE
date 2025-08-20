package com.commonground.be.domain.user.service;


import static com.commonground.be.domain.user.utils.UserRole.MANAGER;
import static com.commonground.be.domain.user.utils.UserRole.USER;

import com.commonground.be.domain.user.dto.ProfileUpdateRequestDto;
import com.commonground.be.domain.user.dto.UserResponseDto;
import com.commonground.be.domain.user.entity.User;
import com.commonground.be.domain.user.repository.UserAdapter;
import com.commonground.be.global.application.exception.UserExceptions;
import com.commonground.be.global.infrastructure.security.jwt.TokenManager;
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
public class UserService implements UserServiceInterface {

	private final UserAdapter userAdapter;
	private final RedisTemplate<String, String> redisTemplate;
	private final TokenManager tokenManager;


	@Override
	@Transactional
	public void logout(String username) {
		redisTemplate.delete(username);
	}

	@Override
	public UserResponseDto getUser(Long userId) {
		User user = userAdapter.findById(userId);
		userAdapter.validateNotDeleted(user.getUsername());
		return new UserResponseDto(user);
	}

	@Override
	@Transactional
	public void withdraw(User user) {
		userAdapter.validateNotDeleted(user.getUsername());

		// SoftDeleteTimeStamp 편의 메서드 활용
		user.softDelete();
		userAdapter.save(user);

		// 계정 탈퇴 시 모든 토큰 무효화 (TokenManager 활용)
		tokenManager.invalidateAllUserTokens(user.getUsername());
		log.info("계정 탈퇴로 인한 토큰 무효화: {}", user.getUsername());
	}

	@Transactional
	public void resign(Long userId) {
		User user = userAdapter.findById(userId);
		// 사용자 삭제 상태 확인
		if (user.getDeletedAt() == null) {
			throw new IllegalStateException("이미 활성화된 사용자입니다.");
		}

		// SoftDeleteTimeStamp 편의 메서드 활용 - 복원
		user.restore();
		userAdapter.save(user);
		log.info("사용자 복원 완료: {}", user.getUsername());
	}

	@Override
	public UserResponseDto updateProfile(Long userId, ProfileUpdateRequestDto requestDto) {
		User user = userAdapter.findById(userId);
		userAdapter.validateNotDeleted(user.getUsername());
		user.updateProfile(requestDto);
		userAdapter.save(user);
		return new UserResponseDto(user);
	}
	
	@Transactional
	public void update(User user, ProfileUpdateRequestDto requestDto) {
		user.updateProfile(requestDto);
		userAdapter.save(user);
	}

	@Override
	public List<UserResponseDto> getAllUsers() {
		return userAdapter.findAll().stream()  // 모든 사용자 목록을 스트림으로 변환
				.map(UserResponseDto::new)  // 각 User 객체를 UserResponseDto로 변환
				.collect(Collectors.toList());  // 결과를 리스트로 수집
	}
	
	public List<UserResponseDto> getUserAllList(User user) {
		checkUserRole(user);
		return getAllUsers();
	}

	/**
	 * 관리자 전용: 권한 체크 없이 모든 사용자 조회 (삭제된 사용자 포함)
	 */
	public List<UserResponseDto> getAllUsersForAdmin() {
		return userAdapter.findAll().stream() // 삭제된 사용자도 포함
				.map(UserResponseDto::new)
				.collect(Collectors.toList());
	}

	/**
	 * 관리자 전용: 특정 사용자 강제 탈퇴
	 */
	@Transactional
	public void forceWithdrawUser(Long userId) {
		User user = userAdapter.findById(userId);
		user.softDelete();
		userAdapter.save(user);
		
		// 해당 사용자의 모든 토큰 무효화
		tokenManager.invalidateAllUserTokens(user.getUsername());
		log.info("관리자에 의한 강제 탈퇴: {}", user.getUsername());
	}

	/**
	 * 관리자 전용: 활성 사용자 수 조회
	 */
	public long getActiveUserCount() {
		return userAdapter.findAll().stream()
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
		return userAdapter.findAll().stream()
				.filter(user -> user.isActive() && user.isRecentlyCreated(days))
				.map(UserResponseDto::new)
				.collect(Collectors.toList());
	}

	// 최근 활동 사용자 조회 (프로필 수정 기준)
	public List<UserResponseDto> getRecentlyActiveUsers(int days) {
		return userAdapter.findAll().stream()
				.filter(user -> user.isActive() && user.isRecentlyUpdated(days))
				.map(UserResponseDto::new)
				.collect(Collectors.toList());
	}

	// 휴면 계정 처리 (90일 이상 업데이트 없음)
	@Transactional
	public int processDormantAccounts() {
		List<User> dormantUsers = userAdapter.findAll().stream()
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
	@Override
	public User findByUsername(String username) {
		return userAdapter.findByUsername(username);
	}
	
	/**
	 * email으로 사용자 조회
	 */
	public User findByEmail(String email) {
		return userAdapter.findByEmail(email);
	}
	
	/**
	 * 사용자 저장
	 */
	public User save(User user) {
		return userAdapter.save(user);
	}
	
	/**
	 * 이메일과 이름 조합으로 사용자 조회 (암호화된 필드 고려)
	 * JWT 토큰의 이메일/이름과 DB의 암호화된 데이터를 매칭
	 */
	public User findByEmailAndName(String email, String name) {
		// 먼저 모든 사용자를 조회하고 암호화된 이메일을 복호화하여 비교
		List<User> allUsers = userAdapter.findAll();
		
		for (User user : allUsers) {
			// 사용자가 활성 상태인지 확인 (삭제되지 않음)
			if (user.getDeletedAt() != null) {
				continue;
			}
			
			// 이메일과 이름이 모두 일치하는 사용자 찾기
			try {
				String decryptedEmail = user.getEmail(); // 자동으로 복호화됨
				if (email.equals(decryptedEmail) && name.equals(user.getName())) {
					return user;
				}
			} catch (Exception e) {
				log.warn("이메일 복호화 실패 - userId: {}", user.getId());
				continue;
			}
		}
		
		throw UserExceptions.userNotFound();
	}
	
	@Override
	public void promoteToManager(Long userId) {
		User user = userAdapter.findById(userId);
		userAdapter.validateNotDeleted(user.getUsername());
		user.promoteToManager();
		userAdapter.save(user);
		log.info("사용자 관리자 승격: {}", user.getUsername());
	}
	
	@Override
	public void demoteToUser(Long userId) {
		User user = userAdapter.findById(userId);
		userAdapter.validateNotDeleted(user.getUsername());
		user.demoteToUser();
		userAdapter.save(user);
		log.info("사용자 관리자 권한 해제: {}", user.getUsername());
	}
	
	@Override
	public String generateAndSendTemporaryPassword(String username) {
		User user = userAdapter.findByUsername(username);
		userAdapter.validateNotDeleted(username);
		
		String tempPassword = userAdapter.generateTemporaryPassword();
		// TODO: 실제 이메일 전송 또는 SMS 전송 구현
		log.info("임시 비밀번호 생성 - username: {}", username);
		
		return tempPassword;
	}

}
