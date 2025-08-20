package com.commonground.be.domain.search.service;

import com.commonground.be.domain.search.dto.response.AutocompleteResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AutocompleteService {

    private final RedisTemplate<String, String> redisTemplate;
    private static final String AUTOCOMPLETE_KEY = "autocomplete:";

    public AutocompleteResponse getAutocompleteSuggestions(String query, int limit) {
        log.info("자동완성 조회 - query: {}, limit: {}", query, limit);

        if (query == null || query.trim().isEmpty()) {
            return new AutocompleteResponse(query, Collections.emptyList());
        }

        query = query.trim().toLowerCase();
        
        try {
            // Redis에서 자동완성 데이터 조회 시도
            List<AutocompleteResponse.SuggestionItem> suggestions = getFromRedis(query, limit);
            
            if (suggestions.isEmpty()) {
                // Redis에 데이터가 없으면 Mock 데이터 생성
                suggestions = generateMockSuggestions(query, limit);
                // Redis에 저장
                saveToRedis(query, suggestions);
            }
            
            return new AutocompleteResponse(query, suggestions);
            
        } catch (Exception e) {
            log.error("자동완성 조회 실패", e);
            return new AutocompleteResponse(query, generateMockSuggestions(query, limit));
        }
    }

    private List<AutocompleteResponse.SuggestionItem> getFromRedis(String query, int limit) {
        try {
            Set<String> suggestions = redisTemplate.opsForZSet()
                    .rangeByScore(AUTOCOMPLETE_KEY + query.charAt(0), 0, Double.MAX_VALUE, 0, limit);
            
            if (suggestions == null) {
                return Collections.emptyList();
            }
            
            return suggestions.stream()
                    .filter(s -> s.toLowerCase().startsWith(query))
                    .map(s -> {
                        Double score = redisTemplate.opsForZSet().score(AUTOCOMPLETE_KEY + query.charAt(0), s);
                        return new AutocompleteResponse.SuggestionItem(
                            s, 
                            "keyword", 
                            score != null ? score.intValue() : 1,
                            Math.min(score != null ? score / 100.0 : 0.5, 1.0)
                        );
                    })
                    .sorted((a, b) -> Double.compare(b.getScore(), a.getScore()))
                    .collect(Collectors.toList());
            
        } catch (Exception e) {
            log.warn("Redis 자동완성 조회 실패", e);
            return Collections.emptyList();
        }
    }

    private void saveToRedis(String query, List<AutocompleteResponse.SuggestionItem> suggestions) {
        try {
            String key = AUTOCOMPLETE_KEY + query.charAt(0);
            
            for (AutocompleteResponse.SuggestionItem suggestion : suggestions) {
                redisTemplate.opsForZSet().add(key, suggestion.getText(), suggestion.getFrequency());
            }
            
            redisTemplate.expire(key, 24, java.util.concurrent.TimeUnit.HOURS);
            
        } catch (Exception e) {
            log.error("Redis 자동완성 저장 실패", e);
        }
    }

    private List<AutocompleteResponse.SuggestionItem> generateMockSuggestions(String query, int limit) {
        Map<String, List<String>> mockData = Map.of(
            "정", Arrays.asList("정치", "정책", "정부", "정치인", "정의"),
            "경", Arrays.asList("경제", "경제정책", "경기", "경영", "경제성장"),
            "코", Arrays.asList("코로나", "코로나19", "코로나백신", "코스피", "코인"),
            "부", Arrays.asList("부동산", "부총리", "부산", "부가세", "부채"),
            "사", Arrays.asList("사회", "사건", "사업", "사람", "사고"),
            "문", Arrays.asList("문화", "문제", "문재인", "문서", "문의"),
            "교", Arrays.asList("교육", "교사", "교통", "교회", "교수"),
            "환", Arrays.asList("환경", "환율", "환자", "환경보호", "환경오염")
        );

        String firstChar = query.substring(0, 1);
        List<String> candidates = mockData.getOrDefault(firstChar, 
            Arrays.asList(query + "관련", query + "정책", query + "뉴스"));

        Random random = new Random();
        
        return candidates.stream()
                .filter(candidate -> candidate.startsWith(query))
                .limit(limit)
                .map(candidate -> new AutocompleteResponse.SuggestionItem(
                    candidate,
                    "keyword",
                    50 + random.nextInt(200), // 50-250 빈도
                    0.6 + (random.nextDouble() * 0.4) // 0.6-1.0 점수
                ))
                .sorted((a, b) -> Double.compare(b.getScore(), a.getScore()))
                .collect(Collectors.toList());
    }

    public void recordQuery(String query) {
        if (query == null || query.trim().isEmpty()) {
            return;
        }

        try {
            query = query.trim().toLowerCase();
            String key = AUTOCOMPLETE_KEY + query.charAt(0);
            
            redisTemplate.opsForZSet().incrementScore(key, query, 1);
            redisTemplate.expire(key, 24, java.util.concurrent.TimeUnit.HOURS);
            
        } catch (Exception e) {
            log.error("자동완성 쿼리 기록 실패: {}", query, e);
        }
    }
}