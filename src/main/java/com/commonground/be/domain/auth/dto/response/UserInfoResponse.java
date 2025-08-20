package com.commonground.be.domain.auth.dto.response;

import com.commonground.be.domain.user.entity.User;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserInfoResponse {
    private Long id;
    private String username;
    private String name;
    private String email;
    private String role;
    
    public static UserInfoResponse from(User user) {
        return new UserInfoResponse(
            user.getId(),
            user.getUsername(),
            user.getName(),
            user.getEmail(),
            user.getUserRole().toString()
        );
    }
}