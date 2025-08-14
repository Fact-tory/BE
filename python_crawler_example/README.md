# Python 크롤링 워커

Java 백엔드와 RabbitMQ를 통해 통신하는 네이버 뉴스 크롤링 워커입니다.

## 🚀 설치 및 실행

### 1. 의존성 설치
```bash
pip install -r requirements.txt
playwright install chromium
```

### 2. 환경변수 설정
```bash
export RABBITMQ_URL="amqp://username:password@localhost:5672"
```

### 3. 워커 실행
```bash
python crawler_worker.py
```

## 🏗️ 아키텍처

### 메시지 플로우
```
Java Backend → RabbitMQ → Python Worker
Java Backend ← RabbitMQ ← Python Worker
```

### 큐 구조
- `crawling.request.queue`: Java → Python (크롤링 요청)
- `crawling.result`: Python → Java (크롤링 결과)
- `crawling.progress`: Python → Java (진행상황)

## 📊 메시지 형식

### 크롤링 요청
```json
{
  "requestId": "uuid",
  "requestType": "NAVER_NEWS", 
  "payload": {
    "sessionId": "session-uuid",
    "officeId": "001",
    "categoryId": "100",
    "maxArticles": 100,
    "includeContent": true
  },
  "timestamp": 1703123456789
}
```

### 크롤링 결과
```json
{
  "requestId": "uuid",
  "success": true,
  "data": [
    {
      "title": "뉴스 제목",
      "content": "뉴스 내용",
      "url": "https://news.naver.com/...",
      "authorName": "기자명",
      "publishedAt": "2023-12-21 09:00:00",
      "discoveredAt": "2023-12-21T09:00:00Z",
      "source": "python_crawler",
      "categoryId": "100",
      "metadata": {
        "mediaName": "언론사명",
        "officeId": "001"
      }
    }
  ],
  "timestamp": 1703123456789
}
```

## 🔧 설정 옵션

### 환경변수
- `RABBITMQ_URL`: RabbitMQ 연결 URL (기본값: amqp://localhost)
- `LOG_LEVEL`: 로그 레벨 (기본값: INFO)
- `MAX_CONCURRENT`: 최대 동시 처리 수 (기본값: 1)

### 크롤링 설정
- 브라우저: Chromium (헤드리스)
- 요청 간격: 0.5초
- 타임아웃: 15초 (개별 페이지), 30초 (리스트 페이지)

## 🚨 주의사항

1. **robots.txt 준수**: 네이버 robots.txt 정책을 확인하고 준수하세요
2. **요청 제한**: 과도한 요청으로 IP 차단당할 수 있습니다
3. **에러 처리**: 크롤링 실패시 자동으로 DLQ로 이동됩니다
4. **메모리 관리**: 브라우저 컨텍스트는 자동으로 정리됩니다

## 📈 모니터링

워커 실행 상태:
```bash
# 로그 확인
tail -f /var/log/crawler_worker.log

# 큐 상태 확인 (RabbitMQ Management UI)
http://localhost:15672
```

## 🐳 Docker 실행

```dockerfile
FROM python:3.11-slim

WORKDIR /app
COPY requirements.txt .
RUN pip install -r requirements.txt && playwright install chromium

COPY . .
CMD ["python", "crawler_worker.py"]
```

```bash
docker build -t python-crawler-worker .
docker run -e RABBITMQ_URL=amqp://rabbitmq:5672 python-crawler-worker
```