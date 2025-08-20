package com.commonground.be.domain.analysis.service;

import com.commonground.be.global.application.exception.CommonException;
import com.commonground.be.global.application.response.ResponseExceptionEnum;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("KeywordExtractionService 단위 테스트")
class KeywordExtractionServiceTest {

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private KeywordExtractionService keywordExtractionService;

    private String testContent;

    @BeforeEach
    void setUp() {
        testContent = "정부가 새로운 경제 정책을 발표했습니다. 이 정책은 국민들의 복지 향상과 사회 발전을 목표로 합니다.";
    }

    @Test
    @DisplayName("키워드 추출 성공")
    void extractKeywords_Success() throws Exception {
        // Given
        String expectedJsonResult = "{\"primaryKeywords\":[{\"keyword\":\"정부\",\"frequency\":5}],\"totalKeywords\":8}";
        given(objectMapper.writeValueAsString(any(Map.class))).willReturn(expectedJsonResult);

        // Mock Thread.sleep to avoid actual delay in tests
        try (MockedStatic<Thread> mockedThread = mockStatic(Thread.class)) {
            mockedThread.when(() -> Thread.sleep(anyLong())).thenAnswer(invocation -> null);

            // When
            String result = keywordExtractionService.extractKeywords(testContent);

            // Then
            assertThat(result).isEqualTo(expectedJsonResult);
            verify(objectMapper).writeValueAsString(any(Map.class));
        }
    }

    @Test
    @DisplayName("키워드 추출 - JSON 직렬화 실패")
    void extractKeywords_JsonSerializationFailure() throws Exception {
        // Given
        given(objectMapper.writeValueAsString(any(Map.class)))
                .willThrow(new JsonProcessingException("JSON 직렬화 오류") {});

        // Mock Thread.sleep to avoid actual delay in tests
        try (MockedStatic<Thread> mockedThread = mockStatic(Thread.class)) {
            mockedThread.when(() -> Thread.sleep(anyLong())).thenAnswer(invocation -> null);

            // When & Then
            assertThatThrownBy(() -> keywordExtractionService.extractKeywords(testContent))
                    .isInstanceOf(CommonException.class)
                    .hasFieldOrPropertyWithValue("responseExceptionEnum", ResponseExceptionEnum.KEYWORD_EXTRACTION_FAILED);
        }
    }

    @Test
    @DisplayName("키워드 추출 - Thread 인터럽트 예외")
    void extractKeywords_InterruptedException() throws Exception {
        // Given
        try (MockedStatic<Thread> mockedThread = mockStatic(Thread.class)) {
            mockedThread.when(() -> Thread.sleep(anyLong())).thenThrow(new InterruptedException("Thread interrupted"));

            // When & Then
            assertThatThrownBy(() -> keywordExtractionService.extractKeywords(testContent))
                    .isInstanceOf(CommonException.class)
                    .hasFieldOrPropertyWithValue("responseExceptionEnum", ResponseExceptionEnum.KEYWORD_EXTRACTION_FAILED);
        }
    }

    @Test
    @DisplayName("키워드 추출 - 빈 콘텐츠")
    void extractKeywords_EmptyContent() throws Exception {
        // Given
        String emptyContent = "";
        String expectedJsonResult = "{\"primaryKeywords\":[],\"totalKeywords\":0}";
        given(objectMapper.writeValueAsString(any(Map.class))).willReturn(expectedJsonResult);

        try (MockedStatic<Thread> mockedThread = mockStatic(Thread.class)) {
            mockedThread.when(() -> Thread.sleep(anyLong())).thenAnswer(invocation -> null);

            // When
            String result = keywordExtractionService.extractKeywords(emptyContent);

            // Then
            assertThat(result).isEqualTo(expectedJsonResult);
            verify(objectMapper).writeValueAsString(any(Map.class));
        }
    }

