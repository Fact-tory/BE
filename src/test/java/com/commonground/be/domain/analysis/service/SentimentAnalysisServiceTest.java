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

import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SentimentAnalysisService 단위 테스트")
class SentimentAnalysisServiceTest {

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private SentimentAnalysisService sentimentAnalysisService;

    private String testContent;

    @BeforeEach
    void setUp() {
        testContent = "오늘은 정말 좋은 날입니다. 새로운 정책이 발표되어 국민들이 기뻐하고 있습니다.";
    }

    @Test
    @DisplayName("감정 분석 성공")
    void analyzeSentiment_Success() throws Exception {
        // Given
        String expectedJsonResult = "{\"sentimentScore\":0.75,\"primaryEmotion\":\"POSITIVE\",\"confidence\":0.85}";
        given(objectMapper.writeValueAsString(any(Map.class))).willReturn(expectedJsonResult);

        // Mock Thread.sleep to avoid actual delay in tests
        try (MockedStatic<Thread> mockedThread = mockStatic(Thread.class)) {
            mockedThread.when(() -> Thread.sleep(anyLong())).thenAnswer(invocation -> null);

            // When
            String result = sentimentAnalysisService.analyzeSentiment(testContent);

            // Then
            assertThat(result).isEqualTo(expectedJsonResult);
            verify(objectMapper).writeValueAsString(any(Map.class));
        }
    }

    @Test
    @DisplayName("감정 분석 - JSON 직렬화 실패")
    void analyzeSentiment_JsonSerializationFailure() throws Exception {
        // Given
        given(objectMapper.writeValueAsString(any(Map.class)))
                .willThrow(new JsonProcessingException("JSON 직렬화 오류") {});

        // Mock Thread.sleep to avoid actual delay in tests
        try (MockedStatic<Thread> mockedThread = mockStatic(Thread.class)) {
            mockedThread.when(() -> Thread.sleep(anyLong())).thenAnswer(invocation -> null);

            // When & Then
            assertThatThrownBy(() -> sentimentAnalysisService.analyzeSentiment(testContent))
                    .isInstanceOf(CommonException.class)
                    .hasFieldOrPropertyWithValue("responseExceptionEnum", ResponseExceptionEnum.SENTIMENT_ANALYSIS_FAILED);
        }
    }

    @Test
    @DisplayName("감정 분석 - Thread 인터럽트 예외")
    void analyzeSentiment_InterruptedException() throws Exception {
        // Given
        try (MockedStatic<Thread> mockedThread = mockStatic(Thread.class)) {
            mockedThread.when(() -> Thread.sleep(anyLong())).thenThrow(new InterruptedException("Thread interrupted"));

            // When & Then
            assertThatThrownBy(() -> sentimentAnalysisService.analyzeSentiment(testContent))
                    .isInstanceOf(CommonException.class)
                    .hasFieldOrPropertyWithValue("responseExceptionEnum", ResponseExceptionEnum.SENTIMENT_ANALYSIS_FAILED);
        }
    }

    @Test
    @DisplayName("감정 분석 - 부정적 콘텐츠")
    void analyzeSentiment_NegativeContent() throws Exception {
        // Given
        String negativeContent = "오늘은 정말 끔찍한 날입니다. 모든 것이 잘못되고 있어서 매우 슬픕니다.";
        String expectedJsonResult = "{\"sentimentScore\":-0.6,\"primaryEmotion\":\"NEGATIVE\",\"confidence\":0.8}";
        given(objectMapper.writeValueAsString(any(Map.class))).willReturn(expectedJsonResult);

        try (MockedStatic<Thread> mockedThread = mockStatic(Thread.class)) {
            mockedThread.when(() -> Thread.sleep(anyLong())).thenAnswer(invocation -> null);

            // When
            String result = sentimentAnalysisService.analyzeSentiment(negativeContent);

            // Then
            assertThat(result).isEqualTo(expectedJsonResult);
            verify(objectMapper).writeValueAsString(any(Map.class));
        }
    }

