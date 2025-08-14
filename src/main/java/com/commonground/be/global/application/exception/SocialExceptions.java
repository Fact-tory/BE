package com.commonground.be.global.application.exception;

import com.commonground.be.global.application.response.ResponseExceptionEnum;

public class SocialExceptions {

    public static CommonException invalidProvider() {
        return new CommonException(ResponseExceptionEnum.INVALID_SOCIAL_PROVIDER);
    }

    public static CommonException tokenRequestFailed() {
        return new CommonException(ResponseExceptionEnum.SOCIAL_TOKEN_REQUEST_FAILED);
    }

    public static CommonException userInfoRequestFailed() {
        return new CommonException(ResponseExceptionEnum.SOCIAL_USER_INFO_REQUEST_FAILED);
    }

    public static CommonException invalidTokenResponse() {
        return new CommonException(ResponseExceptionEnum.INVALID_SOCIAL_TOKEN_RESPONSE);
    }

    public static CommonException invalidUserInfoResponse() {
        return new CommonException(ResponseExceptionEnum.INVALID_SOCIAL_USER_INFO_RESPONSE);
    }

}