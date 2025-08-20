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
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("BiasAnalysisService 단위 테스트")
class BiasAnalysisServiceTest {

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private BiasAnalysisService biasAnalysisService;

    private String testContent;

    @BeforeEach
    void setUp() {
        testContent = "정부의 새로운 정책 발표에 대한 뉴스입니다. 이는 국민들에게 긍정적인 영향을 미칠 것으로 예상됩니다.";
    }

    @Test
    @DisplayName("편향 분석 성공")
    void analyzeBias_Success() throws Exception {
        // Given
        String expectedJsonResult = "{\"biasScore\":50,\"biasType\":\"NEUTRAL\",\"confidence\":0.85}";
        given(objectMapper.writeValueAsString(any(Map.class))).willReturn(expectedJsonResult);

        // Mock Thread.sleep to avoid actual delay in tests
        try (MockedStatic<Thread> mockedThread = mockStatic(Thread.class)) {
            mockedThread.when(() -> Thread.sleep(anyLong())).thenAnswer(invocation -> null);

            // When
            String result = biasAnalysisService.analyzeBias(testContent);

            // Then
            assertThat(result).isEqualTo(expectedJsonResult);
            verify(objectMapper).writeValueAsString(any(Map.class));
        }
    }

    @Test
    @DisplayName("편향 분석 - JSON 직렬화 실패")
    void analyzeBias_JsonSerializationFailure() throws Exception {
        // Given
        given(objectMapper.writeValueAsString(any(Map.class)))
                .willThrow(new JsonProcessingException("JSON 직렬화 오류") {});

        // Mock Thread.sleep to avoid actual delay in tests
        try (MockedStatic<Thread> mockedThread = mockStatic(Thread.class)) {
            mockedThread.when(() -> Thread.sleep(anyLong())).thenAnswer(invocation -> null);

            // When & Then
            assertThatThrownBy(() -> biasAnalysisService.analyzeBias(testContent))
                    .isInstanceOf(CommonException.class)
                    .hasFieldOrPropertyWithValue("responseExceptionEnum", ResponseExceptionEnum.BIAS_ANALYSIS_FAILED);
        }
    }

    @Test
    @DisplayName("편향 분석 - Thread 인터럽트 예외")
    void analyzeBias_InterruptedException() throws Exception {
        // Given
        try (MockedStatic<Thread> mockedThread = mockStatic(Thread.class)) {
            mockedThread.when(() -> Thread.sleep(anyLong())).thenThrow(new InterruptedException("Thread interrupted"));

            // When & Then
            assertThatThrownBy(() -> biasAnalysisService.analyzeBias(testContent))
                    .isInstanceOf(CommonException.class)
                    .hasFieldOrPropertyWithValue("responseExceptionEnum", ResponseExceptionEnum.BIAS_ANALYSIS_FAILED);
        }
    }

    @Test
    @DisplayName("편향 분석 - 빈 콘텐츠")
    void analyzeBias_EmptyContent() throws Exception {
        // Given
        String emptyContent = "";
        String expectedJsonResult = "{\"biasScore\":45,\"biasType\":\"NEUTRAL\"}";
        given(objectMapper.writeValueAsString(any(Map.class))).willReturn(expectedJsonResult);

        try (MockedStatic<Thread> mockedThread = mockStatic(Thread.class)) {
            mockedThread.when(() -> Thread.sleep(anyLong())).thenAnswer(invocation -> null);

            // When
            String result = biasAnalysisService.analyzeBias(emptyContent);

            // Then
            assertThat(result).isEqualTo(expectedJsonResult);
            verify(objectMapper).writeValueAsString(any(Map.class));
        }
    }

    @Test
    @DisplayName("편향 분석 - null 콘텐츠")
    void analyzeBias_NullContent() throws Exception {
        // Given
        String expectedJsonResult = "{\"biasScore\":50,\"biasType\":\"NEUTRAL\"}";
        given(objectMapper.writeValueAsString(any(Map.class))).willReturn(expectedJsonResult);

        try (MockedStatic<Thread> mockedThread = mockStatic(Thread.class)) {
            mockedThread.when(() -> Thread.sleep(anyLong())).thenAnswer(invocation -> null);

            // When
            String result = biasAnalysisService.analyzeBias(null);

            // Then
            assertThat(result).isEqualTo(expectedJsonResult);
            verify(objectMapper).writeValueAsString(any(Map.class));
        }
    }