    @Test
    @DisplayName("감정 분석 - 중립적 콘텐츠")
    void analyzeSentiment_NeutralContent() throws Exception {
        // Given
        String neutralContent = "오늘 회의가 있었습니다. 여러 안건들에 대해 논의했습니다.";
        String expectedJsonResult = "{\"sentimentScore\":0.05,\"primaryEmotion\":\"NEUTRAL\",\"confidence\":0.9}";
        given(objectMapper.writeValueAsString(any(Map.class))).willReturn(expectedJsonResult);

        try (MockedStatic<Thread> mockedThread = mockStatic(Thread.class)) {
            mockedThread.when(() -> Thread.sleep(anyLong())).thenAnswer(invocation -> null);

            // When
            String result = sentimentAnalysisService.analyzeSentiment(neutralContent);

            // Then
            assertThat(result).isEqualTo(expectedJsonResult);
            verify(objectMapper).writeValueAsString(any(Map.class));
        }
    }

    @Test
    @DisplayName("감정 분석 - 빈 콘텐츠")
    void analyzeSentiment_EmptyContent() throws Exception {
        // Given
        String emptyContent = "";
        String expectedJsonResult = "{\"sentimentScore\":0.0,\"primaryEmotion\":\"NEUTRAL\"}";
        given(objectMapper.writeValueAsString(any(Map.class))).willReturn(expectedJsonResult);

        try (MockedStatic<Thread> mockedThread = mockStatic(Thread.class)) {
            mockedThread.when(() -> Thread.sleep(anyLong())).thenAnswer(invocation -> null);

            // When
            String result = sentimentAnalysisService.analyzeSentiment(emptyContent);

            // Then
            assertThat(result).isEqualTo(expectedJsonResult);
            verify(objectMapper).writeValueAsString(any(Map.class));
        }
    }

    @Test
    @DisplayName("감정 분석 - null 콘텐츠")
    void analyzeSentiment_NullContent() throws Exception {
        // Given
        String expectedJsonResult = "{\"sentimentScore\":0.0,\"primaryEmotion\":\"NEUTRAL\"}";
        given(objectMapper.writeValueAsString(any(Map.class))).willReturn(expectedJsonResult);

        try (MockedStatic<Thread> mockedThread = mockStatic(Thread.class)) {
            mockedThread.when(() -> Thread.sleep(anyLong())).thenAnswer(invocation -> null);

            // When
            String result = sentimentAnalysisService.analyzeSentiment(null);

            // Then
            assertThat(result).isEqualTo(expectedJsonResult);
            verify(objectMapper).writeValueAsString(any(Map.class));
        }
    }

    @Test
    @DisplayName("감정 분석 결과 구조 검증")
    void analyzeSentiment_ResultStructureValidation() throws Exception {
        // Given
        given(objectMapper.writeValueAsString(any(Map.class))).willAnswer(invocation -> {
            Map<String, Object> resultMap = invocation.getArgument(0);
            
            // 결과 구조 검증
            assertThat(resultMap).containsKeys(
                "sentimentScore", "primaryEmotion", "confidence", "emotionBreakdown", "analysis", "detectedKeywords"
            );
            
            // sentimentScore는 -1.0~1.0 범위
            Double sentimentScore = (Double) resultMap.get("sentimentScore");
            assertThat(sentimentScore).isBetween(-1.0, 1.0);
            
            // confidence는 0.0-1.0 범위
            Double confidence = (Double) resultMap.get("confidence");
            assertThat(confidence).isBetween(0.0, 1.0);
            
            // primaryEmotion은 유효한 값
            String primaryEmotion = (String) resultMap.get("primaryEmotion");
            assertThat(primaryEmotion).isIn("NEGATIVE", "SLIGHTLY_NEGATIVE", "NEUTRAL", "SLIGHTLY_POSITIVE", "POSITIVE");
            
            return "{\"sentimentScore\":" + sentimentScore + ",\"primaryEmotion\":\"" + primaryEmotion + "\"}";
        });

        try (MockedStatic<Thread> mockedThread = mockStatic(Thread.class)) {
            mockedThread.when(() -> Thread.sleep(anyLong())).thenAnswer(invocation -> null);

            // When
            String result = sentimentAnalysisService.analyzeSentiment(testContent);

            // Then
            assertThat(result).isNotEmpty();
            verify(objectMapper).writeValueAsString(any(Map.class));
        }
    }

