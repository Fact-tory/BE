package com.commonground.be.global.application.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 관리자 권한이 필요한 API 메서드에 적용하는 어노테이션
 *
 * <p>이 어노테이션이 적용된 메서드는 관리자 마스터 토큰을 통해서만 접근할 수 있습니다.
 * AOP(Aspect-Oriented Programming)를 통해 메서드 실행 전에 관리자 권한을 검증합니다.</p>
 *
 * <h3>주요 기능:</h3>
 * <ul>
 *   <li>메서드 레벨 관리자 권한 체크</li>
 *   <li>AOP 기반 자동 권한 검증</li>
 *   <li>커스터마이징 가능한 에러 메시지</li>
 *   <li>HTTP 헤더 기반 토큰 검증</li>
 * </ul>
 *
 * <h3>사용법:</h3>
 * <pre>
 * {@code
 * @AdminRequired
 * @GetMapping("/admin/users")
 * public ResponseEntity<?> getAllUsers() {
 *     // 관리자만 접근 가능한 로직
 *     return ResponseEntity.ok(userService.getAllUsers());
 * }
 *
 * @AdminRequired(message = "사용자 삭제는 관리자만 가능합니다")
 * @DeleteMapping("/admin/users/{id}")
 * public ResponseEntity<?> deleteUser(@PathVariable Long id) {
 *     // 커스텀 에러 메시지와 함께 관리자 권한 체크
 *     userService.deleteUser(id);
 *     return ResponseEntity.ok().build();
 * }
 * }
 * </pre>
 *
 * <h3>권한 검증 과정:</h3>
 * <ol>
 *   <li>HTTP 요청에서 Authorization 또는 X-Admin-Token 헤더 추출</li>
 *   <li>AdminTokenValidator를 통한 토큰 유효성 검증</li>
 *   <li>유효한 경우 메서드 실행, 무효한 경우 예외 발생</li>
 * </ol>
 *
 * <h3>지원하는 HTTP 헤더:</h3>
 * <ul>
 *   <li>Authorization: Bearer {master_token}</li>
 *   <li>X-Admin-Token: {master_token}</li>
 * </ul>
 *
 * @author CommonGround Development Team
 * @see com.commonground.be.global.application.security.AdminAuthAspect
 * @see com.commonground.be.global.infrastructure.security.admin.AdminTokenValidator
 * @since 1.0
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface AdminRequired {

	/**
	 * 관리자 권한 체크 실패 시 반환할 에러 메시지
	 *
	 * <p>기본값은 일반적인 권한 부족 메시지이며,
	 * 특정 기능에 맞는 상세한 메시지로 커스터마이징할 수 있습니다.</p>
	 *
	 * <h3>사용 예시:</h3>
	 * <pre>
	 * {@code
	 * @AdminRequired(message = "사용자 데이터 조회는 관리자만 가능합니다")
	 * public List<User> getUsers() { ... }
	 *
	 * @AdminRequired(message = "시스템 설정 변경 권한이 없습니다")
	 * public void updateSystemConfig() { ... }
	 * }
	 * </pre>
	 *
	 * @return 권한 체크 실패 시 사용할 에러 메시지
	 */
	String message() default "관리자 권한이 필요합니다";

	/**
	 * 권한 체크를 수행할지 여부를 제어합니다.
	 *
	 * <p>개발/테스트 환경에서 임시로 권한 체크를 비활성화하거나,
	 * 조건부로 권한 체크를 수행하고 싶을 때 사용할 수 있습니다.</p>
	 *
	 * <h3>사용 예시:</h3>
	 * <pre>
	 * {@code
	 * @AdminRequired(enabled = false) // 권한 체크 비활성화
	 * @GetMapping("/admin/test")
	 * public String testEndpoint() { ... }
	 * }
	 * </pre>
	 *
	 * @return 권한 체크 활성화 여부 (기본값: true)
	 * @apiNote 프로덕션 환경에서는 항상 true로 유지해야 합니다.
	 */
	boolean enabled() default true;

	/**
	 * 관리자 권한 체크 시 로깅 레벨을 지정합니다.
	 *
	 * <p>보안 감사나 디버깅을 위해 권한 체크 과정을 로깅할 때 사용됩니다.
	 * 민감한 관리자 기능의 경우 상세한 로깅을 활성화할 수 있습니다.</p>
	 *
	 * <h3>로깅 레벨:</h3>
	 * <ul>
	 *   <li>NONE: 로깅 안 함</li>
	 *   <li>INFO: 권한 체크 성공/실패 기본 정보</li>
	 *   <li>DEBUG: 상세한 권한 체크 과정</li>
	 * </ul>
	 *
	 * @return 로깅 레벨 (기본값: INFO)
	 */
	LogLevel logLevel() default LogLevel.INFO;

	/**
	 * 관리자 권한 체크 로깅 레벨을 정의하는 열거형
	 */
	enum LogLevel {
		/**
		 * 로깅 안 함
		 */
		NONE,
		/**
		 * 기본 정보 로깅
		 */
		INFO,
		/**
		 * 상세 정보 로깅
		 */
		DEBUG
	}
}