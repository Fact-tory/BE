-- =================================
-- Factory BE MySQL 초기화 스크립트
-- 데이터베이스 및 사용자 권한 설정
-- =================================

-- UTF8MB4 문자셋으로 데이터베이스 생성 (이모지 지원)
CREATE DATABASE IF NOT EXISTS factory
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

-- 애플리케이션 전용 사용자 생성 및 권한 부여
CREATE USER IF NOT EXISTS 'factory_user'@'%' IDENTIFIED BY 'factory_dev_pass';
GRANT ALL PRIVILEGES ON factory.* TO 'factory_user'@'%';

-- 테스트 데이터베이스 생성 (CI/CD 및 로컬 테스트용)
CREATE DATABASE IF NOT EXISTS factory_test
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

GRANT ALL PRIVILEGES ON factory_test.* TO 'factory_user'@'%';

-- 권한 변경사항 적용
FLUSH PRIVILEGES;

-- 데이터베이스 생성 확인
SHOW DATABASES;