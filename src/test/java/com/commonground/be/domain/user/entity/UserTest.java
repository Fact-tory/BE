package com.commonground.be.domain.user.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.commonground.be.domain.user.dto.ProfileUpdateRequestDto;
import com.commonground.be.domain.user.utils.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * User 엔티티 단위 테스트 클래스
 * <p>
 * 이 테스트는 User 엔티티의 도메인 로직을 검증합니다. 엔티티의 비즈니스 메서드들과 SoftDeleteTimeStamp를 상속받은 생명주기 관련 메서드들을 테스트합니다.
 */
@DisplayName("User 엔티티 단위 테스트")
class UserTest {

	private User testUser;

	/**
	 * 각 테스트 실행 전 테스트용 User 엔티티를 생성합니다.
	 */
	@BeforeEach
	void setUp() {
		testUser = User.builder()
				.username("testuser")
				.name("테스트 사용자")
				.email("test@example.com")
				.role(UserRole.USER)
				.build();
	}

	@Nested
	@DisplayName("User 엔티티 생성 테스트")
	class UserCreationTest {

		@Test
		@DisplayName("빌더 패턴으로 User 생성 성공")
		void createUser_WithBuilder_ShouldCreateUserWithAllFields() {
			// Given: 사용자 생성에 필요한 모든 정보가 주어졌을 때
			String username = "newuser";
			String name = "새로운 사용자";
			String email = "new@example.com";
			UserRole role = UserRole.MANAGER;

			// When: 빌더 패턴으로 User를 생성하면
			User user = User.builder()
					.username(username)
					.name(name)
					.email(email)
					.role(role)
					.build();

			// Then: 모든 필드가 올바르게 설정되어야 함
			assertThat(user.getUsername()).isEqualTo(username);
			assertThat(user.getName()).isEqualTo(name);
			assertThat(user.getEmail()).isEqualTo(email);
			assertThat(user.getUserRole()).isEqualTo(role);
		}

		@Test
		@DisplayName("일반 사용자 권한으로 User 생성")
		void createUser_WithUserRole_ShouldHaveUserRole() {
			// Given: 일반 사용자 권한으로 사용자를 생성할 때
			User user = User.builder()
					.username("regularuser")
					.name("일반 사용자")
					.email("regular@example.com")
					.role(UserRole.USER)
					.build();

			// Then: 사용자 권한이 USER로 설정되어야 함
			assertThat(user.getUserRole()).isEqualTo(UserRole.USER);
		}

		@Test
		@DisplayName("관리자 권한으로 User 생성")
		void createUser_WithManagerRole_ShouldHaveManagerRole() {
			// Given: 관리자 권한으로 사용자를 생성할 때
			User user = User.builder()
					.username("adminuser")
					.name("관리자")
					.email("admin@example.com")
					.role(UserRole.MANAGER)
					.build();

			// Then: 사용자 권한이 MANAGER로 설정되어야 함
			assertThat(user.getUserRole()).isEqualTo(UserRole.MANAGER);
		}
	}

	@Nested
	@DisplayName("프로필 업데이트 테스트")
	class ProfileUpdateTest {

		@Test
		@DisplayName("이메일 업데이트 성공")
		void updateProfile_WithNewEmail_ShouldUpdateEmail() {
			// Given: 새로운 이메일이 포함된 프로필 업데이트 요청이 주어졌을 때
			String originalEmail = testUser.getEmail();
			String newEmail = "updated@example.com";

			ProfileUpdateRequestDto requestDto = new ProfileUpdateRequestDto();
			requestDto.setEmail(newEmail);

			// When: 프로필을 업데이트하면
			testUser.updateProfile(requestDto);

			// Then: 이메일이 변경되어야 함
			assertThat(testUser.getEmail()).isEqualTo(newEmail);
			assertThat(testUser.getEmail()).isNotEqualTo(originalEmail);
		}

		@Test
		@DisplayName("빈 이메일로 업데이트")
		void updateProfile_WithEmptyEmail_ShouldUpdateToEmptyEmail() {
			// Given: 빈 이메일이 포함된 프로필 업데이트 요청
			String emptyEmail = "";
			ProfileUpdateRequestDto requestDto = new ProfileUpdateRequestDto();
			requestDto.setEmail(emptyEmail);

			// When: 프로필을 업데이트하면
			testUser.updateProfile(requestDto);

			// Then: 이메일이 빈 문자열로 변경되어야 함
			assertThat(testUser.getEmail()).isEmpty();
		}

