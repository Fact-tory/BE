package com.commonground.be.global.security.admin;

import com.commonground.be.global.security.FieldEncryption;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * 관리자 토큰 생성 유틸리티
 * 
 * <p>애플리케이션 시작 시 또는 수동으로 관리자 토큰을 생성할 수 있는 유틸리티입니다.
 * 단순한 암호화 기반으로 영구 사용 가능한 관리자 토큰을 생성합니다.</p>
 * 
 * <h3>사용법:</h3>
 * <pre>
 * # 애플리케이션 실행 시 자동 생성 (로그 확인)
 * java -jar app.jar
 * 
 * # 수동 생성 (프로그래밍 방식)
 * AdminTokenGenerator generator = ...;
 * String token = generator.generateToken();
 * </pre>
 * 
 * @author CommonGround Development Team
 * @since 1.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminTokenGenerator implements CommandLineRunner {

    private final FieldEncryption fieldEncryption;
    private final AdminTokenProvider adminTokenProvider;
    
    @Value("${admin.token.identifier:SYSTEM_ADMIN}")
    private String adminIdentifier;
    
    @Value("${admin.token.auto-generate:true}")
    private boolean autoGenerate;

    /**
     * 애플리케이션 시작 시 실행 - 관리자 토큰 자동 생성
     */
    @Override
    public void run(String... args) throws Exception {
        if (autoGenerate) {
            generateAndLogAdminToken();
        }
    }

    /**
     * 관리자 토큰을 생성하고 로그에 출력합니다.
     */
    public void generateAndLogAdminToken() {
        try {
            String adminToken = adminTokenProvider.generateAdminToken();
            
            log.info("=".repeat(80));
            log.info("🔐 관리자 토큰이 생성되었습니다");
            log.info("=".repeat(80));
            log.info("Token: {}", adminToken);
            log.info("");
            log.info("사용법:");
            log.info("  Authorization: Bearer {}", adminToken);
            log.info("  X-Admin-Token: {}", adminToken);
            log.info("");
            log.info("curl 예시:");
            log.info("  curl -H \"Authorization: Bearer {}\" http://localhost:8080/api/v1/admin/users", adminToken);
            log.info("  curl -H \"X-Admin-Token: {}\" http://localhost:8080/api/v1/users/profile", adminToken);
            log.info("=".repeat(80));
            
        } catch (Exception e) {
            log.error("관리자 토큰 생성 실패: {}", e.getMessage());
        }
    }

    /**
     * 새로운 관리자 토큰을 생성합니다.
     * 
     * @return 암호화된 관리자 토큰
     */
    public String generateToken() {
        return adminTokenProvider.generateAdminToken();
    }

    /**
     * 토큰 검증 테스트
     * 
     * @param token 검증할 토큰
     * @return 검증 결과
     */
    public boolean testToken(String token) {
        return adminTokenProvider.validateAdminToken(token);
    }

    /**
     * 개발환경용 메인 메서드 - 토큰 생성 및 테스트
     */
    public static void main(String[] args) {
        System.out.println("관리자 토큰 생성기");
        System.out.println("실제 토큰 생성은 애플리케이션 실행 시 자동으로 수행됩니다.");
        System.out.println("애플리케이션 로그를 확인하세요.");
    }
}