    @Test
    @DisplayName("키워드 추출 - null 콘텐츠")
    void extractKeywords_NullContent() throws Exception {
        // Given
        String expectedJsonResult = "{\"primaryKeywords\":[],\"totalKeywords\":0}";
        given(objectMapper.writeValueAsString(any(Map.class))).willReturn(expectedJsonResult);

        try (MockedStatic<Thread> mockedThread = mockStatic(Thread.class)) {
            mockedThread.when(() -> Thread.sleep(anyLong())).thenAnswer(invocation -> null);

            // When
            String result = keywordExtractionService.extractKeywords(null);

            // Then
            assertThat(result).isEqualTo(expectedJsonResult);
            verify(objectMapper).writeValueAsString(any(Map.class));
        }
    }

    @Test
    @DisplayName("키워드 추출 결과 구조 검증")
    void extractKeywords_ResultStructureValidation() throws Exception {
        // Given
        given(objectMapper.writeValueAsString(any(Map.class))).willAnswer(invocation -> {
            Map<String, Object> resultMap = invocation.getArgument(0);
            
            // 결과 구조 검증
            assertThat(resultMap).containsKeys(
                "primaryKeywords", "relatedKeywords", "entities", "topics", "summary", "totalKeywords"
            );
            
            // primaryKeywords 검증
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> primaryKeywords = (List<Map<String, Object>>) resultMap.get("primaryKeywords");
            assertThat(primaryKeywords).hasSizeBetween(5, 8);
            
            // 각 키워드 객체 구조 검증
            for (Map<String, Object> keyword : primaryKeywords) {
                assertThat(keyword).containsKeys("keyword", "frequency", "relevance", "category");
                assertThat((Integer) keyword.get("frequency")).isBetween(3, 10);
                assertThat((Double) keyword.get("relevance")).isBetween(0.7, 1.0);
            }
            
            // relatedKeywords 검증
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> relatedKeywords = (List<Map<String, Object>>) resultMap.get("relatedKeywords");
            assertThat(relatedKeywords).hasSizeBetween(3, 6);
            
            return "{\"primaryKeywords\":" + primaryKeywords.size() + ",\"totalKeywords\":" + 
                   (primaryKeywords.size() + relatedKeywords.size()) + "}";
        });

        try (MockedStatic<Thread> mockedThread = mockStatic(Thread.class)) {
            mockedThread.when(() -> Thread.sleep(anyLong())).thenAnswer(invocation -> null);

            // When
            String result = keywordExtractionService.extractKeywords(testContent);

            // Then
            assertThat(result).isNotEmpty();
            verify(objectMapper).writeValueAsString(any(Map.class));
        }
    }

    @Test
    @DisplayName("키워드 추출 - 주요 키워드 빈도순 정렬 검증")
    void extractKeywords_PrimaryKeywordsFrequencySort() throws Exception {
        // Given
        given(objectMapper.writeValueAsString(any(Map.class))).willAnswer(invocation -> {
            Map<String, Object> resultMap = invocation.getArgument(0);
            
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> primaryKeywords = (List<Map<String, Object>>) resultMap.get("primaryKeywords");
            
            // 빈도순으로 정렬되어 있는지 검증
            for (int i = 0; i < primaryKeywords.size() - 1; i++) {
                Integer currentFreq = (Integer) primaryKeywords.get(i).get("frequency");
                Integer nextFreq = (Integer) primaryKeywords.get(i + 1).get("frequency");
                assertThat(currentFreq).isGreaterThanOrEqualTo(nextFreq);
            }
            
            return "{\"primaryKeywords\":\"sorted\"}";
        });

        try (MockedStatic<Thread> mockedThread = mockStatic(Thread.class)) {
            mockedThread.when(() -> Thread.sleep(anyLong())).thenAnswer(invocation -> null);

            // When
            String result = keywordExtractionService.extractKeywords(testContent);

            // Then
            assertThat(result).isNotEmpty();
            verify(objectMapper).writeValueAsString(any(Map.class));
        }
    }

