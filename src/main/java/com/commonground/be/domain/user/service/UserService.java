package com.commonground.be.domain.user.service;


import static com.commonground.be.domain.user.utils.UserRole.MANAGER;
import static com.commonground.be.domain.user.utils.UserRole.USER;
import static com.commonground.be.global.response.ResponseExceptionEnum.USER_ALREADY_EXIST;
import static com.commonground.be.global.response.ResponseExceptionEnum.USER_NOT_FOUND;
import static com.commonground.be.global.response.ResponseExceptionEnum.USER_NOT_MANAGER;

import com.commonground.be.domain.user.dto.PasswordChangeRequest;
import com.commonground.be.domain.user.dto.PasswordFindRequest;
import com.commonground.be.domain.user.dto.ProfileUpdateRequestDto;
import com.commonground.be.domain.user.dto.SignupRequestDto;
import com.commonground.be.domain.user.dto.UserResponseDto;
import com.commonground.be.domain.user.entity.User;
import com.commonground.be.domain.user.repository.UserAdapter;
import com.commonground.be.domain.user.repository.UserRepository;
import com.commonground.be.global.exception.UserExceptions;
import jakarta.transaction.Transactional;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

	private final UserAdapter adapter;
	private final PasswordEncoder passwordEncoder;
	private final UserRepository userRepository;
	private final UserAdapter userAdapter;
	private final RedisTemplate<String, String> redisTemplate;
	private final AuthenticationManager authenticationManager;

	@Transactional
	public String signup(SignupRequestDto requestDto) {
		if (adapter.existsByUsername(requestDto.getUsername())) {
			throw UserExceptions.userAlreadyExists();
		}

		String encodedPassword = passwordEncoder.encode(requestDto.getPassword());

		User user = new User(requestDto.getUsername(), encodedPassword, requestDto.getName(),
				requestDto.getNickname(), requestDto.getEmail(), USER, requestDto.getAddress());

		userRepository.save(user);
		return user.getName();
	}

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
		user.updateDeletedAt(LocalDateTime.now());
		userAdapter.save(user);
	}

	@Transactional
	public void resign(Long userId) {
		User user = userAdapter.findById(userId);
		userAdapter.isDeleted(user.getUsername());
		user.updateDeletedAt(null);
		userAdapter.save(user);
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


	public String findPassword(PasswordFindRequest passwordFindRequest) {
		User user = userAdapter.findUserByUsernameAndNameAndEmail(passwordFindRequest);
		String temporaryPassword = userAdapter.generateTemporaryPassword();
		user.setPassword(passwordEncoder.encode(temporaryPassword));
		userRepository.save(user);

		// 인증 객체 갱신
		renewAuthentication(user.getUsername(), temporaryPassword);
		return temporaryPassword;
	}

	public void changePassword(PasswordChangeRequest passwordChangeRequest) {
		User user = userAdapter.findUserByUsernameAndNameAndEmail(passwordChangeRequest);

		validatePassword(passwordChangeRequest.getNewPassword(), user.getPassword());

		user.setPassword(passwordEncoder.encode(passwordChangeRequest.getNewPassword()));
		userRepository.save(user);

		// 인증 객체 갱신
		renewAuthentication(user.getUsername(), user.getPassword());
	}

	public void checkUserRole(User user) {
		if (!user.getUserRole().equals(MANAGER)) {
			throw UserExceptions.notManager();
		}
	}

	private void renewAuthentication(String username, String password) {
		Authentication authentication = authenticationManager.authenticate(
				new UsernamePasswordAuthenticationToken(username, password)
		);

		SecurityContextHolder.getContext().setAuthentication(authentication);
	}

	private void validatePassword(String newPassword, String oldPassword) {
		if (!passwordEncoder.matches(newPassword, oldPassword)) {
			throw UserExceptions.userNotFound();
		}
	}
}
