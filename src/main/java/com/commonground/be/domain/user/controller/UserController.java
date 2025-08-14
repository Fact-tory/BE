package com.commonground.be.domain.user.controller;


import static com.commonground.be.global.application.response.ResponseCodeEnum.SUCCESS_LOGOUT;
import static com.commonground.be.global.application.response.ResponseCodeEnum.USER_DELETE_SUCCESS;
import static com.commonground.be.global.application.response.ResponseCodeEnum.USER_SUCCESS_GET;
import static com.commonground.be.global.application.response.ResponseCodeEnum.USER_UPDATE_SUCCESS;
import static com.commonground.be.global.application.response.ResponseUtils.of;

import com.commonground.be.domain.session.service.SessionService;
import com.commonground.be.domain.user.dto.ProfileUpdateRequestDto;
import com.commonground.be.domain.user.dto.UserResponseDto;
import com.commonground.be.domain.user.dto.UserRoleDto;
import com.commonground.be.domain.user.service.UserService;
import com.commonground.be.global.application.response.HttpResponseDto;
import com.commonground.be.global.infrastructure.security.jwt.JwtProvider;
import com.commonground.be.global.infrastructure.security.jwt.TokenManager;
import com.commonground.be.global.domain.security.UserDetailsImpl;
import com.commonground.be.global.domain.security.AdminUserDetails;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/users")
public class UserController {

	private static final Logger log = LoggerFactory.getLogger(UserController.class);
	private final UserService userService;
	private final SessionService sessionService;
	private final TokenManager tokenManager;
	private final JwtProvider jwtProvider;


	@PostMapping("/logout")
	public ResponseEntity<HttpResponseDto> logout(
			@AuthenticationPrincipal UserDetailsImpl userDetails,
			HttpServletRequest request,
			HttpServletResponse response
	) {
		String username = userDetails.getUsername();

		// 1. 현재 세션 무효화 (JWT에서 세션 ID 추출)
		String accessToken = jwtProvider.getAccessTokenFromHeader(request);
		if (accessToken != null) {
			String sessionId = jwtProvider.getClaimFromToken(accessToken, "sessionId");
			if (sessionId != null) {
				sessionService.invalidateSession(sessionId);
			}
		}

		// 2. 모든 토큰 무효화 (토큰 버전 증가)
		tokenManager.invalidateAllUserTokens(username);

		// 3. 쿠키에서 Refresh Token 제거
		jwtProvider.clearCookie(response, JwtProvider.REFRESH_TOKEN_COOKIE_NAME);

		log.info("사용자 로그아웃 완료: {}", username);
		return of(SUCCESS_LOGOUT);
	}


	@GetMapping("/profile")
	public ResponseEntity<HttpResponseDto> getUser(
			@AuthenticationPrincipal Object userDetails
	) {
		// 관리자 토큰인 경우 첫 번째 일반 사용자 정보를 반환 (예시)
		if (userDetails instanceof AdminUserDetails) {
			List<UserResponseDto> users = userService.getAllUsersForAdmin();
			if (!users.isEmpty()) {
				return of(USER_SUCCESS_GET, users.get(0));
			}
		}

		// 일반 사용자인 경우
		if (userDetails instanceof UserDetailsImpl) {
			UserDetailsImpl normalUser = (UserDetailsImpl) userDetails;
			UserResponseDto response = userService.getUser(normalUser.getUser().getId());
			return of(USER_SUCCESS_GET, response);
		}

		throw new SecurityException("인증 정보를 찾을 수 없습니다");
	}

	@DeleteMapping("/withdraw")
	public ResponseEntity<HttpResponseDto> withdraw(
			@AuthenticationPrincipal Object userDetails
	) {
		// 관리자는 회원탈퇴 불가 (의미 없음)
		if (userDetails instanceof AdminUserDetails) {
			throw new SecurityException("관리자는 회원탈퇴를 할 수 없습니다");
		}

		if (userDetails instanceof UserDetailsImpl) {
			UserDetailsImpl normalUser = (UserDetailsImpl) userDetails;
			userService.withdraw(normalUser.getUser());
			return of(USER_DELETE_SUCCESS);
		}

		throw new SecurityException("인증 정보를 찾을 수 없습니다");
	}


	@PutMapping("/update")
	public ResponseEntity<HttpResponseDto> update(
			@AuthenticationPrincipal Object userDetails,
			@RequestBody ProfileUpdateRequestDto updateDto
	) {
		// 관리자는 프로필 수정 불가 (별도 관리)
		if (userDetails instanceof AdminUserDetails) {
			throw new SecurityException("관리자는 이 API를 사용할 수 없습니다");
		}

		if (userDetails instanceof UserDetailsImpl) {
			UserDetailsImpl normalUser = (UserDetailsImpl) userDetails;
			userService.update(normalUser.getUser(), updateDto);
			return of(USER_UPDATE_SUCCESS);
		}

		throw new SecurityException("인증 정보를 찾을 수 없습니다");
	}


	@GetMapping("/role")
	public UserRoleDto getUserInfo(@AuthenticationPrincipal Object userDetails) {
		// 관리자 토큰인 경우
		if (userDetails instanceof AdminUserDetails) {
			AdminUserDetails admin = (AdminUserDetails) userDetails;
			log.info("admin Role : MANAGER");
			return new UserRoleDto(admin.getUsername(), admin.getUser().getUserRole());
		}

		// 일반 사용자인 경우
		if (userDetails instanceof UserDetailsImpl) {
			UserDetailsImpl normalUser = (UserDetailsImpl) userDetails;
			log.info("user Role : {} ", normalUser.getUser().getUserRole());
			return new UserRoleDto(normalUser.getUsername(), normalUser.getUser().getUserRole());
		}

		throw new SecurityException("인증 정보를 찾을 수 없습니다");
	}

}
