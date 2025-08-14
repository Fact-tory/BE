#!/bin/bash

# =================================
# CommonGround BE 통합 Docker 관리 스크립트
# 모든 개발환경 서비스를 통합 관리
# =================================

set -e  # 에러 발생시 스크립트 중단

# 색상 정의
RED='\\033[0;31m'
GREEN='\\033[0;32m'
YELLOW='\\033[1;33m'
BLUE='\\033[0;34m'
PURPLE='\\033[0;35m'
NC='\\033[0m' # No Color

# 설정 변수
COMPOSE_FILE="docker-compose.dev.yml"
ENV_FILE=".env.dev"
PROJECT_NAME="factory"

# 로그 함수
log_info() { echo -e "${BLUE}[INFO]${NC} $1"; }
log_success() { echo -e "${GREEN}[SUCCESS]${NC} $1"; }
log_warning() { echo -e "${YELLOW}[WARNING]${NC} $1"; }
log_error() { echo -e "${RED}[ERROR]${NC} $1"; }
log_step() { echo -e "${PURPLE}[STEP]${NC} $1"; }

# Docker 설치 확인
check_docker() {
    log_step "Docker 환경을 확인하는 중..."
    
    if ! command -v docker &> /dev/null; then
        log_error "Docker가 설치되어 있지 않습니다."
        echo "설치 방법: https://docs.docker.com/get-docker/"
        exit 1
    fi
    
    if ! command -v docker-compose &> /dev/null && ! docker compose version &> /dev/null 2>&1; then
        log_error "Docker Compose가 설치되어 있지 않습니다."
        echo "설치 방법: https://docs.docker.com/compose/install/"
        exit 1
    fi
    
    if ! docker info &> /dev/null; then
        log_error "Docker 데몬이 실행되지 않았습니다."
        echo "Docker Desktop을 시작하거나 'sudo systemctl start docker' 실행"
        exit 1
    fi
    
    log_success "Docker 환경이 정상적으로 설정되어 있습니다."
}

# 환경파일 확인
check_env_file() {
    log_step "환경 설정을 확인하는 중..."
    
    if [ ! -f "$ENV_FILE" ]; then
        log_error "$ENV_FILE 파일이 없습니다."
        log_info "환경 설정 파일을 생성해주세요."
        exit 1
    fi
    
    log_success "환경 설정 파일이 존재합니다."
}

# 서비스 시작
start_services() {
    log_step "CommonGround 개발환경을 시작합니다..."
    
    # 기존 컨테이너 정리 여부 확인
    read -p "기존 컨테이너를 정리하시겠습니까? (y/N): " cleanup
    if [[ $cleanup =~ ^[Yy]$ ]]; then
        log_info "기존 컨테이너를 정리하는 중..."
        docker-compose -f $COMPOSE_FILE --env-file $ENV_FILE down -v --remove-orphans
    fi
    
    # 서비스 시작
    log_info "Docker 서비스를 시작하는 중..."
    docker-compose -f $COMPOSE_FILE --env-file $ENV_FILE up -d
    
    # 서비스 상태 확인
    log_info "서비스 상태를 확인하는 중..."
    sleep 3
    docker-compose -f $COMPOSE_FILE ps
    
    # 헬스체크 수행
    wait_for_services
    
    log_success "🎉 모든 서비스가 성공적으로 시작되었습니다!"
    show_service_info
}

# 서비스 헬스체크 대기
wait_for_services() {
    log_step "서비스가 준비될 때까지 기다리는 중..."
    
    services=("mysql" "redis" "mongodb" "opensearch")
    ports=(3306 6379 27017 9200)
    
    for i in "${!services[@]}"; do
        service="${services[$i]}"
        port="${ports[$i]}"
        
        log_info "  - $service 서비스 대기 중..."
        
        max_attempts=30
        attempt=1
        
        while [ $attempt -le $max_attempts ]; do
            if check_service_health "$service" "$port"; then
                log_success "  ✅ $service 준비 완료"
                break
            fi
            
            if [ $attempt -eq $max_attempts ]; then
                log_warning "  ⚠️ $service 서비스 헬스체크 타임아웃 (계속 진행)"
            fi
            
            sleep 2
            ((attempt++))
        done
    done
}

# 개별 서비스 헬스체크
check_service_health() {
    local service=$1
    local port=$2
    
    case $service in
        "mysql")
            docker exec ${PROJECT_NAME}-mysql-dev mysqladmin ping -h localhost --silent 2>/dev/null
            ;;
        "redis")
            docker exec ${PROJECT_NAME}-redis-dev redis-cli ping 2>/dev/null | grep -q PONG
            ;;
        "mongodb")
            docker exec ${PROJECT_NAME}-mongodb-dev mongosh --eval "db.adminCommand('ping')" --quiet 2>/dev/null
            ;;
        "opensearch")
            curl -s http://localhost:9200/_cluster/health | grep -q '"status":"green"\\|"status":"yellow"'
            ;;
        *)
            nc -z localhost $port 2>/dev/null
            ;;
    esac
}

