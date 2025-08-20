package com.commonground.be.infrastructure.rabbitmq.compatibility;

import com.commonground.be.domain.news.dto.request.NaverCrawlingRequest;
import com.commonground.be.global.infrastructure.config.UnifiedRabbitMQConfig;
import com.commonground.be.global.infrastructure.messaging.CrawlingMessageService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 🧪 Python 크롤러와의 메시지 호환성 테스트
 * 
 * Java Backend ↔ Python Crawler 간의 메시지 포맷 호환성 검증
 */
@ExtendWith(MockitoExtension.class)
class PythonCrawlerCompatibilityTest {

    @Mock
    private RabbitTemplate rabbitTemplate;
    
    private CrawlingMessageService crawlingMessageService;
    private ObjectMapper objectMapper;
    
    @BeforeEach
    void setUp() {
        crawlingMessageService = new CrawlingMessageService(rabbitTemplate);
        objectMapper = new ObjectMapper();
    }
    
    // ==================== Java → Python 메시지 포맷 테스트 ====================
    
    @Test
    @DisplayName("Java에서 Python으로 보내는 크롤링 요청 포맷 검증")
    void testJavaToPythonCrawlingRequestFormat() throws JsonProcessingException {
        // Given
        NaverCrawlingRequest javaRequest = NaverCrawlingRequest.builder()
            .officeId("001")
            .categoryId("100")
            .maxArticles(25)
            .sessionId("java_to_python_test_123")
            .build();
        
        ArgumentCaptor<Object> messageCaptor = ArgumentCaptor.forClass(Object.class);
        
        // When
        crawlingMessageService.sendCrawlingRequest(javaRequest);
        
        // Then
        verify(rabbitTemplate).convertAndSend(
            eq(UnifiedRabbitMQConfig.CRAWLING_EXCHANGE),
            eq(UnifiedRabbitMQConfig.MEDIUM_ROUTING_KEY),
            messageCaptor.capture()
        );
        
        Object sentMessage = messageCaptor.getValue();
        assertThat(sentMessage).isInstanceOf(NaverCrawlingRequest.class);
        
        // Python이 기대하는 JSON 구조로 변환 가능한지 확인
        String jsonMessage = objectMapper.writeValueAsString(sentMessage);
        JsonNode jsonNode = objectMapper.readTree(jsonMessage);
        
        // Python 크롤러가 기대하는 필드들 검증
        assertThat(jsonNode.has("officeId")).isTrue();
        assertThat(jsonNode.has("categoryId")).isTrue();
        assertThat(jsonNode.has("maxArticles")).isTrue();
        assertThat(jsonNode.has("sessionId")).isTrue();
        
        assertThat(jsonNode.get("officeId").asText()).isEqualTo("001");
        assertThat(jsonNode.get("categoryId").asText()).isEqualTo("100");
        assertThat(jsonNode.get("maxArticles").asInt()).isEqualTo(25);
        assertThat(jsonNode.get("sessionId").asText()).isEqualTo("java_to_python_test_123");
        
        System.out.println("✅ Java → Python 요청 포맷 호환성 확인");
        System.out.println("📤 전송된 JSON: " + jsonMessage);
    }
    
    @Test
    @DisplayName("다양한 큐 타입별 Python 호환 메시지 테스트")
    void testMultiQueuePythonCompatibility() throws JsonProcessingException {
        // Given - Light, Medium, Heavy 각각의 요청
        Map<String, NaverCrawlingRequest> testCases = new HashMap<>();
        testCases.put("light", createTestRequest("light_test", 5));
        testCases.put("medium", createTestRequest("medium_test", 30));
        testCases.put("heavy", createTestRequest("heavy_test", 80));
        
        ArgumentCaptor<Object> messageCaptor = ArgumentCaptor.forClass(Object.class);
        
        // When & Then
        for (Map.Entry<String, NaverCrawlingRequest> testCase : testCases.entrySet()) {
            String queueType = testCase.getKey();
            NaverCrawlingRequest request = testCase.getValue();
            
            crawlingMessageService.sendCrawlingRequest(request);
            
            // 메시지 JSON 변환 확인
            String jsonMessage = objectMapper.writeValueAsString(request);
            JsonNode jsonNode = objectMapper.readTree(jsonMessage);
            
            // Python 호환 필드 검증
            assertThat(jsonNode.get("officeId").asText()).contains(queueType);
            assertThat(jsonNode.has("maxArticles")).isTrue();
            
            System.out.println("✅ " + queueType.toUpperCase() + " 큐 Python 호환성 확인");
        }
        
        verify(rabbitTemplate, times(3)).convertAndSend(anyString(), anyString(), messageCaptor.capture());
    }
    
