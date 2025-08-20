# OAuth2 설정 가이드 (실제 개발/배포용)

## 개요
이 가이드는 Google, Kakao OAuth2를 실제 개발 및 배포 환경에서 설정하는 방법을 안내합니다.

## 1. Google OAuth2 설정

### Google Cloud Console에서 프로젝트 생성
1. [Google Cloud Console](https://console.cloud.google.com/) 접속
2. 새 프로젝트 생성 또는 기존 프로젝트 선택
3. "API 및 서비스" → "사용자 인증 정보" 이동

### OAuth2 클라이언트 ID 생성
1. "사용자 인증 정보 만들기" → "OAuth 클라이언트 ID" 선택
2. 애플리케이션 유형: "웹 애플리케이션" 선택
3. 이름: 프로젝트명 입력
4. **승인된 리디렉션 URI** 추가:
   ```
   개발환경: http://localhost:8080/login/oauth2/code/google
   배포환경: https://yourdomain.com/login/oauth2/code/google
   ```
5. 생성 후 **클라이언트 ID**와 **클라이언트 보안 비밀번호** 저장

### Google API 활성화
- "API 및 서비스" → "라이브러리"에서 **Google+ API** 활성화

## 2. Kakao OAuth2 설정

### Kakao Developers에서 앱 생성
1. [Kakao Developers](https://developers.kakao.com/) 접속
2. "내 애플리케이션" → "애플리케이션 추가하기"
3. 앱 이름, 사업자명 등 정보 입력

### OAuth2 설정
1. 생성된 앱 선택 → "제품 설정" → "카카오 로그인"
2. "카카오 로그인 활성화 설정" ON
3. **Redirect URI** 설정:
   ```
   개발환경: http://localhost:8080/login/oauth2/code/kakao  
   배포환경: https://yourdomain.com/login/oauth2/code/kakao
   ```

### 필수 동의항목 설정
1. "제품 설정" → "카카오 로그인" → "동의항목"
2. 다음 항목을 필수로 설정:
   - 프로필 정보 (닉네임/프로필 사진): **필수**
   - 카카오계정 (이메일): **필수**

### API 키 확인
1. "앱 설정" → "앱 키"에서 **REST API 키** 복사
2. "제품 설정" → "카카오 로그인" → "보안"에서 **Client Secret** 활성화 후 복사

## 3. Spring Boot 설정

### application.yml 설정
```yaml
spring:
  security:
    oauth2:
      client:
        registration:
          google:
            client-id: ${GOOGLE_CLIENT_ID}
            client-secret: ${GOOGLE_CLIENT_SECRET}
            scope: profile,email
            redirect-uri: "{baseUrl}/login/oauth2/code/{registrationId}"
          
          kakao:
            client-id: ${KAKAO_CLIENT_ID}
            client-secret: ${KAKAO_CLIENT_SECRET}  # 선택사항, 보안 강화시 사용
            client-authentication-method: client_secret_post
            authorization-grant-type: authorization_code
            redirect-uri: "{baseUrl}/login/oauth2/code/{registrationId}"
            scope: profile_nickname,profile_image,account_email
            client-name: Kakao

        provider:
          kakao:
            authorization-uri: https://kauth.kakao.com/oauth/authorize
            token-uri: https://kauth.kakao.com/oauth/token
            user-info-uri: https://kapi.kakao.com/v2/user/me
            user-name-attribute: id

# Frontend 설정
app:
  frontend:
    url: ${FRONTEND_URL:http://localhost:3000}  # 프론트엔드 URL
```

### 환경 변수 설정
```bash
# .env 파일 또는 시스템 환경변수
GOOGLE_CLIENT_ID=your-google-client-id
GOOGLE_CLIENT_SECRET=your-google-client-secret
KAKAO_CLIENT_ID=your-kakao-rest-api-key
KAKAO_CLIENT_SECRET=your-kakao-client-secret
FRONTEND_URL=http://localhost:3000
```

## 4. 프로덕션 배포 설정

### 도메인별 환경 설정
```yaml
# application-prod.yml
app:
  frontend:
    url: https://yourdomain.com

spring:
  security:
    oauth2:
      client:
        registration:
          google:
            redirect-uri: https://api.yourdomain.com/login/oauth2/code/google
          kakao:
            redirect-uri: https://api.yourdomain.com/login/oauth2/code/kakao
```

### HTTPS 필수 설정
- **배포환경에서는 반드시 HTTPS 사용**
- OAuth2 Provider들은 HTTP redirect를 허용하지 않음
- SSL 인증서 설정 필요

## 5. 보안 고려사항

### Client Secret 관리
- 환경변수로 관리, 코드에 하드코딩 금지
- 프로덕션과 개발 환경 분리
- 주기적 Client Secret 갱신

### Redirect URI 검증
- 정확한 도메인과 경로 설정
- 와일드카드 사용 금지
- 테스트용 localhost는 개발환경에서만 사용

### 스코프 최소화 원칙
- 필요한 권한만 요청
- 사용자 동의 최소화

## 6. 테스트 방법

### 개발환경 테스트
1. 브라우저에서 접속:
   ```
   http://localhost:8080/oauth2/authorization/google
   http://localhost:8080/oauth2/authorization/kakao
   ```

2. OAuth2 성공 후 JWT 쿠키 확인:
   ```bash
   # Chrome DevTools → Application → Cookies
   AccessToken: eyJhbGciOi...
   RefreshToken: eyJhbGciOi...
   ```

### API 테스트
```bash
# 사용자 정보 조회
curl -X GET "http://localhost:8080/api/v1/auth/me" \
  -H "Cookie: AccessToken=eyJhbGciOi..."

# 토큰 검증
curl -X GET "http://localhost:8080/api/v1/auth/validate" \
  -H "Cookie: AccessToken=eyJhbGciOi..."
```

## 7. 문제 해결

### 자주 발생하는 오류
1. **redirect_uri_mismatch**: Redirect URI 불일치
   - OAuth2 콘솔에서 정확한 URI 설정 확인

2. **invalid_client**: Client ID/Secret 오류
   - 환경변수 설정 확인
   - 복사/붙여넣기 시 공백 제거

3. **access_denied**: 사용자가 권한 거부
   - 동의항목 재확인
   - 필수 권한 최소화

### 로그 확인
```yaml
logging:
  level:
    org.springframework.security: DEBUG
    com.commonground.be: DEBUG
```

## 8. 현재 프로젝트 상태

✅ **구현 완료:**
- **CustomOAuth2UserService**: OAuth2User → User 엔티티 자동 변환
- **암호화된 이메일 처리**: DB 저장/조회 시 자동 암호화/복호화
- **JWT 토큰에 이메일+이름 포함**: 향상된 사용자 식별
- **Redis 기반 Refresh Token 관리**: `RT:user:{userId}` 키 전략으로 동명이인 문제 해결
- **OAuth2 성공 후 자동 JWT 발급**: AuthFacade 파이프라인 통합
- **HttpOnly 쿠키 기반 보안**: XSS 공격 방지
- **세션 관리**: UserAgent 기반 기기 추적
- **통합 로그아웃**: 단일/전체 기기 로그아웃 지원

🔧 **최근 개선사항:**
- Redis 키 전략 개선: `RT:{username}` → `RT:user:{userId}` (동명이인 문제 해결)
- OAuth2 흐름 완전 통합: SecurityConfig → CustomOAuth2UserService → AuthFacade → JWT 발급
- 테스트 파일 정리: 불필요한 통합 테스트 클래스 제거

⚠️ **설정 필요:**
- 실제 Google/Kakao OAuth2 앱 등록
- 프로덕션 환경 도메인 설정  
- HTTPS 인증서 설정

## 9. 테스트 방법

### 빠른 테스트
```bash
# 1. 애플리케이션 실행
./gradlew bootRun

# 2. 브라우저에서 OAuth2 로그인 테스트
http://localhost:8080/oauth2/authorization/google
http://localhost:8080/oauth2/authorization/kakao
```

### HTTP 파일로 API 테스트
```bash
# tests/api/12_oauth2_auth.http 파일 사용
# OAuth2 로그인 → JWT 토큰 확인 → API 호출 테스트
```

현재 OAuth2 소셜로그인 전체 파이프라인이 완성되었으며, 실제 환경에서 사용하려면 위 가이드에 따라 OAuth2 앱 등록만 하면 됩니다.