		@Test
		@DisplayName("null 이메일로 업데이트")
		void updateProfile_WithNullEmail_ShouldUpdateToNull() {
			// Given: null 이메일이 포함된 프로필 업데이트 요청
			ProfileUpdateRequestDto requestDto = new ProfileUpdateRequestDto();
			requestDto.setEmail(null);

			// When: 프로필을 업데이트하면
			testUser.updateProfile(requestDto);

			// Then: 이메일이 null로 변경되어야 함
			assertThat(testUser.getEmail()).isNull();
		}
	}

	@Nested
	@DisplayName("이메일 변경 감지 테스트")
	class EmailChangeDetectionTest {

		@Test
		@DisplayName("다른 이메일로 변경 시 true 반환")
		void isEmailChanged_WithDifferentEmail_ShouldReturnTrue() {
			// Given: 현재 이메일과 다른 새로운 이메일이 주어졌을 때
			String newEmail = "changed@example.com";

			// When: 이메일 변경 여부를 확인하면
			boolean isChanged = testUser.isEmailChanged(newEmail);

			// Then: true를 반환해야 함
			assertThat(isChanged).isTrue();
		}

		@Test
		@DisplayName("동일한 이메일로 변경 시 false 반환")
		void isEmailChanged_WithSameEmail_ShouldReturnFalse() {
			// Given: 현재 이메일과 동일한 이메일이 주어졌을 때
			String sameEmail = testUser.getEmail();

			// When: 이메일 변경 여부를 확인하면
			boolean isChanged = testUser.isEmailChanged(sameEmail);

			// Then: false를 반환해야 함
			assertThat(isChanged).isFalse();
		}

		@Test
		@DisplayName("null 이메일 비교 시 올바른 결과 반환")
		void isEmailChanged_WithNullEmail_ShouldHandleNullComparison() {
			// Given: null 이메일이 주어졌을 때
			String nullEmail = null;

			// When: 이메일 변경 여부를 확인하면
			boolean isChanged = testUser.isEmailChanged(nullEmail);

			// Then: true를 반환해야 함 (기존 이메일이 null이 아니므로)
			assertThat(isChanged).isTrue();
		}

		@Test
		@DisplayName("현재 이메일이 null인 경우 비교")
		void isEmailChanged_WhenCurrentEmailIsNull_ShouldHandleNullComparison() {
			// Given: 현재 이메일이 null인 사용자
			User userWithNullEmail = User.builder()
					.username("nulluser")
					.name("Null 이메일 사용자")
					.email(null)
					.role(UserRole.USER)
					.build();

			// When: 새로운 이메일과 비교하면
			boolean isChangedToValid = userWithNullEmail.isEmailChanged("new@example.com");
			boolean isChangedToNull = userWithNullEmail.isEmailChanged(null);

			// Then: 적절한 결과를 반환해야 함
			assertThat(isChangedToValid).isTrue();
			assertThat(isChangedToNull).isFalse();
		}
	}

	@Nested
	@DisplayName("SoftDeleteTimeStamp 상속 기능 테스트")
	class SoftDeleteTest {

		@Test
		@DisplayName("새로 생성된 사용자는 활성 상태여야 함")
		void newUser_ShouldBeActive() {
			// Given: 새로 생성된 사용자
			// When & Then: 활성 상태여야 함
			assertThat(testUser.isActive()).isTrue();
		}

		@Test
		@DisplayName("사용자 삭제 표시 후 비활성 상태가 되어야 함")
		void markAsDeleted_ShouldMakeUserInactive() {
			// Given: 활성 상태의 사용자
			assertThat(testUser.isActive()).isTrue();

			// When: 삭제 표시를 하면
			testUser.softDelete();

			// Then: 비활성 상태가 되어야 함
			assertThat(testUser.isActive()).isFalse();
		}