    @Test
    @DisplayName("감정 분석 - 감정 분류 정확성 검증")
    void analyzeSentiment_EmotionClassificationAccuracy() throws Exception {
        // Given
        given(objectMapper.writeValueAsString(any(Map.class))).willAnswer(invocation -> {
            Map<String, Object> resultMap = invocation.getArgument(0);
            Double sentimentScore = (Double) resultMap.get("sentimentScore");
            String primaryEmotion = (String) resultMap.get("primaryEmotion");
            
            // 감정 점수와 분류의 일관성 검증
            if (sentimentScore < -0.3) {
                assertThat(primaryEmotion).isEqualTo("NEGATIVE");
            } else if (sentimentScore < -0.1) {
                assertThat(primaryEmotion).isEqualTo("SLIGHTLY_NEGATIVE");
            } else if (sentimentScore < 0.1) {
                assertThat(primaryEmotion).isEqualTo("NEUTRAL");
            } else if (sentimentScore < 0.3) {
                assertThat(primaryEmotion).isEqualTo("SLIGHTLY_POSITIVE");
            } else {
                assertThat(primaryEmotion).isEqualTo("POSITIVE");
            }
            
            return "{\"sentimentScore\":" + sentimentScore + ",\"primaryEmotion\":\"" + primaryEmotion + "\"}";
        });

        try (MockedStatic<Thread> mockedThread = mockStatic(Thread.class)) {
            mockedThread.when(() -> Thread.sleep(anyLong())).thenAnswer(invocation -> null);

            // When & Then - 여러 번 실행하여 다양한 점수 범위 테스트
            for (int i = 0; i < 10; i++) {
                String result = sentimentAnalysisService.analyzeSentiment(testContent + " " + i);
                assertThat(result).isNotEmpty();
            }

            verify(objectMapper, times(10)).writeValueAsString(any(Map.class));
        }
    }

    @Test
    @DisplayName("감정 분석 - 감정 분해 검증")
    void analyzeSentiment_EmotionBreakdownValidation() throws Exception {
        // Given
        given(objectMapper.writeValueAsString(any(Map.class))).willAnswer(invocation -> {
            Map<String, Object> resultMap = invocation.getArgument(0);
            
            // emotionBreakdown 검증
            @SuppressWarnings("unchecked")
            Map<String, Double> emotionBreakdown = (Map<String, Double>) resultMap.get("emotionBreakdown");
            
            assertThat(emotionBreakdown).containsKeys("joy", "sadness", "anger", "fear", "surprise");
            
            // 모든 감정 값이 0-100 범위
            for (Double value : emotionBreakdown.values()) {
                assertThat(value).isBetween(0.0, 100.0);
            }
            
            // 모든 감정의 합이 대략 100이어야 함 (반올림 오차 고려)
            double total = emotionBreakdown.values().stream().mapToDouble(Double::doubleValue).sum();
            assertThat(total).isBetween(99.0, 101.0);
            
            return "{\"emotionBreakdown\":" + emotionBreakdown + "}";
        });

        try (MockedStatic<Thread> mockedThread = mockStatic(Thread.class)) {
            mockedThread.when(() -> Thread.sleep(anyLong())).thenAnswer(invocation -> null);

            // When
            String result = sentimentAnalysisService.analyzeSentiment(testContent);

            // Then
            assertThat(result).isNotEmpty();
            verify(objectMapper).writeValueAsString(any(Map.class));
        }
    }