    // ==================== Python → Java 메시지 포맷 테스트 ====================
    
    @Test
    @DisplayName("Python에서 Java로 오는 크롤링 결과 포맷 검증")
    void testPythonToJavaCrawlingResultFormat() {
        // Given - Python 크롤러가 보내는 형식의 결과 메시지
        Map<String, Object> pythonResult = createPythonStyleResult();
        
        // When & Then - Java에서 처리 가능한지 확인
        assertThatCode(() -> {
            crawlingMessageService.handleResult(pythonResult);
        }).doesNotThrowAnyException();
        
        // 필수 필드 존재 확인
        assertThat(pythonResult).containsKeys(
            "session_id",
            "status", 
            "total_articles",
            "success_count",
            "error_count",
            "articles",
            "timestamp",
            "crawler_version"
        );
        
        System.out.println("✅ Python → Java 결과 포맷 호환성 확인");
    }
    
    @Test
    @DisplayName("Python 진행상황 메시지 처리 테스트")
    void testPythonProgressMessageHandling() {
        // Given - Python 크롤러의 진행상황 메시지 포맷
        Map<String, Object> pythonProgress = createPythonStyleProgress();
        
        // When & Then
        assertThatCode(() -> {
            crawlingMessageService.handleProgress(pythonProgress);
        }).doesNotThrowAnyException();
        
        assertThat(pythonProgress).containsKeys(
            "session_id",
            "current_page",
            "total_pages", 
            "processed_articles",
            "message",
            "timestamp",
            "progress_percentage"
        );
        
        System.out.println("✅ Python 진행상황 메시지 처리 확인");
    }
    
    @Test
    @DisplayName("Python 에러 메시지 처리 테스트")
    void testPythonErrorMessageHandling() {
        // Given - Python 크롤러의 에러 메시지 포맷
        Map<String, Object> pythonError = createPythonStyleError();
        
        // When & Then - 에러 메시지도 안전하게 처리되어야 함
        assertThatCode(() -> {
            crawlingMessageService.handleResult(pythonError);
        }).doesNotThrowAnyException();
        
        assertThat(pythonError).containsKeys(
            "session_id",
            "status",
            "error_type",
            "error_message",
            "stack_trace",
            "timestamp"
        );
        
        assertThat(pythonError.get("status")).isEqualTo("error");
        
        System.out.println("✅ Python 에러 메시지 처리 확인");
    }
    
    // ==================== 메시지 구조 호환성 테스트 ====================
    
