package com.commonground.be.domain.search.service;

import com.commonground.be.domain.search.dto.response.AutocompleteResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.ValueOperations;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AutocompleteService 단위 테스트")
class AutocompleteServiceTest {

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private ZSetOperations<String, String> zSetOperations;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private AutocompleteService autocompleteService;

    @BeforeEach
    void setUp() {
        given(redisTemplate.opsForZSet()).willReturn(zSetOperations);
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
    }

    @Test
    @DisplayName("자동완성 제안 조회 성공 - Redis에 데이터 있음")
    void getAutocompleteSuggestions_WithRedisData_Success() {
        // Given
        String query = "정치";
        int limit = 5;
        
        Set<String> redisSuggestions = new LinkedHashSet<>();
        redisSuggestions.add("정치");
        redisSuggestions.add("정치인");
        redisSuggestions.add("정책");
        
        given(zSetOperations.rangeByScore(eq("autocomplete:정"), eq(0.0), eq(Double.MAX_VALUE), eq(0L), eq((long) limit)))
                .willReturn(redisSuggestions);
        given(zSetOperations.score("autocomplete:정", "정치")).willReturn(100.0);
        given(zSetOperations.score("autocomplete:정", "정치인")).willReturn(80.0);
        given(zSetOperations.score("autocomplete:정", "정책")).willReturn(60.0);

        // When
        AutocompleteResponse response = autocompleteService.getAutocompleteSuggestions(query, limit);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getQuery()).isEqualTo(query);
        assertThat(response.getSuggestions()).isNotEmpty();
        assertThat(response.getSuggestions().get(0).getText()).isEqualTo("정치");
        assertThat(response.getSuggestions().get(0).getFrequency()).isEqualTo(100);
        verify(zSetOperations).rangeByScore(eq("autocomplete:정"), eq(0.0), eq(Double.MAX_VALUE), eq(0L), eq((long) limit));
    }

    @Test
    @DisplayName("자동완성 제안 조회 성공 - Redis에 데이터 없음 (Mock 데이터 생성)")
    void getAutocompleteSuggestions_NoRedisData_GeneratesMockData() {
        // Given
        String query = "정치";
        int limit = 5;
        
        given(zSetOperations.rangeByScore(eq("autocomplete:정"), eq(0.0), eq(Double.MAX_VALUE), eq(0L), eq((long) limit)))
                .willReturn(Collections.emptySet());

        // When
        AutocompleteResponse response = autocompleteService.getAutocompleteSuggestions(query, limit);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getQuery()).isEqualTo(query);
        assertThat(response.getSuggestions()).isNotEmpty();
        assertThat(response.getSuggestions().get(0).getText()).startsWith("정");
        verify(zSetOperations).add(eq("autocomplete:정"), anyString(), anyDouble());
        verify(redisTemplate).expire(eq("autocomplete:정"), eq(24L), eq(TimeUnit.HOURS));
    }

    @Test
    @DisplayName("자동완성 제안 조회 - 빈 검색어")
    void getAutocompleteSuggestions_EmptyQuery_ReturnsEmptyResponse() {
        // Given
        String query = "";
        int limit = 5;

        // When
        AutocompleteResponse response = autocompleteService.getAutocompleteSuggestions(query, limit);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getQuery()).isEmpty();
        assertThat(response.getSuggestions()).isEmpty();
        verifyNoInteractions(zSetOperations);
    }

    @Test
    @DisplayName("자동완성 제안 조회 - null 검색어")
    void getAutocompleteSuggestions_NullQuery_ReturnsEmptyResponse() {
        // Given
        String query = null;
        int limit = 5;

        // When
        AutocompleteResponse response = autocompleteService.getAutocompleteSuggestions(query, limit);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getQuery()).isNull();
        assertThat(response.getSuggestions()).isEmpty();
        verifyNoInteractions(zSetOperations);
    }

    @Test
    @DisplayName("자동완성 제안 조회 - 공백만 있는 검색어")
    void getAutocompleteSuggestions_WhitespaceQuery_ReturnsEmptyResponse() {
        // Given
        String query = "   ";
        int limit = 5;

        // When
        AutocompleteResponse response = autocompleteService.getAutocompleteSuggestions(query, limit);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getQuery()).isEqualTo("   ");
        assertThat(response.getSuggestions()).isEmpty();
        verifyNoInteractions(zSetOperations);
    }

    @Test
    @DisplayName("자동완성 제안 조회 - Redis 예외 발생 시 Mock 데이터 반환")
    void getAutocompleteSuggestions_RedisException_ReturnsMockData() {
        // Given
        String query = "경제";
        int limit = 5;
        
        given(zSetOperations.rangeByScore(anyString(), anyDouble(), anyDouble(), anyLong(), anyLong()))
                .willThrow(new RuntimeException("Redis 연결 오류"));

        // When
        AutocompleteResponse response = autocompleteService.getAutocompleteSuggestions(query, limit);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getQuery()).isEqualTo(query);
        assertThat(response.getSuggestions()).isNotEmpty();
        assertThat(response.getSuggestions().get(0).getText()).startsWith("경");
    }

    @Test
    @DisplayName("자동완성 제안 조회 - 대소문자 구분 없이 처리")
    void getAutocompleteSuggestions_CaseInsensitive() {
        // Given
        String query = "POLITICS";
        int limit = 5;
        
        given(zSetOperations.rangeByScore(anyString(), anyDouble(), anyDouble(), anyLong(), anyLong()))
                .willReturn(Collections.emptySet());

        // When
        AutocompleteResponse response = autocompleteService.getAutocompleteSuggestions(query, limit);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getQuery()).isEqualTo("politics"); // 소문자로 변환되어 처리됨
        verify(zSetOperations).rangeByScore(eq("autocomplete:p"), anyDouble(), anyDouble(), anyLong(), anyLong());
    }

    @Test
    @DisplayName("자동완성 쿼리 기록 성공")
    void recordQuery_Success() {
        // Given
        String query = "정치";

        // When
        autocompleteService.recordQuery(query);

        // Then
        verify(zSetOperations).incrementScore("autocomplete:정", "정치", 1);
        verify(redisTemplate).expire("autocomplete:정", 24, TimeUnit.HOURS);
    }

    @Test
    @DisplayName("자동완성 쿼리 기록 - 빈 검색어")
    void recordQuery_EmptyQuery_DoesNothing() {
        // Given
        String query = "";

        // When
        autocompleteService.recordQuery(query);

        // Then
        verifyNoInteractions(zSetOperations);
        verifyNoInteractions(redisTemplate);
    }

    @Test
    @DisplayName("자동완성 쿼리 기록 - null 검색어")
    void recordQuery_NullQuery_DoesNothing() {
        // Given
        String query = null;

        // When
        autocompleteService.recordQuery(query);

        // Then
        verifyNoInteractions(zSetOperations);
        verifyNoInteractions(redisTemplate);
    }

    @Test
    @DisplayName("자동완성 쿼리 기록 - 공백 포함 검색어 정규화")
    void recordQuery_WhitespaceQuery_Normalized() {
        // Given
        String query = "  정치 정책  ";

        // When
        autocompleteService.recordQuery(query);

        // Then
        verify(zSetOperations).incrementScore("autocomplete:정", "정치 정책", 1);
        verify(redisTemplate).expire("autocomplete:정", 24, TimeUnit.HOURS);
    }

    @Test
    @DisplayName("자동완성 쿼리 기록 - Redis 예외 발생")
    void recordQuery_RedisException_DoesNotThrow() {
        // Given
        String query = "정치";
        doThrow(new RuntimeException("Redis 오류")).when(zSetOperations).incrementScore(anyString(), anyString(), anyDouble());

        // When & Then - 예외가 발생하지 않아야 함
        assertThatCode(() -> autocompleteService.recordQuery(query))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Mock 데이터 생성 - 다양한 첫 글자에 대한 제안")
    void generateMockSuggestions_VariousFirstCharacters() {
        // Given
        String[] queries = {"정", "경", "코", "부", "사", "문", "교", "환"};
        int limit = 3;

        for (String query : queries) {
            given(zSetOperations.rangeByScore(anyString(), anyDouble(), anyDouble(), anyLong(), anyLong()))
                    .willReturn(Collections.emptySet());

            // When
            AutocompleteResponse response = autocompleteService.getAutocompleteSuggestions(query, limit);

            // Then
            assertThat(response.getSuggestions()).isNotEmpty();
            assertThat(response.getSuggestions().get(0).getText()).startsWith(query);
        }
    }

    @Test
    @DisplayName("Mock 데이터 생성 - 알려지지 않은 첫 글자")
    void generateMockSuggestions_UnknownFirstCharacter() {
        // Given
        String query = "ㅋ"; // 알려지지 않은 첫 글자
        int limit = 3;
        
        given(zSetOperations.rangeByScore(anyString(), anyDouble(), anyDouble(), anyLong(), anyLong()))
                .willReturn(Collections.emptySet());

        // When
        AutocompleteResponse response = autocompleteService.getAutocompleteSuggestions(query, limit);

        // Then
        assertThat(response.getSuggestions()).isNotEmpty();
        assertThat(response.getSuggestions().get(0).getText()).contains(query);
    }

    @Test
    @DisplayName("Redis에서 조회된 데이터 필터링 - 검색어로 시작하는 항목만")
    void getFromRedis_FiltersCorrectlyByPrefix() {
        // Given
        String query = "정치";
        int limit = 5;
        
        Set<String> redisSuggestions = new LinkedHashSet<>();
        redisSuggestions.add("정치");     // 매치
        redisSuggestions.add("정치인");   // 매치
        redisSuggestions.add("경제");     // 매치 안됨
        redisSuggestions.add("정책");     // 매치 안됨 (정치로 시작하지 않음)
        
        given(zSetOperations.rangeByScore(eq("autocomplete:정"), eq(0.0), eq(Double.MAX_VALUE), eq(0L), eq((long) limit)))
                .willReturn(redisSuggestions);
        given(zSetOperations.score("autocomplete:정", "정치")).willReturn(100.0);
        given(zSetOperations.score("autocomplete:정", "정치인")).willReturn(80.0);

        // When
        AutocompleteResponse response = autocompleteService.getAutocompleteSuggestions(query, limit);

        // Then
        assertThat(response.getSuggestions()).hasSize(2); // 정치, 정치인만 포함
        assertThat(response.getSuggestions().get(0).getText()).isEqualTo("정치");
        assertThat(response.getSuggestions().get(1).getText()).isEqualTo("정치인");
    }

    @Test
    @DisplayName("Redis 저장 시 예외 발생해도 계속 진행")
    void saveToRedis_ExceptionDuringAdd_ContinuesProcessing() {
        // Given
        String query = "정치";
        int limit = 5;
        
        given(zSetOperations.rangeByScore(anyString(), anyDouble(), anyDouble(), anyLong(), anyLong()))
                .willReturn(Collections.emptySet());
        doThrow(new RuntimeException("Redis 저장 오류")).when(zSetOperations).add(anyString(), anyString(), anyDouble());

        // When & Then - 예외가 발생하지 않고 Mock 데이터가 반환되어야 함
        AutocompleteResponse response = autocompleteService.getAutocompleteSuggestions(query, limit);
        
        assertThat(response).isNotNull();
        assertThat(response.getSuggestions()).isNotEmpty();
    }
}