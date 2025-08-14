package com.commonground.be.domain.session.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.commonground.be.domain.session.dto.SessionCreateRequest;
import com.commonground.be.domain.session.dto.SessionResponse;
import com.commonground.be.domain.session.entity.Session;
import com.commonground.be.domain.session.repository.SessionRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * UserSessionService 단위 테스트 클래스
 * <p>
 * 이 테스트는 UserSessionService의 세션 관리 로직을 검증합니다. 세션 생성, 검증, 무효화, 최대 동시 세션 수 제한 등의 핵심 세션 관리 기능들을
 * 테스트합니다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UserSessionService 단위 테스트")
@ActiveProfiles("test")
class UserSessionServiceTest {

	@InjectMocks
	private UserSessionService userSessionService;

	@Mock
	private SessionRepository sessionRepository;

	// 테스트용 설정값
	private static final Long SESSION_EXPIRATION = 3600L; // 1시간
	private static final Integer MAX_CONCURRENT_SESSIONS = 3;

	private String testUserId;
	private String testUserAgent;
	private String testSessionId;
	private Session testSession;
	private SessionCreateRequest testCreateRequest;

	/**
	 * 각 테스트 실행 전 테스트 데이터와 설정을 초기화합니다.
	 */
	@BeforeEach
	void setUp() {
		testUserId = "user123";
		testUserAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36";
		testSessionId = "session123";

		// 테스트용 설정값 주입
		ReflectionTestUtils.setField(userSessionService, "sessionExpiration", SESSION_EXPIRATION);
		ReflectionTestUtils.setField(userSessionService, "maxConcurrentSessions",
				MAX_CONCURRENT_SESSIONS);

		// 테스트용 세션 생성
		testSession = Session.builder()
				.sessionId(testSessionId)
				.userId(testUserId)
				.userAgent(testUserAgent)
				.createdAt(LocalDateTime.now())
				.lastAccessTime(LocalDateTime.now())
				.expirationSeconds(SESSION_EXPIRATION)
				.active(true)
				.build();

		// 테스트용 세션 생성 요청
		testCreateRequest = SessionCreateRequest.builder().userId(testUserId)
				.userAgent(testUserAgent).build();
	}

	@Nested
	@DisplayName("세션 생성 테스트")
	class CreateSessionTest {

		@Test
		@DisplayName("정상적인 세션 생성 성공")
		void createSession_WithValidRequest_ShouldCreateSession() {
			// Given: 유효한 세션 생성 요청과 최대 세션 수 이하 상황
			when(sessionRepository.countByUserId(testUserId)).thenReturn(2L);
			// doAnswer로 실제 저장 동작 시뮬레이션
			doAnswer(invocation -> {
				Session session = invocation.getArgument(0);
				// 실제 로직처럼 세션 정보 검증
				assertThat(session.getSessionId()).isNotEmpty();
				assertThat(session.getUserId()).isEqualTo(testUserId);
				assertThat(session.getUserAgent()).isEqualTo(testUserAgent);
				assertThat(session.isActive()).isTrue();
				return null;
			}).when(sessionRepository).save(any(Session.class));

			// When: 세션을 생성하면
			SessionResponse response = userSessionService.createSession(testCreateRequest);

			// Then: 세션이 성공적으로 생성되어야 함
			assertThat(response).isNotNull();
			assertThat(response.getUserId()).isEqualTo(testUserId);
			assertThat(response.getUserAgent()).isEqualTo(testUserAgent);
			assertThat(response.isActive()).isTrue();

			verify(sessionRepository).countByUserId(testUserId);
			verify(sessionRepository).save(any(Session.class));
			verify(sessionRepository, times(0)).deleteAllByUserId(anyString()); // 기존 세션 삭제 안함
		}

		@Test
		@DisplayName("최대 세션 수 초과 시 기존 세션 정리 후 생성")
		void createSession_WhenMaxSessionsExceeded_ShouldCleanupAndCreate() {
			// Given: 최대 세션 수를 초과한 상황
			when(sessionRepository.countByUserId(testUserId)).thenReturn(3L); // 최대치와 같음
			doNothing().when(sessionRepository).deleteAllByUserId(testUserId);
			// doAnswer로 실제 저장 동작 시뮬레이션
			doAnswer(invocation -> {
				Session session = invocation.getArgument(0);
				assertThat(session.getUserId()).isEqualTo(testUserId);
				assertThat(session.isActive()).isTrue();
				return null;
			}).when(sessionRepository).save(any(Session.class));

			// When: 세션을 생성하면
			SessionResponse response = userSessionService.createSession(testCreateRequest);

			// Then: 기존 세션이 정리되고 새 세션이 생성되어야 함
			assertThat(response).isNotNull();
			assertThat(response.getUserId()).isEqualTo(testUserId);

			verify(sessionRepository).countByUserId(testUserId);
			verify(sessionRepository).deleteAllByUserId(testUserId);
			verify(sessionRepository).save(any(Session.class));
		}

