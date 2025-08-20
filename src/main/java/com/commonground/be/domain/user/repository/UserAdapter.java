package com.commonground.be.domain.user.repository;

import com.commonground.be.domain.user.entity.User;
import com.commonground.be.domain.user.utils.UserIdentity;
import com.commonground.be.global.application.exception.UserExceptions;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserAdapter implements UserRepositoryInterface {

	private final UserRepository userRepository;

	@Override
	public User findByUsername(String username) {
		return userRepository.findByUsernameAndDeletedAtIsNull(username)
				.orElseThrow(UserExceptions::userNotFound);
	}
	
	@Override
	public User findByEmail(String email) {
		return userRepository.findByEmailAndDeletedAtIsNull(email)
				.orElseThrow(UserExceptions::userNotFound);
	}

	@Override
	public List<User> findAll() {
		return userRepository.findAll();
	}

	@Override
	public boolean existsByUsername(String username) {
		return userRepository.existsByUsernameAndDeletedAtIsNull(username);
	}

	@Override
	public User findById(Long id) {
		return userRepository.findByIdAndDeletedAtIsNull(id)
				.orElseThrow(UserExceptions::userNotFound);
	}

	@Override
	public User save(User user) {
		return userRepository.save(user);
	}

	@Override
	public void validateNotDeleted(String username) {
		User user = findByUsername(username);
		if (user.getDeletedAt() != null) {
			throw UserExceptions.userDeleted();
		}
	}

	@Override
	public void delete(User user) {
		userRepository.delete(user);
	}

	@Override
	public User findByUserIdentity(UserIdentity userIdentity) {
		return userRepository.findUserByUsernameAndNameAndEmailAndDeletedAtIsNull(
						userIdentity.getUsername(), userIdentity.getName(), userIdentity.getEmail())
				.orElseThrow(
						UserExceptions::userNotFound
				);
	}

	@Override
	public String generateTemporaryPassword() {
		int length = 10;
		String upperCaseLetters = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
		String lowerCaseLetters = "abcdefghijklmnopqrstuvwxyz";
		String digits = "0123456789";
		String specialCharacters = "!@#$%^&*()";
		String allCharacters = upperCaseLetters + lowerCaseLetters + digits + specialCharacters;

		Random random = new Random();
		StringBuilder password = new StringBuilder();

		// 각 종류에서 최소 하나의 문자 추가
		password.append(upperCaseLetters.charAt(random.nextInt(upperCaseLetters.length())));
		password.append(lowerCaseLetters.charAt(random.nextInt(lowerCaseLetters.length())));
		password.append(digits.charAt(random.nextInt(digits.length())));
		password.append(specialCharacters.charAt(random.nextInt(specialCharacters.length())));

		// 나머지 자리수를 랜덤하게 채움
		for (int i = 4; i < length; i++) {
			password.append(allCharacters.charAt(random.nextInt(allCharacters.length())));
		}

		// 패스워드를 섞어서 반환
		return shuffleString(password.toString());
	}

	/**
	 * 테스트용 추가 메서드들
	 */
	public User findUserByUsernameAndNameAndEmail(UserIdentity userIdentity) {
		return findByUserIdentity(userIdentity);
	}
	
	public void isDeleted(String username) {
		validateNotDeleted(username);
	}

	// 문자열을 랜덤하게 섞는 메서드
	private String shuffleString(String input) {
		List<Character> characters = input.chars()
				.mapToObj(c -> (char) c)
				.collect(Collectors.toList());
		Collections.shuffle(characters);
		StringBuilder shuffledString = new StringBuilder();
		for (char c : characters) {
			shuffledString.append(c);
		}
		return shuffledString.toString();
	}
}
