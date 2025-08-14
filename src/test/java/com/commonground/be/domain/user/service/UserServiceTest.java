package com.commonground.be.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.commonground.be.domain.user.dto.ProfileUpdateRequestDto;
import com.commonground.be.domain.user.dto.UserResponseDto;
import com.commonground.be.domain.user.entity.User;
import com.commonground.be.domain.user.repository.UserAdapter;
import com.commonground.be.domain.user.repository.UserRepository;
import com.commonground.be.domain.user.utils.UserRole;
import com.commonground.be.global.application.exception.UserExceptions;
import com.commonground.be.global.infrastructure.security.jwt.TokenManager;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * UserService 단위 테스트 클래스
 * 
 * 이 테스트는 UserService의 비즈니스 로직을 검증합니다.
 * 모든 외부 의존성(UserAdapter, TokenManager, Redis 등)은 Mock으로 처리하여
 * 순수한 비즈니스 로직만을 테스트합니다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UserService 단위 테스트")
@ActiveProfiles("test")
class UserServiceTest {

    @InjectMocks
    private UserService userService;

    @Mock
    private UserAdapter userAdapter;

    @Mock
    private UserRepository userRepository;

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private TokenManager tokenManager;

    private User testUser;
    private User managerUser;

    /**
     * 각 테스트 실행 전 공통 테스트 데이터 설정
     * 일반 사용자와 관리자 사용자 객체를 생성합니다.
     */
    @BeforeEach
    void setUp() {
        // 일반 사용자 생성
        testUser = User.builder()
                .username("testuser")
                .name("테스트 사용자")
                .email("test@example.com")
                .role(UserRole.USER)
                .build();

        // 관리자 사용자 생성
        managerUser = User.builder()
                .username("manager")
                .name("관리자")
                .email("manager@example.com")
                .role(UserRole.MANAGER)
                .build();
    }

    @Nested
    @DisplayName("사용자 조회 테스트")
    class GetUserTest {

        @Test
        @DisplayName("정상적인 사용자 ID로 사용자 조회 성공")
        void getUser_WithValidUserId_ShouldReturnUserResponseDto() {
            // Given: 유효한 사용자 ID가 주어졌을 때
            Long userId = 1L;
            when(userAdapter.findById(userId)).thenReturn(testUser);
            doNothing().when(userAdapter).isDeleted(testUser.getUsername());

            // When: 사용자를 조회하면
            UserResponseDto result = userService.getUser(userId);

            // Then: 올바른 사용자 정보가 반환되어야 함
            assertThat(result).isNotNull();
            assertThat(result.getUsername()).isEqualTo(testUser.getUsername());
            assertThat(result.getName()).isEqualTo(testUser.getName());
            
            // 의존성 호출 검증
            verify(userAdapter).findById(userId);
            verify(userAdapter).isDeleted(testUser.getUsername());
        }
    }

    @Nested
    @DisplayName("회원 탈퇴 테스트")
    class WithdrawTest {

        @Test
        @DisplayName("정상적인 회원 탈퇴 처리")
        void withdraw_WithValidUser_ShouldMarkAsDeletedAndInvalidateTokens() {
            // Given: 유효한 사용자가 주어졌을 때
            doNothing().when(userAdapter).isDeleted(testUser.getUsername());
            doNothing().when(userAdapter).save(any(User.class));
            doNothing().when(tokenManager).invalidateAllUserTokens(testUser.getUsername());

            // When: 회원 탈퇴를 수행하면
            userService.withdraw(testUser);

            // Then: 사용자가 삭제 상태로 변경되고 토큰이 무효화되어야 함
            verify(userAdapter).isDeleted(testUser.getUsername());
            verify(userAdapter).save(testUser);
            verify(tokenManager).invalidateAllUserTokens(testUser.getUsername());
        }
    }

    @Nested
    @DisplayName("사용자 복원 테스트")
    class ResignTest {

        @Test
        @DisplayName("삭제된 사용자 복원 성공")
        void resign_WithValidUserId_ShouldRestoreUser() {
            // Given: 삭제된 사용자 ID가 주어졌을 때
            Long userId = 1L;
            when(userAdapter.findById(userId)).thenReturn(testUser);
            doNothing().when(userAdapter).isDeleted(testUser.getUsername());
            doNothing().when(userAdapter).save(any(User.class));

            // When: 사용자를 복원하면
            userService.resign(userId);

            // Then: 사용자가 복원되어야 함
            verify(userAdapter).findById(userId);
            verify(userAdapter).isDeleted(testUser.getUsername());
            verify(userAdapter).save(testUser);
        }
    }

