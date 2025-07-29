package com.commonground.be.domain.user.entity;


import com.commonground.be.domain.user.dto.ProfileUpdateRequestDto;
import com.commonground.be.domain.user.utils.UserRole;
import com.commonground.be.global.stamps.SoftDeleteTimeStamp;
import com.commonground.be.global.security.encrpyt.EmailEncryptionConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
		name = "user",
		indexes = {
				@Index(name = "idx_username", columnList = "username"),
				@Index(name = "idx_name", columnList = "name"),
				@Index(name = "idx_email", columnList = "email"),
				@Index(name = "idx_username_name_email", columnList = "username, name, email")
		}
)
public class User extends SoftDeleteTimeStamp {

	/**
	 * 컬럼 - 연관관계 컬럼을 제외한 컬럼을 정의합니다.
	 */
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(nullable = false, unique = true)
	private Long id;


	@Column(nullable = false, unique = true)
	private String username;


	@Column(nullable = false)
	private String name;

	@Column(nullable = false, unique = true)
	private String nickname;

	@Column(nullable = false, unique = true)
	@Convert(converter = EmailEncryptionConverter.class)
	private String email;

	// 일반 유저 권한과 매니저 권한의 분리 용도
	@Column(nullable = false)
	@Enumerated(EnumType.STRING)
	private UserRole userRole;


	/**
	 * 생성자 - 약속된 형태로만 생성가능하도록 합니다.
	 */
	@Builder
	public User(String username, String name, String nickname, String email, UserRole role) {
		this.username = username;
		this.name = name;
		this.nickname = nickname;
		this.email = email;
		this.userRole = role;
	}

	/**
	 * 연관관계 - Foreign Key 값을 따로 컬럼으로 정의하지 않고 연관 관계로 정의합니다.
	 */

	/**
	 * 연관관계 편의 메소드 - 반대쪽에는 연관관계 편의 메소드가 없도록 주의합니다.
	 */

	/**
	 * 서비스 메소드 - 외부에서 엔티티를 수정할 메소드를 정의합니다. (단일 책임을 가지도록 주의합니다.)
	 */


	public void updateProfile(ProfileUpdateRequestDto requestDto) {
		this.email = requestDto.getEmail();
		this.nickname = requestDto.getNickname();
	}
	
	// 이메일 변경 시 소셜 계정들도 함께 업데이트 필요
	public boolean isEmailChanged(String newEmail) {
		return !this.email.equals(newEmail);
	}
}
