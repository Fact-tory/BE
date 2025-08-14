package com.commonground.be.global.application.exception;

import com.commonground.be.global.application.response.ResponseExceptionEnum;

public class UserExceptions {

    public static CommonException userNotFound() {
        return new CommonException(ResponseExceptionEnum.USER_NOT_FOUND);
    }

    public static CommonException userAlreadyExists() {
        return new CommonException(ResponseExceptionEnum.USER_ALREADY_EXIST);
    }

    public static CommonException userDeleted() {
        return new CommonException(ResponseExceptionEnum.USER_DELETED);
    }

    public static CommonException userNotDeleted() {
        return new CommonException(ResponseExceptionEnum.USER_NOT_DELETED);
    }

    public static CommonException authenticationMismatch() {
        return new CommonException(ResponseExceptionEnum.NOT_FOUND_AUTHENTICATION_INFO);
    }

    public static CommonException signupFailed() {
        return new CommonException(ResponseExceptionEnum.USER_FAIL_SIGNUP);
    }

    public static CommonException userError() {
        return new CommonException(ResponseExceptionEnum.USER_ERROR);
    }

    public static CommonException notManager() {
        return new CommonException(ResponseExceptionEnum.USER_NOT_MANAGER);
    }

}