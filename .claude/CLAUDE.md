# 🐍 PYTHON CRAWLER API 명세서 - Java 백엔드 연동 가이드

> 네이버 크롤러 Python 서비스와 Java Spring Boot 백엔드 간 완전한 연동 가이드

## 📋 개요

**Python Crawler Service**는 Java 백엔드로부터 RabbitMQ를 통해 크롤링 요청을 받아 네이버 뉴스를 수집하고 결과를 반환하는 비동기 서비스입니다.

### 🎯 핵심 통신 구조

```
Java Backend → RabbitMQ → Python Crawler → RabbitMQ → Java Backend
     ↓                           ↓                         ↑
   요청 전송                 크롤링 수행              결과 수신
```

---

## 🔄 RabbitMQ 큐 구조

### 📨 큐 정의 (`rabbitmq_service.py`)

| 큐 이름 | 실제 큐명 | 방향 | 용도 |
|---------|-----------|------|------|
| `crawling_requests` | `crawler.requests` | Java → Python | 크롤링 요청 전송 |
| `crawling_results` | `crawler.results` | Python → Java | 크롤링 결과 반환 |
| `progress_updates` | `crawler.progress` | Python → Java | 실시간 진행상황 |
| `health_checks` | `crawler.health` | Python → Java | 헬스체크 정보 |

### ⚙️ 큐 설정
- **Durable**: `true` (서버 재시작 후에도 유지)
- **Auto Delete**: `false`
- **Prefetch Count**: `3` (동시 처리 제한)
- **Message TTL**: Java에서 설정 권장 (5분)

---

## 📤 1. 크롤링 요청 (Java → Python)

### 🎯 큐: `crawler.requests`

### 📋 요청 메시지 형식

```json
{
  "requestId": "req_20231225_001",
  "requestType": "NAVER_CRAWLING",
  "payload": {
    "sessionId": "session_uuid_v4",
    "officeId": "001",
    "categoryId": "100", 
    "maxArticles": 50,
    "maxScrollAttempts": 5,
    "includeContent": true,
    "includeSummary": false,
    "minContentLength": 100,
    "excludeKeywords": "광고,홍보"
  },
  "timestamp": 1703433600000
}
```

### 🔧 Java DTO 예시

```java
@Data
@Builder
public class CrawlingRequestMessage {
    private String requestId;
    private String requestType = "NAVER_CRAWLING";
    private CrawlingPayload payload;
    private Long timestamp;
    
    @Data
    @Builder
    public static class CrawlingPayload {
        private String sessionId;
        private String officeId;        // 필수: 언론사 코드 (예: "001"=연합뉴스)
        private String categoryId;      // 필수: 카테고리 코드 (예: "100"=정치)
        private Integer maxArticles = 100;     // 최대 수집 기사 수 (1-1000)
        private Integer maxScrollAttempts = 5; // 최대 스크롤 시도 횟수 (1-20)
        private Boolean includeContent = true;  // 본문 포함 여부
        private Boolean includeSummary = false; // 요약 포함 여부  
        private Integer minContentLength = 100; // 최소 본문 길이 (50+)
        private String excludeKeywords;         // 제외 키워드 (쉼표 구분)
    }
}
```

### ✅ 유효성 검증
- `officeId`: 필수, 공백 불가
- `categoryId`: 필수, 공백 불가
- `maxArticles`: 1-1000 범위
- `maxScrollAttempts`: 1-20 범위
- `minContentLength`: 50 이상

---

## 📥 2. 크롤링 결과 (Python → Java)

### 🎯 큐: `crawler.results`

### 📋 성공 응답 형식

```json
{
  "requestId": "req_20231225_001",
  "success": true,
  "data": [
    {
      "url": "https://news.naver.com/main/read.naver?mode=LSD&mid=sec&sid1=100&oid=001&aid=0014362584",
      "title": "뉴스 제목",
      "content": "뉴스 본문 내용...",
      "summary": null,
      "authorName": "홍길동 기자",
      "publishedAt": "2023-12-25T10:30:00+09:00",
      "officeId": "001",
      "categoryId": "100",
      "articleId": "0014362584",
      "originalUrl": "https://news.naver.com/...",
      "discoveredAt": "2023-12-25T10:35:00Z",
      "responseTime": 2.5,
      "source": "naver_crawler",
      "metadata": {}
    }
  ],
  "timestamp": 1703433900000
}
```

### 📋 실패 응답 형식

```json
{
  "requestId": "req_20231225_001", 
  "success": false,
  "error": "해당 언론사의 크롤링이 이미 진행 중입니다.",
  "timestamp": 1703433900000
}
```

### 🔧 Java Response DTO

```java
@Data
public class CrawlingResultMessage {
    private String requestId;
    private Boolean success;
    private List<NewsArticleDto> data;
    private String error;
    private Long timestamp;
    
    @Data
    public static class NewsArticleDto {
        private String url;
        private String title;
        private String content;
        private String summary;
        private String authorName;
        private String publishedAt;      // ISO 8601 형식
        private String officeId;
        private String categoryId;
        private String articleId;
        private String originalUrl;
        private String discoveredAt;     // ISO 8601 형식
        private Double responseTime;
        private String source;
        private Map<String, Object> metadata;
    }
}
```

