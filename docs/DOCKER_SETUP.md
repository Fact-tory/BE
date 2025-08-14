# Docker 개발환경 설정 가이드

Factory BE 프로젝트의 Docker 개발환경 설정 및 실행 가이드입니다.

## 📋 목차
- [사전 요구사항](#사전-요구사항)
- [서비스 구성](#서비스-구성)
- [빠른 시작](#빠른-시작)
- [서비스별 실행](#서비스별-실행)
- [접속 정보](#접속-정보)
- [문제 해결](#문제-해결)

## 🔧 사전 요구사항

### 필수 소프트웨어
- **Docker Desktop**: 최신 버전 권장
- **Docker Compose**: v2.0 이상

### 설치 확인
```bash
docker --version
docker-compose --version
```

## 🏗️ 서비스 구성

### 개발환경에서 실행되는 서비스들:

| 서비스 | 포트 | 설명 | 라이센스 |
|--------|------|------|----------|
| **MySQL 8.0** | 3307 | 메인 데이터베이스 | GPL |
| **Redis 7.0** | 6379 | 캐시 및 세션 저장소 | BSD |
| **MongoDB 7.0** | 27017 | 뉴스 데이터 저장소 | SSPL |
| **RabbitMQ 3.12** | 5672, 15672 | 메시지 큐 (크롤링 통신) | MPL 2.0 |
| **OpenSearch 2.12** | 9200 | 검색 엔진 | **Apache 2.0 (완전 무료)** |
| **OpenSearch Dashboards** | 5601 | 검색 대시보드 | **Apache 2.0 (완전 무료)** |

## 🚀 빠른 시작

### 1. 모든 서비스 시작
```bash
# 개발환경 전체 스택 실행
docker-compose -f docker-compose.dev.yml up -d

# 로그 확인
docker-compose -f docker-compose.dev.yml logs -f
```

### 2. Spring Boot 애플리케이션 실행
Docker 서비스들이 실행된 후:
```bash
# Gradle을 사용하는 경우
./gradlew bootRun --args='--spring.profiles.active=dev'

# 또는 IDE에서 실행 시 VM Options:
-Dspring.profiles.active=dev
```

### 3. 서비스 중지
```bash
# 모든 서비스 중지
docker-compose -f docker-compose.dev.yml down

# 볼륨까지 삭제 (데이터 초기화)
docker-compose -f docker-compose.dev.yml down -v
```

## 🎛️ 서비스별 실행

### 필수 서비스만 실행
```bash
# MySQL + Redis만 실행
docker-compose -f docker-compose.dev.yml up -d mysql redis

# MongoDB 추가
docker-compose -f docker-compose.dev.yml up -d mongodb
```

### 검색 서비스 실행
```bash
# OpenSearch + Dashboards
docker-compose -f docker-compose.dev.yml up -d opensearch opensearch-dashboards
```

### 개별 서비스 재시작
```bash
# 특정 서비스 재시작
docker-compose -f docker-compose.dev.yml restart mysql
docker-compose -f docker-compose.dev.yml restart opensearch
```

## 🔌 접속 정보

### 데이터베이스 연결 정보

#### MySQL
```yaml
Host: localhost:3307
Database: factory
Username: factory_user
Password: factory_dev_pass
Root Password: factory_dev_root
```

#### MongoDB  
```yaml
Host: localhost:27017
Database: factory
Username: root
Password: factory_dev_mongo
Connection String: mongodb://root:factory_dev_mongo@localhost:27017/factory
```

#### Redis
```yaml
Host: localhost:6379
Password: (없음)
```

#### OpenSearch
```yaml
Host: localhost:9200
Username: (없음 - 보안 비활성화)
Password: (없음 - 보안 비활성화)
```

### 웹 대시보드

#### OpenSearch Dashboards
- **URL**: http://localhost:5601
- **설명**: 검색 데이터 시각화 및 인덱스 관리

## 🏃‍♂️ Spring Boot 연동

### application-dev.yml 예시
```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3307/factory
    username: factory_user
    password: factory_dev_pass
  
  data:
    redis:
      host: localhost
      port: 6379
    
    mongodb:
      uri: mongodb://root:factory_dev_mongo@localhost:27017/factory

opensearch:
  url: http://localhost:9200
```

## 🛠️ 문제 해결

### 포트 충돌 해결
```bash
# 포트 사용 중인 프로세스 확인 (macOS/Linux)
lsof -i :3307
lsof -i :9200

# Windows
netstat -ano | findstr :3307
```

### 컨테이너 상태 확인
```bash
# 실행 중인 컨테이너 확인
docker ps

# 모든 컨테이너 (중지된 것 포함)
docker ps -a

# 특정 서비스 로그 확인
docker-compose -f docker-compose.dev.yml logs opensearch
docker-compose -f docker-compose.dev.yml logs mysql
```

### 데이터 초기화
```bash
# 모든 데이터 삭제 후 재시작
docker-compose -f docker-compose.dev.yml down -v
docker-compose -f docker-compose.dev.yml up -d
```

### OpenSearch 헬스체크
```bash
# 클러스터 상태 확인
curl -X GET "localhost:9200/_cluster/health"

# 인덱스 목록 확인
curl -X GET "localhost:9200/_cat/indices"
```

## 💡 유용한 명령어

### 로그 실시간 모니터링
```bash
# 모든 서비스 로그
docker-compose -f docker-compose.dev.yml logs -f

# 특정 서비스만
docker-compose -f docker-compose.dev.yml logs -f mysql opensearch
```

### 컨테이너 내부 접근
```bash
# MySQL 콘솔 접속
docker exec -it factory-mysql-dev mysql -u factory_user -p

# MongoDB 콘솔 접속
docker exec -it factory-mongodb-dev mongosh -u root -p

# Redis CLI 접속 (분산 락 확인)
docker exec -it factory-redis-dev redis-cli

# RabbitMQ 관리 명령 (Java-Python 통신 모니터링)
docker exec -it factory-rabbitmq-dev rabbitmqctl status
docker exec -it factory-rabbitmq-dev rabbitmqctl list_queues
docker exec -it factory-rabbitmq-dev rabbitmqctl list_exchanges
```

### 디스크 사용량 확인
```bash
# Docker 전체 사용량
docker system df

# 불필요한 이미지/컨테이너 정리
docker system prune -a
```

## 🔄 개발 워크플로우

### 일반적인 개발 시작 순서
1. Docker 서비스 시작: `docker-compose -f docker-compose.dev.yml up -d`
2. 서비스 헬스체크 대기 (약 30초)
3. Spring Boot 애플리케이션 실행
4. 개발 작업 수행
5. 작업 완료 후: `docker-compose -f docker-compose.dev.yml down`

### 프로덕션과 다른 설정

이 개발환경은 다음과 같이 프로덕션과 다르게 최적화되어 있습니다:

- **보안**: OpenSearch 보안 플러그인 비활성화
- **메모리**: 각 서비스당 최소 메모리로 제한 (512MB)
- **데이터 지속성**: 빠른 재시작을 위한 최적화된 설정
- **로깅**: 개발에 최적화된 로그 레벨

---

**📝 참고**: 이 설정은 개발 전용입니다. 프로덕션 환경에서는 보안 설정을 반드시 활성화하세요.