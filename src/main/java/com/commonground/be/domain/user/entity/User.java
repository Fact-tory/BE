package com.commonground.be.domain.user.entity;


import com.commonground.be.domain.user.dto.ProfileUpdateRequestDto;
import com.commonground.be.domain.user.utils.UserRole;
import com.commonground.be.global.stamps.TimeStamp;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
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
public class User extends TimeStamp {

	/**
	 * 컬럼 - 연관관계 컬럼을 제외한 컬럼을 정의합니다.
	 */
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(nullable = false, unique = true)
	private Long id;

	@Column(unique = true)
	private Long kakaoId;

	@Column(nullable = false, unique = true)
	private String username;

	@Column(nullable = false)
	private String password;

	@Column(nullable = false)
	private String name;

	@Column(nullable = false, unique = true)
	private String nickname;

	@Column(nullable = false, unique = true)
	private String email;

	// 일반 유저 권한과 매니저 권한의 분리 용도
	@Column(nullable = false)
	@Enumerated(EnumType.STRING)
	private UserRole userRole;

	// E-Commerce Domain에서 배송을 위한 주소, 후에 배송 시스템 추가 후 이관
	@Column(nullable = false)
	private String address;

	// soft delete 방식과 같다고 할 수 있습니다.
	private LocalDateTime deletedAt;


	/**
	 * 생성자 - 약속된 형태로만 생성가능하도록 합니다.
	 */
	@Builder
	public User(String username, String encodedPassword, String name, String nickname, String email,
			UserRole role, String address, Long kakaoId) {
		this.username = username;
		this.password = encodedPassword;
		this.name = name;
		this.nickname = nickname;
		this.email = email;
		this.userRole = role;
		this.address = address;
		this.kakaoId = kakaoId;
	}

	public User(String username, String encodedPassword, String name, String nickname, String email,
			UserRole role, String address) {
		this.username = username;
		this.password = encodedPassword;
		this.name = name;
		this.nickname = nickname;
		this.email = email;
		this.userRole = role;
		this.address = address;
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

	public void updateDeletedAt(LocalDateTime date) {
		this.deletedAt = date;
	}

	public void updateProfile(ProfileUpdateRequestDto requestDto) {
		this.email = requestDto.getEmail();
		this.nickname = requestDto.getNickname();
		this.address = requestDto.getAddress();
	}
}
