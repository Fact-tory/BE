#!/bin/bash

# Factory BE 개발환경 Docker 관리 스크립트
# 사용법: ./scripts/dev-docker.sh [command] [options]

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
COMPOSE_FILE="docker-compose.dev.yml"
PROJECT_NAME="factory"

# 로고 출력
print_logo() {
    echo -e "${BLUE}"
    echo "╔═══════════════════════════════════════════════════════════════════════════════╗"
    echo "║                                                                               ║"
    echo "║                        🏭 Factory BE Development Environment                  ║"
    echo "║                              Docker Management Script                         ║"
    echo "║                                                                               ║"
    echo "╚═══════════════════════════════════════════════════════════════════════════════╝"
    echo -e "${NC}"
}

# 도움말 출력
show_help() {
    print_logo
    echo -e "${WHITE}사용법:${NC} ./scripts/dev-docker.sh [command] [options]"
    echo ""
    echo -e "${YELLOW}주요 명령어:${NC}"
    echo -e "  ${GREEN}start${NC}        모든 서비스 시작"
    echo -e "  ${GREEN}stop${NC}         모든 서비스 중지"
    echo -e "  ${GREEN}restart${NC}      모든 서비스 재시작"
    echo -e "  ${GREEN}status${NC}       서비스 상태 확인"
    echo -e "  ${GREEN}logs${NC}         로그 확인 (실시간)"
    echo -e "  ${GREEN}clean${NC}        모든 데이터 초기화 후 재시작"
    echo ""
    echo -e "${YELLOW}개별 서비스 관리:${NC}"
    echo -e "  ${GREEN}mysql${NC}        MySQL만 시작/중지/재시작"
    echo -e "  ${GREEN}redis${NC}        Redis만 시작/중지/재시작" 
    echo -e "  ${GREEN}mongodb${NC}      MongoDB만 시작/중지/재시작"
    echo -e "  ${GREEN}opensearch${NC}   OpenSearch만 시작/중지/재시작"
    echo -e "  ${GREEN}dashboards${NC}   OpenSearch Dashboards만 시작/중지/재시작"
    echo ""
    echo -e "${YELLOW}유틸리티:${NC}"
    echo -e "  ${GREEN}health${NC}       전체 서비스 헬스체크"
    echo -e "  ${GREEN}connect${NC}      데이터베이스 콘솔 접속"
    echo -e "  ${GREEN}backup${NC}       데이터 백업"
    echo -e "  ${GREEN}restore${NC}      데이터 복원"
    echo -e "  ${GREEN}cleanup${NC}      불필요한 Docker 리소스 정리"
    echo ""
    echo -e "${YELLOW}정보 확인:${NC}"
    echo -e "  ${GREEN}info${NC}         연결 정보 출력"
    echo -e "  ${GREEN}ports${NC}        포트 사용 현황"
    echo -e "  ${GREEN}help${NC}         이 도움말 표시"
    echo ""
    echo -e "${YELLOW}예시:${NC}"
    echo -e "  ${CYAN}./scripts/dev-docker.sh start${NC}           # 모든 서비스 시작"
    echo -e "  ${CYAN}./scripts/dev-docker.sh mysql start${NC}     # MySQL만 시작"
    echo -e "  ${CYAN}./scripts/dev-docker.sh logs mysql${NC}      # MySQL 로그만 확인"
    echo -e "  ${CYAN}./scripts/dev-docker.sh connect mysql${NC}   # MySQL 콘솔 접속"
    echo ""
}

# 서비스 상태 확인
check_status() {
    echo -e "${BLUE}📊 서비스 상태 확인${NC}"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    docker-compose -f $COMPOSE_FILE ps --format "table {{.Name}}\t{{.Status}}\t{{.Ports}}"
    echo ""
}

# 연결 정보 출력
show_info() {
    print_logo
    echo -e "${WHITE}🔌 데이터베이스 연결 정보${NC}"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    echo ""
    echo -e "${YELLOW}MySQL:${NC}"
    echo -e "  Host: ${CYAN}localhost:3307${NC}"
    echo -e "  Database: ${CYAN}factory${NC}" 
    echo -e "  Username: ${CYAN}factory_user${NC}"
    echo -e "  Password: ${CYAN}factory_dev_pass${NC}"
    echo -e "  Root Password: ${CYAN}factory_dev_root${NC}"
    echo ""
    echo -e "${YELLOW}MongoDB:${NC}"
    echo -e "  Host: ${CYAN}localhost:27017${NC}"
    echo -e "  Database: ${CYAN}factory${NC}"
    echo -e "  Username: ${CYAN}root${NC}"
    echo -e "  Password: ${CYAN}factory_dev_mongo${NC}"
    echo -e "  Connection String: ${CYAN}mongodb://root:factory_dev_mongo@localhost:27017/factory${NC}"
    echo ""
    echo -e "${YELLOW}Redis:${NC}"
    echo -e "  Host: ${CYAN}localhost:6379${NC}"
    echo -e "  Password: ${CYAN}(없음)${NC}"
    echo ""
    echo -e "${YELLOW}OpenSearch:${NC}"
    echo -e "  Host: ${CYAN}http://localhost:9200${NC}"
    echo -e "  Username: ${CYAN}(없음 - 보안 비활성화)${NC}"
    echo -e "  Dashboard: ${CYAN}http://localhost:5601${NC}"
    echo ""
    echo -e "${WHITE}🏃‍♂️ Spring Boot application-dev.yml 예시:${NC}"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    echo -e "${CYAN}spring:
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
  url: http://localhost:9200${NC}"
    echo ""
}

