package com.commonground.be.global.application.exception;

import com.commonground.be.global.application.response.ResponseExceptionEnum;

public class AuthExceptions {

	public static CommonException refreshTokenUnavailable() {
		return new CommonException(ResponseExceptionEnum.REFRESH_TOKEN_UNAVAILABLE);
	}

	public static CommonException invalidRefreshToken() {
		return new CommonException(ResponseExceptionEnum.INVALID_REFRESHTOKEN);
	}

	public static CommonException unauthorizedAccess() {
		return new CommonException(ResponseExceptionEnum.UNAUTHORIZED_ACCESS);
	}

	public static CommonException roleChangeFailed() {
		return new CommonException(ResponseExceptionEnum.FAIL_TO_CHANGE_ROLE);
	}

}