		@Test
		@DisplayName("삭제된 사용자 복원 후 활성 상태가 되어야 함")
		void restore_ShouldMakeUserActive() {
			// Given: 삭제된 사용자
			testUser.softDelete();
			assertThat(testUser.isActive()).isFalse();

			// When: 복원하면
			testUser.restore();

			// Then: 활성 상태가 되어야 함
			assertThat(testUser.isActive()).isTrue();
		}

		@Test
		@DisplayName("사용자 생성 후 경과일 조회")
		void getDaysSinceCreated_ShouldReturnValidDays() {
			// Given: 새로 생성된 사용자
			// When: 생성 후 경과일을 조회하면
			long daysSinceCreated = testUser.getDaysSinceCreated();

			// Then: 0 이상의 값이 반환되어야 함
			assertThat(daysSinceCreated).isGreaterThanOrEqualTo(0L);
		}

		@Test
		@DisplayName("사용자 수정 후 경과일 조회")
		void getDaysSinceUpdated_ShouldReturnValidDays() {
			// Given: 생성된 사용자
			// When: 수정 후 경과일을 조회하면
			long daysSinceUpdated = testUser.getDaysSinceUpdated();

			// Then: 0 이상의 값이 반환되어야 함
			assertThat(daysSinceUpdated).isGreaterThanOrEqualTo(0L);
		}

		@Test
		@DisplayName("최근 생성된 사용자 확인 (7일 이내)")
		void isRecentlyCreated_WithinSevenDays_ShouldReturnTrue() {
			// Given: 새로 생성된 사용자
			// When: 7일 이내 생성 여부를 확인하면
			boolean isRecent = testUser.isRecentlyCreated(7);

			// Then: true를 반환해야 함
			assertThat(isRecent).isTrue();
		}

		@Test
		@DisplayName("최근 업데이트된 사용자 확인 (7일 이내)")
		void isRecentlyUpdated_WithinSevenDays_ShouldReturnTrue() {
			// Given: 새로 생성된 사용자
			// When: 7일 이내 업데이트 여부를 확인하면
			boolean isRecentlyUpdated = testUser.isRecentlyUpdated(7);

			// Then: true를 반환해야 함
			assertThat(isRecentlyUpdated).isTrue();
		}

		@Test
		@DisplayName("0일 조건으로 최근 생성 확인")
		void isRecentlyCreated_WithZeroDays_ShouldReturnTrue() {
			// Given: 새로 생성된 사용자
			// When: 0일 이내 생성 여부를 확인하면
			boolean isRecent = testUser.isRecentlyCreated(0);

			// Then: true를 반환해야 함 (당일 생성이므로)
			assertThat(isRecent).isTrue();
		}
	}

	@Nested
	@DisplayName("사용자 필드 검증 테스트")
	class UserFieldValidationTest {

		@Test
		@DisplayName("사용자명이 올바르게 설정되어야 함")
		void getUsername_ShouldReturnCorrectUsername() {
			// When & Then: 사용자명이 올바르게 반환되어야 함
			assertThat(testUser.getUsername()).isEqualTo("testuser");
		}

		@Test
		@DisplayName("사용자 이름이 올바르게 설정되어야 함")
		void getName_ShouldReturnCorrectName() {
			// When & Then: 사용자 이름이 올바르게 반환되어야 함
			assertThat(testUser.getName()).isEqualTo("테스트 사용자");
		}

		@Test
		@DisplayName("이메일이 올바르게 설정되어야 함")
		void getEmail_ShouldReturnCorrectEmail() {
			// When & Then: 이메일이 올바르게 반환되어야 함
			assertThat(testUser.getEmail()).isEqualTo("test@example.com");
		}

		@Test
		@DisplayName("사용자 권한이 올바르게 설정되어야 함")
		void getUserRole_ShouldReturnCorrectRole() {
			// When & Then: 사용자 권한이 올바르게 반환되어야 함
			assertThat(testUser.getUserRole()).isEqualTo(UserRole.USER);
		}

		@Test
		@DisplayName("ID는 초기에 null이어야 함")
		void getId_ShouldBeNullInitially() {
			// When & Then: 새로 생성된 엔티티의 ID는 null이어야 함
			assertThat(testUser.getId()).isNull();
		}
	}
}