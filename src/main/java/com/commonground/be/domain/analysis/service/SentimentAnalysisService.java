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
public class SentimentAnalysisService {

    private final ObjectMapper objectMapper;
    private final Random random = new Random();

    public String analyzeSentiment(String content) {
        try {
            log.info("감정 분석 수행 중...");
            
            // 시뮬레이션을 위한 지연
            Thread.sleep(800 + random.nextInt(1500));
            
            // Mock 감정 분석 결과 생성
            Map<String, Object> sentimentResult = createMockSentimentResult(content);
            
            return objectMapper.writeValueAsString(sentimentResult);
        } catch (Exception e) {
            log.error("감정 분석 중 오류 발생", e);
            throw new CommonException(ResponseExceptionEnum.SENTIMENT_ANALYSIS_FAILED);
        }
    }

    private Map<String, Object> createMockSentimentResult(String content) {
        Map<String, Object> result = new HashMap<>();
        
        // 감정 점수 생성 (-1.0: 매우 부정, 0: 중립, 1.0: 매우 긍정)
        double sentimentScore = -0.5 + random.nextDouble(); // -0.5 ~ 0.5 범위
        
        // 주요 감정 결정
        String primaryEmotion = determinePrimaryEmotion(sentimentScore);
        
        // 신뢰도 점수
        double confidence = 0.75 + (random.nextDouble() * 0.2); // 0.75-0.95 범위
        
        result.put("sentimentScore", Math.round(sentimentScore * 100.0) / 100.0);
        result.put("primaryEmotion", primaryEmotion);
        result.put("confidence", Math.round(confidence * 100.0) / 100.0);
        result.put("emotionBreakdown", generateEmotionBreakdown());
        result.put("analysis", generateSentimentAnalysis(primaryEmotion, sentimentScore));
        result.put("detectedKeywords", generateEmotionalKeywords(primaryEmotion));
        
        return result;
    }

    private String determinePrimaryEmotion(double sentimentScore) {
        if (sentimentScore < -0.3) return "NEGATIVE";
        if (sentimentScore < -0.1) return "SLIGHTLY_NEGATIVE";
        if (sentimentScore < 0.1) return "NEUTRAL";
        if (sentimentScore < 0.3) return "SLIGHTLY_POSITIVE";
        return "POSITIVE";
    }

    private Map<String, Double> generateEmotionBreakdown() {
        Map<String, Double> emotions = new HashMap<>();
        
        // 기본 감정들의 비율 생성 (합이 100%가 되도록)
        double joy = random.nextDouble() * 30;
        double sadness = random.nextDouble() * 25;
        double anger = random.nextDouble() * 20;
        double fear = random.nextDouble() * 15;
        double surprise = random.nextDouble() * 10;
        
        double total = joy + sadness + anger + fear + surprise;
        
        emotions.put("joy", Math.round((joy / total * 100) * 100.0) / 100.0);
        emotions.put("sadness", Math.round((sadness / total * 100) * 100.0) / 100.0);
        emotions.put("anger", Math.round((anger / total * 100) * 100.0) / 100.0);
        emotions.put("fear", Math.round((fear / total * 100) * 100.0) / 100.0);
        emotions.put("surprise", Math.round((surprise / total * 100) * 100.0) / 100.0);
        
        return emotions;
    }

    private String generateSentimentAnalysis(String primaryEmotion, double sentimentScore) {
        return switch (primaryEmotion) {
            case "NEGATIVE" -> "전반적으로 부정적인 감정이 강하게 드러납니다. 비판적이거나 우려스러운 톤이 감지됩니다.";
            case "SLIGHTLY_NEGATIVE" -> "약간의 부정적 감정이 감지되지만, 심각한 수준은 아닙니다.";
            case "NEUTRAL" -> "감정적으로 중립적인 톤을 유지하고 있으며, 객관적인 서술이 주를 이룹니다.";
            case "SLIGHTLY_POSITIVE" -> "약간의 긍정적 감정이 느껴지며, 희망적인 톤이 엿보입니다.";
            case "POSITIVE" -> "전반적으로 긍정적이고 희망적인 감정이 강하게 표현되고 있습니다.";
            default -> "감정 분석을 완료했습니다.";
        };
    }

    private List<String> generateEmotionalKeywords(String primaryEmotion) {
        Map<String, List<String>> emotionalKeywords = Map.of(
            "NEGATIVE", Arrays.asList("우려", "비판", "문제", "실패", "위험"),
            "SLIGHTLY_NEGATIVE", Arrays.asList("아쉬움", "염려", "부족", "개선"),
            "NEUTRAL", Arrays.asList("사실", "현황", "상황", "보고", "발표"),
            "SLIGHTLY_POSITIVE", Arrays.asList("기대", "개선", "발전", "성과"),
            "POSITIVE", Arrays.asList("성공", "우수", "탁월", "최고", "혁신")
        );
        
        List<String> keywords = emotionalKeywords.getOrDefault(primaryEmotion, 
            Arrays.asList("분석", "내용", "텍스트"));
        
        // 랜덤하게 2-4개 키워드 선택
        List<String> shuffled = keywords.stream().sorted((a, b) -> random.nextInt(3) - 1).toList();
        return shuffled.subList(0, Math.min(2 + random.nextInt(3), shuffled.size()));
    }
}