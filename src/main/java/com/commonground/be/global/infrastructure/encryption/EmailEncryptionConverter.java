package com.commonground.be.global.infrastructure.encryption;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 이메일 필드 자동 암호화/복호화 JPA Converter
 */
@Slf4j
@Component
@Converter
@RequiredArgsConstructor
public class EmailEncryptionConverter implements AttributeConverter<String, String> {

    private final FieldEncryption fieldEncryption;

    @Override
    public String convertToDatabaseColumn(String attribute) {
        if (attribute == null || attribute.isEmpty()) {
            return attribute;
        }
        
        try {
            String encrypted = fieldEncryption.encrypt(attribute);
            log.debug("이메일 암호화 완료");
            return encrypted;
        } catch (Exception e) {
            log.error("이메일 암호화 실패: {}", e.getMessage());
            throw new RuntimeException("이메일 암호화 처리 중 오류 발생", e);
        }
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isEmpty()) {
            return dbData;
        }
        
        try {
            String decrypted = fieldEncryption.decrypt(dbData);
            log.debug("이메일 복호화 완료");
            return decrypted;
        } catch (Exception e) {
            log.error("이메일 복호화 실패: {}", e.getMessage());
            throw new RuntimeException("이메일 복호화 처리 중 오류 발생", e);
        }
    }
}