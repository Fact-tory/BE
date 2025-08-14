package com.commonground.be.global.application.exception;

import com.commonground.be.global.application.response.ResponseExceptionEnum;
import lombok.Getter;

@Getter
public class CommonException extends RuntimeException {

    private final ResponseExceptionEnum responseExceptionEnum;

    public CommonException(ResponseExceptionEnum responseExceptionEnum) {
        super(responseExceptionEnum.getMessage());
        this.responseExceptionEnum = responseExceptionEnum;
    }

}
