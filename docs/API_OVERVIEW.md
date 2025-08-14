# 📡 Factory BE API 개요

> Java Backend + Python Crawler 하이브리드 시스템의 RESTful API 아키텍처

## 🎯 API 설계 원칙

### 1. 도메인 중심 설계
```
/api/v1/auth/*      # 인증 및 권한
/api/v1/news/*      # 뉴스 관리 및 Python 크롤링 제어
/api/v1/crawling/*  # 하이브리드 크롤링 API (RabbitMQ 통신)
/api/v1/analysis/*  # AI 분석 기능
/api/v1/search/*    # 검색 기능
/api/v1/users/*     # 사용자 관리
```

### 2. HTTP 메서드 컨벤션
- `GET` - 조회 (검색, 목록, 상세, 크롤링 진행상황)
- `POST` - 생성 (Python 크롤링 시작, AI 분석, 사용자 등록)
- `PUT` - 전체 수정
- `PATCH` - 부분 수정
- `DELETE` - 삭제

### 3. 응답 형식 표준화
> 📁 [ApiResponse.java](src/main/java/com/commonground/be/global/application/response/ApiResponse.java)

```json
{
  "success": true,
  "data": { /* 실제 데이터 */ },
  "meta": {
    "version": "v1.0",
    "timestamp": "2025-08-12T10:30:00Z"
  }
}
```

## 🔐 인증 시스템

### JWT 토큰 기반 인증
> 📁 [JwtTokenProvider.java](src/main/java/com/commonground/be/global/application/security/JwtTokenProvider.java)

```http
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
```

### 소셜 로그인 지원
- **Kakao**: [📁 KakaoService.java](src/main/java/com/commonground/be/domain/social/kakao/service/KakaoService.java)
- **Google**: [📁 GoogleService.java](src/main/java/com/commonground/be/domain/social/google/service/GoogleService.java)

## 📊 데이터 플로우

### 하이브리드 뉴스 수집 파이프라인
```mermaid
graph LR
    A[네이버 뉴스 API] --> B[Python 크롤러]
    B --> C[RabbitMQ] 
    C --> D[Java Backend]
    D --> E[MongoDB 저장]
    E --> F[AI 분석]
    F --> G[OpenSearch 인덱싱]
    G --> H[사용자 검색]
```

### 핵심 서비스 (하이브리드 아키텍처)
1. **크롤링 오케스트레이션**: [📁 CrawlingOrchestrationService.java](src/main/java/com/commonground/be/domain/news/service/CrawlingOrchestrationService.java)
2. **메시지 큐 통신**: [📁 CrawlingQueueService.java](src/main/java/com/commonground/be/domain/news/service/CrawlingQueueService.java)
3. **Python 크롤러**: [📁 enhanced_crawler_worker.py](python_crawler_advanced/enhanced_crawler_worker.py)
4. **AI 분석**: [📁 NewsServiceImpl.java](src/main/java/com/commonground/be/domain/news/service/NewsServiceImpl.java)
5. **검색 인덱싱**: [📁 OpenSearchIndexingService.java](src/main/java/com/commonground/be/domain/news/service/OpenSearchIndexingService.java)

## 🚀 실시간 기능

### WebSocket 연결
> 📁 [WebSocketConfig.java](src/main/java/com/commonground/be/global/infrastructure/config/WebSocketConfig.java)

```javascript
const ws = new WebSocket('ws://localhost:8080/api/v1/ws/analysis/{sessionId}');
```

### 진행 상황 추적
> 📁 [WebSocketProgressService.java](src/main/java/com/commonground/be/domain/news/service/WebSocketProgressService.java)

```json
{
  "type": "progress_update",
  "sessionId": "550e8400-e29b-41d4-a716-446655440000",
  "progress": 75,
  "currentStep": "AI 편향성 분석 중...",
  "estimatedRemaining": 15
}
```

## 📈 성능 최적화

### 캐싱 전략
> 📁 [RedisConfig.java](src/main/java/com/commonground/be/global/infrastructure/config/RedisConfig.java)

- **Redis**: 실시간 데이터, 세션 관리
- **애플리케이션 레벨**: Spring Cache 활용
- **데이터베이스**: 인덱스 최적화

