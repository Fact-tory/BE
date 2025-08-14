# 📚 README 언어 블록 가이드

> GitHub README에서 자주 사용하는 언어 블록과 활용법

## 🔧 백엔드 개발 관련

### Java/Spring Boot
```java
// Spring Boot Controller 예시
@RestController
@RequestMapping("/api/v1")
public class ApiController {
    
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("OK");
    }
}
```

### Build Tools
```gradle
// Gradle 설정
dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-web'
    implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
    testImplementation 'org.springframework.boot:spring-boot-starter-test'
}
```

```maven
<!-- Maven 설정 -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
</dependency>
```

## 🗄️ 데이터베이스 관련

### SQL
```sql
-- MySQL 테이블 생성
CREATE TABLE users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    email VARCHAR(255) UNIQUE NOT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);
```

### NoSQL (MongoDB)
```javascript
// MongoDB 쿼리
db.news_articles.find({
  "category": "POLITICS",
  "publishedAt": { $gte: new Date("2025-01-01") }
}).sort({ "publishedAt": -1 }).limit(10)
```

### NoSQL (Redis)
```redis
# Redis 명령어
SET user:1000 "John Doe"
EXPIRE user:1000 3600
HGET user:1000:profile email
```

## 🐳 인프라 관련

### Docker
```dockerfile
# Dockerfile
FROM openjdk:17-jdk-slim
COPY target/app.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app.jar"]
```

```yaml
# docker-compose.yml
version: '3.8'
services:
  app:
    build: .
    ports:
      - "8080:8080"
    environment:
      - SPRING_PROFILES_ACTIVE=docker
    depends_on:
      - mysql
      - redis
```

### Kubernetes
```yaml
# k8s deployment
apiVersion: apps/v1
kind: Deployment
metadata:
  name: factory-be
spec:
  replicas: 3
  selector:
    matchLabels:
      app: factory-be
  template:
    metadata:
      labels:
        app: factory-be
    spec:
      containers:
      - name: app
        image: factory-be:latest
        ports:
        - containerPort: 8080
```

## ⚙️ 설정 파일

### YAML (Spring Boot)
```yaml
# application.yml
spring:
  datasource:
    url: jdbc:mysql://localhost:3307/factory
    username: factory_user
    password: ${DB_PASSWORD}
  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: false
  data:
    redis:
      host: localhost
      port: 6379
```

### Properties
```properties
# application.properties
server.port=8080
spring.datasource.url=jdbc:mysql://localhost:3307/factory
spring.jpa.hibernate.ddl-auto=validate
logging.level.com.commonground.be=DEBUG
```

### JSON 설정
```json
{
  "name": "factory-be",
  "version": "1.0.0",
  "scripts": {
    "start": "./scripts/dev-app.sh run",
    "test": "./scripts/dev-app.sh test",
    "docker:start": "./scripts/dev-docker.sh start"
  }
}
```

## 🔧 스크립트 및 자동화

### Bash/Shell
```bash
#!/bin/bash
# 개발 환경 시작 스크립트

echo "🚀 Factory BE 개발환경을 시작합니다..."

# Docker 서비스 시작
docker-compose -f docker-compose.dev.yml up -d

# 헬스체크
./scripts/health-check.sh

echo "✅ 모든 서비스가 시작되었습니다!"
```

### PowerShell (Windows)
```powershell
# Windows 개발환경 시작
Write-Host "🚀 Factory BE 개발환경을 시작합니다..." -ForegroundColor Green

docker-compose -f docker-compose.dev.yml up -d

if ($LASTEXITCODE -eq 0) {
    Write-Host "✅ Docker 서비스가 시작되었습니다!" -ForegroundColor Green
} else {
    Write-Host "❌ Docker 시작에 실패했습니다." -ForegroundColor Red
}
```

## 🤖 CI/CD 관련

### GitHub Actions
```yaml
# .github/workflows/ci.yml
name: CI
on:
  push:
    branches: [ main, develop ]
  pull_request:
    branches: [ main ]

jobs:
  test:
    runs-on: ubuntu-latest
    steps:
    - uses: actions/checkout@v3
    - name: Set up JDK 17
      uses: actions/setup-java@v3
      with:
        java-version: '17'
        distribution: 'temurin'
    - name: Run tests
      run: ./gradlew test
```

### Jenkins
```groovy
// Jenkinsfile
pipeline {
    agent any
    stages {
        stage('Build') {
            steps {
                sh './gradlew clean build'
            }
        }
        stage('Test') {
            steps {
                sh './gradlew test'
            }
        }
        stage('Deploy') {
            steps {
                sh 'docker build -t factory-be .'
                sh 'docker run -d -p 8080:8080 factory-be'
            }
        }
    }
}
```

