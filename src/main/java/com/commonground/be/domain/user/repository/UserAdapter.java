package com.commonground.be.domain.user.repository;

import com.commonground.be.domain.user.entity.User;
import com.commonground.be.domain.user.utils.UserIdentity;
import com.commonground.be.global.exception.UserExceptions;
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
public class UserAdapter {

	private final UserRepository userRepository;

	public User findByUsername(String username) {
		return userRepository.findByUsername(username)
				.orElseThrow(UserExceptions::userNotFound);
	}

	public List<User> findAll() {
		return userRepository.findAll();
	}

	public boolean existsByUsername(String username) {
		return userRepository.existsByUsername(username);
	}

	public User findById(Long id) {
		return userRepository.findById(id)
				.orElseThrow(UserExceptions::userNotFound);
	}

	public void save(User user) {
		userRepository.save(user);
	}

	public void isDeleted(String username) {
		User user = findByUsername(username);
		if (user.getDeletedAt() != null) {
			throw UserExceptions.userDeleted();
		}
	}

	public void delete(User user) {
		userRepository.delete(user);
	}

	public User findUserByUsernameAndNameAndEmail(UserIdentity userIdentity) {
		return userRepository.findUserByUsernameAndNameAndEmail(
						userIdentity.getUsername(), userIdentity.getName(), userIdentity.getEmail())
				.orElseThrow(
						UserExceptions::userNotFound
				);
	}

	// 10자리 랜덤한 패스워드를 생성하는 메서드
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