    @Test
    @DisplayName("편향 분석 - 좌편향 콘텐츠")
    void analyzeBias_LeftLeaningContent() throws Exception {
        // Given
        String leftLeaningContent = "현 정부의 정책은 완전히 실패했습니다. 국민들이 심각하게 고통받고 있습니다.";
        String expectedJsonResult = "{\"biasScore\":20,\"biasType\":\"LEFT_LEANING\"}";
        given(objectMapper.writeValueAsString(any(Map.class))).willReturn(expectedJsonResult);

        try (MockedStatic<Thread> mockedThread = mockStatic(Thread.class)) {
            mockedThread.when(() -> Thread.sleep(anyLong())).thenAnswer(invocation -> null);

            // When
            String result = biasAnalysisService.analyzeBias(leftLeaningContent);

            // Then
            assertThat(result).isEqualTo(expectedJsonResult);
            verify(objectMapper).writeValueAsString(any(Map.class));
        }
    }

    @Test
    @DisplayName("편향 분석 - 우편향 콘텐츠")
    void analyzeBias_RightLeaningContent() throws Exception {
        // Given
        String rightLeaningContent = "정부의 훌륭한 정책 덕분에 모든 것이 완벽합니다. 이보다 더 좋을 수 없습니다.";
        String expectedJsonResult = "{\"biasScore\":80,\"biasType\":\"RIGHT_LEANING\"}";
        given(objectMapper.writeValueAsString(any(Map.class))).willReturn(expectedJsonResult);

        try (MockedStatic<Thread> mockedThread = mockStatic(Thread.class)) {
            mockedThread.when(() -> Thread.sleep(anyLong())).thenAnswer(invocation -> null);

            // When
            String result = biasAnalysisService.analyzeBias(rightLeaningContent);

            // Then
            assertThat(result).isEqualTo(expectedJsonResult);
            verify(objectMapper).writeValueAsString(any(Map.class));
        }
    }

    @Test
    @DisplayName("편향 분석 - 매우 긴 콘텐츠")
    void analyzeBias_VeryLongContent() throws Exception {
        // Given
        StringBuilder longContentBuilder = new StringBuilder();
        for (int i = 0; i < 1000; i++) {
            longContentBuilder.append("이것은 매우 긴 텍스트입니다. ");
        }
        String longContent = longContentBuilder.toString();
        
        String expectedJsonResult = "{\"biasScore\":55,\"biasType\":\"NEUTRAL\"}";
        given(objectMapper.writeValueAsString(any(Map.class))).willReturn(expectedJsonResult);

        try (MockedStatic<Thread> mockedThread = mockStatic(Thread.class)) {
            mockedThread.when(() -> Thread.sleep(anyLong())).thenAnswer(invocation -> null);

            // When
            String result = biasAnalysisService.analyzeBias(longContent);

            // Then
            assertThat(result).isEqualTo(expectedJsonResult);
            verify(objectMapper).writeValueAsString(any(Map.class));
        }
    }

    @Test
    @DisplayName("편향 분석 - 특수 문자 포함 콘텐츠")
    void analyzeBias_SpecialCharactersContent() throws Exception {
        // Given
        String specialCharContent = "정부 정책! @#$%^&*() 국민들의 반응은??? 😀😃😄";
        String expectedJsonResult = "{\"biasScore\":48,\"biasType\":\"NEUTRAL\"}";
        given(objectMapper.writeValueAsString(any(Map.class))).willReturn(expectedJsonResult);

        try (MockedStatic<Thread> mockedThread = mockStatic(Thread.class)) {
            mockedThread.when(() -> Thread.sleep(anyLong())).thenAnswer(invocation -> null);

            // When
            String result = biasAnalysisService.analyzeBias(specialCharContent);

            // Then
            assertThat(result).isEqualTo(expectedJsonResult);
            verify(objectMapper).writeValueAsString(any(Map.class));
        }
    }