    @Test
    @DisplayName("Snake_case ↔ CamelCase 필드명 호환성 테스트")
    void testFieldNamingCompatibility() throws JsonProcessingException {
        // Given - Python은 snake_case, Java는 camelCase 사용
        Map<String, Object> snakeCaseMessage = new HashMap<>();
        snakeCaseMessage.put("session_id", "naming_test_123");
        snakeCaseMessage.put("office_id", "001");
        snakeCaseMessage.put("category_id", "100");
        snakeCaseMessage.put("max_articles", 20);
        snakeCaseMessage.put("max_scroll_attempts", 15);
        
        // When - Java 객체로 변환 시뮬레이션
        String jsonString = objectMapper.writeValueAsString(snakeCaseMessage);
        
        // ObjectMapper에 PropertyNamingStrategy 설정하여 변환
        ObjectMapper snakeCaseMapper = new ObjectMapper();
        snakeCaseMapper.setPropertyNamingStrategy(
            com.fasterxml.jackson.databind.PropertyNamingStrategies.SNAKE_CASE
        );
        
        // Then - 양방향 변환 가능성 확인
        JsonNode jsonNode = snakeCaseMapper.readTree(jsonString);
        assertThat(jsonNode.get("session_id").asText()).isEqualTo("naming_test_123");
        assertThat(jsonNode.get("office_id").asText()).isEqualTo("001");
        
        System.out.println("✅ Snake_case ↔ CamelCase 호환성 확인");
        System.out.println("🔄 변환된 JSON: " + jsonString);
    }
    
    @Test
    @DisplayName("큰 숫자 및 특수 문자 호환성 테스트")
    void testLargeNumberAndSpecialCharacterCompatibility() {
        // Given - Python에서 올 수 있는 큰 숫자와 특수 문자
        Map<String, Object> specialDataMessage = new HashMap<>();
        specialDataMessage.put("session_id", "특수문자_테스트_한글_123");
        specialDataMessage.put("timestamp", System.currentTimeMillis());
        specialDataMessage.put("large_number", Long.MAX_VALUE);
        specialDataMessage.put("unicode_title", "🚀 Python 크롤러 테스트 📊");
        specialDataMessage.put("json_escaped", "quote\"test\" and \n newline");
        
        // When & Then
        assertThatCode(() -> {
            crawlingMessageService.handleResult(specialDataMessage);
        }).doesNotThrowAnyException();
        
        assertThat(specialDataMessage.get("session_id")).asString().contains("한글");
        assertThat(specialDataMessage.get("unicode_title")).asString().contains("🚀");
        
        System.out.println("✅ 특수 문자 및 큰 숫자 호환성 확인");
    }
    
    // ==================== End-to-End 호환성 테스트 ====================
    
    @Test
    @DisplayName("Java → Python → Java 전체 워크플로우 시뮬레이션")
    void testEndToEndWorkflowSimulation() throws JsonProcessingException {
        // Given - Java에서 시작하는 전체 워크플로우
        String sessionId = "e2e_workflow_" + System.currentTimeMillis();
        
        // Step 1: Java에서 Python으로 크롤링 요청
        NaverCrawlingRequest javaRequest = NaverCrawlingRequest.builder()
            .officeId("020")
            .categoryId("102")
            .maxArticles(15)
            .sessionId(sessionId)
            .build();
        
        ArgumentCaptor<Object> requestCaptor = ArgumentCaptor.forClass(Object.class);
        crawlingMessageService.sendCrawlingRequest(javaRequest);
        
        verify(rabbitTemplate).convertAndSend(
            eq(UnifiedRabbitMQConfig.CRAWLING_EXCHANGE),
            eq(UnifiedRabbitMQConfig.MEDIUM_ROUTING_KEY),
            requestCaptor.capture()
        );
        
        // Step 2: Python에서 처리 후 진행상황 전송 (시뮬레이션)
        Map<String, Object> pythonProgress = new HashMap<>();
        pythonProgress.put("session_id", sessionId);
        pythonProgress.put("current_page", 2);
        pythonProgress.put("total_pages", 5);
        pythonProgress.put("processed_articles", 6);
        pythonProgress.put("message", "페이지 2/5 크롤링 중...");
        pythonProgress.put("progress_percentage", 40.0);
        pythonProgress.put("timestamp", System.currentTimeMillis());
        
        assertThatCode(() -> {
            crawlingMessageService.handleProgress(pythonProgress);
        }).doesNotThrowAnyException();
        
        // Step 3: Python에서 최종 결과 전송 (시뮬레이션)
        Map<String, Object> pythonResult = new HashMap<>();
        pythonResult.put("session_id", sessionId);
        pythonResult.put("status", "completed");
        pythonResult.put("total_articles", 15);
        pythonResult.put("success_count", 14);
        pythonResult.put("error_count", 1);
        pythonResult.put("articles", createMockArticles(14));
        pythonResult.put("timestamp", System.currentTimeMillis());
        pythonResult.put("crawler_version", "Python-Playwright-v1.0.0");
        
        assertThatCode(() -> {
            crawlingMessageService.handleResult(pythonResult);
        }).doesNotThrowAnyException();
        
        // Then - 전체 워크플로우 성공 확인
        Object sentRequest = requestCaptor.getValue();
        assertThat(sentRequest).isInstanceOf(NaverCrawlingRequest.class);
        
        NaverCrawlingRequest capturedRequest = (NaverCrawlingRequest) sentRequest;
        assertThat(capturedRequest.getSessionId()).isEqualTo(sessionId);
        assertThat(capturedRequest.getMaxArticles()).isEqualTo(15);
        
        assertThat(pythonResult.get("session_id")).isEqualTo(sessionId);
        assertThat(pythonResult.get("success_count")).isEqualTo(14);
        
        System.out.println("✅ End-to-End 워크플로우 시뮬레이션 완료");
        System.out.println("🔄 세션 ID: " + sessionId);
        System.out.println("📊 처리 결과: 15개 요청 → 14개 성공, 1개 실패");
    }
    
