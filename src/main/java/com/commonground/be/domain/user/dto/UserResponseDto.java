package com.commonground.be.domain.user.dto;


import com.commonground.be.domain.user.entity.User;
import lombok.Getter;

@Getter
public class UserResponseDto {

    private Long userId;
    private String username;
    private String name;
    private String nickname;
    private String email;

    public UserResponseDto(User user) {
        this.userId = user.getId();
        this.username = user.getUsername();
        this.name = user.getName();
        this.nickname = user.getNickname();
        this.email = user.getEmail();
    }
}
