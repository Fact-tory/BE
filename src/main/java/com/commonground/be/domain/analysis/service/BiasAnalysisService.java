package com.commonground.be.domain.analysis.service;

import com.commonground.be.global.application.exception.CommonException;
import com.commonground.be.global.application.response.ResponseExceptionEnum;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

@Slf4j
@Service
@RequiredArgsConstructor
public class BiasAnalysisService {

    private final ObjectMapper objectMapper;
    private final Random random = new Random();

    public String analyzeBias(String content) {
        try {
            log.info("편향 분석 수행 중...");
            
            // 시뮬레이션을 위한 지연
            Thread.sleep(1000 + random.nextInt(2000));
            
            // Mock 편향 분석 결과 생성
            Map<String, Object> biasResult = createMockBiasResult(content);
            
            return objectMapper.writeValueAsString(biasResult);
        } catch (Exception e) {
            log.error("편향 분석 중 오류 발생", e);
            throw new CommonException(ResponseExceptionEnum.BIAS_ANALYSIS_FAILED);
        }
    }

    private Map<String, Object> createMockBiasResult(String content) {
        Map<String, Object> result = new HashMap<>();
        
        // 편향 점수 (0: 매우 좌편향, 50: 중립, 100: 매우 우편향)
        int biasScore = 30 + random.nextInt(40); // 30-70 범위
        
        // 편향 유형 결정
        String biasType = determineBiasType(biasScore);
        
        // 신뢰도 점수
        double confidence = 0.7 + (random.nextDouble() * 0.25); // 0.7-0.95 범위
        
        result.put("biasScore", biasScore);
        result.put("biasType", biasType);
        result.put("confidence", Math.round(confidence * 100.0) / 100.0);
        result.put("analysis", generateBiasAnalysis(biasScore, biasType));
        result.put("detectedPatterns", generateDetectedPatterns());
        result.put("recommendations", generateRecommendations(biasType));
        
        return result;
    }

    private String determineBiasType(int biasScore) {
        if (biasScore < 25) return "LEFT_LEANING";
        if (biasScore < 40) return "SLIGHTLY_LEFT";
        if (biasScore < 60) return "NEUTRAL";
        if (biasScore < 75) return "SLIGHTLY_RIGHT";
        return "RIGHT_LEANING";
    }

    private String generateBiasAnalysis(int biasScore, String biasType) {
        return switch (biasType) {
            case "LEFT_LEANING" -> "해당 콘텐츠는 진보적 관점에서 서술되었으며, 정부 정책에 비판적인 시각을 보입니다.";
            case "SLIGHTLY_LEFT" -> "약간의 진보적 성향이 감지되지만, 전반적으로 균형잡힌 서술을 보입니다.";
            case "NEUTRAL" -> "정치적 편향성이 거의 감지되지 않으며, 객관적인 서술을 유지하고 있습니다.";
            case "SLIGHTLY_RIGHT" -> "약간의 보수적 성향이 감지되지만, 전반적으로 균형잡힌 서술을 보입니다.";
            case "RIGHT_LEANING" -> "해당 콘텐츠는 보수적 관점에서 서술되었으며, 현 정부에 우호적인 시각을 보입니다.";
            default -> "편향성 분석을 완료했습니다.";
        };
    }

    private List<String> generateDetectedPatterns() {
        List<String> allPatterns = Arrays.asList(
            "감정적 표현 사용",
            "일방적 주장",
            "반대 의견 배제",
            "선택적 팩트 제시",
            "과장된 표현",
            "확증편향적 서술",
            "균형잡힌 관점 제시",
            "객관적 사실 중심 서술"
        );
        
        // 랜덤하게 2-4개 패턴 선택
        List<String> shuffled = allPatterns.stream().sorted((a, b) -> random.nextInt(3) - 1).toList();
        return shuffled.subList(0, 2 + random.nextInt(3));
    }

    private List<String> generateRecommendations(String biasType) {
        if ("NEUTRAL".equals(biasType)) {
            return Arrays.asList(
                "현재 균형잡힌 시각을 유지하고 있습니다.",
                "계속해서 객관적인 관점을 유지하세요."
            );
        }
        
        return Arrays.asList(
            "다양한 관점의 의견을 수집해보세요.",
            "반대 입장의 근거도 함께 검토해보세요.",
            "감정적 표현보다는 팩트에 기반한 서술을 권장합니다.",
            "전문가들의 다양한 의견을 참고하세요."
        );
    }
}