		@Test
		@DisplayName("null UserAgent로 세션 생성")
		void createSession_WithNullUserAgent_ShouldCreateSessionWithNullUserAgent() {
			// Given: UserAgent가 null인 요청
			testCreateRequest.setUserAgent(null);
			when(sessionRepository.countByUserId(testUserId)).thenReturn(1L);
			// doAnswer로 null UserAgent 처리 검증
			doAnswer(invocation -> {
				Session session = invocation.getArgument(0);
				assertThat(session.getUserId()).isEqualTo(testUserId);
				assertThat(session.getUserAgent()).isNull();
				return null;
			}).when(sessionRepository).save(any(Session.class));

			// When: 세션을 생성하면
			SessionResponse response = userSessionService.createSession(testCreateRequest);

			// Then: null UserAgent로도 세션이 생성되어야 함
			assertThat(response).isNotNull();
			assertThat(response.getUserId()).isEqualTo(testUserId);

			verify(sessionRepository).save(any(Session.class));
		}

		@Test
		@DisplayName("빈 UserAgent로 세션 생성")
		void createSession_WithEmptyUserAgent_ShouldCreateSessionWithEmptyUserAgent() {
			// Given: UserAgent가 빈 문자열인 요청
			testCreateRequest.setUserAgent("");
			when(sessionRepository.countByUserId(testUserId)).thenReturn(1L);
			// doAnswer로 빈 UserAgent 처리 검증
			doAnswer(invocation -> {
				Session session = invocation.getArgument(0);
				assertThat(session.getUserId()).isEqualTo(testUserId);
				assertThat(session.getUserAgent()).isEmpty();
				return null;
			}).when(sessionRepository).save(any(Session.class));

			// When: 세션을 생성하면
			SessionResponse response = userSessionService.createSession(testCreateRequest);

			// Then: 빈 UserAgent로도 세션이 생성되어야 함
			assertThat(response).isNotNull();
			assertThat(response.getUserId()).isEqualTo(testUserId);

			verify(sessionRepository).save(any(Session.class));
		}
	}

	@Nested
	@DisplayName("세션 조회 테스트")
	class GetSessionTest {

		@Test
		@DisplayName("존재하는 세션 조회 성공")
		void getSession_WithExistingSessionId_ShouldReturnSession() {
			// Given: 존재하는 세션
			when(sessionRepository.findById(testSessionId)).thenReturn(Optional.of(testSession));

			// When: 세션을 조회하면
			Optional<SessionResponse> result = userSessionService.getSession(testSessionId);

			// Then: 세션이 반환되어야 함
			assertThat(result).isPresent();
			assertThat(result.get().getSessionId()).isEqualTo(testSessionId);
			assertThat(result.get().getUserId()).isEqualTo(testUserId);
			assertThat(result.get().getUserAgent()).isEqualTo(testUserAgent);

			verify(sessionRepository).findById(testSessionId);
		}

		@Test
		@DisplayName("존재하지 않는 세션 조회 시 빈 Optional 반환")
		void getSession_WithNonExistentSessionId_ShouldReturnEmpty() {
			// Given: 존재하지 않는 세션 ID
			String nonExistentSessionId = "nonexistent";
			when(sessionRepository.findById(nonExistentSessionId)).thenReturn(Optional.empty());

			// When: 세션을 조회하면
			Optional<SessionResponse> result = userSessionService.getSession(nonExistentSessionId);

			// Then: 빈 Optional이 반환되어야 함
			assertThat(result).isEmpty();
			verify(sessionRepository).findById(nonExistentSessionId);
		}

		@Test
		@DisplayName("null 세션 ID로 조회")
		void getSession_WithNullSessionId_ShouldHandleGracefully() {
			// Given: null 세션 ID
			when(sessionRepository.findById(null)).thenReturn(Optional.empty());

			// When: null 세션 ID로 조회하면
			Optional<SessionResponse> result = userSessionService.getSession(null);

			// Then: 빈 Optional이 반환되어야 함
			assertThat(result).isEmpty();
			verify(sessionRepository).findById(null);
		}
	}

	@Nested
	@DisplayName("세션 접근 시간 업데이트 테스트")
	class UpdateSessionAccessTest {