    @Nested
    @DisplayName("프로필 업데이트 테스트")
    class UpdateTest {

        @Test
        @DisplayName("프로필 업데이트 성공")
        void update_WithValidData_ShouldUpdateProfile() {
            // Given: 유효한 프로필 업데이트 데이터가 주어졌을 때
            ProfileUpdateRequestDto requestDto = new ProfileUpdateRequestDto();
            requestDto.setEmail("updated@example.com");

            doNothing().when(userAdapter).save(any(User.class));

            // When: 프로필을 업데이트하면
            userService.update(testUser, requestDto);

            // Then: 사용자 정보가 저장되어야 함
            verify(userAdapter).save(testUser);
        }
    }

    @Nested
    @DisplayName("권한 검증 테스트")
    class CheckUserRoleTest {

        @Test
        @DisplayName("관리자 권한 검증 성공")
        void checkUserRole_WithManagerRole_ShouldNotThrowException() {
            // Given: 관리자 권한을 가진 사용자가 주어졌을 때
            // When & Then: 예외가 발생하지 않아야 함
            userService.checkUserRole(managerUser);
        }

        @Test
        @DisplayName("일반 사용자 권한 검증 실패")
        void checkUserRole_WithUserRole_ShouldThrowException() {
            // Given: 일반 사용자 권한을 가진 사용자가 주어졌을 때
            // When & Then: UserExceptions.notManager() 예외가 발생해야 함
            assertThatThrownBy(() -> userService.checkUserRole(testUser))
                    .isInstanceOf(RuntimeException.class);
        }
    }

    @Nested
    @DisplayName("관리자 전용 기능 테스트")
    class AdminFunctionTest {

        @Test
        @DisplayName("모든 사용자 목록 조회 (관리자용)")
        void getAllUsersForAdmin_ShouldReturnAllUsers() {
            // Given: 여러 사용자가 존재할 때 (삭제된 사용자 포함)
            List<User> allUsers = Arrays.asList(testUser, managerUser);
            when(userRepository.findAll()).thenReturn(allUsers);

            // When: 관리자용 전체 사용자 목록을 조회하면
            List<UserResponseDto> result = userService.getAllUsersForAdmin();

            // Then: 모든 사용자가 반환되어야 함
            assertThat(result).hasSize(2);
            assertThat(result.get(0).getUsername()).isEqualTo(testUser.getUsername());
            assertThat(result.get(1).getUsername()).isEqualTo(managerUser.getUsername());
            
            verify(userRepository).findAll();
        }

        @Test
        @DisplayName("사용자 강제 탈퇴 (관리자용)")
        void forceWithdrawUser_ShouldMarkAsDeletedAndInvalidateTokens() {
            // Given: 탈퇴시킬 사용자 ID가 주어졌을 때
            Long userId = 1L;
            when(userAdapter.findById(userId)).thenReturn(testUser);
            doNothing().when(userAdapter).save(any(User.class));
            doNothing().when(tokenManager).invalidateAllUserTokens(testUser.getUsername());

            // When: 관리자가 사용자를 강제 탈퇴시키면
            userService.forceWithdrawUser(userId);

            // Then: 사용자가 삭제 상태로 변경되고 토큰이 무효화되어야 함
            verify(userAdapter).findById(userId);
            verify(userAdapter).save(testUser);
            verify(tokenManager).invalidateAllUserTokens(testUser.getUsername());
        }

        @Test
        @DisplayName("활성 사용자 수 조회")
        void getActiveUserCount_ShouldReturnActiveUserCount() {
            // Given: 활성/비활성 사용자가 혼재할 때
            User activeUser1 = User.builder().username("active1").build();
            User activeUser2 = User.builder().username("active2").build();
            
            List<User> allUsers = Arrays.asList(activeUser1, activeUser2);
            when(userRepository.findAll()).thenReturn(allUsers);

            // When: 활성 사용자 수를 조회하면
            long activeCount = userService.getActiveUserCount();

            // Then: 활성 사용자 수가 반환되어야 함
            assertThat(activeCount).isEqualTo(2L);
            verify(userRepository).findAll();
        }
    }