    @Test
    @DisplayName("키워드 추출 - 키워드 카테고리 분류 검증")
    void extractKeywords_KeywordCategorization() throws Exception {
        // Given
        given(objectMapper.writeValueAsString(any(Map.class))).willAnswer(invocation -> {
            Map<String, Object> resultMap = invocation.getArgument(0);
            
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> primaryKeywords = (List<Map<String, Object>>) resultMap.get("primaryKeywords");
            
            // 키워드 카테고리 적절성 검증
            for (Map<String, Object> keywordInfo : primaryKeywords) {
                String keyword = (String) keywordInfo.get("keyword");
                String category = (String) keywordInfo.get("category");
                
                // 특정 키워드의 카테고리가 적절한지 검증
                switch (keyword) {
                    case "정부", "정책" -> assertThat(category).isEqualTo("정치");
                    case "경제" -> assertThat(category).isEqualTo("경제");
                    case "사회", "국민" -> assertThat(category).isEqualTo("사회");
                    case "발표" -> assertThat(category).isEqualTo("미디어");
                    case "지원", "개선" -> assertThat(category).isEqualTo("정책");
                    default -> assertThat(category).isEqualTo("일반");
                }
            }
            
            return "{\"keywordCategorization\":\"validated\"}";
        });

        try (MockedStatic<Thread> mockedThread = mockStatic(Thread.class)) {
            mockedThread.when(() -> Thread.sleep(anyLong())).thenAnswer(invocation -> null);

            // When
            String result = keywordExtractionService.extractKeywords(testContent);

            // Then
            assertThat(result).isNotEmpty();
            verify(objectMapper).writeValueAsString(any(Map.class));
        }
    }

    @Test
    @DisplayName("키워드 추출 - 엔티티 추출 검증")
    void extractKeywords_EntityExtractionValidation() throws Exception {
        // Given
        given(objectMapper.writeValueAsString(any(Map.class))).willAnswer(invocation -> {
            Map<String, Object> resultMap = invocation.getArgument(0);
            
            @SuppressWarnings("unchecked")
            Map<String, List<String>> entities = (Map<String, List<String>>) resultMap.get("entities");
            
            // 엔티티 카테고리 검증
            assertThat(entities).containsKeys("PERSON", "ORGANIZATION", "LOCATION", "DATE");
            
            // 각 카테고리별 엔티티 존재 검증
            assertThat(entities.get("PERSON")).isNotEmpty();
            assertThat(entities.get("ORGANIZATION")).isNotEmpty();
            assertThat(entities.get("LOCATION")).isNotEmpty();
            assertThat(entities.get("DATE")).isNotEmpty();
            
            // 엔티티 값 적절성 검증
            assertThat(entities.get("ORGANIZATION")).contains("정부", "국회", "기재부", "교육부");
            assertThat(entities.get("LOCATION")).contains("서울", "부산", "대구", "한국");
            
            return "{\"entities\":\"validated\"}";
        });

        try (MockedStatic<Thread> mockedThread = mockStatic(Thread.class)) {
            mockedThread.when(() -> Thread.sleep(anyLong())).thenAnswer(invocation -> null);

            // When
            String result = keywordExtractionService.extractKeywords(testContent);

            // Then
            assertThat(result).isNotEmpty();
            verify(objectMapper).writeValueAsString(any(Map.class));
        }
    }

    @Test
    @DisplayName("키워드 추출 - 토픽 분류 검증")
    void extractKeywords_TopicClassificationValidation() throws Exception {
        // Given
        given(objectMapper.writeValueAsString(any(Map.class))).willAnswer(invocation -> {
            Map<String, Object> resultMap = invocation.getArgument(0);
            
            @SuppressWarnings("unchecked")
            List<String> topics = (List<String>) resultMap.get("topics");
            
            // 토픽 개수 검증
            assertThat(topics).hasSizeBetween(2, 4);
            
            // 토픽 값 유효성 검증
            List<String> validTopics = List.of(
                "정치/정책", "경제/재정", "사회/복지", "교육/문화", 
                "환경/에너지", "기술/혁신", "보건/의료", "국제/외교"
            );
            
            for (String topic : topics) {
                assertThat(validTopics).contains(topic);
            }
            
            // 중복 토픽 없음 검증
            assertThat(topics).doesNotHaveDuplicates();
            
            return "{\"topics\":" + topics + "}";
        });

        try (MockedStatic<Thread> mockedThread = mockStatic(Thread.class)) {
            mockedThread.when(() -> Thread.sleep(anyLong())).thenAnswer(invocation -> null);

            // When
            String result = keywordExtractionService.extractKeywords(testContent);

            // Then
            assertThat(result).isNotEmpty();
            verify(objectMapper).writeValueAsString(any(Map.class));
        }
    }

