#!/bin/bash

# Factory BE Spring Boot 애플리케이션 관리 스크립트
# 사용법: ./scripts/dev-app.sh [command] [options]

set -e

# 색상 정의
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
PURPLE='\033[0;35m'
CYAN='\033[0;36m'
WHITE='\033[1;37m'
NC='\033[0m' # No Color

# 설정
APP_NAME="Factory BE"
PROFILE="dev"
GRADLE_CMD="./gradlew"

# 로고 출력
print_logo() {
    echo -e "${PURPLE}"
    echo "╔═══════════════════════════════════════════════════════════════════════════════╗"
    echo "║                                                                               ║"
    echo "║                      🏭 Factory BE Spring Boot Application                    ║"
    echo "║                              Development Script                               ║"
    echo "║                                                                               ║"
    echo "╚═══════════════════════════════════════════════════════════════════════════════╝"
    echo -e "${NC}"
}

# 도움말 출력
show_help() {
    print_logo
    echo -e "${WHITE}사용법:${NC} ./scripts/dev-app.sh [command] [options]"
    echo ""
    echo -e "${YELLOW}주요 명령어:${NC}"
    echo -e "  ${GREEN}run${NC}          개발 모드로 애플리케이션 실행"
    echo -e "  ${GREEN}build${NC}        애플리케이션 빌드"
    echo -e "  ${GREEN}test${NC}         테스트 실행"
    echo -e "  ${GREEN}clean${NC}        빌드 캐시 정리"
    echo -e "  ${GREEN}deps${NC}         의존성 다운로드"
    echo ""
    echo -e "${YELLOW}프로파일 옵션:${NC}"
    echo -e "  ${GREEN}--profile dev${NC}     개발 환경 (기본값)"
    echo -e "  ${GREEN}--profile test${NC}    테스트 환경"
    echo -e "  ${GREEN}--profile local${NC}   로컬 환경"
    echo ""
    echo -e "${YELLOW}테스트 옵션:${NC}"
    echo -e "  ${GREEN}test unit${NC}         단위 테스트만 실행"
    echo -e "  ${GREEN}test integration${NC}  통합 테스트만 실행"
    echo -e "  ${GREEN}test coverage${NC}     테스트 커버리지 리포트 생성"
    echo ""
    echo -e "${YELLOW}빌드 옵션:${NC}"
    echo -e "  ${GREEN}build jar${NC}         JAR 파일 생성"
    echo -e "  ${GREEN}build docker${NC}      Docker 이미지 빌드"
    echo ""
    echo -e "${YELLOW}유틸리티:${NC}"
    echo -e "  ${GREEN}check${NC}        Docker 서비스 상태 확인"
    echo -e "  ${GREEN}logs${NC}         애플리케이션 로그 확인"
    echo -e "  ${GREEN}pid${NC}          실행 중인 프로세스 확인"
    echo -e "  ${GREEN}kill${NC}         실행 중인 애플리케이션 종료"
    echo -e "  ${GREEN}help${NC}         이 도움말 표시"
    echo ""
    echo -e "${YELLOW}예시:${NC}"
    echo -e "  ${CYAN}./scripts/dev-app.sh run${NC}                    # 개발 모드 실행"
    echo -e "  ${CYAN}./scripts/dev-app.sh run --profile test${NC}     # 테스트 프로파일로 실행"
    echo -e "  ${CYAN}./scripts/dev-app.sh test coverage${NC}          # 테스트 커버리지 확인"
    echo -e "  ${CYAN}./scripts/dev-app.sh build docker${NC}           # Docker 이미지 빌드"
    echo ""
}

