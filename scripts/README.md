# 🛠️ Factory BE 개발 스크립트

Factory BE 프로젝트의 개발 환경을 쉽게 관리할 수 있는 스크립트 모음입니다.

## 📋 스크립트 목록

| 스크립트 | 설명 | 주요 기능 |
|---------|------|-----------|
| `dev-docker.sh` | Docker 환경 관리 | 서비스 시작/중지, 헬스체크, 연결 정보 |
| `dev-app.sh` | Spring Boot 앱 관리 | 앱 실행, 빌드, 테스트 |

## 🚀 빠른 시작

### 1. 개발 환경 시작
```bash
# Docker 서비스 시작
./scripts/dev-docker.sh start

# Spring Boot 애플리케이션 실행
./scripts/dev-app.sh run
```

### 2. 전체 워크플로우
```bash
# 1. Docker 서비스 상태 확인
./scripts/dev-docker.sh status

# 2. 헬스체크
./scripts/dev-docker.sh health

# 3. 애플리케이션 실행
./scripts/dev-app.sh run

# 4. 테스트 실행
./scripts/dev-app.sh test

# 5. 빌드
./scripts/dev-app.sh build
```

## 🐳 Docker 스크립트 (dev-docker.sh)

### 주요 명령어

```bash
# 📊 상태 확인
./scripts/dev-docker.sh status           # 서비스 상태
./scripts/dev-docker.sh health           # 헬스체크
./scripts/dev-docker.sh info             # 연결 정보

# 🚀 서비스 관리
./scripts/dev-docker.sh start            # 전체 시작
./scripts/dev-docker.sh stop             # 전체 중지
./scripts/dev-docker.sh restart          # 전체 재시작
./scripts/dev-docker.sh clean            # 데이터 초기화 후 재시작

# 🎛️ 개별 서비스
./scripts/dev-docker.sh mysql start      # MySQL만 시작
./scripts/dev-docker.sh redis restart    # Redis 재시작
./scripts/dev-docker.sh opensearch stop  # OpenSearch 중지

# 📄 로그 및 연결
./scripts/dev-docker.sh logs             # 전체 로그
./scripts/dev-docker.sh logs mysql       # MySQL 로그만
./scripts/dev-docker.sh connect mysql    # MySQL 콘솔
./scripts/dev-docker.sh connect redis    # Redis CLI
```

### 연결 정보 출력 예시
```bash
./scripts/dev-docker.sh info
```
```
🔌 데이터베이스 연결 정보
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

MySQL:
  Host: localhost:3307
  Database: factory
  Username: factory_user
  Password: factory_dev_pass

OpenSearch:
  Host: http://localhost:9200
  Dashboard: http://localhost:5601
```

## 🏃‍♂️ 애플리케이션 스크립트 (dev-app.sh)

### 주요 명령어

```bash
# 🚀 실행
./scripts/dev-app.sh run                 # 개발 모드 실행
./scripts/dev-app.sh run --profile test  # 테스트 프로파일로 실행

# 🔨 빌드
./scripts/dev-app.sh build               # JAR 빌드
./scripts/dev-app.sh build docker        # Docker 이미지 빌드

# 🧪 테스트
./scripts/dev-app.sh test                # 전체 테스트
./scripts/dev-app.sh test unit           # 단위 테스트만
./scripts/dev-app.sh test coverage       # 커버리지 리포트

# 🛠️ 유틸리티
./scripts/dev-app.sh check               # Docker 서비스 확인
./scripts/dev-app.sh pid                 # 실행 중인 프로세스
./scripts/dev-app.sh kill                # 애플리케이션 종료
./scripts/dev-app.sh logs                # 로그 확인
./scripts/dev-app.sh clean               # 빌드 캐시 정리
```

## 💡 사용 예시

### 일반적인 개발 시작
```bash
# 1. Docker 서비스 시작 및 확인
./scripts/dev-docker.sh start
./scripts/dev-docker.sh health

# 2. 애플리케이션 실행
./scripts/dev-app.sh run
```

### 테스트 및 빌드
```bash
# 테스트 커버리지 확인
./scripts/dev-app.sh test coverage

# Docker 이미지 빌드
./scripts/dev-app.sh build docker
```

### 문제 해결
```bash
# 포트 사용 현황 확인
./scripts/dev-docker.sh ports

# 특정 서비스 로그 확인
./scripts/dev-docker.sh logs opensearch

# 애플리케이션 강제 종료
./scripts/dev-app.sh kill
```

### 데이터 초기화
```bash
# 모든 데이터베이스 데이터 초기화
./scripts/dev-docker.sh clean

# 빌드 캐시 정리
./scripts/dev-app.sh clean
```

## 🎨 기능 특징

### 🌈 컬러 출력
- 각 상태와 메시지를 색상으로 구분
- 성공(녹색), 경고(노란색), 에러(빨간색), 정보(파란색)

### 📊 실시간 상태 확인
- Docker 컨테이너 상태 모니터링
- 포트 사용 현황 확인
- 헬스체크 자동화

### 🔒 안전한 작업
- 데이터 삭제 작업 시 확인 프롬프트
- 실행 중인 프로세스 안전하게 종료
- 명확한 경고 메시지

### 📖 자세한 도움말
- 각 스크립트마다 `help` 명령어 지원
- 예시와 함께 상세한 사용법 제공
- 명령어 자동완성 힌트

## 🚨 주의사항

### 포트 충돌
- 기본 MySQL 포트(3306) 대신 3307 사용
- 로컬에 설치된 서비스와 충돌 방지

### 데이터 초기화
- `clean` 명령어는 모든 데이터를 삭제합니다
- 중요한 데이터가 있다면 백업 필요

### 권한 문제
```bash
# 스크립트 실행 권한이 없는 경우
chmod +x scripts/*.sh
```

## 🆘 문제 해결

### 스크립트 실행 안됨
```bash
# 실행 권한 부여
chmod +x scripts/dev-docker.sh scripts/dev-app.sh

# 스크립트 디렉토리에서 실행
cd /path/to/project
./scripts/dev-docker.sh help
```

### Docker 서비스 시작 실패
```bash
# Docker 데몬 상태 확인
docker info

# 포트 사용 확인
./scripts/dev-docker.sh ports

# 강제 정리 후 재시작
./scripts/dev-docker.sh clean
```

### 애플리케이션 실행 실패
```bash
# Docker 서비스 먼저 확인
./scripts/dev-app.sh check

# 실행 중인 프로세스 확인
./scripts/dev-app.sh pid

# 빌드 캐시 정리
./scripts/dev-app.sh clean
```

---

**💡 팁**: 각 스크립트는 `help` 명령어로 자세한 사용법을 확인할 수 있습니다!