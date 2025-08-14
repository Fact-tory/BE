# 🕷️ 네이버 뉴스 크롤러

Poetry + FastAPI 기반의 네이버 뉴스 크롤링 서비스입니다. Java 백엔드와 RabbitMQ 연동 또는 완전 독립 실행이 가능합니다.

## 🚀 빠른 시작

### 1. Poetry 설치 및 의존성 설치
```bash
# Poetry 설치 (없는 경우)
curl -sSL https://install.python-poetry.org | python3 -

# 의존성 설치
poetry install

# Playwright 브라우저 설치
poetry run playwright install chromium
```

### 2. 환경변수 설정
```bash
cp .env.example .env
# .env 파일을 환경에 맞게 수정
```

### 3. 실행 방법

#### 🔥 독립 실행 (추천)
```bash
# FastAPI 서버 실행
poetry run crawler server --host 0.0.0.0 --port 8000

# 또는 직접 실행  
poetry run python -m crawler.server
```

#### 🕷️ CLI로 즉시 크롤링
```bash
# 연합뉴스(001) 정치(100) 뉴스 10개 크롤링
poetry run crawler crawl 001 100 --max-articles 10

# 결과를 파일로 저장
poetry run crawler crawl 020 102 --max-articles 20 --output result.json

# 텍스트 형식으로 출력
poetry run crawler crawl 001 101 --format text
```

#### 🧪 테스트 실행
```bash
poetry run crawler test
```

## 📊 API 사용법 (FastAPI 서버)

서버 실행 후 http://localhost:8000/docs 에서 Swagger UI 확인 가능

### 주요 엔드포인트

```bash
# 헬스체크
curl http://localhost:8000/health

# 크롤링 실행
curl -X POST "http://localhost:8000/crawl/naver" \
  -H "Content-Type: application/json" \
  -d '{
    "sessionId": "test-session",
    "officeId": "001", 
    "categoryId": "100",
    "maxArticles": 10,
    "includeContent": true
  }'

# 크롤링 결과 확인
curl http://localhost:8000/sessions/test-session

# 모든 세션 목록
curl http://localhost:8000/sessions
```

## 🐳 Docker 실행

### 단독 실행
```bash
# 이미지 빌드
docker build -t naver-news-crawler .

# 서버 모드로 실행
docker run -p 8000:8000 -v $(pwd)/output:/app/output naver-news-crawler

# CLI 모드로 실행
docker run -v $(pwd)/output:/app/output naver-news-crawler \
  poetry run crawler crawl 001 100 --max-articles 5
```

### Docker Compose (완전한 환경)
```bash
# 크롤러 + RabbitMQ 실행
docker-compose up -d

# 크롤러 + RabbitMQ + Java 백엔드 실행 
docker-compose --profile with-java up -d

# 로그 확인
docker-compose logs -f crawler-server

# 종료
docker-compose down
```

## 🏗️ 실행 모드

### 1. 독립 실행 모드 (Standalone)
- Java 백엔드 없이 완전 독립 실행
- 크롤링 결과를 로컬 파일로 저장 (`./output/` 디렉토리)
- FastAPI 서버로 REST API 제공

### 2. 큐 연동 모드 (Queue Only)  
- RabbitMQ만 연결된 상태
- Java 백엔드는 연결 안됨
- 큐에 메시지만 전송

### 3. 통합 연동 모드 (Integrated)
- Java 백엔드 + RabbitMQ 모두 연결
- 완전한 분산 시스템으로 동작

## 📁 결과 파일 구조

크롤링 결과는 `output/` 디렉토리에 다음 형식으로 저장됩니다:

```
output/
├── crawling_session-id_20231221_143022.json
├── crawling_test-session_20231221_143155.json
└── ...
```

각 파일은 다음 정보를 포함합니다:
- 세션 정보 (ID, 시작/종료 시간, 소요 시간)
- 크롤링 통계 (요청/수집/성공/실패 수)
- 뉴스 데이터 배열 (제목, 내용, URL, 기자명, 발행시간 등)

## ⚙️ 설정 옵션

### 환경변수 (.env 파일)

| 변수명 | 기본값 | 설명 |
|--------|--------|------|
| HOST | 0.0.0.0 | FastAPI 서버 호스트 |
| PORT | 8000 | FastAPI 서버 포트 |
| RABBITMQ_URL | amqp://localhost:5672 | RabbitMQ 연결 URL |
| JAVA_BACKEND_URL | http://localhost:8080 | Java 백엔드 URL |
| MAX_CONCURRENT_CRAWLS | 3 | 최대 동시 크롤링 수 |
| CRAWLING_DELAY | 0.5 | 요청 간 지연시간(초) |
| OUTPUT_DIR | ./output | 결과 저장 디렉토리 |
| LOG_LEVEL | INFO | 로그 레벨 |

### CLI 명령어 옵션

```bash
# 설정 확인
poetry run crawler config-show

# 서버 실행 옵션
poetry run crawler server --help

# 크롤링 옵션
poetry run crawler crawl --help
```

## 🧪 네이버 뉴스 코드 참고

### 언론사 코드 (officeId)
- 001: 연합뉴스
- 020: 동아일보  
- 023: 조선일보
- 025: 중앙일보
- 028: 한겨레
- 032: 경향신문

### 카테고리 코드 (categoryId)
- 100: 정치
- 101: 경제
- 102: 사회
- 103: 생활/문화
- 104: 세계
- 105: IT/과학

## 🔍 모니터링

### RabbitMQ 관리 UI
http://localhost:15672 (guest/guest)

### FastAPI 문서
- Swagger UI: http://localhost:8000/docs
- ReDoc: http://localhost:8000/redoc

### 로그 확인
```bash
# 실시간 로그
poetry run crawler server --debug

# Docker 로그
docker-compose logs -f crawler-server
```

## 🚨 주의사항

1. **네이버 이용약관 준수**: robots.txt 및 이용약관을 확인하고 준수하세요
2. **요청 제한**: 과도한 요청으로 IP 차단될 수 있습니다
3. **에러 처리**: 크롤링 실패시 로그 확인 후 재시도하세요
4. **리소스 관리**: 장시간 실행시 메모리 사용량을 모니터링하세요

## 🛠️ 개발

### 코드 포맷팅
```bash
poetry run black .
poetry run isort .
```

### 테스트 실행
```bash
poetry run pytest
```

### 타입 체크
```bash
poetry run mypy crawler/
```