package com.commonground.be.global.domain.security;

import com.commonground.be.domain.user.utils.UserRole;
import java.util.Collection;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * 관리자 마스터 토큰을 위한 Spring Security UserDetails 구현체
 *
 * <p>이 클래스는 관리자 마스터 토큰 인증 시 Spring Security 컨텍스트에 설정되는
 * 가상의 관리자 사용자 정보를 제공합니다. 실제 데이터베이스의 사용자가 아닌 시스템 레벨의 관리자 권한을 나타냅니다.</p>
 *
 * <h3>주요 특징:</h3>
 * <ul>
 *   <li>모든 권한(ADMIN, MANAGER, USER)을 보유</li>
 *   <li>패스워드 인증 불필요 (토큰 기반)</li>
 *   <li>계정 만료/잠금 없음</li>
 *   <li>가상 사용자 ID (-1L) 사용</li>
 * </ul>
 *
 * <h3>사용 시나리오:</h3>
 * <ol>
 *   <li>관리자가 마스터 토큰으로 API 호출</li>
 *   <li>JwtAuthorizationFilter에서 토큰 검증</li>
 *   <li>이 클래스의 인스턴스를 Security Context에 설정</li>
 *   <li>모든 Controller에서 관리자 권한으로 접근 가능</li>
 * </ol>
 *
 * @author CommonGround Development Team
 * @see com.commonground.be.global.infrastructure.security.admin.AdminTokenValidator
 * @see com.commonground.be.global.infrastructure.security.filter.JwtAuthorizationFilter
 * @see org.springframework.security.core.userdetails.UserDetails
 * @since 1.0
 */
public class AdminUserDetails implements UserDetails {

	/**
	 * 시스템 관리자의 고정 사용자명
	 *
	 * <p>실제 사용자 테이블에 존재하지 않는 가상의 관리자 계정명입니다.
	 * 로그나 감사 기록에서 시스템 관리자의 활동을 식별하는 데 사용됩니다.</p>
	 */
	private static final String ADMIN_USERNAME = "SYSTEM_ADMIN";

	/**
	 * 관리자의 권한 목록을 반환합니다.
	 *
	 * <p>시스템 관리자는 모든 레벨의 권한을 보유합니다:
	 * <ul>
	 *   <li>ROLE_ADMIN: 관리자 전용 기능 접근</li>
	 *   <li>ROLE_MANAGER: 매니저 레벨 기능 접근</li>
	 *   <li>ROLE_USER: 일반 사용자 기능 접근</li>
	 * </ul>
	 * </p>
	 *
	 * @return 관리자가 보유한 모든 권한의 컬렉션
	 * @apiNote Spring Security의 권한 체크에서 사용됩니다. @PreAuthorize, @Secured 어노테이션과 연동됩니다.
	 */
	@Override
	public Collection<? extends GrantedAuthority> getAuthorities() {
		// 관리자는 시스템의 모든 권한을 보유
		return List.of(
				new SimpleGrantedAuthority("ROLE_ADMIN"),
				new SimpleGrantedAuthority("ROLE_MANAGER"),
				new SimpleGrantedAuthority("ROLE_USER")
		);
	}

	/**
	 * 관리자의 패스워드를 반환합니다.
	 *
	 * <p>관리자는 토큰 기반 인증을 사용하므로 패스워드가 필요하지 않습니다.
	 * 따라서 항상 null을 반환합니다.</p>
	 *
	 * @return 항상 null (패스워드 인증 사용 안 함)
	 */
	@Override
	public String getPassword() {
		return null; // 관리자는 패스워드 인증을 사용하지 않음
	}

	/**
	 * 관리자의 사용자명을 반환합니다.
	 *
	 * @return 시스템 관리자의 고정 사용자명 "SYSTEM_ADMIN"
	 */
	@Override
	public String getUsername() {
		return ADMIN_USERNAME;
	}

	/**
	 * 계정 만료 여부를 확인합니다.
	 *
	 * <p>시스템 관리자 계정은 만료되지 않습니다.</p>
	 *
	 * @return 항상 true (계정 만료 없음)
	 */
	@Override
	public boolean isAccountNonExpired() {
		return true;
	}

