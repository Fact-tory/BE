package com.commonground.be.domain.social.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.commonground.be.domain.social.entity.SocialAccount.SocialProvider;
import com.commonground.be.domain.user.entity.User;
import com.commonground.be.domain.user.utils.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * SocialAccount 엔티티 단위 테스트 클래스
 * <p>
 * 이 테스트는 SocialAccount 엔티티의 도메인 로직을 검증합니다. 소셜 계정 생성, 이메일 동기화, 연관관계 등의 기능을 테스트합니다.
 */
@DisplayName("SocialAccount 엔티티 단위 테스트")
class SocialAccountTest {

	private User testUser;
	private SocialAccount kakaoAccount;
	private SocialAccount googleAccount;

	/**
	 * 각 테스트 실행 전 테스트용 User 엔티티와 SocialAccount 엔티티들을 생성합니다.
	 */
	@BeforeEach
	void setUp() {
		// 테스트용 User 생성
		testUser = User.builder()
				.username("testuser")
				.name("테스트 사용자")
				.email("test@example.com")
				.role(UserRole.USER)
				.build();

		// 테스트용 Kakao SocialAccount 생성
		kakaoAccount = SocialAccount.builder()
				.user(testUser)
				.provider(SocialProvider.KAKAO)
				.socialId("12345")
				.email("kakao@example.com")
				.socialUsername("카카오사용자")
				.build();

		// 테스트용 Google SocialAccount 생성
		googleAccount = SocialAccount.builder()
				.user(testUser)
				.provider(SocialProvider.GOOGLE)
				.socialId("67890")
				.email("google@example.com")
				.socialUsername("구글사용자")
				.build();
	}

	@Nested
	@DisplayName("SocialAccount 엔티티 생성 테스트")
	class SocialAccountCreationTest {

		@Test
		@DisplayName("빌더 패턴으로 Kakao SocialAccount 생성 성공")
		void createKakaoSocialAccount_WithBuilder_ShouldCreateWithAllFields() {
			// Given: Kakao 소셜 계정 생성에 필요한 모든 정보가 주어졌을 때
			User user = testUser;
			SocialProvider provider = SocialProvider.KAKAO;
			String socialId = "kakao123";
			String email = "newkakao@example.com";
			String socialUsername = "새로운카카오사용자";

			// When: 빌더 패턴으로 SocialAccount를 생성하면
			SocialAccount socialAccount = SocialAccount.builder()
					.user(user)
					.provider(provider)
					.socialId(socialId)
					.email(email)
					.socialUsername(socialUsername)
					.build();

			// Then: 모든 필드가 올바르게 설정되어야 함
			assertThat(socialAccount.getUser()).isEqualTo(user);
			assertThat(socialAccount.getProvider()).isEqualTo(provider);
			assertThat(socialAccount.getSocialId()).isEqualTo(socialId);
			assertThat(socialAccount.getEmail()).isEqualTo(email);
			assertThat(socialAccount.getSocialUsername()).isEqualTo(socialUsername);
		}

		@Test
		@DisplayName("빌더 패턴으로 Google SocialAccount 생성 성공")
		void createGoogleSocialAccount_WithBuilder_ShouldCreateWithAllFields() {
			// Given: Google 소셜 계정 생성에 필요한 모든 정보가 주어졌을 때
			User user = testUser;
			SocialProvider provider = SocialProvider.GOOGLE;
			String socialId = "google123";
			String email = "newgoogle@example.com";
			String socialUsername = "새로운구글사용자";

			// When: 빌더 패턴으로 SocialAccount를 생성하면
			SocialAccount socialAccount = SocialAccount.builder()
					.user(user)
					.provider(provider)
					.socialId(socialId)
					.email(email)
					.socialUsername(socialUsername)
					.build();

			// Then: 모든 필드가 올바르게 설정되어야 함
			assertThat(socialAccount.getUser()).isEqualTo(user);
			assertThat(socialAccount.getProvider()).isEqualTo(provider);
			assertThat(socialAccount.getSocialId()).isEqualTo(socialId);
			assertThat(socialAccount.getEmail()).isEqualTo(email);
			assertThat(socialAccount.getSocialUsername()).isEqualTo(socialUsername);
		}