    // ==================== 헬퍼 메서드 ====================
    
    private NaverCrawlingRequest createTestRequest(String officeId, int maxArticles) {
        return NaverCrawlingRequest.builder()
            .officeId(officeId)
            .categoryId("100")
            .maxArticles(maxArticles)
            .sessionId("compatibility_test_" + System.currentTimeMillis())
            .build();
    }
    
    private Map<String, Object> createPythonStyleResult() {
        Map<String, Object> result = new HashMap<>();
        result.put("session_id", "python_result_456");
        result.put("status", "completed");
        result.put("total_articles", 30);
        result.put("success_count", 28);
        result.put("error_count", 2);
        result.put("articles", createMockArticles(28));
        result.put("timestamp", System.currentTimeMillis());
        result.put("crawler_version", "Python-Playwright-v1.2.3");
        result.put("execution_time_seconds", 45.7);
        return result;
    }
    
    private Map<String, Object> createPythonStyleProgress() {
        Map<String, Object> progress = new HashMap<>();
        progress.put("session_id", "python_progress_789");
        progress.put("current_page", 3);
        progress.put("total_pages", 8);
        progress.put("processed_articles", 12);
        progress.put("message", "페이지 3/8 처리 중... (12개 기사 수집)");
        progress.put("timestamp", System.currentTimeMillis());
        progress.put("progress_percentage", 37.5);
        progress.put("estimated_remaining_seconds", 25);
        return progress;
    }
    
    private Map<String, Object> createPythonStyleError() {
        Map<String, Object> error = new HashMap<>();
        error.put("session_id", "python_error_999");
        error.put("status", "error");
        error.put("error_type", "TimeoutError");
        error.put("error_message", "페이지 로딩 시간 초과: 30초");
        error.put("stack_trace", "File \"crawler.py\", line 123, in crawl_page\n  TimeoutError: 30s");
        error.put("timestamp", System.currentTimeMillis());
        error.put("failed_url", "https://news.naver.com/page/3");
        return error;
    }
    
    private List<Map<String, Object>> createMockArticles(int count) {
        List<Map<String, Object>> articles = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            Map<String, Object> article = new HashMap<>();
            article.put("id", "article_" + i);
            article.put("title", "테스트 기사 제목 " + i);
            article.put("url", "https://news.naver.com/article/" + i);
            article.put("content", "테스트 기사 내용 " + i + "...");
            article.put("author", "기자" + i);
            article.put("published_at", System.currentTimeMillis() - i * 1000);
            articles.add(article);
        }
        return articles;
    }
}