# 헬스체크
health_check() {
    echo -e "${BLUE}🏥 서비스 헬스체크${NC}"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    
    # MySQL 체크
    echo -n -e "${YELLOW}MySQL (3307): ${NC}"
    if timeout 5 bash -c "</dev/tcp/localhost/3307" 2>/dev/null; then
        echo -e "${GREEN}✅ 정상${NC}"
    else
        echo -e "${RED}❌ 연결 실패${NC}"
    fi
    
    # Redis 체크  
    echo -n -e "${YELLOW}Redis (6379): ${NC}"
    if timeout 5 bash -c "</dev/tcp/localhost/6379" 2>/dev/null; then
        echo -e "${GREEN}✅ 정상${NC}"
    else
        echo -e "${RED}❌ 연결 실패${NC}"
    fi
    
    # MongoDB 체크
    echo -n -e "${YELLOW}MongoDB (27017): ${NC}"
    if timeout 5 bash -c "</dev/tcp/localhost/27017" 2>/dev/null; then
        echo -e "${GREEN}✅ 정상${NC}"
    else
        echo -e "${RED}❌ 연결 실패${NC}"
    fi
    
    # OpenSearch 체크
    echo -n -e "${YELLOW}OpenSearch (9200): ${NC}"
    if curl -s http://localhost:9200/_cluster/health > /dev/null 2>&1; then
        echo -e "${GREEN}✅ 정상${NC}"
        # 클러스터 상태도 확인
        CLUSTER_STATUS=$(curl -s http://localhost:9200/_cluster/health | grep -o '"status":"[^"]*"' | cut -d'"' -f4)
        echo -e "  클러스터 상태: ${CYAN}${CLUSTER_STATUS}${NC}"
    else
        echo -e "${RED}❌ 연결 실패${NC}"
    fi
    
    # Dashboards 체크
    echo -n -e "${YELLOW}OpenSearch Dashboards (5601): ${NC}"
    if timeout 5 bash -c "</dev/tcp/localhost/5601" 2>/dev/null; then
        echo -e "${GREEN}✅ 정상${NC}"
    else
        echo -e "${RED}❌ 연결 실패${NC}"
    fi
    echo ""
}

# 포트 사용 현황
show_ports() {
    echo -e "${BLUE}🔍 포트 사용 현황${NC}"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    
    PORTS=(3307 6379 27017 9200 5601)
    PORT_NAMES=("MySQL" "Redis" "MongoDB" "OpenSearch" "Dashboards")
    
    for i in "${!PORTS[@]}"; do
        PORT=${PORTS[$i]}
        NAME=${PORT_NAMES[$i]}
        echo -n -e "${YELLOW}${NAME} (${PORT}): ${NC}"
        
        if lsof -i :$PORT > /dev/null 2>&1; then
            PROCESS=$(lsof -i :$PORT | tail -n 1 | awk '{print $1}')
            echo -e "${GREEN}사용 중 (${PROCESS})${NC}"
        else
            echo -e "${RED}사용 안함${NC}"
        fi
    done
    echo ""
}

# 데이터베이스 연결
connect_db() {
    case $1 in
        mysql)
            echo -e "${BLUE}🔌 MySQL 연결 중...${NC}"
            docker exec -it ${PROJECT_NAME}-mysql-dev mysql -u factory_user -p factory
            ;;
        mongodb)
            echo -e "${BLUE}🔌 MongoDB 연결 중...${NC}"
            docker exec -it ${PROJECT_NAME}-mongodb-dev mongosh -u root -p factory
            ;;
        redis)
            echo -e "${BLUE}🔌 Redis 연결 중...${NC}"
            docker exec -it ${PROJECT_NAME}-redis-dev redis-cli
            ;;
        *)
            echo -e "${RED}❌ 지원되지 않는 데이터베이스: $1${NC}"
            echo -e "${YELLOW}사용 가능: mysql, mongodb, redis${NC}"
            ;;
    esac
}