# 서비스 정보 표시
show_service_info() {
    echo
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    echo "🚀 CommonGround 개발환경 서비스 접속 정보"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    echo
    echo "📦 데이터베이스 서비스"
    echo "  • MySQL:     localhost:3306"
    echo "    - Database: factory"
    echo "    - User:     commonground_user"
    echo "    - Password: commonground_dev_pass"
    echo
    echo "  • MongoDB:   localhost:27017"
    echo "    - URI:      mongodb://root:commonground_dev_mongo@localhost:27017/factory?authSource=admin"
    echo
    echo "💾 캐시 및 검색 서비스"
    echo "  • Redis:     localhost:6379 (인증 없음)"
    echo
    echo "  • OpenSearch: http://localhost:9200"
    echo "    - User:     admin"
    echo "    - Password: commonground_dev_search"
    echo
    echo "  • OpenSearch Dashboards: http://localhost:5601"
    echo
    echo "🛠️  관리 명령어"
    echo "  • 로그 확인:    docker-compose -f $COMPOSE_FILE logs -f [service]"
    echo "  • 서비스 재시작: docker-compose -f $COMPOSE_FILE restart [service]"
    echo "  • 서비스 중지:   $0 stop"
    echo
    echo "🏃 Spring Boot 애플리케이션 시작"
    echo "  ./gradlew bootRun"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
}

# 서비스 중지
stop_services() {
    log_step "서비스를 중지합니다..."
    
    read -p "데이터를 완전히 삭제하시겠습니까? (y/N): " delete_data
    if [[ $delete_data =~ ^[Yy]$ ]]; then
        log_warning "⚠️ 모든 데이터를 삭제합니다..."
        docker-compose -f $COMPOSE_FILE --env-file $ENV_FILE down -v --remove-orphans
        log_success "서비스가 중지되고 모든 데이터가 삭제되었습니다."
    else
        log_info "데이터를 보존하며 서비스를 중지합니다..."
        docker-compose -f $COMPOSE_FILE --env-file $ENV_FILE down
        log_success "서비스가 중지되었습니다. 데이터는 보존되었습니다."
    fi
}

# 서비스 상태 확인
check_status() {
    log_step "서비스 상태를 확인합니다..."
    
    echo
    echo "📊 실행 중인 컨테이너:"
    if docker ps --format "table {{.Names}}\\t{{.Status}}\\t{{.Ports}}" | grep -q $PROJECT_NAME; then
        docker ps --format "table {{.Names}}\\t{{.Status}}\\t{{.Ports}}" | grep $PROJECT_NAME
        echo
        
        # 연결 테스트
        log_info "🔌 서비스 연결 테스트:"
        test_connections
    else
        log_warning "실행 중인 CommonGround 서비스가 없습니다."
        echo "서비스를 시작하려면: $0 start"
    fi
}

# 연결 테스트
test_connections() {
    services=("MySQL:3306" "Redis:6379" "MongoDB:27017" "OpenSearch:9200" "Dashboards:5601")
    
    for service_port in "${services[@]}"; do
        IFS=':' read -r service port <<< "$service_port"
        if nc -z localhost $port 2>/dev/null; then
            log_success "  ✅ $service (localhost:$port)"
        else
            log_warning "  ❌ $service (localhost:$port)"
        fi
    done
}

# 로그 확인
show_logs() {
    log_step "서비스 로그를 확인합니다..."
    
    echo
    echo "실행 중인 서비스:"
    docker ps --format "table {{.Names}}\\t{{.Status}}" | grep $PROJECT_NAME || {
        log_warning "실행 중인 서비스가 없습니다."
        return
    }
    
    echo
    echo "로그 확인 옵션:"
    echo "1) 모든 서비스 로그"
    echo "2) 특정 서비스 로그"
    echo "3) 실시간 로그 모니터링"
    
    read -p "선택 (1-3): " log_choice
    
    case $log_choice in
        1)
            docker-compose -f $COMPOSE_FILE --env-file $ENV_FILE logs
            ;;
        2)
            read -p "서비스명 입력 (mysql/redis/mongodb/opensearch/opensearch-dashboards): " service_name
            docker-compose -f $COMPOSE_FILE --env-file $ENV_FILE logs "$service_name" || {
                log_error "서비스 '$service_name'를 찾을 수 없습니다."
            }
            ;;
        3)
            log_info "실시간 로그 모니터링 (Ctrl+C로 종료)"
            docker-compose -f $COMPOSE_FILE --env-file $ENV_FILE logs -f
            ;;
        *)
            log_warning "올바른 번호를 입력하세요."
            ;;
    esac
}

