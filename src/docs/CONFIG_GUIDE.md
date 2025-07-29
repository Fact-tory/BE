# CommonGround BE 설정 가이드

## 📋 목차
- [환경변수 설정](#환경변수-설정)
- [소셜 로그인 설정](#소셜-로그인-설정)
- [데이터베이스 설정](#데이터베이스-설정)
- [성능 튜닝](#성능-튜닝)
- [로깅 설정](#로깅-설정)
- [보안 설정](#보안-설정)

## 🔧 환경변수 설정

### 1. 환경변수 파일 생성
```bash
# .env.example을 복사하여 .env 파일 생성
cp .env.example .env

# 실제 값으로 환경변수 수정
vi .env
```

### 2. 필수 환경변수
| 변수명 | 설명 | 예시값 |
|--------|------|---------|
| `SERVER_PORT` | 서버 포트 | `8080` |
| `JWT_SECRET_KEY` | JWT 서명 키 | `openssl rand -base64 32` |
| `DB_URL` | MySQL 주소 | `localhost:3306` |
| `REDIS_HOST` | Redis 주소 | `localhost` |

## 🔐 소셜 로그인 설정

### Google OAuth2 설정
1. [Google Cloud Console](https://console.cloud.google.com/) 접속
2. 새 프로젝트 생성 또는 기존 프로젝트 선택
3. **API 및 서비스** → **사용자 인증 정보** → **OAuth 2.0 클라이언트 ID 생성**
4. 애플리케이션 유형: **웹 애플리케이션**
5. 승인된 리디렉션 URI: `http://localhost:3000/oauth/google`
6. 발급받은 `클라이언트 ID`와 `클라이언트 보안 비밀`을 환경변수에 설정

### Kakao OAuth2 설정
1. [카카오 개발자센터](https://developers.kakao.com/) 접속
2. **내 애플리케이션** → **애플리케이션 추가하기**
3. **앱 키** → **REST API 키** 복사
4. **플랫폼** → **Web** → 도메인 등록: `http://localhost:3000`
5. **카카오 로그인** → **Redirect URI** 등록: `http://localhost:3000/oauth/kakao`
6. **동의항목** → **이메일**, **닉네임** 필수 동의로 설정

## 🗄️ 데이터베이스 설정

### MySQL 8.0 설정
```sql
-- 데이터베이스 생성
CREATE DATABASE factory CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- 사용자 생성 및 권한 부여
CREATE USER 'your_user'@'localhost' IDENTIFIED BY 'your_password';
GRANT ALL PRIVILEGES ON factory.* TO 'your_user'@'localhost';
FLUSH PRIVILEGES;
```

### Redis 설정
```bash
# Redis 서버 실행 (기본 포트 6379)
redis-server

# Redis 연결 테스트
redis-cli ping
```

## ⚡ 성능 튜닝

### 환경별 권장 설정

#### 개발 환경
```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 5
      minimum-idle: 2
  jpa:
    properties:
      hibernate:
        show_sql: true
logging:
  level:
    com.commonground.be: DEBUG
```

#### 운영 환경
```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
  jpa:
    properties:
      hibernate:
        show_sql: false
logging:
  level:
    com.commonground.be: INFO
```

### JVM 튜닝 옵션
```bash
# 메모리 설정
-Xms512m -Xmx1024m

# GC 설정 (Java 11+)
-XX:+UseG1GC -XX:MaxGCPauseMillis=200

# JIT 컴파일 최적화
-XX:+TieredCompilation -XX:TieredStopAtLevel=1
```

## 📊 로깅 설정

### 로그 레벨 가이드
- **TRACE**: 매우 상세한 디버깅 정보
- **DEBUG**: 디버깅 정보 (개발 환경)
- **INFO**: 일반적인 정보 (기본값)
- **WARN**: 경고 메시지
- **ERROR**: 오류 메시지만

### 환경별 권장 로그 레벨
```bash
# 개발 환경
LOG_LEVEL=DEBUG
SQL_LOG_LEVEL=DEBUG
HIBERNATE_SHOW_SQL=true

# 스테이징 환경
LOG_LEVEL=INFO
SQL_LOG_LEVEL=WARN
HIBERNATE_SHOW_SQL=false

# 운영 환경
LOG_LEVEL=WARN
SQL_LOG_LEVEL=ERROR
HIBERNATE_SHOW_SQL=false
```

## 🔒 보안 설정

### 비밀키 생성
```bash
# JWT 비밀키 생성 (256비트)
openssl rand -base64 32

# 필드 암호화 키 생성 (정확히 32자)
openssl rand -hex 16

# 관리자 마스터 토큰 생성
openssl rand -base64 48
```

### 보안 체크리스트
- [ ] 모든 비밀키가 충분히 복잡한가?
- [ ] 환경변수 파일(.env)이 .gitignore에 포함되어 있는가?
- [ ] 운영 환경에서 HTTPS를 사용하는가?
- [ ] 데이터베이스 접근이 적절히 제한되어 있는가?
- [ ] Redis가 패스워드로 보호되어 있는가?

## 🚀 빠른 시작

1. **환경변수 설정**
   ```bash
   cp .env.example .env
   # .env 파일의 값들을 실제 값으로 수정
   ```

2. **데이터베이스 준비**
   ```bash
   # MySQL, Redis 서버 실행 확인
   mysql -u root -p
   redis-cli ping
   ```

3. **애플리케이션 실행**
   ```bash
   ./gradlew bootRun
   ```

4. **헬스체크**
   ```bash
   curl http://localhost:8080/health
   ```

## 🔍 문제 해결

### 자주 발생하는 오류

#### 1. MySQL 연결 오류
```
Caused by: java.sql.SQLException: Access denied for user
```
**해결방법**: DB_USER, DB_PASSWORD 환경변수 확인

#### 2. Redis 연결 오류
```
Unable to connect to Redis
```
**해결방법**: Redis 서버 실행 상태 및 REDIS_HOST, REDIS_PORT 확인

#### 3. JWT 토큰 오류
```
JWT signature does not match locally computed signature
```
**해결방법**: JWT_SECRET_KEY 환경변수 확인 및 재생성

#### 4. 소셜 로그인 오류
```
OAuth2 client authentication failed
```
**해결방법**: Google/Kakao 개발자 콘솔에서 클라이언트 설정 재확인

## 📞 지원

설정 관련 문제가 있을 경우:
1. 로그 파일 확인: `logs/spring.log`
2. 환경변수 검증: `.env` 파일 내용 확인
3. 데이터베이스 연결 테스트
4. Redis 연결 테스트