		@Test
		@DisplayName("모든 SocialProvider 타입으로 생성 가능")
		void createSocialAccount_WithAllProviderTypes_ShouldCreateSuccessfully() {
			// Given: 모든 소셜 프로바이더 타입
			SocialProvider[] providers = {
					SocialProvider.KAKAO,
					SocialProvider.GOOGLE,
					SocialProvider.NAVER,
					SocialProvider.FACEBOOK
			};

			// When & Then: 각 프로바이더로 소셜 계정 생성이 가능해야 함
			for (SocialProvider provider : providers) {
				SocialAccount account = SocialAccount.builder()
						.user(testUser)
						.provider(provider)
						.socialId("test-" + provider.name().toLowerCase())
						.email("test@" + provider.name().toLowerCase() + ".com")
						.socialUsername("test-user")
						.build();

				assertThat(account.getProvider()).isEqualTo(provider);
				assertThat(account.getSocialId()).contains(provider.name().toLowerCase());
			}
		}

		@Test
		@DisplayName("socialUsername이 null인 계정 생성")
		void createSocialAccount_WithNullSocialUsername_ShouldCreateSuccessfully() {
			// Given: socialUsername이 null인 계정 생성 요청
			// When: socialUsername 없이 SocialAccount를 생성하면
			SocialAccount account = SocialAccount.builder()
					.user(testUser)
					.provider(SocialProvider.KAKAO)
					.socialId("test123")
					.email("test@example.com")
					.socialUsername(null)
					.build();

			// Then: socialUsername이 null로 설정되어야 함
			assertThat(account.getSocialUsername()).isNull();
			assertThat(account.getUser()).isEqualTo(testUser);
			assertThat(account.getProvider()).isEqualTo(SocialProvider.KAKAO);
		}
	}

	@Nested
	@DisplayName("이메일 업데이트 테스트")
	class EmailUpdateTest {

		@Test
		@DisplayName("이메일 업데이트 성공")
		void updateEmail_WithNewEmail_ShouldUpdateEmail() {
			// Given: 기존 이메일이 설정된 소셜 계정
			String originalEmail = kakaoAccount.getEmail();
			String newEmail = "updated@example.com";

			// When: 이메일을 업데이트하면
			kakaoAccount.updateEmail(newEmail);

			// Then: 이메일이 변경되어야 함
			assertThat(kakaoAccount.getEmail()).isEqualTo(newEmail);
			assertThat(kakaoAccount.getEmail()).isNotEqualTo(originalEmail);
		}

		@Test
		@DisplayName("빈 문자열로 이메일 업데이트")
		void updateEmail_WithEmptyString_ShouldUpdateToEmptyString() {
			// Given: 기존 이메일이 설정된 소셜 계정
			String emptyEmail = "";

			// When: 빈 문자열로 이메일을 업데이트하면
			kakaoAccount.updateEmail(emptyEmail);

			// Then: 이메일이 빈 문자열로 변경되어야 함
			assertThat(kakaoAccount.getEmail()).isEmpty();
		}

		@Test
		@DisplayName("null로 이메일 업데이트")
		void updateEmail_WithNull_ShouldUpdateToNull() {
			// Given: 기존 이메일이 설정된 소셜 계정
			// When: null로 이메일을 업데이트하면
			kakaoAccount.updateEmail(null);

			// Then: 이메일이 null로 변경되어야 함
			assertThat(kakaoAccount.getEmail()).isNull();
		}