    @Test
    @DisplayName("감정 분석 - 감정별 키워드 검증")
    void analyzeSentiment_EmotionalKeywordsValidation() throws Exception {
        // Given
        given(objectMapper.writeValueAsString(any(Map.class))).willAnswer(invocation -> {
            Map<String, Object> resultMap = invocation.getArgument(0);
            String primaryEmotion = (String) resultMap.get("primaryEmotion");
            
            @SuppressWarnings("unchecked")
            java.util.List<String> detectedKeywords = (java.util.List<String>) resultMap.get("detectedKeywords");
            
            // 키워드는 2-4개
            assertThat(detectedKeywords).hasSizeBetween(2, 4);
            
            // 감정에 따른 키워드 적절성 검증
            switch (primaryEmotion) {
                case "NEGATIVE" -> {
                    java.util.List<String> negativeKeywords = java.util.Arrays.asList("우려", "비판", "문제", "실패", "위험");
                    assertThat(detectedKeywords).allMatch(negativeKeywords::contains);
                }
                case "POSITIVE" -> {
                    java.util.List<String> positiveKeywords = java.util.Arrays.asList("성공", "우수", "탁월", "최고", "혁신");
                    assertThat(detectedKeywords).allMatch(positiveKeywords::contains);
                }
                case "NEUTRAL" -> {
                    java.util.List<String> neutralKeywords = java.util.Arrays.asList("사실", "현황", "상황", "보고", "발표");
                    assertThat(detectedKeywords).allMatch(neutralKeywords::contains);
                }
            }
            
            return "{\"detectedKeywords\":" + detectedKeywords + "}";
        });

        try (MockedStatic<Thread> mockedThread = mockStatic(Thread.class)) {
            mockedThread.when(() -> Thread.sleep(anyLong())).thenAnswer(invocation -> null);

            // When
            String result = sentimentAnalysisService.analyzeSentiment(testContent);

            // Then
            assertThat(result).isNotEmpty();
            verify(objectMapper).writeValueAsString(any(Map.class));
        }
    }

    @Test
    @DisplayName("감정 분석 - 분석 텍스트 생성 검증")
    void analyzeSentiment_AnalysisTextValidation() throws Exception {
        // Given
        given(objectMapper.writeValueAsString(any(Map.class))).willAnswer(invocation -> {
            Map<String, Object> resultMap = invocation.getArgument(0);
            String primaryEmotion = (String) resultMap.get("primaryEmotion");
            String analysis = (String) resultMap.get("analysis");
            
            // 분석 텍스트가 감정에 적합한지 검증
            assertThat(analysis).isNotEmpty();
            
            switch (primaryEmotion) {
                case "NEGATIVE" -> assertThat(analysis).contains("부정적");
                case "POSITIVE" -> assertThat(analysis).contains("긍정적");
                case "NEUTRAL" -> assertThat(analysis).contains("중립적");
                case "SLIGHTLY_NEGATIVE" -> assertThat(analysis).contains("약간");
                case "SLIGHTLY_POSITIVE" -> assertThat(analysis).contains("약간");
            }
            
            return "{\"analysis\":\"" + analysis + "\"}";
        });

        try (MockedStatic<Thread> mockedThread = mockStatic(Thread.class)) {
            mockedThread.when(() -> Thread.sleep(anyLong())).thenAnswer(invocation -> null);

            // When
            String result = sentimentAnalysisService.analyzeSentiment(testContent);

            // Then
            assertThat(result).isNotEmpty();
            verify(objectMapper).writeValueAsString(any(Map.class));
        }
    }

    @Test
    @DisplayName("감정 분석 - 매우 긴 콘텐츠")
    void analyzeSentiment_VeryLongContent() throws Exception {
        // Given
        StringBuilder longContentBuilder = new StringBuilder();
        for (int i = 0; i < 1000; i++) {
            longContentBuilder.append("이것은 매우 긴 감정적인 텍스트입니다. ");
        }
        String longContent = longContentBuilder.toString();
        
        String expectedJsonResult = "{\"sentimentScore\":0.15,\"primaryEmotion\":\"SLIGHTLY_POSITIVE\"}";
        given(objectMapper.writeValueAsString(any(Map.class))).willReturn(expectedJsonResult);

        try (MockedStatic<Thread> mockedThread = mockStatic(Thread.class)) {
            mockedThread.when(() -> Thread.sleep(anyLong())).thenAnswer(invocation -> null);

            // When
            String result = sentimentAnalysisService.analyzeSentiment(longContent);

            // Then
            assertThat(result).isEqualTo(expectedJsonResult);
            verify(objectMapper).writeValueAsString(any(Map.class));
        }
    }