## 🌐 API 관련

### HTTP/REST
```http
### 사용자 로그인
POST http://localhost:8080/api/v1/auth/login
Content-Type: application/json

{
  "email": "user@example.com",
  "password": "password123"
}

### 뉴스 목록 조회
GET http://localhost:8080/api/v1/news?page=1&limit=20
Authorization: Bearer {{token}}
```

### cURL
```bash
# API 테스트
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "user@example.com",
    "password": "password123"
  }'
```

### GraphQL
```graphql
# GraphQL 쿼리
query GetNews($limit: Int!, $category: String) {
  news(limit: $limit, category: $category) {
    id
    title
    summary
    publishedAt
    mediaOutlet {
      name
    }
  }
}
```

## 🧪 테스트 관련

### JUnit 5
```java
// 단위 테스트
@ExtendWith(MockitoExtension.class)
class NewsServiceTest {
    
    @Mock
    private NewsRepository newsRepository;
    
    @InjectMocks
    private NewsService newsService;
    
    @Test
    @DisplayName("뉴스 목록 조회 테스트")
    void shouldReturnNewsList() {
        // given
        List<News> mockNews = Arrays.asList(new News("title", "content"));
        when(newsRepository.findAll()).thenReturn(mockNews);
        
        // when
        List<News> result = newsService.getAllNews();
        
        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTitle()).isEqualTo("title");
    }
}
```

### Testcontainers
```java
// 통합 테스트
@Testcontainers
class NewsIntegrationTest {
    
    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("factory_test")
            .withUsername("test_user")
            .withPassword("test_pass");
    
    @Test
    void shouldPersistNews() {
        // 테스트 코드...
    }
}
```

## 📊 모니터링 관련

### Prometheus
```yaml
# prometheus.yml
global:
  scrape_interval: 15s

scrape_configs:
  - job_name: 'factory-be'
    static_configs:
      - targets: ['localhost:8080']
    metrics_path: '/actuator/prometheus'
```

### Grafana
```json
{
  "dashboard": {
    "title": "Factory BE Metrics",
    "panels": [
      {
        "title": "Request Rate",
        "type": "graph",
        "targets": [
          {
            "expr": "rate(http_requests_total[5m])"
          }
        ]
      }
    ]
  }
}
```

## 🎨 마크다운 스타일링

### 뱃지 (Shields.io)
```markdown
![Java](https://img.shields.io/badge/Java-17-orange)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.x-green)
![Docker](https://img.shields.io/badge/Docker-Ready-blue)
![License](https://img.shields.io/badge/License-MIT-yellow)
```

### 알림 박스
```markdown
> **⚠️ 주의**: 이 설정은 개발환경 전용입니다.

> **💡 팁**: `./scripts/dev-docker.sh health`로 서비스 상태를 확인하세요.

> **🔥 중요**: 프로덕션 배포 전 반드시 보안 설정을 변경하세요.
```

### 접이식 섹션
```markdown
<details>
<summary>📋 상세 설정 보기</summary>

```yaml
# 상세 Docker 설정
version: '3.8'
services:
  app:
    build: .
    ports:
      - "8080:8080"
```

</details>
```

## 🚀 프로젝트별 커스텀 블록

### 뉴스 크롤링 관련
```python
# AI 분석 파이프라인
def analyze_news_bias(article_content):
    # 편향성 분석
    bias_score = bias_analyzer.analyze(article_content)
    
    # 키워드 추출
    keywords = keyword_extractor.extract(article_content)
    
    # 감정 분석
    sentiment = sentiment_analyzer.analyze(article_content)
    
    return {
        'bias': bias_score,
        'keywords': keywords,
        'sentiment': sentiment
    }
```

### WebSocket 실시간 통신
```javascript
// WebSocket 연결
const ws = new WebSocket('ws://localhost:8080/api/v1/ws/analysis');

ws.onmessage = function(event) {
    const progress = JSON.parse(event.data);
    updateProgressBar(progress.percentage);
    updateStatusMessage(progress.currentStep);
};
```

### 환경변수 설정
```env
# .env.dev
DB_HOST=localhost
DB_PORT=3307
DB_NAME=factory
DB_USER=factory_user
DB_PASSWORD=factory_dev_pass

REDIS_HOST=localhost
REDIS_PORT=6379

OPENSEARCH_URL=http://localhost:9200
```

---

**💡 팁**: 
- 코드 블록에 파일명을 주석으로 표시하면 더 명확합니다
- 실제 작동하는 예시를 포함하면 개발자들이 바로 복사해서 사용할 수 있습니다
- 언어별 구문 하이라이팅을 활용하면 가독성이 크게 향상됩니다