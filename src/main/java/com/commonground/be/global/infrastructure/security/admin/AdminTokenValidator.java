package com.commonground.be.global.infrastructure.security.admin;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 관리자 마스터 토큰 검증 컴포넌트
 *
 * <p>이 클래스는 환경변수로 설정된 관리자 마스터 토큰을 검증하여
 * 시스템 관리자가 별도의 인증 과정 없이 모든 API에 접근할 수 있도록 합니다.</p>
 *
 * <h3>주요 기능:</h3>
 * <ul>
 *   <li>환경변수 기반 마스터 토큰 관리</li>
 *   <li>Bearer 토큰 형식 지원</li>
 *   <li>X-Admin-Token 헤더 지원</li>
 *   <li>토큰 검증 로깅</li>
 * </ul>
 *
 * <h3>사용법:</h3>
 * <pre>
 * // HTTP 요청 헤더에 다음 중 하나 사용:
 * Authorization: Bearer {master_token}
 * X-Admin-Token: {master_token}
 * </pre>
 *
 * <h3>설정:</h3>
 * <pre>
 * # application.yml
 * admin:
 *   master-token: your_secure_master_token_here
 * </pre>
 *
 * @author CommonGround Development Team
 * @see com.commonground.be.global.infrastructure.security.filter.JwtAuthorizationFilter
 * @since 1.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminTokenValidator {

	/**
	 * 환경변수에서 주입받는 마스터 토큰
	 *
	 * <p>이 토큰은 시스템 관리자가 모든 API에 접근할 수 있는 최고 권한 토큰입니다.
	 * 보안을 위해 충분히 복잡하고 예측 불가능한 값으로 설정해야 합니다.</p>
	 */
	@Value("${admin.master-token}")
	private String masterToken;

	/**
	 * 암호화 기반 관리자 토큰 제공자
	 */
	private final AdminTokenProvider adminTokenProvider;

	/**
	 * 관리자 토큰의 유효성을 검증합니다.
	 *
	 * <p>다음 두 가지 방식의 관리자 토큰을 지원합니다:
	 * <ol>
	 *   <li>마스터 토큰: 환경변수로 설정된 고정 토큰 (개발용)</li>
	 *   <li>암호화 토큰: AdminTokenProvider로 생성된 암호화 토큰 (권장)</li>
	 * </ol>
	 * </p>
	 *
	 * @param token 클라이언트에서 전송한 토큰 문자열
	 * @return 유효한 관리자 토큰인 경우 true, 그렇지 않으면 false
	 */
	public boolean isValidAdminToken(String token) {
		if (token == null || token.isEmpty()) {
			log.debug("관리자 토큰이 제공되지 않음");
			return false;
		}

		// Bearer 접두사 제거
		String cleanToken = extractTokenFromBearer(token);

		// 1순위: 암호화 토큰 검증 시도
		if (adminTokenProvider.validateAdminToken(cleanToken)) {
			log.info("관리자 암호화 토큰 인증 성공");
			return true;
		}

		// 2순위: 마스터 토큰 검증 (개발환경 호환)
		if (masterToken.equals(cleanToken)) {
			log.info("관리자 마스터 토큰 인증 성공 - 개발환경");
			return true;
		}

		// 모든 검증 실패
		String tokenPreview = cleanToken.length() > 10
				? cleanToken.substring(0, 10) + "..."
				: cleanToken + "...";
		log.warn("유효하지 않은 관리자 토큰 접근 시도: {}", tokenPreview);

		return false;
	}

	/**
	 * 관리자 권한 확인을 위한 커스텀 HTTP 헤더명
	 *
	 * <p>Authorization 헤더 대신 사용할 수 있는 관리자 전용 헤더입니다.
	 * 이 헤더를 사용하면 JWT 토큰과 관리자 토큰을 명확히 구분할 수 있습니다.</p>
	 *
	 * <h3>사용 예시:</h3>
	 * <pre>
	 * X-Admin-Token: your_master_token_here
	 * </pre>
	 *
	 * @see com.commonground.be.global.infrastructure.security.filter.JwtAuthorizationFilter
	 */
	public static final String ADMIN_TOKEN_HEADER = "X-Admin-Token";

	/**
	 * 설정된 마스터 토큰의 존재 여부를 확인합니다.
	 *
	 * <p>애플리케이션 시작 시 마스터 토큰이 올바르게 설정되었는지 검증하는 데 사용됩니다.
	 * 토큰이 설정되지 않은 경우 관리자 기능이 비활성화됩니다.</p>
	 *
	 * @return 마스터 토큰이 설정된 경우 true, 그렇지 않으면 false
	 * @since 1.0
	 */
	public boolean isMasterTokenConfigured() {
		boolean isConfigured = masterToken != null && !masterToken.trim().isEmpty();

		if (!isConfigured) {
			log.warn("관리자 마스터 토큰이 설정되지 않음 - 관리자 기능 비활성화");
		}

		return isConfigured;
	}

	/**
	 * 토큰에서 Bearer 접두사를 제거하는 유틸리티 메서드
	 *
	 * <p>Authorization 헤더에서 "Bearer " 접두사를 안전하게 제거합니다.
	 * 접두사가 없는 경우 원본 토큰을 그대로 반환합니다.</p>
	 *
	 * @param bearerToken Bearer 접두사가 포함된 토큰 문자열
	 * @return Bearer 접두사가 제거된 순수 토큰 문자열
	 * @throws IllegalArgumentException 입력 토큰이 null인 경우
	 * @since 1.0
	 */
	public static String extractTokenFromBearer(String bearerToken) {
		if (bearerToken == null) {
			throw new IllegalArgumentException("Bearer 토큰은 null일 수 없습니다");
		}

		return bearerToken.startsWith("Bearer ")
				? bearerToken.substring(7)
				: bearerToken;
	}
}