    @Test
    @DisplayName("감정 분석 - 특수 문자 포함 콘텐츠")
    void analyzeSentiment_SpecialCharactersContent() throws Exception {
        // Given
        String specialCharContent = "와!!! 정말 좋은 소식이네요~~~ 😀😃😄 #좋은날 @모든분";
        String expectedJsonResult = "{\"sentimentScore\":0.8,\"primaryEmotion\":\"POSITIVE\"}";
        given(objectMapper.writeValueAsString(any(Map.class))).willReturn(expectedJsonResult);

        try (MockedStatic<Thread> mockedThread = mockStatic(Thread.class)) {
            mockedThread.when(() -> Thread.sleep(anyLong())).thenAnswer(invocation -> null);

            // When
            String result = sentimentAnalysisService.analyzeSentiment(specialCharContent);

            // Then
            assertThat(result).isEqualTo(expectedJsonResult);
            verify(objectMapper).writeValueAsString(any(Map.class));
        }
    }

    @Test
    @DisplayName("감정 분석 - 혼합된 감정 콘텐츠")
    void analyzeSentiment_MixedEmotionContent() throws Exception {
        // Given
        String mixedContent = "오늘은 좋은 소식도 있었지만 안타까운 일도 있었습니다. 전반적으로는 그럭저럭입니다.";
        String expectedJsonResult = "{\"sentimentScore\":0.02,\"primaryEmotion\":\"NEUTRAL\"}";
        given(objectMapper.writeValueAsString(any(Map.class))).willReturn(expectedJsonResult);

        try (MockedStatic<Thread> mockedThread = mockStatic(Thread.class)) {
            mockedThread.when(() -> Thread.sleep(anyLong())).thenAnswer(invocation -> null);

            // When
            String result = sentimentAnalysisService.analyzeSentiment(mixedContent);

            // Then
            assertThat(result).isEqualTo(expectedJsonResult);
            verify(objectMapper).writeValueAsString(any(Map.class));
        }
    }

    @Test
    @DisplayName("감정 분석 - 런타임 예외 처리")
    void analyzeSentiment_RuntimeException() throws Exception {
        // Given
        given(objectMapper.writeValueAsString(any(Map.class)))
                .willThrow(new RuntimeException("예상치 못한 오류"));

        try (MockedStatic<Thread> mockedThread = mockStatic(Thread.class)) {
            mockedThread.when(() -> Thread.sleep(anyLong())).thenAnswer(invocation -> null);

            // When & Then
            assertThatThrownBy(() -> sentimentAnalysisService.analyzeSentiment(testContent))
                    .isInstanceOf(CommonException.class)
                    .hasFieldOrPropertyWithValue("responseExceptionEnum", ResponseExceptionEnum.SENTIMENT_ANALYSIS_FAILED);
        }
    }

    @Test
    @DisplayName("감정 분석 - 랜덤 지연 시간 테스트")
    void analyzeSentiment_RandomDelayTest() throws Exception {
        // Given
        String expectedJsonResult = "{\"sentimentScore\":0.3,\"primaryEmotion\":\"SLIGHTLY_POSITIVE\"}";
        given(objectMapper.writeValueAsString(any(Map.class))).willReturn(expectedJsonResult);

        try (MockedStatic<Thread> mockedThread = mockStatic(Thread.class)) {
            // 지연 시간이 800-2300ms 범위인지 검증
            mockedThread.when(() -> Thread.sleep(anyLong())).thenAnswer(invocation -> {
                Long delay = invocation.getArgument(0);
                assertThat(delay).isBetween(800L, 2300L);
                return null;
            });

            // When
            String result = sentimentAnalysisService.analyzeSentiment(testContent);

            // Then
            assertThat(result).isEqualTo(expectedJsonResult);
            mockedThread.verify(() -> Thread.sleep(anyLong()));
        }
    }
}