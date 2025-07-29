package com.commonground.be.domain.session.dto;

import lombok.Builder;
import lombok.Data;

/**
 * 세션 생성 요청 DTO
 */
@Data
@Builder
public class SessionCreateRequest {
    private String userId;
    private String userAgent;
}