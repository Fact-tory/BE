package com.commonground.be.domain.user.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;

import com.commonground.be.domain.user.entity.User;
import com.commonground.be.domain.user.utils.UserIdentity;
import com.commonground.be.domain.user.utils.UserRole;
import com.commonground.be.global.application.exception.UserExceptions;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * UserAdapter 단위 테스트 클래스
 * 
 * 이 테스트는 UserAdapter의 순수 함수와 비즈니스 로직을 검증합니다.
 * 데이터베이스 조회/저장 로직과 임시 비밀번호 생성, 문자열 셔플 등의
 * 유틸리티 메서드들을 테스트합니다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UserAdapter 단위 테스트")
class UserAdapterTest {

    @InjectMocks
    private UserAdapter userAdapter;

    @Mock
    private UserRepository userRepository;

    private User testUser;
    private User deletedUser;

    /**
     * 각 테스트 실행 전 테스트용 User 엔티티들을 생성합니다.
     */
    @BeforeEach
    void setUp() {
        // 일반 활성 사용자 생성
        testUser = User.builder()
                .username("testuser")
                .name("테스트 사용자")
                .email("test@example.com")
                .role(UserRole.USER)
                .build();

        // 삭제된 사용자 생성 (리플렉션을 통해 deletedAt 설정)
        deletedUser = User.builder()
                .username("deleteduser")
                .name("삭제된 사용자")
                .email("deleted@example.com")
                .role(UserRole.USER)
                .build();
        deletedUser.softDelete(); // SoftDeleteTimeStamp의 편의 메서드 사용
    }

    @Nested
    @DisplayName("사용자 조회 테스트")
    class UserLookupTest {

        @Test
        @DisplayName("username으로 사용자 조회 성공")
        void findByUsername_WithValidUsername_ShouldReturnUser() {
            // Given: 유효한 username과 Mock 설정
            String username = "testuser";
            when(userRepository.findByUsername(username)).thenReturn(Optional.of(testUser));

            // When: username으로 사용자를 조회하면
            User result = userAdapter.findByUsername(username);

            // Then: 올바른 사용자가 반환되어야 함
            assertThat(result).isEqualTo(testUser);
            verify(userRepository).findByUsername(username);
        }

        @Test
        @DisplayName("존재하지 않는 username으로 조회 시 예외 발생")
        void findByUsername_WithNonExistentUsername_ShouldThrowException() {
            // Given: 존재하지 않는 username
            String username = "nonexistent";
            when(userRepository.findByUsername(username)).thenReturn(Optional.empty());

            // When & Then: UserExceptions.userNotFound() 예외가 발생해야 함
            assertThatThrownBy(() -> userAdapter.findByUsername(username))
                    .isInstanceOf(RuntimeException.class);
            verify(userRepository).findByUsername(username);
        }

        @Test
        @DisplayName("ID로 사용자 조회 성공")
        void findById_WithValidId_ShouldReturnUser() {
            // Given: 유효한 ID와 Mock 설정
            Long userId = 1L;
            when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));

            // When: ID로 사용자를 조회하면
            User result = userAdapter.findById(userId);

