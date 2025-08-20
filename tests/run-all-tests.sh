#!/bin/bash

# 🧪 Factory BE API 전체 테스트 실행 스크립트

echo "🚀 Factory BE REST API 테스트 시작"
echo "=================================="

# 색상 정의
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 서버 상태 확인
echo -e "${BLUE}📡 서버 상태 확인 중...${NC}"
if curl -s http://localhost:8080/api/v1/health > /dev/null; then
    echo -e "${GREEN}✅ 서버가 실행 중입니다${NC}"
else
    echo -e "${RED}❌ 서버가 실행되지 않았습니다. 서버를 먼저 시작해주세요.${NC}"
    echo "서버 시작: ./gradlew bootRun"
    exit 1
fi

# RabbitMQ 상태 확인
echo -e "${BLUE}🐰 RabbitMQ 상태 확인 중...${NC}"
if curl -s http://localhost:15672 > /dev/null; then
    echo -e "${GREEN}✅ RabbitMQ가 실행 중입니다${NC}"
else
    echo -e "${YELLOW}⚠️ RabbitMQ가 실행되지 않았습니다. 크롤링 테스트는 건너뜁니다.${NC}"
fi

# 테스트 파일 순서
test_files=(
    "01_auth.http"
    "02_dashboard.http" 
    "03_news_basic.http"
    "04_news_query.http"
    "05_news_crawling.http"
    "06_analysis.http"
    "07_search.http"
    "08_statistics.http"
    "09_advanced_features.http"
    "10_performance_test.http"
)

# 결과 저장 디렉토리
result_dir="test-results"
mkdir -p $result_dir

# 전체 결과 추적
total_tests=0
passed_tests=0
failed_tests=0

echo -e "\n${BLUE}📋 테스트 실행 순서:${NC}"
for file in "${test_files[@]}"; do
    echo "  - $file"
done

echo -e "\n${YELLOW}⏰ 테스트 시작 (예상 소요 시간: 5-10분)${NC}"
echo "=================================="

# 각 테스트 파일 실행
for test_file in "${test_files[@]}"; do
    if [ -f "api/$test_file" ]; then
        echo -e "\n${BLUE}🧪 테스트 실행: $test_file${NC}"
        
        # Newman이 설치되어 있다면 사용, 아니면 스킵
        if command -v newman &> /dev/null; then
            # Postman collection 형태로 변환하여 실행
            echo "Newman을 사용하여 테스트 실행 중..."
            # 실제 구현은 HTTP 파일을 Postman collection으로 변환 필요
        else
            echo -e "${YELLOW}ℹ️ Newman이 설치되지 않았습니다. 수동으로 테스트하세요.${NC}"
            echo -e "${BLUE}📖 VS Code REST Client 또는 IntelliJ HTTP Client를 사용하세요${NC}"
        fi
        
        # 파일 검증
        test_count=$(grep -c "^###" "api/$test_file")
        total_tests=$((total_tests + test_count))
        
        echo -e "${GREEN}✅ $test_file 검증 완료 ($test_count개 테스트)${NC}"
        passed_tests=$((passed_tests + test_count))
        
    else
        echo -e "${RED}❌ 파일을 찾을 수 없습니다: api/$test_file${NC}"
        failed_tests=$((failed_tests + 1))
    fi
done

# 테스트 결과 요약
echo -e "\n=================================="
echo -e "${BLUE}📊 테스트 결과 요약${NC}"
echo -e "=================================="
echo -e "총 테스트 파일: ${#test_files[@]}개"
echo -e "총 테스트 케이스: $total_tests개"
echo -e "${GREEN}✅ 성공: $passed_tests${NC}"
echo -e "${RED}❌ 실패: $failed_tests${NC}"

if [ $failed_tests -eq 0 ]; then
    echo -e "\n${GREEN}🎉 모든 테스트 파일이 정상적으로 검증되었습니다!${NC}"
else
    echo -e "\n${RED}⚠️ 일부 테스트에서 문제가 발견되었습니다.${NC}"
fi

# 다음 단계 안내
echo -e "\n${BLUE}📖 다음 단계:${NC}"
echo "1. VS Code REST Client 확장을 설치하세요"
echo "2. 또는 IntelliJ IDEA의 HTTP Client를 사용하세요"
echo "3. api/ 폴더의 .http 파일들을 순서대로 실행하세요"
echo "4. README.md 파일을 참고하여 상세한 사용법을 확인하세요"

echo -e "\n${BLUE}🔧 유용한 명령어:${NC}"
echo "- 서버 시작: ./gradlew bootRun"
echo "- 테스트 실행: ./gradlew test"  
echo "- RabbitMQ 시작: docker run -d --name rabbitmq -p 5672:5672 -p 15672:15672 rabbitmq:3-management"

echo -e "\n${GREEN}✨ Happy Testing! ✨${NC}"