		@Test
		@DisplayName("존재하는 세션의 접근 시간 업데이트 성공")
		void updateSessionAccess_WithExistingSession_ShouldUpdateLastAccessTime() {
			// Given: 존재하는 세션
			when(sessionRepository.findById(testSessionId)).thenReturn(Optional.of(testSession));
			// doAnswer로 접근 시간 업데이트 검증
			doAnswer(invocation -> {
				Session session = invocation.getArgument(0);
				assertThat(session.getLastAccessTime()).isNotNull();
				return null;
			}).when(sessionRepository).save(testSession);

			// When: 세션 접근 시간을 업데이트하면
			userSessionService.updateSessionAccess(testSessionId);

			// Then: 세션이 저장되어야 함
			verify(sessionRepository).findById(testSessionId);
			verify(sessionRepository).save(testSession);
		}

		@Test
		@DisplayName("존재하지 않는 세션의 접근 시간 업데이트 시도")
		void updateSessionAccess_WithNonExistentSession_ShouldNotThrowException() {
			// Given: 존재하지 않는 세션 ID
			String nonExistentSessionId = "nonexistent";
			when(sessionRepository.findById(nonExistentSessionId)).thenReturn(Optional.empty());

			// When & Then: 예외가 발생하지 않아야 함
			userSessionService.updateSessionAccess(nonExistentSessionId);

			verify(sessionRepository).findById(nonExistentSessionId);
			verify(sessionRepository, times(0)).save(any(Session.class));
		}

		@Test
		@DisplayName("null 세션 ID로 접근 시간 업데이트")
		void updateSessionAccess_WithNullSessionId_ShouldHandleGracefully() {
			// Given: null 세션 ID
			when(sessionRepository.findById(null)).thenReturn(Optional.empty());

			// When & Then: 예외가 발생하지 않아야 함
			userSessionService.updateSessionAccess(null);

			verify(sessionRepository).findById(null);
			verify(sessionRepository, times(0)).save(any(Session.class));
		}
	}

	@Nested
	@DisplayName("세션 검증 테스트")
	class ValidateSessionTest {

		@Test
		@DisplayName("유효한 세션 검증 성공")
		void validateSession_WithValidSession_ShouldReturnTrue() {
			// Given: 유효한 세션 (활성 + 만료되지 않음)
			Session validSession = Session.builder()
					.sessionId(testSessionId)
					.userId(testUserId)
					.createdAt(LocalDateTime.now())
					.expirationSeconds(3600L)
					.active(true)
					.build();
			when(sessionRepository.findById(testSessionId)).thenReturn(Optional.of(validSession));

			// When: 세션을 검증하면
			boolean isValid = userSessionService.validateSession(testSessionId);

			// Then: true를 반환해야 함
			assertThat(isValid).isTrue();
			verify(sessionRepository).findById(testSessionId);
		}

		@Test
		@DisplayName("비활성 세션 검증 실패")
		void validateSession_WithInactiveSession_ShouldReturnFalse() {
			// Given: 비활성 세션
			Session inactiveSession = Session.builder()
					.sessionId(testSessionId)
					.userId(testUserId)
					.createdAt(LocalDateTime.now())
					.expirationSeconds(3600L)
					.active(false) // 비활성
					.build();
			when(sessionRepository.findById(testSessionId)).thenReturn(
					Optional.of(inactiveSession));

			// When: 세션을 검증하면
			boolean isValid = userSessionService.validateSession(testSessionId);

			// Then: false를 반환해야 함
			assertThat(isValid).isFalse();
			verify(sessionRepository).findById(testSessionId);
		}

		@Test
		@DisplayName("만료된 세션 검증 실패")
		void validateSession_WithExpiredSession_ShouldReturnFalse() {
			// Given: 만료된 세션 (과거 시간으로 생성)
			Session expiredSession = Session.builder()
					.sessionId(testSessionId)
					.userId(testUserId)
					.createdAt(LocalDateTime.now().minusHours(2)) // 2시간 전 생성
					.expirationSeconds(3600L) // 1시간 만료
					.active(true)
					.build();
			when(sessionRepository.findById(testSessionId)).thenReturn(Optional.of(expiredSession));

			// When: 세션을 검증하면
			boolean isValid = userSessionService.validateSession(testSessionId);

			// Then: false를 반환해야 함
			assertThat(isValid).isFalse();
			verify(sessionRepository).findById(testSessionId);
		}