		@Test
		@DisplayName("동일한 이메일로 업데이트")
		void updateEmail_WithSameEmail_ShouldRemainSame() {
			// Given: 기존 이메일이 설정된 소셜 계정
			String currentEmail = kakaoAccount.getEmail();

			// When: 동일한 이메일로 업데이트하면
			kakaoAccount.updateEmail(currentEmail);

			// Then: 이메일이 그대로 유지되어야 함
			assertThat(kakaoAccount.getEmail()).isEqualTo(currentEmail);
		}

		@Test
		@DisplayName("여러 번 이메일 업데이트")
		void updateEmail_MultipleTimes_ShouldKeepLatestEmail() {
			// Given: 기존 소셜 계정
			String firstEmail = "first@example.com";
			String secondEmail = "second@example.com";
			String thirdEmail = "third@example.com";

			// When: 여러 번 이메일을 업데이트하면
			kakaoAccount.updateEmail(firstEmail);
			kakaoAccount.updateEmail(secondEmail);
			kakaoAccount.updateEmail(thirdEmail);

			// Then: 마지막으로 설정한 이메일이 유지되어야 함
			assertThat(kakaoAccount.getEmail()).isEqualTo(thirdEmail);
		}
	}

	@Nested
	@DisplayName("User 연관관계 테스트")
	class UserRelationshipTest {

		@Test
		@DisplayName("SocialAccount에서 User 참조 확인")
		void socialAccount_ShouldHaveCorrectUserReference() {
			// When & Then: SocialAccount가 올바른 User를 참조해야 함
			assertThat(kakaoAccount.getUser()).isEqualTo(testUser);
			assertThat(googleAccount.getUser()).isEqualTo(testUser);
		}

		@Test
		@DisplayName("같은 User에 대한 여러 SocialAccount 생성")
		void multipleAccountsForSameUser_ShouldHaveSameUserReference() {
			// Given: 동일한 User에 대한 여러 소셜 계정
			SocialAccount naverAccount = SocialAccount.builder()
					.user(testUser)
					.provider(SocialProvider.NAVER)
					.socialId("naver123")
					.email("naver@example.com")
					.socialUsername("네이버사용자")
					.build();

			// When & Then: 모든 계정이 동일한 User를 참조해야 함
			assertThat(kakaoAccount.getUser()).isEqualTo(testUser);
			assertThat(googleAccount.getUser()).isEqualTo(testUser);
			assertThat(naverAccount.getUser()).isEqualTo(testUser);
		}

		@Test
		@DisplayName("다른 User에 대한 SocialAccount 생성")
		void socialAccountForDifferentUser_ShouldHaveDifferentUserReference() {
			// Given: 다른 User 생성
			User anotherUser = User.builder()
					.username("anotheruser")
					.name("다른 사용자")
					.email("another@example.com")
					.role(UserRole.USER)
					.build();

			SocialAccount anotherAccount = SocialAccount.builder()
					.user(anotherUser)
					.provider(SocialProvider.KAKAO)
					.socialId("another123")
					.email("another.kakao@example.com")
					.socialUsername("다른카카오사용자")
					.build();

			// When & Then: 다른 User를 참조해야 함
			assertThat(anotherAccount.getUser()).isEqualTo(anotherUser);
			assertThat(anotherAccount.getUser()).isNotEqualTo(testUser);
		}
	}

	@Nested
	@DisplayName("SocialProvider 열거형 테스트")
	class SocialProviderTest {

		@Test
		@DisplayName("모든 SocialProvider 값 확인")
		void socialProvider_ShouldHaveAllExpectedValues() {
			// Given: SocialProvider 열거형
			// When & Then: 모든 예상되는 값들이 존재해야 함
			assertThat(SocialProvider.values()).containsExactlyInAnyOrder(
					SocialProvider.KAKAO,
					SocialProvider.GOOGLE,
					SocialProvider.NAVER,
					SocialProvider.FACEBOOK
			);
		}

