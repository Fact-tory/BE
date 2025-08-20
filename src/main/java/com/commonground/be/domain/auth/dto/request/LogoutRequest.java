package com.commonground.be.domain.auth.dto.request;

import lombok.Data;

@Data
public class LogoutRequest {
    private Boolean everywhere = false;
    private String kakaoAccessToken;
}