---

## 📊 3. 진행상황 업데이트 (Python → Java)

### 🎯 큐: `crawler.progress`

### 📋 진행상황 메시지 형식

```json
{
  "requestId": "req_20231225_001",
  "messageType": "crawling_progress",
  "timestamp": 1703433700000,
  "progress": {
    "sessionId": "session_uuid_v4",
    "status": "crawling",
    "progress": 45,
    "message": "25개 기사 추출 중...",
    "totalArticles": 50,
    "processedArticles": 25,
    "successCount": 23,
    "failCount": 2,
    "successRate": 92.0,
    "timestamp": "2023-12-25T10:33:00Z"
  }
}
```

### 📈 진행 상태 종류

| 상태 | 설명 | 진행률 |
|------|------|--------|
| `discovering_urls` | 기사 URL 수집 중 | 10% |
| `urls_discovered` | URL 발견 완료 | 20% |
| `crawling` | 기사 내용 추출 중 | 21-79% |
| `crawling_completed` | 추출 완료 | 80% |
| `validating` | 데이터 검증 중 | 90% |
| `completed` | 크롤링 완료 | 100% |
| `failed` | 크롤링 실패 | 0% |

### 🔧 Java Progress DTO

```java
@Data
public class CrawlingProgressMessage {
    private String requestId;
    private String messageType;
    private Long timestamp;
    private ProgressData progress;
    
    @Data
    public static class ProgressData {
        private String sessionId;
        private String status;
        private Integer progress;       // 0-100
        private String message;
        private Integer totalArticles;
        private Integer processedArticles;
        private Integer successCount;
        private Integer failCount;
        private Double successRate;
        private String timestamp;
    }
}
```

---

## 🔍 4. 헬스체크 (Python → Java)

### 🎯 큐: `crawler.health`

### 📋 헬스체크 메시지 형식

```json
{
  "requestId": "health_check_001",
  "messageType": "health_check",
  "timestamp": 1703433600000,
  "status": "healthy",
  "activeSessions": ["session_1", "session_2"],
  "systemMetrics": {
    "workerCount": 3,
    "activeConnections": 5,
    "memoryUsage": "245MB",
    "browserInstances": 1,
    "uptime": "2h 15m"
  }
}
```

---

## ⚙️ 5. 동시성 제어 & 분산 락

### 🔒 Redis 분산 락 키 형식
```
naver_crawling:{officeId}:{categoryId}
```

### 📋 락 설정
- **대기 시간**: 5초
- **보유 시간**: 10분 (600초)
- **타임아웃 메시지**: "해당 언론사의 크롤링이 이미 진행 중입니다."

### 🎯 동작 방식
1. 동일한 `officeId + categoryId` 조합은 동시 실행 불가
2. 다른 조합은 병렬 실행 가능
3. 락 획득 실패 시 즉시 오류 응답

---

## 🔧 6. Java 백엔드 구현 가이드

### 📤 요청 전송 예시

```java
@Service
@RequiredArgsConstructor
public class CrawlingService {
    
    private final RabbitTemplate rabbitTemplate;
    
    public void requestCrawling(CrawlingRequestDto request) {
        String requestId = generateRequestId();
        
        CrawlingRequestMessage message = CrawlingRequestMessage.builder()
            .requestId(requestId)
            .requestType("NAVER_CRAWLING")
            .payload(CrawlingRequestMessage.CrawlingPayload.builder()
                .sessionId(UUID.randomUUID().toString())
                .officeId(request.getOfficeId())
                .categoryId(request.getCategoryId())
                .maxArticles(request.getMaxArticles())
                .includeContent(true)
                .build())
            .timestamp(System.currentTimeMillis())
            .build();
            
        rabbitTemplate.convertAndSend("crawler.requests", message);
        
        // DB에 요청 상태 저장
        saveCrawlingRequest(requestId, "PENDING");
    }
}
```

### 📥 결과 수신 예시

```java
@RabbitListener(queues = "crawler.results")
public void handleCrawlingResult(CrawlingResultMessage result) {
    String requestId = result.getRequestId();
    
    if (result.getSuccess()) {
        // 성공 처리
        List<NewsArticle> articles = convertToEntities(result.getData());
        newsArticleRepository.saveAll(articles);
        updateCrawlingStatus(requestId, "COMPLETED");
        
        // WebSocket으로 클라이언트에 알림
        sendWebSocketNotification(requestId, "크롤링 완료");
        
    } else {
        // 실패 처리  
        updateCrawlingStatus(requestId, "FAILED", result.getError());
        scheduleRetry(requestId);  // 재시도 스케줄링
    }
}
```

### 📊 진행상황 수신 예시

```java
@RabbitListener(queues = "crawler.progress")
public void handleProgress(CrawlingProgressMessage progress) {
    String requestId = progress.getRequestId();
    ProgressData data = progress.getProgress();
    
    // DB 진행상황 업데이트
    updateCrawlingProgress(requestId, data);
    
    // WebSocket으로 실시간 전송
    webSocketService.sendProgress(requestId, data);
}
```