# 데이터베이스 접속
connect_database() {
    log_step "데이터베이스 접속..."
    
    echo "접속할 데이터베이스를 선택하세요:"
    echo "1) MySQL"
    echo "2) MongoDB" 
    echo "3) Redis CLI"
    
    read -p "선택 (1-3): " db_choice
    
    case $db_choice in
        1)
            if ! nc -z localhost 3306 2>/dev/null; then
                log_error "MySQL 서비스가 실행되지 않았습니다."
                return 1
            fi
            
            echo "MySQL 사용자 선택:"
            echo "1) root"
            echo "2) commonground_user"
            
            read -p "선택 (1-2): " user_choice
            case $user_choice in
                1) docker exec -it ${PROJECT_NAME}-mysql-dev mysql -u root -p ;;
                2) docker exec -it ${PROJECT_NAME}-mysql-dev mysql -u commonground_user -p factory ;;
                *) log_warning "올바른 번호를 입력하세요." ;;
            esac
            ;;
        2)
            if ! nc -z localhost 27017 2>/dev/null; then
                log_error "MongoDB 서비스가 실행되지 않았습니다."
                return 1
            fi
            docker exec -it ${PROJECT_NAME}-mongodb-dev mongosh -u root -p commonground_dev_mongo --authenticationDatabase admin factory
            ;;
        3)
            if ! nc -z localhost 6379 2>/dev/null; then
                log_error "Redis 서비스가 실행되지 않았습니다."
                return 1
            fi
            docker exec -it ${PROJECT_NAME}-redis-dev redis-cli
            ;;
        *)
            log_warning "올바른 번호를 입력하세요."
            ;;
    esac
}

# 개발 도구
dev_tools() {
    log_step "개발 도구 메뉴"
    
    echo "개발 도구를 선택하세요:"
    echo "1) OpenSearch 인덱스 확인"
    echo "2) MongoDB 컬렉션 확인"
    echo "3) Redis 키 확인"
    echo "4) 서비스 헬스체크"
    echo "5) 뒤로 가기"
    
    read -p "선택 (1-5): " tool_choice
    
    case $tool_choice in
        1)
            log_info "OpenSearch 인덱스 정보:"
            curl -s -u admin:commonground_dev_search http://localhost:9200/_cat/indices?v || log_error "OpenSearch 연결 실패"
            ;;
        2)
            log_info "MongoDB 컬렉션 정보:"
            docker exec ${PROJECT_NAME}-mongodb-dev mongosh -u root -p commonground_dev_mongo --authenticationDatabase admin --eval "use factory; show collections" || log_error "MongoDB 연결 실패"
            ;;
        3)
            log_info "Redis 키 정보:"
            docker exec ${PROJECT_NAME}-redis-dev redis-cli info keyspace || log_error "Redis 연결 실패"
            ;;
        4)
            check_status
            ;;
        5)
            return
            ;;
        *)
            log_warning "올바른 번호를 입력하세요."
            ;;
    esac
}

# 메인 메뉴
show_menu() {
    clear
    echo
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    echo "🐳 CommonGround Backend Docker 관리 도구"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    echo
    echo "1️⃣  서비스 시작"
    echo "2️⃣  서비스 중지"
    echo "3️⃣  서비스 상태 확인"
    echo "4️⃣  로그 확인"
    echo "5️⃣  데이터베이스 접속"
    echo "6️⃣  개발 도구"
    echo "9️⃣  종료"
    echo
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
}

# CLI 인수 처리
handle_cli_args() {
    case "${1:-}" in
        "start")
            start_services
            exit 0
            ;;
        "stop")
            stop_services
            exit 0
            ;;
        "status")
            check_status
            exit 0
            ;;
        "logs")
            show_logs
            exit 0
            ;;
        "-h"|"--help"|"help")
            echo "사용법: $0 [start|stop|status|logs|help]"
            echo
            echo "명령어:"
            echo "  start   - 모든 서비스 시작"
            echo "  stop    - 모든 서비스 중지"
            echo "  status  - 서비스 상태 확인"
            echo "  logs    - 서비스 로그 확인"
            echo "  help    - 도움말 표시"
            echo
            echo "인수 없이 실행하면 대화형 메뉴가 표시됩니다."
            exit 0
            ;;
        "")
            # 대화형 모드
            ;;
        *)
            log_error "알 수 없는 명령어: $1"
            echo "사용법: $0 [start|stop|status|logs|help]"
            exit 1
            ;;
    esac
}

# 메인 함수
main() {
    # CLI 인수가 있으면 처리
    handle_cli_args "$@"
    
    # 환경 확인
    check_docker
    check_env_file
    
    # 대화형 메뉴
    while true; do
        show_menu
        read -p "메뉴를 선택하세요 (1-9): " menu_choice
        
        case $menu_choice in
            1) start_services ;;
            2) stop_services ;;
            3) check_status ;;
            4) show_logs ;;
            5) connect_database ;;
            6) dev_tools ;;
            9) 
                log_info "스크립트를 종료합니다."
                exit 0
                ;;
            *)
                log_warning "올바른 번호를 입력하세요 (1-9)"
                ;;
        esac
        
        echo
        read -p "계속하려면 Enter를 누르세요..."
    done
}

# 스크립트 실행
main "$@"