    @Test
    @DisplayName("편향 분석 결과 구조 검증")
    void analyzeBias_ResultStructureValidation() throws Exception {
        // Given
        given(objectMapper.writeValueAsString(any(Map.class))).willAnswer(invocation -> {
            Map<String, Object> resultMap = invocation.getArgument(0);
            
            // 결과 구조 검증
            assertThat(resultMap).containsKeys(
                "biasScore", "biasType", "confidence", "analysis", "detectedPatterns", "recommendations"
            );
            
            // biasScore는 0-100 범위
            Integer biasScore = (Integer) resultMap.get("biasScore");
            assertThat(biasScore).isBetween(0, 100);
            
            // confidence는 0.0-1.0 범위
            Double confidence = (Double) resultMap.get("confidence");
            assertThat(confidence).isBetween(0.0, 1.0);
            
            // biasType은 유효한 값
            String biasType = (String) resultMap.get("biasType");
            assertThat(biasType).isIn("LEFT_LEANING", "SLIGHTLY_LEFT", "NEUTRAL", "SLIGHTLY_RIGHT", "RIGHT_LEANING");
            
            return "{\"biasScore\":" + biasScore + ",\"biasType\":\"" + biasType + "\"}";
        });

        try (MockedStatic<Thread> mockedThread = mockStatic(Thread.class)) {
            mockedThread.when(() -> Thread.sleep(anyLong())).thenAnswer(invocation -> null);

            // When
            String result = biasAnalysisService.analyzeBias(testContent);

            // Then
            assertThat(result).isNotEmpty();
            verify(objectMapper).writeValueAsString(any(Map.class));
        }
    }

    @Test
    @DisplayName("편향 분석 - 다양한 편향 타입 시뮬레이션")
    void analyzeBias_VariousBiasTypes() throws Exception {
        // Test each bias type range
        try (MockedStatic<Thread> mockedThread = mockStatic(Thread.class)) {
            mockedThread.when(() -> Thread.sleep(anyLong())).thenAnswer(invocation -> null);

            // Test LEFT_LEANING (score < 25)
            given(objectMapper.writeValueAsString(any(Map.class))).willAnswer(invocation -> {
                Map<String, Object> resultMap = invocation.getArgument(0);
                Integer biasScore = (Integer) resultMap.get("biasScore");
                String biasType = (String) resultMap.get("biasType");
                
                if (biasScore < 25) {
                    assertThat(biasType).isEqualTo("LEFT_LEANING");
                } else if (biasScore < 40) {
                    assertThat(biasType).isEqualTo("SLIGHTLY_LEFT");
                } else if (biasScore < 60) {
                    assertThat(biasType).isEqualTo("NEUTRAL");
                } else if (biasScore < 75) {
                    assertThat(biasType).isEqualTo("SLIGHTLY_RIGHT");
                } else {
                    assertThat(biasType).isEqualTo("RIGHT_LEANING");
                }
                
                return "{\"biasScore\":" + biasScore + ",\"biasType\":\"" + biasType + "\"}";
            });

            // Test multiple times to hit different score ranges
            for (int i = 0; i < 10; i++) {
                String result = biasAnalysisService.analyzeBias(testContent + " " + i);
                assertThat(result).isNotEmpty();
            }

            verify(objectMapper, times(10)).writeValueAsString(any(Map.class));
        }
    }

    @Test
    @DisplayName("편향 분석 - 랜덤 시드 테스트")
    void analyzeBias_RandomSeedTest() throws Exception {
        // Given
        String expectedJsonResult1 = "{\"biasScore\":45,\"biasType\":\"NEUTRAL\"}";
        String expectedJsonResult2 = "{\"biasScore\":55,\"biasType\":\"NEUTRAL\"}";
        
        given(objectMapper.writeValueAsString(any(Map.class)))
                .willReturn(expectedJsonResult1, expectedJsonResult2);

        try (MockedStatic<Thread> mockedThread = mockStatic(Thread.class)) {
            mockedThread.when(() -> Thread.sleep(anyLong())).thenAnswer(invocation -> null);

            // When - 같은 콘텐츠로 두 번 분석
            String result1 = biasAnalysisService.analyzeBias(testContent);
            String result2 = biasAnalysisService.analyzeBias(testContent);

            // Then - 결과가 다를 수 있음 (랜덤 요소로 인해)
            assertThat(result1).isEqualTo(expectedJsonResult1);
            assertThat(result2).isEqualTo(expectedJsonResult2);
            verify(objectMapper, times(2)).writeValueAsString(any(Map.class));
        }
    }