    @Test
    @DisplayName("키워드 추출 - 요약 생성 검증")
    void extractKeywords_SummaryGenerationValidation() throws Exception {
        // Given
        given(objectMapper.writeValueAsString(any(Map.class))).willAnswer(invocation -> {
            Map<String, Object> resultMap = invocation.getArgument(0);
            
            String summary = (String) resultMap.get("summary");
            Integer totalKeywords = (Integer) resultMap.get("totalKeywords");
            
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> primaryKeywords = (List<Map<String, Object>>) resultMap.get("primaryKeywords");
            
            @SuppressWarnings("unchecked")
            List<String> topics = (List<String>) resultMap.get("topics");
            
            // 요약 텍스트 형식 검증
            assertThat(summary).containsPattern("총 \\d+개의 주요 키워드와 \\d+개의 토픽이 식별되었습니다");
            assertThat(summary).contains("정치, 경제, 사회 분야");
            
            // 총 키워드 수 정확성 검증
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> relatedKeywords = (List<Map<String, Object>>) resultMap.get("relatedKeywords");
            Integer expectedTotal = primaryKeywords.size() + relatedKeywords.size();
            assertThat(totalKeywords).isEqualTo(expectedTotal);
            
            return "{\"summary\":\"" + summary + "\",\"totalKeywords\":" + totalKeywords + "}";
        });

        try (MockedStatic<Thread> mockedThread = mockStatic(Thread.class)) {
            mockedThread.when(() -> Thread.sleep(anyLong())).thenAnswer(invocation -> null);

            // When
            String result = keywordExtractionService.extractKeywords(testContent);

            // Then
            assertThat(result).isNotEmpty();
            verify(objectMapper).writeValueAsString(any(Map.class));
        }
    }

    @Test
    @DisplayName("키워드 추출 - 관련 키워드 검증")
    void extractKeywords_RelatedKeywordsValidation() throws Exception {
        // Given
        given(objectMapper.writeValueAsString(any(Map.class))).willAnswer(invocation -> {
            Map<String, Object> resultMap = invocation.getArgument(0);
            
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> relatedKeywords = (List<Map<String, Object>>) resultMap.get("relatedKeywords");
            
            // 관련 키워드 개수 검증
            assertThat(relatedKeywords).hasSizeBetween(3, 6);
            
            // 각 관련 키워드 구조 검증
            for (Map<String, Object> keywordInfo : relatedKeywords) {
                assertThat(keywordInfo).containsKeys("keyword", "frequency", "relevance");
                
                // 빈도는 주요 키워드보다 낮아야 함
                Integer frequency = (Integer) keywordInfo.get("frequency");
                assertThat(frequency).isBetween(1, 4);
                
                // 관련도는 주요 키워드보다 낮아야 함
                Double relevance = (Double) keywordInfo.get("relevance");
                assertThat(relevance).isBetween(0.5, 0.8);
            }
            
            return "{\"relatedKeywords\":\"validated\"}";
        });

        try (MockedStatic<Thread> mockedThread = mockStatic(Thread.class)) {
            mockedThread.when(() -> Thread.sleep(anyLong())).thenAnswer(invocation -> null);

            // When
            String result = keywordExtractionService.extractKeywords(testContent);

            // Then
            assertThat(result).isNotEmpty();
            verify(objectMapper).writeValueAsString(any(Map.class));
        }
    }

    @Test
    @DisplayName("키워드 추출 - 매우 긴 콘텐츠")
    void extractKeywords_VeryLongContent() throws Exception {
        // Given
        StringBuilder longContentBuilder = new StringBuilder();
        for (int i = 0; i < 1000; i++) {
            longContentBuilder.append("정부 정책 경제 사회 발전 국민 복지 ");
        }
        String longContent = longContentBuilder.toString();
        
        String expectedJsonResult = "{\"primaryKeywords\":10,\"totalKeywords\":15}";
        given(objectMapper.writeValueAsString(any(Map.class))).willReturn(expectedJsonResult);

        try (MockedStatic<Thread> mockedThread = mockStatic(Thread.class)) {
            mockedThread.when(() -> Thread.sleep(anyLong())).thenAnswer(invocation -> null);

            // When
            String result = keywordExtractionService.extractKeywords(longContent);

            // Then
            assertThat(result).isEqualTo(expectedJsonResult);
            verify(objectMapper).writeValueAsString(any(Map.class));
        }
    }

