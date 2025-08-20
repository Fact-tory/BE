package com.commonground.be.domain.dashboard.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class PopularSearchService {

    private final RedisTemplate<String, String> redisTemplate;
    private static final String POPULAR_SEARCH_KEY = "popular_searches";
    private static final String SEARCH_COUNT_KEY = "search_count:";

    public List<String> getPopularSearches(int limit) {
        log.info("인기 검색어 조회 - limit: {}", limit);

        try {
            // Redis에서 인기 검색어 조회 시도
            Set<String> popularSearches = redisTemplate.opsForZSet()
                    .reverseRange(POPULAR_SEARCH_KEY, 0, limit - 1);
            
            if (popularSearches != null && !popularSearches.isEmpty()) {
                return new ArrayList<>(popularSearches);
            }
        } catch (Exception e) {
            log.warn("Redis에서 인기 검색어 조회 실패, Mock 데이터 사용", e);
        }

        // Redis 실패시 Mock 데이터 반환
        return generateMockPopularSearches(limit);
    }

    public void recordSearch(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return;
        }

        try {
            keyword = keyword.trim().toLowerCase();
            
            // 검색 횟수 증가
            String countKey = SEARCH_COUNT_KEY + keyword;
            redisTemplate.opsForValue().increment(countKey, 1);
            redisTemplate.expire(countKey, 7, TimeUnit.DAYS); // 7일 보관
            
            // 인기 검색어 순위에 추가/업데이트
            Double currentScore = redisTemplate.opsForZSet().score(POPULAR_SEARCH_KEY, keyword);
            double newScore = (currentScore != null ? currentScore : 0) + 1;
            
            redisTemplate.opsForZSet().add(POPULAR_SEARCH_KEY, keyword, newScore);
            redisTemplate.expire(POPULAR_SEARCH_KEY, 7, TimeUnit.DAYS); // 7일 보관
            
            log.debug("검색어 기록됨: {} (점수: {})", keyword, newScore);
            
        } catch (Exception e) {
            log.error("검색어 기록 실패: {}", keyword, e);
        }
    }

    public Map<String, Long> getSearchStats() {
        Map<String, Long> stats = new HashMap<>();
        
        try {
            Long totalSearches = redisTemplate.opsForZSet().count(POPULAR_SEARCH_KEY, 0, Double.MAX_VALUE);
            stats.put("totalSearches", totalSearches != null ? totalSearches : 0L);
            
            Long uniqueKeywords = redisTemplate.opsForZSet().zCard(POPULAR_SEARCH_KEY);
            stats.put("uniqueKeywords", uniqueKeywords != null ? uniqueKeywords : 0L);
            
        } catch (Exception e) {
            log.error("검색 통계 조회 실패", e);
            stats.put("totalSearches", 0L);
            stats.put("uniqueKeywords", 0L);
        }
        
        return stats;
    }

    private List<String> generateMockPopularSearches(int limit) {
        List<String> mockSearches = Arrays.asList(
            "정치", "경제", "코로나", "주식", "부동산",
            "날씨", "스포츠", "연예", "IT", "건강",
            "교육", "여행", "문화", "환경", "국제"
        );
        
        Collections.shuffle(mockSearches);
        return mockSearches.subList(0, Math.min(limit, mockSearches.size()));
    }
}