    @Test
    @DisplayName("편향 분석 - 런타임 예외 처리")
    void analyzeBias_RuntimeException() throws Exception {
        // Given
        given(objectMapper.writeValueAsString(any(Map.class)))
                .willThrow(new RuntimeException("예상치 못한 오류"));

        try (MockedStatic<Thread> mockedThread = mockStatic(Thread.class)) {
            mockedThread.when(() -> Thread.sleep(anyLong())).thenAnswer(invocation -> null);

            // When & Then
            assertThatThrownBy(() -> biasAnalysisService.analyzeBias(testContent))
                    .isInstanceOf(CommonException.class)
                    .hasFieldOrPropertyWithValue("responseExceptionEnum", ResponseExceptionEnum.BIAS_ANALYSIS_FAILED);
        }
    }

    @Test
    @DisplayName("편향 분석 - 검출 패턴 검증")
    void analyzeBias_DetectedPatternsValidation() throws Exception {
        // Given
        given(objectMapper.writeValueAsString(any(Map.class))).willAnswer(invocation -> {
            Map<String, Object> resultMap = invocation.getArgument(0);
            
            // detectedPatterns는 리스트이고 2-4개 요소를 가져야 함
            @SuppressWarnings("unchecked")
            java.util.List<String> patterns = (java.util.List<String>) resultMap.get("detectedPatterns");
            assertThat(patterns).hasSizeBetween(2, 4);
            
            // 각 패턴은 유효한 값이어야 함
            java.util.List<String> validPatterns = java.util.Arrays.asList(
                "감정적 표현 사용", "일방적 주장", "반대 의견 배제", "선택적 팩트 제시",
                "과장된 표현", "확증편향적 서술", "균형잡힌 관점 제시", "객관적 사실 중심 서술"
            );
            
            for (String pattern : patterns) {
                assertThat(validPatterns).contains(pattern);
            }
            
            return "{\"detectedPatterns\":" + patterns + "}";
        });

        try (MockedStatic<Thread> mockedThread = mockStatic(Thread.class)) {
            mockedThread.when(() -> Thread.sleep(anyLong())).thenAnswer(invocation -> null);

            // When
            String result = biasAnalysisService.analyzeBias(testContent);

            // Then
            assertThat(result).isNotEmpty();
            verify(objectMapper).writeValueAsString(any(Map.class));
        }
    }

    @Test
    @DisplayName("편향 분석 - 추천사항 검증")
    void analyzeBias_RecommendationsValidation() throws Exception {
        // Given
        given(objectMapper.writeValueAsString(any(Map.class))).willAnswer(invocation -> {
            Map<String, Object> resultMap = invocation.getArgument(0);
            String biasType = (String) resultMap.get("biasType");
            
            @SuppressWarnings("unchecked")
            java.util.List<String> recommendations = (java.util.List<String>) resultMap.get("recommendations");
            
            if ("NEUTRAL".equals(biasType)) {
                // NEUTRAL인 경우 다른 추천사항
                assertThat(recommendations).contains("현재 균형잡힌 시각을 유지하고 있습니다.");
            } else {
                // 편향된 경우 개선 추천사항
                assertThat(recommendations).anyMatch(rec -> rec.contains("다양한 관점") || rec.contains("반대 입장"));
            }
            
            return "{\"recommendations\":" + recommendations + "}";
        });

        try (MockedStatic<Thread> mockedThread = mockStatic(Thread.class)) {
            mockedThread.when(() -> Thread.sleep(anyLong())).thenAnswer(invocation -> null);

            // When
            String result = biasAnalysisService.analyzeBias(testContent);

            // Then
            assertThat(result).isNotEmpty();
            verify(objectMapper).writeValueAsString(any(Map.class));
        }
    }
}