		@Test
		@DisplayName("존재하지 않는 세션 검증 실패")
		void validateSession_WithNonExistentSession_ShouldReturnFalse() {
			// Given: 존재하지 않는 세션
			String nonExistentSessionId = "nonexistent";
			when(sessionRepository.findById(nonExistentSessionId)).thenReturn(Optional.empty());

			// When: 세션을 검증하면
			boolean isValid = userSessionService.validateSession(nonExistentSessionId);

			// Then: false를 반환해야 함
			assertThat(isValid).isFalse();
			verify(sessionRepository).findById(nonExistentSessionId);
		}

		@Test
		@DisplayName("null 세션 ID 검증 실패")
		void validateSession_WithNullSessionId_ShouldReturnFalse() {
			// Given: null 세션 ID
			when(sessionRepository.findById(null)).thenReturn(Optional.empty());

			// When: null 세션 ID를 검증하면
			boolean isValid = userSessionService.validateSession(null);

			// Then: false를 반환해야 함
			assertThat(isValid).isFalse();
			verify(sessionRepository).findById(null);
		}
	}

	@Nested
	@DisplayName("세션 무효화 테스트")
	class InvalidateSessionTest {

		@Test
		@DisplayName("존재하는 세션 무효화 성공")
		void invalidateSession_WithExistingSession_ShouldDeactivateAndDelete() {
			// Given: 존재하는 세션
			when(sessionRepository.findById(testSessionId)).thenReturn(Optional.of(testSession));
			doNothing().when(sessionRepository).deleteById(testSessionId);

			// When: 세션을 무효화하면
			userSessionService.invalidateSession(testSessionId);

			// Then: 세션이 비활성화되고 삭제되어야 함
			verify(sessionRepository).findById(testSessionId);
			verify(sessionRepository).deleteById(testSessionId);
			assertThat(testSession.isActive()).isFalse(); // 비활성화 확인
		}

		@Test
		@DisplayName("존재하지 않는 세션 무효화 시도")
		void invalidateSession_WithNonExistentSession_ShouldNotThrowException() {
			// Given: 존재하지 않는 세션
			String nonExistentSessionId = "nonexistent";
			when(sessionRepository.findById(nonExistentSessionId)).thenReturn(Optional.empty());

			// When & Then: 예외가 발생하지 않아야 함
			userSessionService.invalidateSession(nonExistentSessionId);

			verify(sessionRepository).findById(nonExistentSessionId);
			verify(sessionRepository, times(0)).deleteById(anyString());
		}

		@Test
		@DisplayName("null 세션 ID로 무효화 시도")
		void invalidateSession_WithNullSessionId_ShouldHandleGracefully() {
			// Given: null 세션 ID
			when(sessionRepository.findById(null)).thenReturn(Optional.empty());

			// When & Then: 예외가 발생하지 않아야 함
			userSessionService.invalidateSession(null);

			verify(sessionRepository).findById(null);
			verify(sessionRepository, times(0)).deleteById(anyString());
		}
	}

	@Nested
	@DisplayName("사용자 모든 세션 무효화 테스트")
	class InvalidateAllUserSessionsTest {

		@Test
		@DisplayName("사용자의 모든 세션 무효화 성공")
		void invalidateAllUserSessions_WithValidUserId_ShouldDeleteAllUserSessions() {
			// Given: 유효한 사용자 ID
			doNothing().when(sessionRepository).deleteAllByUserId(testUserId);

			// When: 사용자의 모든 세션을 무효화하면
			userSessionService.invalidateAllUserSessions(testUserId);

			// Then: 해당 사용자의 모든 세션이 삭제되어야 함
			verify(sessionRepository).deleteAllByUserId(testUserId);
		}

		@Test
		@DisplayName("null 사용자 ID로 모든 세션 무효화")
		void invalidateAllUserSessions_WithNullUserId_ShouldHandleGracefully() {
			// Given: null 사용자 ID
			doNothing().when(sessionRepository).deleteAllByUserId(null);

			// When: null 사용자 ID로 모든 세션을 무효화하면
			userSessionService.invalidateAllUserSessions(null);

			// Then: Repository 호출이 수행되어야 함
			verify(sessionRepository).deleteAllByUserId(null);
		}

		@Test
		@DisplayName("빈 문자열 사용자 ID로 모든 세션 무효화")
		void invalidateAllUserSessions_WithEmptyUserId_ShouldHandleGracefully() {
			// Given: 빈 문자열 사용자 ID
			String emptyUserId = "";
			doNothing().when(sessionRepository).deleteAllByUserId(emptyUserId);

			// When: 빈 문자열 사용자 ID로 모든 세션을 무효화하면
			userSessionService.invalidateAllUserSessions(emptyUserId);

			// Then: Repository 호출이 수행되어야 함
			verify(sessionRepository).deleteAllByUserId(emptyUserId);
		}
	}