	/**
	 * 계정 잠금 여부를 확인합니다.
	 *
	 * <p>시스템 관리자 계정은 잠금되지 않습니다.</p>
	 *
	 * @return 항상 true (계정 잠금 없음)
	 */
	@Override
	public boolean isAccountNonLocked() {
		return true;
	}

	/**
	 * 자격 증명 만료 여부를 확인합니다.
	 *
	 * <p>관리자 토큰은 환경변수로 관리되므로 만료되지 않습니다.</p>
	 *
	 * @return 항상 true (자격 증명 만료 없음)
	 */
	@Override
	public boolean isCredentialsNonExpired() {
		return true;
	}

	/**
	 * 계정 활성화 여부를 확인합니다.
	 *
	 * <p>시스템 관리자 계정은 항상 활성화 상태입니다.</p>
	 *
	 * @return 항상 true (계정 활성화)
	 */
	@Override
	public boolean isEnabled() {
		return true;
	}

	/**
	 * UserDetailsImpl과 호환성을 위한 getUser() 메서드
	 *
	 * <p>기존 컨트롤러에서 UserDetails.getUser()를 호출하는 경우
	 * 관리자도 동일한 인터페이스로 접근할 수 있도록 합니다.</p>
	 *
	 * @return 가상의 관리자 사용자 객체
	 * @see AdminUser
	 */
	public AdminUser getUser() {
		return new AdminUser();
	}

	/**
	 * 시스템 관리자를 나타내는 가상 User 클래스
	 *
	 * <p>실제 User 엔티티와 동일한 인터페이스를 제공하여
	 * 컨트롤러에서 일관된 방식으로 사용자 정보에 접근할 수 있도록 합니다.</p>
	 *
	 * <h3>주요 특징:</h3>
	 * <ul>
	 *   <li>가상 ID: -1L (실제 사용자와 구분)</li>
	 *   <li>최고 권한: UserRole.MANAGER</li>
	 *   <li>고정 사용자명: SYSTEM_ADMIN</li>
	 * </ul>
	 *
	 * @author CommonGround Development Team
	 * @since 1.0
	 */
	public static class AdminUser {

		/**
		 * 관리자의 사용자명을 반환합니다.
		 *
		 * @return 시스템 관리자 사용자명 "SYSTEM_ADMIN"
		 */
		public String getUsername() {
			return ADMIN_USERNAME;
		}

		/**
		 * 관리자의 사용자 역할을 반환합니다.
		 *
		 * <p>시스템에서 가장 높은 권한인 MANAGER 역할을 반환합니다.
		 * 이를 통해 모든 비즈니스 로직에서 최고 권한으로 처리됩니다.</p>
		 *
		 * @return UserRole.MANAGER (최고 권한)
		 */
		public UserRole getUserRole() {
			return UserRole.MANAGER; // 가장 높은 권한
		}

		/**
		 * 관리자의 고유 ID를 반환합니다.
		 *
		 * <p>실제 사용자 테이블의 ID와 충돌하지 않도록 음수 값(-1L)을 사용합니다.
		 * 이를 통해 로그나 감사 기록에서 시스템 관리자의 활동을 식별할 수 있습니다.</p>
		 *
		 * @return -1L (가상 관리자 ID)
		 */
		public Long getId() {
			return -1L; // 가상 ID - 실제 사용자와 구분하기 위한 음수 값
		}

		/**
		 * 관리자 정보의 문자열 표현을 반환합니다.
		 *
		 * <p>디버깅이나 로깅 목적으로 사용됩니다.</p>
		 *
		 * @return 관리자 정보를 포함한 문자열
		 */
		@Override
		public String toString() {
			return String.format("AdminUser{id=%d, username='%s', role=%s}",
					getId(), getUsername(), getUserRole());
		}
	}

	/**
	 * AdminUserDetails 정보의 문자열 표현을 반환합니다.
	 *
	 * <p>디버깅이나 로깅 목적으로 사용됩니다.</p>
	 *
	 * @return AdminUserDetails 정보를 포함한 문자열
	 */
	@Override
	public String toString() {
		return String.format("AdminUserDetails{username='%s', authorities=%s, enabled=%s}",
				getUsername(), getAuthorities(), isEnabled());
	}
}