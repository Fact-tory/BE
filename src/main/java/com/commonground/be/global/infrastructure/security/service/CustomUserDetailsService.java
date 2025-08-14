package com.commonground.be.global.infrastructure.security.service;

import com.commonground.be.domain.user.entity.User;
import com.commonground.be.domain.user.service.UserService;
import com.commonground.be.global.domain.security.UserDetailsImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * Spring Security UserDetailsService 구현체 JWT 토큰에서 사용자 정보를 로드할 때 사용
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

	private final UserService userService;

	@Override
	public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
		log.debug("사용자 정보 로드 시도: {}", username);

		try {
			User user = userService.findByUsername(username);
			log.debug("사용자 정보 로드 성공: {}, 역할: {}", username, user.getUserRole());
			return new UserDetailsImpl(user);
		} catch (Exception e) {
			log.warn("사용자 정보 로드 실패: {}", username);
			throw new UsernameNotFoundException("사용자를 찾을 수 없습니다: " + username, e);
		}
	}

	/**
	 * 세션 ID를 포함한 UserDetails 생성
	 */
	public UserDetails loadUserByUsernameWithSession(String username, String sessionId)
			throws UsernameNotFoundException {
		log.debug("세션 포함 사용자 정보 로드 시도: {}, 세션: {}", username, sessionId);

		try {
			User user = userService.findByUsername(username);
			log.debug("세션 포함 사용자 정보 로드 성공: {}, 세션: {}", username, sessionId);
			return new UserDetailsImpl(user, sessionId);
		} catch (Exception e) {
			log.warn("세션 포함 사용자 정보 로드 실패: {}, 세션: {}", username, sessionId);
			throw new UsernameNotFoundException("사용자를 찾을 수 없습니다: " + username, e);
		}
	}
}