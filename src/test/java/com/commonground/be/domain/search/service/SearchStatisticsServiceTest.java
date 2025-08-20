package com.commonground.be.domain.search.service;

import com.commonground.be.domain.search.dto.response.SearchStatisticsResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.ValueOperations;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SearchStatisticsService 단위 테스트")
class SearchStatisticsServiceTest {

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private HashOperations<String, Object, Object> hashOperations;

    @Mock
    private SetOperations<String, String> setOperations;

    @InjectMocks
    private SearchStatisticsService searchStatisticsService;

    @BeforeEach
    void setUp() {
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(redisTemplate.opsForHash()).willReturn(hashOperations);
        given(redisTemplate.opsForSet()).willReturn(setOperations);
    }

    @Test
    @DisplayName("검색 통계 조회 성공 - Redis에 데이터 있음")
    void getSearchStatistics_WithRedisData_Success() {
        // Given
        String today = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        
        // General stats mocking
        given(valueOperations.get("search_stats:total")).willReturn("1000");
        given(valueOperations.get("search_stats:unique")).willReturn("500");
        given(valueOperations.get("daily_search:" + today)).willReturn("50");
        
        // Category stats mocking
        given(hashOperations.keys("category_stats")).willReturn(Set.of("POLITICS", "ECONOMY"));
        given(hashOperations.get("category_stats", "POLITICS")).willReturn("300");
        given(hashOperations.get("category_stats", "ECONOMY")).willReturn("200");
        
        // Hourly stats mocking
        for (int hour = 0; hour < 24; hour++) {
            String hourKey = "hourly_search:" + String.format("%02d", hour);
            given(valueOperations.get(hourKey)).willReturn(String.valueOf(30 + hour));
        }

        // When
        SearchStatisticsResponse response = searchStatisticsService.getSearchStatistics();

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getGeneral()).isNotNull();
        assertThat(response.getGeneral().getTotalSearches()).isEqualTo(1000L);
        assertThat(response.getGeneral().getUniqueQueries()).isEqualTo(500L);
        assertThat(response.getGeneral().getTodaySearches()).isEqualTo(50L);
        
        assertThat(response.getCategoryStats()).isNotNull();
        assertThat(response.getCategoryStats().get("POLITICS")).isEqualTo(300L);
        assertThat(response.getCategoryStats().get("ECONOMY")).isEqualTo(200L);
        