	@Nested
	@DisplayName("활성 세션 수 조회 테스트")
	class GetActiveSessionCountTest {

		@Test
		@DisplayName("사용자의 활성 세션 수 조회 성공")
		void getActiveSessionCount_WithValidUserId_ShouldReturnCount() {
			// Given: 사용자가 2개의 활성 세션을 가지고 있는 상황
			when(sessionRepository.countByUserId(testUserId)).thenReturn(2L);

			// When: 활성 세션 수를 조회하면
			long sessionCount = userSessionService.getActiveSessionCount(testUserId);

			// Then: 올바른 세션 수가 반환되어야 함
			assertThat(sessionCount).isEqualTo(2L);
			verify(sessionRepository).countByUserId(testUserId);
		}

		@Test
		@DisplayName("세션이 없는 사용자의 세션 수 조회")
		void getActiveSessionCount_WithUserHavingNoSessions_ShouldReturnZero() {
			// Given: 세션이 없는 사용자
			when(sessionRepository.countByUserId(testUserId)).thenReturn(0L);

			// When: 활성 세션 수를 조회하면
			long sessionCount = userSessionService.getActiveSessionCount(testUserId);

			// Then: 0이 반환되어야 함
			assertThat(sessionCount).isEqualTo(0L);
			verify(sessionRepository).countByUserId(testUserId);
		}

		@Test
		@DisplayName("null 사용자 ID로 세션 수 조회")
		void getActiveSessionCount_WithNullUserId_ShouldHandleGracefully() {
			// Given: null 사용자 ID
			when(sessionRepository.countByUserId(null)).thenReturn(0L);

			// When: null 사용자 ID로 세션 수를 조회하면
			long sessionCount = userSessionService.getActiveSessionCount(null);

			// Then: Repository 호출이 수행되어야 함
			assertThat(sessionCount).isEqualTo(0L);
			verify(sessionRepository).countByUserId(null);
		}
	}

	@Nested
	@DisplayName("통합 시나리오 테스트")
	class IntegrationScenarioTest {

		@Test
		@DisplayName("세션 생성부터 무효화까지 전체 플로우")
		void sessionLifecycle_CreateValidateAndInvalidate_ShouldWorkCorrectly() {
			// Given: 세션 생성을 위한 Mock 설정
			when(sessionRepository.countByUserId(testUserId)).thenReturn(1L);
			// doAnswer로 전체 라이프사이클 처리 검증
			doAnswer(invocation -> {
				Session session = invocation.getArgument(0);
				assertThat(session.getUserId()).isEqualTo(testUserId);
				assertThat(session.isActive()).isTrue();
				return null;
			}).when(sessionRepository).save(any(Session.class));
			when(sessionRepository.findById(anyString())).thenReturn(Optional.of(testSession));
			doNothing().when(sessionRepository).deleteById(anyString());

			// When: 세션 생성 -> 검증 -> 무효화
			SessionResponse created = userSessionService.createSession(testCreateRequest);
			boolean isValid = userSessionService.validateSession(created.getSessionId());
			userSessionService.invalidateSession(created.getSessionId());

			// Then: 모든 단계가 성공적으로 수행되어야 함
			assertThat(created).isNotNull();
			assertThat(isValid).isTrue();

			verify(sessionRepository).save(any(Session.class));
			verify(sessionRepository, times(2)).findById(anyString());
			verify(sessionRepository).deleteById(anyString());
		}

		@Test
		@DisplayName("최대 세션 수 도달 후 새 세션 생성 시나리오")
		void maxSessionsReached_CreateNewSession_ShouldCleanupAndCreate() {
			// Given: 최대 세션 수에 도달한 상황
			when(sessionRepository.countByUserId(testUserId)).thenReturn(3L); // 최대치
			doNothing().when(sessionRepository).deleteAllByUserId(testUserId);
			// doAnswer로 세션 생성 로직 검증
			doAnswer(invocation -> {
				Session session = invocation.getArgument(0);
				assertThat(session.getUserId()).isEqualTo(testUserId);
				assertThat(session.isActive()).isTrue();
				return null;
			}).when(sessionRepository).save(any(Session.class));

			// When: 새 세션을 생성하면
			SessionResponse created = userSessionService.createSession(testCreateRequest);

			// Then: 기존 세션이 정리되고 새 세션이 생성되어야 함
			assertThat(created).isNotNull();
			assertThat(created.getUserId()).isEqualTo(testUserId);

			verify(sessionRepository).countByUserId(testUserId);
			verify(sessionRepository).deleteAllByUserId(testUserId);
			verify(sessionRepository).save(any(Session.class));
		}
	}
}