            // Then: 올바른 사용자가 반환되어야 함
            assertThat(result).isEqualTo(testUser);
            verify(userRepository).findById(userId);
        }

        @Test
        @DisplayName("존재하지 않는 ID로 조회 시 예외 발생")
        void findById_WithNonExistentId_ShouldThrowException() {
            // Given: 존재하지 않는 ID
            Long userId = 999L;
            when(userRepository.findById(userId)).thenReturn(Optional.empty());

            // When & Then: UserExceptions.userNotFound() 예외가 발생해야 함
            assertThatThrownBy(() -> userAdapter.findById(userId))
                    .isInstanceOf(RuntimeException.class);
            verify(userRepository).findById(userId);
        }

        @Test
        @DisplayName("복합 조건으로 사용자 조회 성공")
        void findUserByUsernameAndNameAndEmail_WithValidIdentity_ShouldReturnUser() {
            // Given: 유효한 UserIdentity Mock 객체와 Mock 설정
            UserIdentity userIdentity = mock(UserIdentity.class);
            when(userIdentity.getUsername()).thenReturn("testuser");
            when(userIdentity.getName()).thenReturn("테스트 사용자");
            when(userIdentity.getEmail()).thenReturn("test@example.com");
            when(userRepository.findUserByUsernameAndNameAndEmailAndDeletedAtIsNull(
                    userIdentity.getUsername(), 
                    userIdentity.getName(), 
                    userIdentity.getEmail()))
                    .thenReturn(Optional.of(testUser));

            // When: 복합 조건으로 사용자를 조회하면
            User result = userAdapter.findUserByUsernameAndNameAndEmail(userIdentity);

            // Then: 올바른 사용자가 반환되어야 함
            assertThat(result).isEqualTo(testUser);
            verify(userRepository).findUserByUsernameAndNameAndEmailAndDeletedAtIsNull(
                    userIdentity.getUsername(), 
                    userIdentity.getName(), 
                    userIdentity.getEmail());
        }
    }

    @Nested
    @DisplayName("사용자 존재 여부 확인 테스트")
    class UserExistenceTest {

        @Test
        @DisplayName("존재하는 username 확인 시 true 반환")
        void existsByUsername_WithExistingUsername_ShouldReturnTrue() {
            // Given: 존재하는 username
            String username = "testuser";
            when(userRepository.existsByUsername(username)).thenReturn(true);

            // When: 사용자 존재 여부를 확인하면
            boolean exists = userAdapter.existsByUsername(username);

            // Then: true를 반환해야 함
            assertThat(exists).isTrue();
            verify(userRepository).existsByUsername(username);
        }

        @Test
        @DisplayName("존재하지 않는 username 확인 시 false 반환")
        void existsByUsername_WithNonExistentUsername_ShouldReturnFalse() {
            // Given: 존재하지 않는 username
            String username = "nonexistent";
            when(userRepository.existsByUsername(username)).thenReturn(false);

            // When: 사용자 존재 여부를 확인하면
            boolean exists = userAdapter.existsByUsername(username);

            // Then: false를 반환해야 함
            assertThat(exists).isFalse();
            verify(userRepository).existsByUsername(username);
        }
    }

    @Nested
    @DisplayName("사용자 목록 조회 테스트")
    class UserListTest {

        @Test
        @DisplayName("모든 사용자 목록 조회 성공")
        void findAll_ShouldReturnAllUsers() {
            // Given: 여러 사용자가 존재하는 상황
            User anotherUser = User.builder()
                    .username("anotheruser")
                    .name("다른 사용자")
                    .email("another@example.com")
                    .role(UserRole.MANAGER)
                    .build();
            List<User> userList = Arrays.asList(testUser, anotherUser);
            when(userRepository.findAll()).thenReturn(userList);

            // When: 모든 사용자를 조회하면
            List<User> result = userAdapter.findAll();

            // Then: 모든 사용자가 반환되어야 함
            assertThat(result).hasSize(2);
            assertThat(result).containsExactly(testUser, anotherUser);
            verify(userRepository).findAll();
        }

        @Test
        @DisplayName("사용자가 없는 경우 빈 목록 반환")
        void findAll_WithNoUsers_ShouldReturnEmptyList() {
            // Given: 사용자가 없는 상황
            when(userRepository.findAll()).thenReturn(Arrays.asList());

            // When: 모든 사용자를 조회하면
            List<User> result = userAdapter.findAll();

            // Then: 빈 목록이 반환되어야 함
            assertThat(result).isEmpty();
            verify(userRepository).findAll();
        }
    }

    @Nested
    @DisplayName("사용자 저장/삭제 테스트")
    class UserPersistenceTest {

        @Test
        @DisplayName("사용자 저장 성공")
        void save_WithValidUser_ShouldSaveUser() {
            // Given: 저장할 사용자
            when(userRepository.save(testUser)).thenReturn(testUser);

            // When: 사용자를 저장하면
            userAdapter.save(testUser);

            // Then: 저장이 호출되어야 함
            verify(userRepository).save(testUser);
        }

        @Test
        @DisplayName("사용자 삭제 성공")
        void delete_WithValidUser_ShouldDeleteUser() {
            // Given: 삭제할 사용자
            doNothing().when(userRepository).delete(testUser);

            // When: 사용자를 삭제하면
            userAdapter.delete(testUser);

            // Then: 삭제가 호출되어야 함
            verify(userRepository).delete(testUser);
        }
    }

    @Nested
    @DisplayName("사용자 삭제 상태 확인 테스트")
    class UserDeletionStatusTest {

        @Test
        @DisplayName("활성 사용자 삭제 상태 확인 - 예외 없이 통과")
        void isDeleted_WithActiveUser_ShouldNotThrowException() {
            // Given: 활성 사용자
            when(userRepository.findByUsername(testUser.getUsername()))
                    .thenReturn(Optional.of(testUser));

            // When & Then: 예외가 발생하지 않아야 함
            userAdapter.isDeleted(testUser.getUsername());
            verify(userRepository).findByUsername(testUser.getUsername());
        }

        @Test
        @DisplayName("삭제된 사용자 확인 시 예외 발생")
        void isDeleted_WithDeletedUser_ShouldThrowException() {
            // Given: 삭제된 사용자
            when(userRepository.findByUsername(deletedUser.getUsername()))
                    .thenReturn(Optional.of(deletedUser));

            // When & Then: UserExceptions.userDeleted() 예외가 발생해야 함
            assertThatThrownBy(() -> userAdapter.isDeleted(deletedUser.getUsername()))
                    .isInstanceOf(RuntimeException.class);
            verify(userRepository).findByUsername(deletedUser.getUsername());
        }
    }

    @Nested
    @DisplayName("임시 비밀번호 생성 테스트")
    class TemporaryPasswordGenerationTest {

        @Test
        @DisplayName("임시 비밀번호 길이 확인")
        void generateTemporaryPassword_ShouldReturnPasswordWithCorrectLength() {
            // When: 임시 비밀번호를 생성하면
            String password = userAdapter.generateTemporaryPassword();

            // Then: 10자리 길이여야 함
            assertThat(password).hasSize(10);
        }

        @Test
        @DisplayName("임시 비밀번호 구성 요소 확인")
        void generateTemporaryPassword_ShouldContainAllRequiredCharacterTypes() {
            // When: 임시 비밀번호를 생성하면
            String password = userAdapter.generateTemporaryPassword();

            // Then: 대문자, 소문자, 숫자, 특수문자가 모두 포함되어야 함
            assertThat(password).matches(".*[A-Z].*"); // 대문자 포함
            assertThat(password).matches(".*[a-z].*"); // 소문자 포함
            assertThat(password).matches(".*[0-9].*"); // 숫자 포함
            assertThat(password).matches(".*[!@#$%^&*()].*"); // 특수문자 포함
        }

        @Test
        @DisplayName("임시 비밀번호가 허용된 문자만 포함하는지 확인")
        void generateTemporaryPassword_ShouldContainOnlyAllowedCharacters() {
            // When: 임시 비밀번호를 생성하면
            String password = userAdapter.generateTemporaryPassword();

            // Then: 허용된 문자만 포함해야 함
            String allowedPattern = "^[A-Za-z0-9!@#$%^&*()]+$";
            assertThat(password).matches(allowedPattern);
        }

        @RepeatedTest(10)
        @DisplayName("여러 번 생성해도 항상 유효한 비밀번호 생성")
        void generateTemporaryPassword_ShouldGenerateValidPasswordConsistently() {
            // When: 임시 비밀번호를 여러 번 생성하면
            String password = userAdapter.generateTemporaryPassword();

            // Then: 항상 유효한 조건을 만족해야 함
            assertThat(password).hasSize(10);
            assertThat(password).matches(".*[A-Z].*");
            assertThat(password).matches(".*[a-z].*");
            assertThat(password).matches(".*[0-9].*");
            assertThat(password).matches(".*[!@#$%^&*()].*");
        }

        @RepeatedTest(10)
        @DisplayName("생성된 비밀번호들이 서로 다른지 확인")
        void generateTemporaryPassword_ShouldGenerateDifferentPasswords() {
            // When: 연속으로 두 개의 비밀번호를 생성하면
            String password1 = userAdapter.generateTemporaryPassword();
            String password2 = userAdapter.generateTemporaryPassword();

            // Then: 서로 다른 비밀번호가 생성되어야 함 (높은 확률로)
            // 주의: 이론적으로는 같을 수 있지만 실제로는 거의 불가능
            assertThat(password1).isNotEqualTo(password2);
        }
    }

    @Nested
    @DisplayName("문자열 셔플 기능 테스트")
    class StringShuffleTest {

        @Test
        @DisplayName("문자열 섞기 후 길이 보존")
        void shuffleString_ShouldPreserveLength() {
            // Given: 테스트용 문자열
            String original = "ABCD1234!@#$";

            // When: 문자열을 섞으면 (리플렉션을 통해 private 메서드 호출)
            String shuffled = callShuffleString(original);

            // Then: 길이가 보존되어야 함
            assertThat(shuffled).hasSameSizeAs(original);
        }

        @Test
        @DisplayName("문자열 섞기 후 모든 문자 보존")
        void shuffleString_ShouldPreserveAllCharacters() {
            // Given: 테스트용 문자열
            String original = "ABCD1234!@#$";

            // When: 문자열을 섞으면
            String shuffled = callShuffleString(original);

            // Then: 모든 문자가 보존되어야 함
            for (char c : original.toCharArray()) {
                assertThat(shuffled).contains(String.valueOf(c));
            }
        }

        @Test
        @DisplayName("빈 문자열 섞기")
        void shuffleString_WithEmptyString_ShouldReturnEmptyString() {
            // Given: 빈 문자열
            String original = "";

            // When: 빈 문자열을 섞으면
            String shuffled = callShuffleString(original);

            // Then: 여전히 빈 문자열이어야 함
            assertThat(shuffled).isEmpty();
        }

        @Test
        @DisplayName("단일 문자 섞기")
        void shuffleString_WithSingleCharacter_ShouldReturnSameCharacter() {
            // Given: 단일 문자
            String original = "A";

            // When: 단일 문자를 섞으면
            String shuffled = callShuffleString(original);

            // Then: 동일한 문자가 반환되어야 함
            assertThat(shuffled).isEqualTo(original);
        }

        @RepeatedTest(10)
        @DisplayName("문자열 섞기의 무작위성 확인")
        void shuffleString_ShouldProduceRandomResults() {
            // Given: 충분히 긴 문자열
            String original = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";

            // When: 같은 문자열을 여러 번 섞으면
            String shuffled1 = callShuffleString(original);
            String shuffled2 = callShuffleString(original);

            // Then: 높은 확률로 다른 결과가 나와야 함
            // 주의: 이론적으로는 같을 수 있지만 26자리 문자열에서는 거의 불가능
            assertThat(shuffled1).isNotEqualTo(shuffled2);
        }

        /**
         * 리플렉션을 통해 private shuffleString 메서드를 호출하는 헬퍼 메서드
         * 실제로는 generateTemporaryPassword()를 통해 간접 테스트할 수도 있지만,
         * 단위 테스트의 명확성을 위해 직접 테스트합니다.
         */
        private String callShuffleString(String input) {
            try {
                var method = UserAdapter.class.getDeclaredMethod("shuffleString", String.class);
                method.setAccessible(true);
                return (String) method.invoke(userAdapter, input);
            } catch (Exception e) {
                throw new RuntimeException("Failed to call shuffleString method", e);
            }
        }
    }

    @Nested
    @DisplayName("비밀번호 생성과 셔플의 통합 테스트")
    class PasswordGenerationIntegrationTest {

        @Test
        @DisplayName("생성된 비밀번호가 실제로 섞인 상태인지 확인")
        void generateTemporaryPassword_ShouldShuffleCharacters() {
            // Given: 여러 번의 비밀번호 생성
            // When: 여러 개의 비밀번호를 생성하면
            String[] passwords = new String[5];
            for (int i = 0; i < 5; i++) {
                passwords[i] = userAdapter.generateTemporaryPassword();
            }

            // Then: 각 비밀번호가 최소 필수 문자를 포함하면서도 패턴이 다양해야 함
            for (String password : passwords) {
                // 기본 조건 확인
                assertThat(password).hasSize(10);
                assertThat(password).matches(".*[A-Z].*");
                assertThat(password).matches(".*[a-z].*");
                assertThat(password).matches(".*[0-9].*");
                assertThat(password).matches(".*[!@#$%^&*()].*");

                // 첫 4자리가 항상 같은 패턴이 아닌지 확인 (섞였는지 확인)
                // 예: 첫 글자가 항상 대문자가 아니어야 함
                char firstChar = password.charAt(0);
                assertThat(Character.isUpperCase(firstChar)).isNotEqualTo(true); // 항상 대문자는 아니어야 함
            }
        }
    }
}