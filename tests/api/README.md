# 📋 API 테스트 가이드

## 🎯 개요

Factory BE REST API를 테스트할 수 있는 HTTP 파일 모음입니다.

## 📁 테스트 파일

| 파일 | 설명 |
|------|------|
| `01_oauth2_auth.http` | OAuth2 소셜 로그인 |
| `02_dashboard.http` | 대시보드 데이터 |
| `03_news_basic.http` | 뉴스 CRUD |
| `04_news_query.http` | 뉴스 조회 |
| `05_news_crawling.http` | 뉴스 크롤링 |
| `06_analysis.http` | AI 분석 |
| `07_search.http` | 검색 기능 |
| `08_statistics.http` | 통계 조회 |
| `06_batch.http` | 배치 작업 |
| `09_advanced_features.http` | 고급 기능 |
| `10_performance_test.http` | 성능 테스트 |

## 🚀 사용 방법

### 환경 설정
```http
@baseUrl = http://localhost:8080/api/v1
@accessToken = {{your_jwt_token}}
@adminToken = {{admin_master_token}}
```

### 테스트 순서
1. OAuth2 인증 → 토큰 획득
2. 대시보드/뉴스 → 기본 기능 확인
3. 크롤링/분석 → 고급 기능 테스트

### IDE 사용법
- **IntelliJ/WebStorm**: `Ctrl+Enter` 또는 ▶️ 버튼
- **VS Code**: REST Client 확장 + "Send Request"
- **Postman**: HTTP 파일 Import

## 📋 주의사항

- JWT 토큰 필요 (OAuth2 인증 후 획득)
- 관리자 API는 `adminToken` 필요
- 크롤링 테스트는 RabbitMQ 연동 필요
- ID 값들은 실제 생성된 값으로 교체

## 🔍 문제 해결

| 오류 | 원인 | 해결방법 |
|------|------|----------|
| 401 Unauthorized | 토큰 만료/잘못됨 | 토큰 재발급 |
| 403 Forbidden | 권한 부족 | 관리자 토큰 확인 |
| 404 Not Found | 존재하지 않는 ID | 실제 ID로 교체 |
| 500 Server Error | 서버 오류 | 로그 확인 |