		@Test
		@DisplayName("SocialProvider 문자열 변환 확인")
		void socialProvider_ShouldConvertToStringCorrectly() {
			// When & Then: 각 프로바이더가 올바른 문자열로 변환되어야 함
			assertThat(SocialProvider.KAKAO.toString()).isEqualTo("KAKAO");
			assertThat(SocialProvider.GOOGLE.toString()).isEqualTo("GOOGLE");
			assertThat(SocialProvider.NAVER.toString()).isEqualTo("NAVER");
			assertThat(SocialProvider.FACEBOOK.toString()).isEqualTo("FACEBOOK");
		}

		@Test
		@DisplayName("SocialProvider valueOf 확인")
		void socialProvider_ShouldParseFromString() {
			// When & Then: 문자열로부터 올바른 enums 값을 파싱해야 함
			assertThat(SocialProvider.valueOf("KAKAO")).isEqualTo(SocialProvider.KAKAO);
			assertThat(SocialProvider.valueOf("GOOGLE")).isEqualTo(SocialProvider.GOOGLE);
			assertThat(SocialProvider.valueOf("NAVER")).isEqualTo(SocialProvider.NAVER);
			assertThat(SocialProvider.valueOf("FACEBOOK")).isEqualTo(SocialProvider.FACEBOOK);
		}
	}

	@Nested
	@DisplayName("필드 접근자 테스트")
	class FieldAccessorTest {

		@Test
		@DisplayName("모든 필드 getter 메서드 확인")
		void allGetters_ShouldReturnCorrectValues() {
			// When & Then: 모든 getter가 올바른 값을 반환해야 함
			assertThat(kakaoAccount.getUser()).isEqualTo(testUser);
			assertThat(kakaoAccount.getProvider()).isEqualTo(SocialProvider.KAKAO);
			assertThat(kakaoAccount.getSocialId()).isEqualTo("12345");
			assertThat(kakaoAccount.getEmail()).isEqualTo("kakao@example.com");
			assertThat(kakaoAccount.getSocialUsername()).isEqualTo("카카오사용자");
		}

		@Test
		@DisplayName("ID는 초기에 null이어야 함")
		void getId_ShouldBeNullInitially() {
			// When & Then: 새로 생성된 엔티티의 ID는 null이어야 함
			assertThat(kakaoAccount.getId()).isNull();
			assertThat(googleAccount.getId()).isNull();
		}

		@Test
		@DisplayName("소셜 ID와 이메일은 다를 수 있음")
		void socialIdAndEmail_CanBeDifferent() {
			// When & Then: 소셜 ID와 이메일은 독립적인 값이어야 함
			assertThat(kakaoAccount.getSocialId()).isNotEqualTo(kakaoAccount.getEmail());
			assertThat(googleAccount.getSocialId()).isNotEqualTo(googleAccount.getEmail());
		}
	}

	@Nested
	@DisplayName("TimeStamp 상속 기능 테스트")
	class TimeStampTest {

		@Test
		@DisplayName("새로 생성된 SocialAccount는 생성일시가 설정되어야 함")
		void newSocialAccount_ShouldHaveCreatedDate() {
			// When & Then: 생성일시 관련 메서드들이 유효한 값을 반환해야 함
			long daysSinceCreated = kakaoAccount.getDaysSinceCreated();
			long daysSinceUpdated = kakaoAccount.getDaysSinceUpdated();

			assertThat(daysSinceCreated).isGreaterThanOrEqualTo(0L);
			assertThat(daysSinceUpdated).isGreaterThanOrEqualTo(0L);
		}

		@Test
		@DisplayName("최근 생성된 SocialAccount 확인")
		void isRecentlyCreated_ShouldReturnTrue() {
			// When & Then: 최근 생성된 계정으로 인식되어야 함
			boolean isRecentlyCreated = kakaoAccount.isRecentlyCreated(7);
			boolean isRecentlyUpdated = kakaoAccount.isRecentlyUpdated(7);

			assertThat(isRecentlyCreated).isTrue();
			assertThat(isRecentlyUpdated).isTrue();
		}
	}
}