package com.commonground.be.domain.auth.controller;

import com.commonground.be.domain.auth.dto.request.LogoutRequest;
import com.commonground.be.domain.auth.dto.request.WithdrawRequest;
import com.commonground.be.domain.auth.facade.AuthFacade;
import com.commonground.be.global.application.response.HttpResponseDto;
import com.commonground.be.global.application.response.ResponseCodeEnum;
import com.commonground.be.global.application.response.ResponseUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * OAuth2 소셜로그인 기반 인증 컨트롤러
 * 실제 OAuth2 로그인은 Spring Security가 자동 처리 (/oauth2/authorization/{provider})
 * 이 컨트롤러는 인증 후 사용자 관리 API만 제공
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

	private final AuthFacade authFacade;

	/**
	 * 토큰 재발급
	 * POST /api/v1/auth/reissue
	 */
	@PostMapping("/reissue")
	public ResponseEntity<HttpResponseDto> reissueAccessToken(
			HttpServletRequest request,
			HttpServletResponse response
	) {
		log.info("🔄 [CONTROLLER] 토큰 재발급 요청 - URI: {}, Method: {}, Client-IP: {}", 
				request.getRequestURI(), request.getMethod(), getClientIp(request));

		try {
			// 🔄 Facade를 통한 토큰 재발급 파이프라인
			AuthFacade.AuthFlowResult flowResult = authFacade.reissueTokenFlow(request, response);

			if (flowResult.isSuccess()) {
				log.info("✅ [CONTROLLER] 토큰 재발급 성공 - Client-IP: {}", getClientIp(request));
				return ResponseUtils.of(ResponseCodeEnum.SUCCESS, flowResult.getData());
			} else {
				log.warn("❌ [CONTROLLER] 토큰 재발급 실패 - Client-IP: {}, 오류: {}", 
						getClientIp(request), flowResult.getMessage());
				return ResponseUtils.of(ResponseCodeEnum.UNAUTHORIZED_ACCESS, 
						Map.of("message", flowResult.getMessage(), "success", false));
			}
		} catch (Exception e) {
			log.error("💥 [CONTROLLER] 토큰 재발급 처리 중 예외 발생 - Client-IP: {}, 오류: {}", 
					getClientIp(request), e.getMessage(), e);
			return ResponseUtils.of(ResponseCodeEnum.INTERNAL_SERVER_ERROR, 
					Map.of("message", "토큰 재발급 처리 중 오류가 발생했습니다.", "success", false, "error", e.getMessage()));
		}
	}

	/**
	 * 클라이언트 IP 추출 (디버깅용)
	 */
	private String getClientIp(HttpServletRequest request) {
		String xForwardedFor = request.getHeader("X-Forwarded-For");
		if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
			return xForwardedFor.split(",")[0].trim();
		}
		String xRealIp = request.getHeader("X-Real-IP");
		if (xRealIp != null && !xRealIp.isEmpty()) {
			return xRealIp;
		}
		return request.getRemoteAddr();
	}

	/**
	 * 현재 사용자 정보 조회
	 * GET /api/v1/auth/me
	 */
	@GetMapping("/me")
	public ResponseEntity<HttpResponseDto> getCurrentUser(
			@AuthenticationPrincipal UserDetails userDetails
	) {
		// 👤 Facade를 통한 사용자 정보 조회 파이프라인
		AuthFacade.AuthFlowResult flowResult = authFacade.getCurrentUserFlow(userDetails);

		ResponseCodeEnum responseCode = flowResult.isSuccess() ? ResponseCodeEnum.SUCCESS
				: ResponseCodeEnum.UNAUTHORIZED_ACCESS;
		return ResponseUtils.of(responseCode, flowResult.getData());
	}

	/**
	 * 통합 로그아웃 (일반 로그아웃)
	 * POST /api/v1/auth/logout
	 */
	@PostMapping("/logout")
	public ResponseEntity<HttpResponseDto> logout(
			@AuthenticationPrincipal UserDetails userDetails,
			@RequestBody(required = false) LogoutRequest logoutRequest,
			HttpServletRequest request,
			HttpServletResponse response
	) {
		// 🚪 Facade를 통한 통합 로그아웃 파이프라인
		AuthFacade.AuthFlowResult flowResult = authFacade.logoutFlow(userDetails, logoutRequest,
				request, response);

		ResponseCodeEnum responseCode = flowResult.isSuccess() ? ResponseCodeEnum.SUCCESS
				: ResponseCodeEnum.INTERNAL_SERVER_ERROR;
		return ResponseUtils.of(responseCode, Map.of("message", flowResult.getMessage()));
	}

	// 회원탈퇴 기능 제거 - 소셜 로그인 전용 서비스
	// 데이터 보관 정책: 약관을 통한 정기적 정리 예정

	/**
	 * 토큰 검증 (선택사항 - 프론트엔드에서 토큰 상태 확인용)
	 * GET /api/v1/auth/validate
	 */
	@GetMapping("/validate")
	public ResponseEntity<HttpResponseDto> validateToken(
			@AuthenticationPrincipal UserDetails userDetails
	) {
		// 인증된 사용자라면 토큰이 유효함
		if (userDetails != null) {
			return ResponseUtils.of(ResponseCodeEnum.SUCCESS, Map.of(
					"valid", true,
					"username", userDetails.getUsername(),
					"authorities", userDetails.getAuthorities()
			));
		} else {
			return ResponseUtils.of(ResponseCodeEnum.UNAUTHORIZED_ACCESS, Map.of(
					"valid", false,
					"message", "토큰이 유효하지 않습니다."
			));
		}
	}
}