# Docker 서비스 상태 확인
check_docker_services() {
    echo -e "${BLUE}🐳 Docker 서비스 상태 확인${NC}"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    
    SERVICES=("factory-mysql-dev" "factory-redis-dev" "factory-mongodb-dev" "factory-opensearch-dev")
    PORTS=(3307 6379 27017 9200)
    
    ALL_RUNNING=true
    
    for i in "${!SERVICES[@]}"; do
        SERVICE=${SERVICES[$i]}
        PORT=${PORTS[$i]}
        
        echo -n -e "${YELLOW}${SERVICE}: ${NC}"
        
        if docker ps --format "{{.Names}}" | grep -q "^${SERVICE}$"; then
            STATUS=$(docker inspect --format="{{.State.Health.Status}}" $SERVICE 2>/dev/null || echo "unknown")
            if [ "$STATUS" = "healthy" ] || [ "$STATUS" = "unknown" ]; then
                echo -e "${GREEN}✅ 실행 중${NC}"
            else
                echo -e "${YELLOW}⚠️  시작 중 ($STATUS)${NC}"
                ALL_RUNNING=false
            fi
        else
            echo -e "${RED}❌ 중지됨${NC}"
            ALL_RUNNING=false
        fi
    done
    
    echo ""
    if [ "$ALL_RUNNING" = true ]; then
        echo -e "${GREEN}✅ 모든 필수 서비스가 실행 중입니다.${NC}"
    else
        echo -e "${RED}⚠️  일부 서비스가 실행되지 않고 있습니다.${NC}"
        echo -e "${CYAN}💡 Docker 서비스 시작: ${WHITE}./scripts/dev-docker.sh start${NC}"
    fi
    echo ""
}

# 애플리케이션 실행
run_app() {
    PROFILE_ARG="dev"
    
    # 프로파일 파라미터 확인
    for arg in "$@"; do
        case $arg in
            --profile)
                shift
                PROFILE_ARG="$1"
                shift
                ;;
        esac
    done
    
    print_logo
    echo -e "${GREEN}🚀 $APP_NAME 실행 중... (프로파일: $PROFILE_ARG)${NC}"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    
    # Docker 서비스 확인
    check_docker_services
    
    echo -e "${BLUE}⏳ 애플리케이션 시작 중...${NC}"
    echo -e "${YELLOW}종료하려면 Ctrl+C를 누르세요${NC}"
    echo ""
    
    # Spring Boot 실행
    $GRADLE_CMD bootRun --args="--spring.profiles.active=$PROFILE_ARG"
}

# 빌드
build_app() {
    BUILD_TYPE="jar"
    
    # 빌드 타입 확인
    if [ "$1" = "docker" ]; then
        BUILD_TYPE="docker"
    elif [ "$1" = "jar" ]; then
        BUILD_TYPE="jar"
    fi
    
    echo -e "${BLUE}🔨 $APP_NAME 빌드 중... (타입: $BUILD_TYPE)${NC}"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    
    case $BUILD_TYPE in
        jar)
            echo -e "${YELLOW}JAR 파일 생성 중...${NC}"
            $GRADLE_CMD build -x test
            echo -e "${GREEN}✅ JAR 빌드 완료!${NC}"
            echo -e "${CYAN}빌드 파일 위치: ${WHITE}build/libs/${NC}"
            ;;
        docker)
            echo -e "${YELLOW}Docker 이미지 빌드 중...${NC}"
            $GRADLE_CMD build -x test
            docker build -t factory-be:latest .
            echo -e "${GREEN}✅ Docker 이미지 빌드 완료!${NC}"
            echo -e "${CYAN}이미지 이름: ${WHITE}factory-be:latest${NC}"
            ;;
    esac
}

# 테스트 실행
run_tests() {
    TEST_TYPE="all"
    
    # 테스트 타입 확인
    case $1 in
        unit)
            TEST_TYPE="unit"
            ;;
        integration)
            TEST_TYPE="integration"
            ;;
        coverage)
            TEST_TYPE="coverage"
            ;;
    esac
    
    echo -e "${BLUE}🧪 테스트 실행 중... (타입: $TEST_TYPE)${NC}"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    
    case $TEST_TYPE in
        unit)
            echo -e "${YELLOW}단위 테스트 실행 중...${NC}"
            $GRADLE_CMD test --tests "*UnitTest"
            ;;
        integration)
            echo -e "${YELLOW}통합 테스트 실행 중...${NC}"
            $GRADLE_CMD test --tests "*IntegrationTest"
            ;;
        coverage)
            echo -e "${YELLOW}테스트 커버리지 리포트 생성 중...${NC}"
            $GRADLE_CMD test jacocoTestReport
            echo -e "${GREEN}✅ 커버리지 리포트 생성 완료!${NC}"
            echo -e "${CYAN}리포트 위치: ${WHITE}build/reports/jacoco/test/html/index.html${NC}"
            ;;
        all)
            echo -e "${YELLOW}전체 테스트 실행 중...${NC}"
            $GRADLE_CMD test
            ;;
    esac
    
    if [ $? -eq 0 ]; then
        echo -e "${GREEN}✅ 테스트 완료!${NC}"
    else
        echo -e "${RED}❌ 테스트 실패!${NC}"
        exit 1
    fi
}

