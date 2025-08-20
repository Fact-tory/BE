# 📋 REST API 테스트 가이드

## 🎯 개요

이 폴더는 Factory BE REST API의 모든 엔드포인트를 체계적으로 테스트할 수 있는 HTTP 파일들을 포함합니다.

## 📁 파일 구조

```
tests/api/
├── README.md                    # 이 파일
├── 01_auth.http                # 인증 및 세션 관리 API
├── 02_dashboard.http           # 대시보드 API  
├── 03_news_basic.http          # 뉴스 기본 CRUD API
├── 04_news_query.http          # 뉴스 조회 API
├── 05_news_crawling.http       # 뉴스 크롤링 API
├── 06_analysis.http            # 뉴스 분석 API
├── 07_search.http              # 검색 API
├── 08_statistics.http          # 통계 API
├── 09_advanced_features.http   # 고급 기능 API
└── 10_performance_test.http    # 성능 및 스트레스 테스트
```

## 🚀 사용 방법

### 1. 환경 설정

각 HTTP 파일 상단의 환경 변수를 확인하고 필요시 수정하세요:

```http
@baseUrl = http://localhost:8080/api/v1
@accessToken = eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9
@adminToken = ADMIN_MASTER_TOKEN=6dFou5GQmxpcxmmWXgEzyBtdouijeqAUJowj00oJMl81HfFEQsgp22YObVlEe1pg
```

### 2. 순서대로 테스트

**권장 테스트 순서:**

1. **01_auth.http** - 인증 토큰 획득
2. **02_dashboard.http** - 대시보드 기능 확인  
3. **03_news_basic.http** - 뉴스 CRUD 기본 기능
4. **04_news_query.http** - 뉴스 조회 기능
5. **05_news_crawling.http** - 크롤링 기능 (RabbitMQ 연동)
6. **06_analysis.http** - 분석 기능
7. **07_search.http** - 검색 기능 (OpenSearch 연동)
8. **08_statistics.http** - 통계 기능
9. **09_advanced_features.http** - 고급 기능들
10. **10_performance_test.http** - 성능 테스트

### 3. IDE별 실행 방법

#### **IntelliJ IDEA / WebStorm**
- HTTP 파일 열기
- 요청 옆의 ▶️ 버튼 클릭
- 또는 `Ctrl+Enter` (Windows/Linux) / `Cmd+Enter` (Mac)

#### **VS Code** 
- REST Client 확장 설치 필요
- 요청 위의 "Send Request" 클릭
- 또는 `Ctrl+Alt+R` (Windows/Linux) / `Cmd+Alt+R` (Mac)

#### **Postman**
- HTTP 파일 내용을 Postman으로 Import
- Collection으로 관리 가능

## 🔧 테스트 시 주의사항

### 인증 관련
- 대부분의 API는 JWT 토큰 필요
- 관리자 API는 `adminToken` 필요
- 토큰 만료시 `01_auth.http`에서 재발급

### 데이터 의존성
- 일부 테스트는 이전 테스트 결과에 의존
- 뉴스 ID, 분석 ID 등은 실제 생성된 값으로 교체 필요

### 크롤링 테스트
- `05_news_crawling.http`는 RabbitMQ 연동 필요
- Python 크롤러 엔진이 실행 중이어야 함
- 실제 크롤링이 수행되므로 시간 소요

### 성능 테스트
- `10_performance_test.http`는 시스템 부하 발생
- 프로덕션 환경에서는 주의해서 실행
- 동시 요청 테스트시 리소스 모니터링 필요

## 📊 예상 응답

### 성공 응답 형식
```json
{
  "success": true,
  "message": "Success",
  "data": {
    // 실제 데이터
  }
}
```

### 실패 응답 형식
```json
{
  "success": false,
  "message": "에러 메시지",
  "error": "ERROR_CODE",
  "details": "상세 에러 내용"
}
```

## 🐛 문제 해결

### 자주 발생하는 오류

1. **401 Unauthorized**
   - 토큰 만료 또는 잘못된 토큰
   - `01_auth.http`에서 토큰 재발급

2. **403 Forbidden**
   - 권한 부족
   - 관리자 토큰 확인 또는 권한 요청

3. **404 Not Found**
   - 존재하지 않는 리소스 ID
   - 실제 존재하는 ID로 교체

4. **500 Internal Server Error**
   - 서버 내부 오류
   - 로그 확인 및 개발팀 문의

5. **Connection Error**
   - 서버 미실행
   - `baseUrl` 확인

### 환경별 설정

#### 개발 환경
```http
@baseUrl = http://localhost:8080/api/v1
```

#### 스테이징 환경
```http
@baseUrl = https://staging-api.factory.com/api/v1
```

#### 프로덕션 환경
```http
@baseUrl = https://api.factory.com/api/v1
```

## 📈 성능 지표

정상적인 응답 시간 기준:
- **기본 조회**: < 100ms
- **검색 API**: < 500ms  
- **분석 시작**: < 200ms
- **크롤링 요청**: < 300ms
- **통계 조회**: < 1000ms

## 🔄 자동화

### CI/CD 파이프라인 통합
```bash
# Newman (CLI Postman) 사용 예시
newman run api-tests.postman_collection.json \
  --environment dev.postman_environment.json \
  --reporters cli,json \
  --reporter-json-export results.json
```

### 스크립트 실행
```bash
# 모든 테스트 실행
./run-all-tests.sh

# 특정 테스트만 실행  
./run-test.sh auth
./run-test.sh crawling
```

## 📞 지원

테스트 관련 문의:
- **개발팀**: dev@factory.com
- **이슈 등록**: GitHub Issues
- **문서**: [API 명세서](../docs/rest-api.md)