---

## 🚨 7. 오류 처리 & 재시도 전략

### 📋 주요 오류 유형

| 오류 유형 | 원인 | 재시도 여부 |
|-----------|------|-------------|
| `ConcurrencyException` | 분산 락 획득 실패 | ✅ 1분 후 재시도 |
| `ValidationError` | 잘못된 요청 데이터 | ❌ 재시도 불가 |
| `TimeoutError` | 페이지 로딩 타임아웃 | ✅ 즉시 재시도 |
| `NetworkError` | 네트워크 연결 실패 | ✅ 5분 후 재시도 |
| `BrowserError` | 브라우저 인스턴스 오류 | ✅ 브라우저 재시작 후 재시도 |

### 🔄 Java 백엔드 재시도 로직

```java
@Retryable(
    value = {ConcurrencyException.class, TimeoutException.class},
    maxAttempts = 3,
    backoff = @Backoff(delay = 60000, multiplier = 2)
)
public void requestCrawlingWithRetry(CrawlingRequestDto request) {
    requestCrawling(request);
}
```

---

## 📈 8. 모니터링 & 성능 지표

### 📊 수집해야 할 메트릭

```java
// 처리량 지표
private final MeterRegistry meterRegistry;

public void recordCrawlingMetrics(CrawlingResultMessage result) {
    Timer.Sample sample = Timer.start(meterRegistry);
    
    meterRegistry.counter("crawling.requests.total",
        "office_id", result.getData().get(0).getOfficeId(),
        "status", result.getSuccess() ? "success" : "failed"
    ).increment();
    
    if (result.getSuccess()) {
        meterRegistry.gauge("crawling.articles.count", result.getData().size());
        sample.stop(Timer.builder("crawling.duration").register(meterRegistry));
    }
}
```

### 🎯 모니터링 포인트
- **큐 깊이**: 대기 중인 메시지 수
- **처리 시간**: 요청부터 완료까지 소요 시간
- **성공률**: 성공한 크롤링 비율
- **품질 점수**: 수집된 기사의 품질 지표
- **동시성**: 동시 실행 중인 크롤링 수

---

## 🔧 9. 환경 설정

### 📋 Python 크롤러 설정 (`config.py`)

```yaml
# 환경변수 설정
CRAWLER_RABBITMQ_URL=amqp://localhost/
CRAWLER_REDIS_URL=redis://localhost:6379
CRAWLER_MAX_CONCURRENT_CRAWLS=3
CRAWLER_CRAWLING_DELAY=1.0
CRAWLER_PAGE_TIMEOUT=30000
```

### 🔧 Java 백엔드 설정

```yaml
spring:
  rabbitmq:
    host: localhost
    port: 5672
    username: guest
    password: guest
    listener:
      simple:
        prefetch: 1
        concurrency: 5
        max-concurrency: 10
  redis:
    host: localhost
    port: 6379
```

---

## 🚀 10. 배포 & 스케일링

### 📦 Python 워커 실행

```bash
# 단일 워커 실행
python -m naver.workers.crawler_worker

# Docker로 다중 워커 실행  
docker-compose up --scale crawler-worker=3
```

### 🎯 스케일링 전략
- **수평 스케일링**: 워커 인스턴스 증가
- **큐 모니터링**: 메시지 쌓임 감지 시 자동 스케일링
- **부하 분산**: 언론사별 워커 분산 배치

---

## 🔍 11. 트러블슈팅

### ❓ 자주 발생하는 문제

| 문제 | 원인 | 해결책 |
|------|------|--------|
| 메시지 무한 재시도 | 데드 레터 큐 미설정 | Java에서 DLX 설정 |
| 크롤링 중복 실행 | 분산 락 실패 | Redis 연결 상태 확인 |
| 진행상황 미수신 | WebSocket 연결 끊김 | 연결 상태 재확인 |
| 낮은 품질 기사 | 필터링 설정 부족 | `minContentLength` 증가 |

### 🔧 디버깅 가이드

```java
// 메시지 전송 상태 확인
@EventListener
public void handleMessageSent(MessageSentEvent event) {
    log.info("Message sent: {}", event.getMessageId());
}

// 크롤링 타임아웃 모니터링
@Scheduled(fixedDelay = 300000) // 5분마다
public void checkTimeoutCrawling() {
    List<CrawlingRequest> timeoutRequests = 
        crawlingRepository.findTimeoutRequests(10); // 10분 초과
    
    timeoutRequests.forEach(this::handleTimeout);
}
```

---

## 📋 요약

이 Python 크롤러는 **완전한 비동기 아키텍처**로 Java 백엔드와 견고하게 연동됩니다:

✅ **RabbitMQ 기반 메시지 통신**
✅ **Redis 분산 락으로 동시성 제어**
✅ **실시간 진행상황 스트리밍**
✅ **구조화된 오류 처리**
✅ **확장 가능한 워커 아키텍처**

**Java 백엔드**에서는 요청 관리, 재시도 로직, 상태 추적, 모니터링을 담당하고,
**Python 크롤러**는 순수 크롤링 작업에 집중하는 **명확한 역할 분담**을 제공합니다.