        assertThat(response.getTimeRangeStats()).isNotNull();
        assertThat(response.getTimeRangeStats()).hasSize(24);
    }

    @Test
    @DisplayName("검색 통계 조회 성공 - Redis에 데이터 없음 (Mock 데이터 반환)")
    void getSearchStatistics_NoRedisData_ReturnsMockData() {
        // Given
        given(valueOperations.get(anyString())).willReturn(null);
        given(hashOperations.keys(anyString())).willReturn(Set.of());

        // When
        SearchStatisticsResponse response = searchStatisticsService.getSearchStatistics();

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getGeneral()).isNotNull();
        assertThat(response.getGeneral().getTotalSearches()).isEqualTo(0L);
        assertThat(response.getGeneral().getUniqueQueries()).isEqualTo(0L);
        
        assertThat(response.getCategoryStats()).isNotNull();
        assertThat(response.getCategoryStats().get("POLITICS")).isEqualTo(450L); // Mock 데이터
        assertThat(response.getCategoryStats().get("ECONOMY")).isEqualTo(380L);
        
        assertThat(response.getTimeRangeStats()).isNotNull();
        assertThat(response.getTimeRangeStats()).hasSize(24);
    }

    @Test
    @DisplayName("검색 통계 조회 - Redis 예외 발생 시 Mock 데이터 반환")
    void getSearchStatistics_RedisException_ReturnsMockData() {
        // Given
        given(valueOperations.get(anyString())).willThrow(new RuntimeException("Redis 연결 오류"));

        // When
        SearchStatisticsResponse response = searchStatisticsService.getSearchStatistics();

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getGeneral()).isNotNull();
        assertThat(response.getGeneral().getTotalSearches()).isEqualTo(1250L); // Mock 데이터
        assertThat(response.getGeneral().getUniqueQueries()).isEqualTo(890L);
        
        assertThat(response.getCategoryStats()).isNotNull();
        assertThat(response.getCategoryStats().get("POLITICS")).isEqualTo(450L);
        
        assertThat(response.getTimeRangeStats()).isNotNull();
        assertThat(response.getTimeRangeStats()).hasSize(24);
    }

    @Test
    @DisplayName("검색 기록 성공")
    void recordSearch_Success() {
        // Given
        String query = "테스트 검색어";
        String category = "POLITICS";
        String today = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        int currentHour = LocalDateTime.now().getHour();
        String hourKey = "hourly_search:" + String.format("%02d", currentHour);
        
        given(setOperations.size("unique_queries")).willReturn(100L);

        // When
        searchStatisticsService.recordSearch(query, category);

        // Then
        verify(valueOperations).increment("search_stats:total", 1);
        verify(valueOperations).increment("daily_search:" + today, 1);
        verify(valueOperations).increment(hourKey, 1);
        verify(hashOperations).increment("category_stats", category, 1);
        verify(setOperations).add("unique_queries", query.toLowerCase().trim());
        verify(valueOperations).set("search_stats:unique", "100");
    }

    @Test
    @DisplayName("검색 기록 - null 검색어")
    void recordSearch_NullQuery_SkipsUniqueQueryUpdate() {
        // Given
        String query = null;
        String category = "POLITICS";

        // When
        searchStatisticsService.recordSearch(query, category);

        // Then
        verify(valueOperations).increment("search_stats:total", 1);
        verify(hashOperations).increment("category_stats", category, 1);
        verify(setOperations, never()).add(eq("unique_queries"), anyString());
    }

    @Test
    @DisplayName("검색 기록 - 빈 검색어")
    void recordSearch_EmptyQuery_SkipsUniqueQueryUpdate() {
        // Given
        String query = "";
        String category = "POLITICS";

        // When
        searchStatisticsService.recordSearch(query, category);

        // Then
        verify(valueOperations).increment("search_stats:total", 1);
        verify(hashOperations).increment("category_stats", category, 1);
        verify(setOperations, never()).add(eq("unique_queries"), anyString());
    }

    @Test
    @DisplayName("검색 기록 - null 카테고리")
    void recordSearch_NullCategory_SkipsCategoryUpdate() {
        // Given
        String query = "테스트 검색어";
        String category = null;

        // When
        searchStatisticsService.recordSearch(query, category);

        // Then
        verify(valueOperations).increment("search_stats:total", 1);
        verify(hashOperations, never()).increment(eq("category_stats"), anyString(), anyLong());
        verify(setOperations).add("unique_queries", query.toLowerCase().trim());
    }

    @Test
    @DisplayName("검색 기록 - 빈 카테고리")
    void recordSearch_EmptyCategory_SkipsCategoryUpdate() {
        // Given
        String query = "테스트 검색어";
        String category = "";

        // When
        searchStatisticsService.recordSearch(query, category);

        // Then
        verify(valueOperations).increment("search_stats:total", 1);
        verify(hashOperations, never()).increment(eq("category_stats"), anyString(), anyLong());
        verify(setOperations).add("unique_queries", query.toLowerCase().trim());
    }

    @Test
    @DisplayName("검색 기록 - 공백 포함 카테고리")
    void recordSearch_WhitespaceCategory_SkipsCategoryUpdate() {
        // Given
        String query = "테스트 검색어";
        String category = "   ";

        // When
        searchStatisticsService.recordSearch(query, category);

        // Then
        verify(valueOperations).increment("search_stats:total", 1);
        verify(hashOperations, never()).increment(eq("category_stats"), anyString(), anyLong());
        verify(setOperations).add("unique_queries", query.toLowerCase().trim());
    }

    @Test
    @DisplayName("검색 기록 - Redis 예외 발생해도 계속 진행")
    void recordSearch_RedisException_DoesNotThrow() {
        // Given
        String query = "테스트 검색어";
        String category = "POLITICS";
        
        doThrow(new RuntimeException("Redis 오류")).when(valueOperations).increment(anyString(), anyLong());

        // When & Then - 예외가 발생하지 않아야 함
        assertThatCode(() -> searchStatisticsService.recordSearch(query, category))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("일반 통계 조회 - 부분 데이터만 있는 경우")
    void getGeneralStats_PartialData() {
        // Given
        given(valueOperations.get("search_stats:total")).willReturn("500");
        given(valueOperations.get("search_stats:unique")).willReturn(null); // 데이터 없음
        given(valueOperations.get(anyString())).willReturn(null); // 나머지 데이터 없음

        // When
        SearchStatisticsResponse response = searchStatisticsService.getSearchStatistics();

        // Then
        assertThat(response.getGeneral().getTotalSearches()).isEqualTo(500L);
        assertThat(response.getGeneral().getUniqueQueries()).isEqualTo(0L); // 기본값
        assertThat(response.getGeneral().getTodaySearches()).isEqualTo(0L); // 기본값
    }

    @Test
    @DisplayName("카테고리 통계 조회 - 일부 카테고리만 있는 경우")
    void getCategoryStats_PartialCategories() {
        // Given
        given(hashOperations.keys("category_stats")).willReturn(Set.of("POLITICS"));
        given(hashOperations.get("category_stats", "POLITICS")).willReturn("300");

        // When
        SearchStatisticsResponse response = searchStatisticsService.getSearchStatistics();

        // Then
        assertThat(response.getCategoryStats()).containsKey("POLITICS");
        assertThat(response.getCategoryStats().get("POLITICS")).isEqualTo(300L);
        assertThat(response.getCategoryStats()).hasSize(1);
    }

    @Test
    @DisplayName("카테고리 통계 조회 - 잘못된 형식의 데이터")
    void getCategoryStats_InvalidData() {
        // Given
        given(hashOperations.keys("category_stats")).willReturn(Set.of("POLITICS"));
        given(hashOperations.get("category_stats", "POLITICS")).willReturn("invalid_number");

        // When & Then - NumberFormatException이 발생할 수 있지만 catch되어 Mock 데이터 반환
        SearchStatisticsResponse response = searchStatisticsService.getSearchStatistics();
        
        assertThat(response.getCategoryStats()).isNotNull();
        assertThat(response.getCategoryStats().get("POLITICS")).isEqualTo(450L); // Mock 데이터로 대체
    }

    @Test
    @DisplayName("시간대별 통계 조회 - 일부 시간대만 데이터 있음")
    void getTimeRangeStats_PartialHours() {
        // Given
        given(valueOperations.get("hourly_search:09")).willReturn("50");
        given(valueOperations.get("hourly_search:10")).willReturn("60");
        // 나머지 시간대는 null 반환

        // When
        SearchStatisticsResponse response = searchStatisticsService.getSearchStatistics();

        // Then
        assertThat(response.getTimeRangeStats()).hasSize(24);
        assertThat(response.getTimeRangeStats().get("09:00")).isEqualTo(50L);
        assertThat(response.getTimeRangeStats().get("10:00")).isEqualTo(60L);
        // null인 시간대는 랜덤값으로 채워짐
        assertThat(response.getTimeRangeStats().get("00:00")).isGreaterThanOrEqualTo(0L);
    }

    @Test
    @DisplayName("주간 검색수 계산 - 데이터 있음")
    void calculateWeekSearches_WithData() {
        // Given
        LocalDateTime now = LocalDateTime.now();
        for (int i = 0; i < 7; i++) {
            String date = now.minusDays(i).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            given(valueOperations.get("daily_search:" + date)).willReturn(String.valueOf(50 + i));
        }

        // When
        SearchStatisticsResponse response = searchStatisticsService.getSearchStatistics();

        // Then
        // calculateWeekSearches는 private 메서드이므로 간접적으로 검증
        assertThat(response.getGeneral().getThisWeekSearches()).isGreaterThan(0L);
    }

    @Test
    @DisplayName("주간 검색수 계산 - 데이터 없음")
    void calculateWeekSearches_NoData() {
        // Given
        given(valueOperations.get(anyString())).willReturn(null);

        // When
        SearchStatisticsResponse response = searchStatisticsService.getSearchStatistics();

        // Then
        assertThat(response.getGeneral().getThisWeekSearches()).isEqualTo(312L); // Mock 값
    }

    @Test
    @DisplayName("Mock 통계 생성 검증")
    void createMockStatistics_VerifyStructure() {
        // Given
        doThrow(new RuntimeException("전체 실패")).when(valueOperations).get(anyString());

        // When
        SearchStatisticsResponse response = searchStatisticsService.getSearchStatistics();

        // Then
        assertThat(response).isNotNull();
        
        // GeneralStats 검증
        assertThat(response.getGeneral()).isNotNull();
        assertThat(response.getGeneral().getTotalSearches()).isEqualTo(1250L);
        assertThat(response.getGeneral().getUniqueQueries()).isEqualTo(890L);
        assertThat(response.getGeneral().getTodaySearches()).isEqualTo(45L);
        assertThat(response.getGeneral().getThisWeekSearches()).isEqualTo(312L);
        assertThat(response.getGeneral().getAverageResultsPerSearch()).isEqualTo(15.7);
        
        // CategoryStats 검증
        assertThat(response.getCategoryStats()).hasSize(5);
        assertThat(response.getCategoryStats()).containsKeys("POLITICS", "ECONOMY", "SOCIETY", "CULTURE", "INTERNATIONAL");
        
        // TimeRangeStats 검증
        assertThat(response.getTimeRangeStats()).hasSize(24);
        for (int hour = 0; hour < 24; hour++) {
            String timeKey = String.format("%02d:00", hour);
            assertThat(response.getTimeRangeStats()).containsKey(timeKey);
            assertThat(response.getTimeRangeStats().get(timeKey)).isGreaterThanOrEqualTo(0L);
        }
    }
}