# 로그 확인
show_logs() {
    if [ -z "$1" ]; then
        echo -e "${BLUE}📄 전체 서비스 로그 (실시간)${NC}"
        echo -e "${YELLOW}Ctrl+C로 종료${NC}"
        echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
        docker-compose -f $COMPOSE_FILE logs -f
    else
        echo -e "${BLUE}📄 $1 서비스 로그 (실시간)${NC}"
        echo -e "${YELLOW}Ctrl+C로 종료${NC}"
        echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
        docker-compose -f $COMPOSE_FILE logs -f $1
    fi
}

# 개별 서비스 관리
manage_service() {
    SERVICE=$1
    ACTION=$2
    
    case $ACTION in
        start)
            echo -e "${GREEN}🚀 $SERVICE 시작 중...${NC}"
            docker-compose -f $COMPOSE_FILE up -d $SERVICE
            ;;
        stop)
            echo -e "${YELLOW}⏹️  $SERVICE 중지 중...${NC}"
            docker-compose -f $COMPOSE_FILE stop $SERVICE
            ;;
        restart)
            echo -e "${BLUE}🔄 $SERVICE 재시작 중...${NC}"
            docker-compose -f $COMPOSE_FILE restart $SERVICE
            ;;
        *)
            echo -e "${GREEN}🚀 $SERVICE 시작 중...${NC}"
            docker-compose -f $COMPOSE_FILE up -d $SERVICE
            ;;
    esac
}

# 전체 시작
start_all() {
    echo -e "${GREEN}🚀 전체 서비스 시작 중...${NC}"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    docker-compose -f $COMPOSE_FILE up -d
    echo ""
    echo -e "${BLUE}⏳ 서비스 초기화 대기 중... (30초)${NC}"
    sleep 5
    check_status
    echo ""
    echo -e "${GREEN}✅ 모든 서비스가 시작되었습니다!${NC}"
    echo -e "${CYAN}💡 헬스체크: ${WHITE}./scripts/dev-docker.sh health${NC}"
    echo -e "${CYAN}💡 연결 정보: ${WHITE}./scripts/dev-docker.sh info${NC}"
}

# 전체 중지
stop_all() {
    echo -e "${YELLOW}⏹️  전체 서비스 중지 중...${NC}"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    docker-compose -f $COMPOSE_FILE down
    echo -e "${GREEN}✅ 모든 서비스가 중지되었습니다.${NC}"
}

# 전체 재시작
restart_all() {
    echo -e "${BLUE}🔄 전체 서비스 재시작 중...${NC}"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    docker-compose -f $COMPOSE_FILE restart
    echo -e "${GREEN}✅ 모든 서비스가 재시작되었습니다.${NC}"
}

# 클린 시작 (데이터 초기화)
clean_start() {
    echo -e "${RED}🧹 모든 데이터를 초기화하고 재시작합니다.${NC}"
    echo -e "${YELLOW}⚠️  이 작업은 모든 데이터베이스 데이터를 삭제합니다!${NC}"
    read -p "계속하시겠습니까? (y/N): " -n 1 -r
    echo
    if [[ $REPLY =~ ^[Yy]$ ]]; then
        echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
        docker-compose -f $COMPOSE_FILE down -v
        docker-compose -f $COMPOSE_FILE up -d
        echo -e "${GREEN}✅ 클린 시작이 완료되었습니다!${NC}"
    else
        echo -e "${BLUE}취소되었습니다.${NC}"
    fi
}

# Docker 정리
cleanup_docker() {
    echo -e "${BLUE}🧹 Docker 리소스 정리${NC}"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    echo -e "${YELLOW}사용하지 않는 이미지, 컨테이너, 네트워크를 정리합니다.${NC}"
    read -p "계속하시겠습니까? (y/N): " -n 1 -r
    echo
    if [[ $REPLY =~ ^[Yy]$ ]]; then
        docker system prune -a --volumes
        echo -e "${GREEN}✅ Docker 정리가 완료되었습니다.${NC}"
    else
        echo -e "${BLUE}취소되었습니다.${NC}"
    fi
}

# 메인 로직
case $1 in
    help|--help|-h)
        show_help
        ;;
    start)
        start_all
        ;;
    stop)
        stop_all
        ;;
    restart)
        restart_all
        ;;
    status)
        check_status
        ;;
    logs)
        show_logs $2
        ;;
    clean)
        clean_start
        ;;
    health)
        health_check
        ;;
    info)
        show_info
        ;;
    ports)
        show_ports
        ;;
    connect)
        connect_db $2
        ;;
    cleanup)
        cleanup_docker
        ;;
    mysql|redis|mongodb|opensearch|dashboards)
        manage_service $1 $2
        ;;
    *)
        echo -e "${RED}❌ 알 수 없는 명령어: $1${NC}"
        echo -e "${YELLOW}도움말을 보려면: ${WHITE}./scripts/dev-docker.sh help${NC}"
        exit 1
        ;;
esac