# 의존성 다운로드
download_dependencies() {
    echo -e "${BLUE}📦 의존성 다운로드 중...${NC}"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    $GRADLE_CMD dependencies --refresh-dependencies
    echo -e "${GREEN}✅ 의존성 다운로드 완료!${NC}"
}

# 빌드 캐시 정리
clean_build() {
    echo -e "${BLUE}🧹 빌드 캐시 정리 중...${NC}"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    $GRADLE_CMD clean
    echo -e "${GREEN}✅ 빌드 캐시 정리 완료!${NC}"
}

# 실행 중인 프로세스 확인
check_pid() {
    echo -e "${BLUE}🔍 실행 중인 애플리케이션 확인${NC}"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    
    JAVA_PROCESSES=$(ps aux | grep "java.*factory" | grep -v grep)
    
    if [ -n "$JAVA_PROCESSES" ]; then
        echo -e "${GREEN}실행 중인 프로세스:${NC}"
        echo "$JAVA_PROCESSES" | while read line; do
            PID=$(echo $line | awk '{print $2}')
            CMD=$(echo $line | awk '{for(i=11;i<=NF;i++) printf "%s ", $i; print ""}')
            echo -e "${YELLOW}PID: ${PID}${NC} - $CMD"
        done
    else
        echo -e "${YELLOW}실행 중인 Factory BE 애플리케이션이 없습니다.${NC}"
    fi
    echo ""
}

# 애플리케이션 종료
kill_app() {
    echo -e "${RED}🛑 실행 중인 애플리케이션 종료${NC}"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    
    PIDS=$(ps aux | grep "java.*factory" | grep -v grep | awk '{print $2}')
    
    if [ -n "$PIDS" ]; then
        echo -e "${YELLOW}다음 프로세스를 종료합니다:${NC}"
        echo "$PIDS"
        
        read -p "계속하시겠습니까? (y/N): " -n 1 -r
        echo
        
        if [[ $REPLY =~ ^[Yy]$ ]]; then
            echo "$PIDS" | xargs kill -9
            echo -e "${GREEN}✅ 애플리케이션이 종료되었습니다.${NC}"
        else
            echo -e "${BLUE}취소되었습니다.${NC}"
        fi
    else
        echo -e "${YELLOW}종료할 프로세스가 없습니다.${NC}"
    fi
    echo ""
}

# 로그 확인
show_logs() {
    echo -e "${BLUE}📄 애플리케이션 로그 확인${NC}"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    
    LOG_FILE="logs/application.log"
    
    if [ -f "$LOG_FILE" ]; then
        echo -e "${YELLOW}최근 로그 (마지막 50줄):${NC}"
        echo -e "${CYAN}파일: $LOG_FILE${NC}"
        echo ""
        tail -n 50 "$LOG_FILE"
    else
        echo -e "${YELLOW}로그 파일을 찾을 수 없습니다: $LOG_FILE${NC}"
        echo -e "${CYAN}애플리케이션이 실행 중인지 확인해주세요.${NC}"
    fi
    echo ""
}

# 메인 로직
case $1 in
    help|--help|-h)
        show_help
        ;;
    run)
        shift
        run_app "$@"
        ;;
    build)
        build_app $2
        ;;
    test)
        run_tests $2
        ;;
    clean)
        clean_build
        ;;
    deps)
        download_dependencies
        ;;
    check)
        check_docker_services
        ;;
    pid)
        check_pid
        ;;
    kill)
        kill_app
        ;;
    logs)
        show_logs
        ;;
    *)
        echo -e "${RED}❌ 알 수 없는 명령어: $1${NC}"
        echo -e "${YELLOW}도움말을 보려면: ${WHITE}./scripts/dev-app.sh help${NC}"
        exit 1
        ;;
esac