    @Nested
    @DisplayName("사용자 조회 메서드 테스트")
    class UserLookupTest {

        @Test
        @DisplayName("username으로 사용자 조회")
        void findByUsername_WithValidUsername_ShouldReturnUser() {
            // Given: 유효한 username이 주어졌을 때
            String username = "testuser";
            when(userAdapter.findByUsername(username)).thenReturn(testUser);

            // When: username으로 사용자를 조회하면
            User result = userService.findByUsername(username);

            // Then: 올바른 사용자가 반환되어야 함
            assertThat(result).isEqualTo(testUser);
            verify(userAdapter).findByUsername(username);
        }

        @Test
        @DisplayName("사용자 활성 상태 확인")
        void isUserActive_WithActiveUser_ShouldReturnTrue() {
            // Given: 활성 사용자의 username이 주어졌을 때
            String username = "testuser";
            when(userAdapter.findByUsername(username)).thenReturn(testUser);

            // When: 사용자 활성 상태를 확인하면
            boolean isActive = userService.isUserActive(username);

            // Then: 활성 상태가 반환되어야 함
            assertThat(isActive).isTrue();
            verify(userAdapter).findByUsername(username);
        }

        @Test
        @DisplayName("사용자 계정 생성 경과일 조회")
        void getUserAccountAge_WithValidUsername_ShouldReturnDaysSinceCreated() {
            // Given: 유효한 username이 주어졌을 때
            String username = "testuser";
            when(userAdapter.findByUsername(username)).thenReturn(testUser);

            // When: 계정 생성 경과일을 조회하면
            long accountAge = userService.getUserAccountAge(username);

            // Then: 경과일이 반환되어야 함
            assertThat(accountAge).isGreaterThanOrEqualTo(0L);
            verify(userAdapter).findByUsername(username);
        }
    }

    @Nested
    @DisplayName("로그아웃 테스트")
    class LogoutTest {

        @Test
        @DisplayName("로그아웃 시 Redis에서 사용자 세션 삭제")
        void logout_WithValidUsername_ShouldDeleteFromRedis() {
            // Given: 유효한 username이 주어졌을 때
            String username = "testuser";
            doNothing().when(redisTemplate).delete(username);

            // When: 로그아웃을 수행하면
            userService.logout(username);

            // Then: Redis에서 사용자 세션이 삭제되어야 함
            verify(redisTemplate).delete(username);
        }
    }

    @Nested
    @DisplayName("시간 기반 사용자 조회 테스트")
    class TimestampBasedQueryTest {

        @Test
        @DisplayName("신규 가입자 조회 (7일 이내)")
        void getRecentUsers_WithValidDays_ShouldReturnRecentUsers() {
            // Given: 신규 가입자가 존재할 때
            List<User> allUsers = Arrays.asList(testUser, managerUser);
            when(userRepository.findAll()).thenReturn(allUsers);

            // When: 7일 이내 신규 가입자를 조회하면
            List<UserResponseDto> recentUsers = userService.getRecentUsers(7);

            // Then: 결과가 반환되어야 함
            assertThat(recentUsers).isNotNull();
            verify(userRepository).findAll();
        }

        @Test
        @DisplayName("최근 활동 사용자 조회")
        void getRecentlyActiveUsers_WithValidDays_ShouldReturnActiveUsers() {
            // Given: 활동 사용자가 존재할 때
            List<User> allUsers = Arrays.asList(testUser, managerUser);
            when(userRepository.findAll()).thenReturn(allUsers);

            // When: 최근 활동 사용자를 조회하면
            List<UserResponseDto> activeUsers = userService.getRecentlyActiveUsers(7);

            // Then: 결과가 반환되어야 함
            assertThat(activeUsers).isNotNull();
            verify(userRepository).findAll();
        }

        @Test
        @DisplayName("휴면 계정 처리")
        void processDormantAccounts_ShouldReturnDormantAccountCount() {
            // Given: 휴면 계정이 존재할 때
            List<User> allUsers = Arrays.asList(testUser, managerUser);
            when(userRepository.findAll()).thenReturn(allUsers);

            // When: 휴면 계정을 처리하면
            int dormantCount = userService.processDormantAccounts();

            // Then: 휴면 계정 수가 반환되어야 함
            assertThat(dormantCount).isGreaterThanOrEqualTo(0);
            verify(userRepository).findAll();
        }
    }
}