    @Test
    @DisplayName("키워드 추출 - 특수 문자 포함 콘텐츠")
    void extractKeywords_SpecialCharactersContent() throws Exception {
        // Given
        String specialCharContent = "정부!!! @#$%^&*() 정책~~~ 경제 😀😃😄 #키워드 @추출";
        String expectedJsonResult = "{\"primaryKeywords\":3,\"totalKeywords\":5}";
        given(objectMapper.writeValueAsString(any(Map.class))).willReturn(expectedJsonResult);

        try (MockedStatic<Thread> mockedThread = mockStatic(Thread.class)) {
            mockedThread.when(() -> Thread.sleep(anyLong())).thenAnswer(invocation -> null);

            // When
            String result = keywordExtractionService.extractKeywords(specialCharContent);

            // Then
            assertThat(result).isEqualTo(expectedJsonResult);
            verify(objectMapper).writeValueAsString(any(Map.class));
        }
    }

    @Test
    @DisplayName("키워드 추출 - 런타임 예외 처리")
    void extractKeywords_RuntimeException() throws Exception {
        // Given
        given(objectMapper.writeValueAsString(any(Map.class)))
                .willThrow(new RuntimeException("예상치 못한 오류"));

        try (MockedStatic<Thread> mockedThread = mockStatic(Thread.class)) {
            mockedThread.when(() -> Thread.sleep(anyLong())).thenAnswer(invocation -> null);

            // When & Then
            assertThatThrownBy(() -> keywordExtractionService.extractKeywords(testContent))
                    .isInstanceOf(CommonException.class)
                    .hasFieldOrPropertyWithValue("responseExceptionEnum", ResponseExceptionEnum.KEYWORD_EXTRACTION_FAILED);
        }
    }

    @Test
    @DisplayName("키워드 추출 - 랜덤 지연 시간 테스트")
    void extractKeywords_RandomDelayTest() throws Exception {
        // Given
        String expectedJsonResult = "{\"primaryKeywords\":5,\"totalKeywords\":8}";
        given(objectMapper.writeValueAsString(any(Map.class))).willReturn(expectedJsonResult);

        try (MockedStatic<Thread> mockedThread = mockStatic(Thread.class)) {
            // 지연 시간이 600-1600ms 범위인지 검증
            mockedThread.when(() -> Thread.sleep(anyLong())).thenAnswer(invocation -> {
                Long delay = invocation.getArgument(0);
                assertThat(delay).isBetween(600L, 1600L);
                return null;
            });

            // When
            String result = keywordExtractionService.extractKeywords(testContent);

            // Then
            assertThat(result).isEqualTo(expectedJsonResult);
            mockedThread.verify(() -> Thread.sleep(anyLong()));
        }
    }

    @Test
    @DisplayName("키워드 추출 - 키워드 중복 제거 검증")
    void extractKeywords_DuplicateKeywordRemoval() throws Exception {
        // Given
        given(objectMapper.writeValueAsString(any(Map.class))).willAnswer(invocation -> {
            Map<String, Object> resultMap = invocation.getArgument(0);
            
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> primaryKeywords = (List<Map<String, Object>>) resultMap.get("primaryKeywords");
            
            // 중복 키워드가 없는지 검증
            List<String> keywordTexts = primaryKeywords.stream()
                    .map(kw -> (String) kw.get("keyword"))
                    .toList();
            
            assertThat(keywordTexts).doesNotHaveDuplicates();
            
            return "{\"primaryKeywords\":\"no_duplicates\"}";
        });

        try (MockedStatic<Thread> mockedThread = mockStatic(Thread.class)) {
            mockedThread.when(() -> Thread.sleep(anyLong())).thenAnswer(invocation -> null);

            // When
            String result = keywordExtractionService.extractKeywords(testContent);

            // Then
            assertThat(result).isNotEmpty();
            verify(objectMapper).writeValueAsString(any(Map.class));
        }
    }
}