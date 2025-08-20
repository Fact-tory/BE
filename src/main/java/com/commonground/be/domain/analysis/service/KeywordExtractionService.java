package com.commonground.be.domain.analysis.service;

import com.commonground.be.global.application.exception.CommonException;
import com.commonground.be.global.application.response.ResponseExceptionEnum;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class KeywordExtractionService {

    private final ObjectMapper objectMapper;
    private final Random random = new Random();

    public String extractKeywords(String content) {
        try {
            log.info("키워드 추출 수행 중...");
            
            // 시뮬레이션을 위한 지연
            Thread.sleep(600 + random.nextInt(1000));
            
            // Mock 키워드 추출 결과 생성
            Map<String, Object> keywordResult = createMockKeywordResult(content);
            
            return objectMapper.writeValueAsString(keywordResult);
        } catch (Exception e) {
            log.error("키워드 추출 중 오류 발생", e);
            throw new CommonException(ResponseExceptionEnum.KEYWORD_EXTRACTION_FAILED);
        }
    }

    private Map<String, Object> createMockKeywordResult(String content) {
        Map<String, Object> result = new HashMap<>();
        
        // 주요 키워드 생성
        List<Map<String, Object>> primaryKeywords = generatePrimaryKeywords();
        
        // 관련 키워드 생성
        List<Map<String, Object>> relatedKeywords = generateRelatedKeywords();
        
        // 엔티티 추출
        Map<String, List<String>> entities = extractEntities();
        
        // 토픽 분류
        List<String> topics = generateTopics();
        
        result.put("primaryKeywords", primaryKeywords);
        result.put("relatedKeywords", relatedKeywords);
        result.put("entities", entities);
        result.put("topics", topics);
        result.put("summary", generateSummary(primaryKeywords.size(), topics.size()));
        result.put("totalKeywords", primaryKeywords.size() + relatedKeywords.size());
        
        return result;
    }

    private List<Map<String, Object>> generatePrimaryKeywords() {
        String[] keywords = {
            "정부", "정책", "경제", "사회", "국민", "발표", "시행", "계획", 
            "지원", "개선", "강화", "확대", "도입", "추진", "발전", "성장"
        };
        
        List<Map<String, Object>> primaryKeywords = new ArrayList<>();
        Set<String> usedKeywords = new HashSet<>();
        
        // 5-8개의 주요 키워드 생성
        int keywordCount = 5 + random.nextInt(4);
        
        for (int i = 0; i < keywordCount; i++) {
            String keyword;
            do {
                keyword = keywords[random.nextInt(keywords.length)];
            } while (usedKeywords.contains(keyword));
            
            usedKeywords.add(keyword);
            
            Map<String, Object> keywordInfo = new HashMap<>();
            keywordInfo.put("keyword", keyword);
            keywordInfo.put("frequency", 3 + random.nextInt(8)); // 3-10 빈도
            keywordInfo.put("relevance", Math.round((0.7 + random.nextDouble() * 0.3) * 100.0) / 100.0); // 0.7-1.0
            keywordInfo.put("category", determineKeywordCategory(keyword));
            
            primaryKeywords.add(keywordInfo);
        }
        
        // 빈도순으로 정렬
        return primaryKeywords.stream()
                .sorted((a, b) -> Integer.compare((Integer) b.get("frequency"), (Integer) a.get("frequency")))
                .collect(Collectors.toList());
    }

    private List<Map<String, Object>> generateRelatedKeywords() {
        String[] relatedWords = {
            "시민", "기업", "산업", "교육", "문화", "환경", "기술", "혁신",
            "일자리", "복지", "보건", "안전", "국제", "협력", "투자", "개발"
        };
        
        List<Map<String, Object>> relatedKeywords = new ArrayList<>();
        
        // 3-6개의 관련 키워드 생성
        int relatedCount = 3 + random.nextInt(4);
        
        for (int i = 0; i < relatedCount; i++) {
            String keyword = relatedWords[random.nextInt(relatedWords.length)];
            
            Map<String, Object> keywordInfo = new HashMap<>();
            keywordInfo.put("keyword", keyword);
            keywordInfo.put("frequency", 1 + random.nextInt(4)); // 1-4 빈도
            keywordInfo.put("relevance", Math.round((0.5 + random.nextDouble() * 0.3) * 100.0) / 100.0); // 0.5-0.8
            
            relatedKeywords.add(keywordInfo);
        }
        
        return relatedKeywords;
    }

    private String determineKeywordCategory(String keyword) {
        Map<String, String> categoryMap = Map.of(
            "정부", "정치",
            "정책", "정치",
            "경제", "경제",
            "사회", "사회",
            "국민", "사회",
            "발표", "미디어",
            "지원", "정책",
            "개선", "정책"
        );
        
        return categoryMap.getOrDefault(keyword, "일반");
    }

    private Map<String, List<String>> extractEntities() {
        Map<String, List<String>> entities = new HashMap<>();
        
        // 인물
        entities.put("PERSON", Arrays.asList("김영수", "박민정", "이도현"));
        
        // 조직
        entities.put("ORGANIZATION", Arrays.asList("정부", "국회", "기재부", "교육부"));
        
        // 지역
        entities.put("LOCATION", Arrays.asList("서울", "부산", "대구", "한국"));
        
        // 날짜
        entities.put("DATE", Arrays.asList("2024년", "올해", "내년", "12월"));
        
        return entities;
    }

    private List<String> generateTopics() {
        String[] allTopics = {
            "정치/정책", "경제/재정", "사회/복지", "교육/문화", 
            "환경/에너지", "기술/혁신", "보건/의료", "국제/외교"
        };
        
        // 2-4개 토픽 선택
        List<String> topics = new ArrayList<>();
        Set<String> usedTopics = new HashSet<>();
        
        int topicCount = 2 + random.nextInt(3);
        
        for (int i = 0; i < topicCount; i++) {
            String topic;
            do {
                topic = allTopics[random.nextInt(allTopics.length)];
            } while (usedTopics.contains(topic));
            
            usedTopics.add(topic);
            topics.add(topic);
        }
        
        return topics;
    }

    private String generateSummary(int primaryCount, int topicCount) {
        return String.format("총 %d개의 주요 키워드와 %d개의 토픽이 식별되었습니다. " +
                "정치, 경제, 사회 분야의 키워드가 주로 추출되었습니다.", 
                primaryCount, topicCount);
    }
}