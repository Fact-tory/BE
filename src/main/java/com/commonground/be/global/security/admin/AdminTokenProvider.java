package com.commonground.be.global.security.admin;

import com.commonground.be.global.security.encrpyt.FieldEncryption;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * 단순 암호화 기반 관리자 토큰 제공자
 * 
 * <p>복잡한 JWT 대신 단순한 암호화 방식을 사용하는 관리자 토큰 시스템입니다.
 * 시간 제한 없이 키 기반 암호화/복호화로 관리자 인증을 처리합니다.</p>
 * 
 * <h3>주요 특징:</h3>
 * <ul>
 *   <li>시간 제한 없는 영구 토큰</li>
 *   <li>단순한 AES 암호화/복호화</li>
 *   <li>고정된 관리자 정보 암호화</li>
 *   <li>키 기반 토큰 검증</li>
 * </ul>
 * 
 * @author CommonGround Development Team
 * @since 1.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminTokenProvider {

    private final FieldEncryption fieldEncryption;
    
    /**
     * 관리자 토큰 식별자
     */
    @Value("${admin.token.identifier:SYSTEM_ADMIN}")
    private String adminIdentifier;

    /**
     * 관리자 토큰을 생성합니다.
     * 
     * <p>고정된 관리자 식별자를 암호화하여 토큰을 생성합니다.
     * 시간 제한이 없으므로 한 번 생성된 토큰은 계속 사용 가능합니다.</p>
     * 
     * @return 암호화된 관리자 토큰
     */
    public String generateAdminToken() {
        String tokenData = String.format("%s|%s|ADMIN", adminIdentifier, Instant.now().getEpochSecond());
        String encryptedToken = fieldEncryption.encrypt(tokenData);
        
        log.info("관리자 토큰 생성 완료");
        return encryptedToken;
    }

    /**
     * 관리자 토큰의 유효성을 검증합니다.
     * 
     * @param token 검증할 암호화된 토큰
     * @return 유효한 관리자 토큰인 경우 true
     */
    public boolean validateAdminToken(String token) {
        if (token == null || token.trim().isEmpty()) {
            return false;
        }

        try {
            String decryptedData = fieldEncryption.decrypt(token);
            
            // 토큰 데이터 파싱: IDENTIFIER|TIMESTAMP|ROLE
            String[] parts = decryptedData.split("\\|");
            if (parts.length >= 3) {
                String identifier = parts[0];
                String role = parts[2];
                
                boolean isValid = adminIdentifier.equals(identifier) && "ADMIN".equals(role);
                
                if (isValid) {
                    log.debug("관리자 암호화 토큰 검증 성공");
                } else {
                    log.warn("잘못된 관리자 토큰 데이터: identifier={}, role={}", identifier, role);
                }
                
                return isValid;
            }
            
            log.warn("관리자 토큰 형식 오류: {}", decryptedData);
            return false;
            
        } catch (Exception e) {
            log.warn("관리자 토큰 복호화 실패: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 토큰에서 관리자 정보를 추출합니다.
     * 
     * @param token 암호화된 토큰
     * @return 관리자 정보, 유효하지 않은 경우 null
     */
    public AdminInfo getAdminInfo(String token) {
        if (!validateAdminToken(token)) {
            return null;
        }

        try {
            String decryptedData = fieldEncryption.decrypt(token);
            String[] parts = decryptedData.split("\\|");
            
            return AdminInfo.builder()
                    .identifier(parts[0])
                    .createdAt(Instant.ofEpochSecond(Long.parseLong(parts[1])))
                    .role(parts[2])
                    .build();
                    
        } catch (Exception e) {
            log.error("관리자 정보 추출 실패: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 관리자 정보를 담는 클래스
     */
    public static class AdminInfo {
        private final String identifier;
        private final Instant createdAt;
        private final String role;

        private AdminInfo(Builder builder) {
            this.identifier = builder.identifier;
            this.createdAt = builder.createdAt;
            this.role = builder.role;
        }

        public static Builder builder() {
            return new Builder();
        }

        public String getIdentifier() { return identifier; }
        public Instant getCreatedAt() { return createdAt; }
        public String getRole() { return role; }

        @Override
        public String toString() {
            return String.format("AdminInfo{identifier='%s', role='%s', createdAt=%s}",
                    identifier, role, createdAt);
        }

        public static class Builder {
            private String identifier;
            private Instant createdAt;
            private String role;

            public Builder identifier(String identifier) { this.identifier = identifier; return this; }
            public Builder createdAt(Instant createdAt) { this.createdAt = createdAt; return this; }
            public Builder role(String role) { this.role = role; return this; }

            public AdminInfo build() {
                return new AdminInfo(this);
            }
        }
    }
}