### 페이지네이션
```http
GET /api/v1/news/realtime?page=1&limit=20
```

```json
{
  "data": {
    "items": [...],
    "pagination": {
      "page": 1,
      "limit": 20,
      "total": 1500,
      "totalPages": 75
    }
  }
}
```

## 🛡️ 보안 기능

### 입력 검증
> 📁 [CreateNewsRequest.java](src/main/java/com/commonground/be/domain/news/dto/request/CreateNewsRequest.java)

- Bean Validation 활용
- 커스텀 검증 어노테이션
- SQL Injection 방지

### 데이터 암호화
> 📁 [FieldEncryption.java](src/main/java/com/commonground/be/global/infrastructure/encryption/FieldEncryption.java)

- 개인정보 필드 암호화
- AES-256 암호화 알고리즘

## 📝 에러 처리

### 전역 예외 핸들러
> 📁 [GlobalExceptionAdvice.java](src/main/java/com/commonground/be/global/application/exception/GlobalExceptionAdvice.java)

```json
{
  "success": false,
  "error": {
    "code": "INVALID_PARAMETER",
    "message": "잘못된 파라미터입니다",
    "details": {
      "field": "category",
      "value": "5",
      "reason": "유효하지 않은 카테고리 ID"
    }
  },
  "timestamp": "2025-08-12T10:30:00Z"
}
```

### 커스텀 예외 클래스
- [📁 NewsExceptions.java](src/main/java/com/commonground/be/global/application/exception/NewsExceptions.java)
- [📁 AuthExceptions.java](src/main/java/com/commonground/be/global/application/exception/AuthExceptions.java)
- [📁 CrawlerExceptions.java](src/main/java/com/commonground/be/global/application/exception/CrawlerExceptions.java)

## 🔍 API 문서

### 상세 문서
- [REST API 명세서](rest-api.md) - 모든 엔드포인트 상세 설명
- [데이터베이스 스키마](schema.md) - DB 구조 및 관계
- [ERD 다이어그램](mermaid.md) - 시각적 데이터 구조

### 개발 도구
- **Swagger UI**: http://localhost:8080/swagger-ui.html
- **OpenAPI 3.0**: 자동 문서 생성
- **Postman Collection**: API 테스트 컬렉션

## 🚀 고도화된 메시지 처리

### RabbitMQ 고급 기능
> 📁 [RabbitMQAdvancedConfig.java](src/main/java/com/commonground/be/global/infrastructure/config/RabbitMQAdvancedConfig.java)

- **Dead Letter Queue**: 실패한 메시지 처리
- **메시지 재시도**: 지수적 백오프 전략
- **타임아웃 처리**: 장시간 응답 없는 메시지 처리
- **헬스체크**: Python 크롤러 상태 모니터링

### 메시지 처리 흐름
```mermaid
graph TD
    A[크롤링 요청] --> B[Enhanced Message Queue]
    B --> C{성공?}
    C -->|예| D[결과 반환]
    C -->|실패| E[재시도 큐]
    E --> F{재시도 횟수}
    F -->|<3회| B
    F -->|≥3회| G[Dead Letter Queue]
    G --> H[실패 처리]
```

### 향상된 기능
> 📁 [EnhancedMessageProcessingService.java](src/main/java/com/commonground/be/domain/news/service/EnhancedMessageProcessingService.java)

- **실패 원인 분석**: Redis에 실패 로그 저장
- **자동 재시도**: 네트워크 오류 등 일시적 실패 자동 복구
- **상태 모니터링**: 실시간 시스템 상태 추적
- **타임아웃 관리**: 장시간 응답 없는 요청 감지 및 처리

## 🧪 테스팅

### API 테스트
> 📁 [NewsApiIntegrationTest.java](src/test/java/com/commonground/be/integration/NewsApiIntegrationTest.java)

```bash
# 전체 테스트 실행
./scripts/dev-app.sh test

# 통합 테스트만 실행
./scripts/dev-app.sh test integration
```

### 테스트 커버리지
```bash
# 커버리지 리포트 생성
./scripts/dev-app.sh test coverage
```

---

**💡 팁**: API 개발 시 [REST API 명세서](rest-api.md)를 참조하여 일관된 인터페이스를 구현하세요!