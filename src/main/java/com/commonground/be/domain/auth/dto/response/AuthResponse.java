package com.commonground.be.domain.auth.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponse {
    private boolean success;
    private String message;
    private Map<String, Object> data;
    
    public static AuthResponse success(String message) {
        return new AuthResponse(true, message, null);
    }
    
    public static AuthResponse success(String message, Map<String, Object> data) {
        return new AuthResponse(true, message, data);
    }
    
    public static AuthResponse failure(String message) {
        return new AuthResponse(false, message, null);
    }
}