package com.commonground.be.domain.social.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class SocialUserInfo {
    private final String socialId;
    private final String name;
    private final String email;
    private final String provider;
    
    // 기존 메서드명 호환성을 위한 getter 추가
    public String getId() {
        return this.socialId;
    }
    
    public String getUsername() {
        return this.name;
    }
}