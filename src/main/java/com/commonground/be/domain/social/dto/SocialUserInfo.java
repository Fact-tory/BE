package com.commonground.be.domain.social.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class SocialUserInfo {
    private final String id;
    private final String username;
    private final String email;
    private final String provider;
}