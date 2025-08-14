package com.commonground.be.global.domain.security;

import com.commonground.be.domain.user.entity.User;
import java.util.Collection;
import java.util.Collections;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * Spring Security UserDetails 구현체 JWT 토큰 기반 인증에서 사용자 정보를 담는 객체
 */
@Getter
@RequiredArgsConstructor
public class UserDetailsImpl implements UserDetails {

	private final User user;
	private final String sessionId; // 세션 ID (선택적)

	public UserDetailsImpl(User user) {
		this.user = user;
		this.sessionId = null;
	}

	@Override
	public Collection<? extends GrantedAuthority> getAuthorities() {
		return Collections.singleton(
				new SimpleGrantedAuthority("ROLE_" + user.getUserRole().name())
		);
	}

	@Override
	public String getPassword() {
		// 소셜 로그인 전용이므로 패스워드 없음
		return "";
	}

	@Override
	public String getUsername() {
		return user.getUsername();
	}

	@Override
	public boolean isAccountNonExpired() {
		return !user.isDeleted(); // soft delete 상태 확인
	}

	@Override
	public boolean isAccountNonLocked() {
		return !user.isDeleted();
	}

	@Override
	public boolean isCredentialsNonExpired() {
		return true; // JWT 토큰에서 만료 관리
	}

	@Override
	public boolean isEnabled() {
		return !user.isDeleted();
	}

	/**
	 * 사용자 ID 반환
	 */
	public Long getUserId() {
		return user.getId();
	}

	/**
	 * 사용자 역할 반환
	 */
	public String getRole() {
		return user.getUserRole().name();
	}
}