package com.commonground.be.domain.searchhistory.facade;

import com.commonground.be.domain.searchhistory.dto.response.SearchHistoryResponse;
import com.commonground.be.domain.searchhistory.service.SearchHistoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("SearchHistoryFacade 단위 테스트")
class SearchHistoryFacadeTest {

    @Mock
    private SearchHistoryService searchHistoryService;

    @InjectMocks
    private SearchHistoryFacade searchHistoryFacade;

    private UserDetails mockUserDetails;
    private String testUserId;

    @BeforeEach
    void setUp() {
        mockUserDetails = mock(UserDetails.class);
        testUserId = "testUser";
        given(mockUserDetails.getUsername()).willReturn(testUserId);
    }

    @Test
    @DisplayName("검색 기록 저장 파이프라인 성공")
    void recordSearchFlow_Success() {
        // Given
        String query = "테스트 검색어";
        Integer resultCount = 10;
        String category = "POLITICS";
        String filters = "";

        // When
        SearchHistoryFacade.SearchHistoryFlowResult result = 
                searchHistoryFacade.recordSearchFlow(mockUserDetails, query, resultCount, category, filters);

        // Then
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getMessage()).contains("검색 기록이 저장되었습니다");
        verify(searchHistoryService).recordSearch(testUserId, query, resultCount, category, filters);
    }

    @Test
    @DisplayName("검색 히스토리 조회 파이프라인 성공")
    void getSearchHistoryFlow_Success() {
        // Given
        Pageable pageable = PageRequest.of(0, 10);
        SearchHistoryResponse mockResponse = SearchHistoryResponse.builder()
                .history(Arrays.asList())
                .pageInfo(SearchHistoryResponse.PageInfo.builder()
                        .currentPage(1)
                        .totalPages(1)
                        .totalElements(0)
                        .size(10)
                        .hasNext(false)
                        .hasPrevious(false)
                        .build())
                .build();
        
        given(searchHistoryService.getSearchHistory(testUserId, pageable)).willReturn(mockResponse);

        // When
        SearchHistoryFacade.SearchHistoryFlowResult result = 
                searchHistoryFacade.getSearchHistoryFlow(mockUserDetails, pageable);

        // Then
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData()).isEqualTo(mockResponse);
        assertThat(result.getMessage()).contains("검색 히스토리 조회 완료");
    }

    @Test
    @DisplayName("최근 검색어 조회 파이프라인 성공")
    void getRecentQueriesFlow_Success() {
        // Given
        int limit = 5;
        List<String> mockQueries = Arrays.asList("검색어1", "검색어2", "검색어3");
        given(searchHistoryService.getRecentSearchQueries(testUserId, limit)).willReturn(mockQueries);

        // When
        SearchHistoryFacade.SearchHistoryFlowResult result = 
                searchHistoryFacade.getRecentQueriesFlow(mockUserDetails, limit);

        // Then
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData()).isEqualTo(mockQueries);
        assertThat(result.getMessage()).contains("최근 검색어 조회 완료");
    }

    @Test
    @DisplayName("검색 히스토리 삭제 파이프라인 성공")
    void clearSearchHistoryFlow_Success() {
        // When
        SearchHistoryFacade.SearchHistoryFlowResult result = 
                searchHistoryFacade.clearSearchHistoryFlow(mockUserDetails);

        // Then
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getMessage()).contains("검색 히스토리가 삭제되었습니다");
        verify(searchHistoryService).clearSearchHistory(testUserId);
    }

    @Test
    @DisplayName("특정 검색 기록 삭제 파이프라인 성공")
    void deleteSearchHistoryFlow_Success() {
        // Given
        Long historyId = 1L;

        // When
        SearchHistoryFacade.SearchHistoryFlowResult result = 
                searchHistoryFacade.deleteSearchHistoryFlow(mockUserDetails, historyId);

        // Then
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getMessage()).contains("검색 기록이 삭제되었습니다");
        verify(searchHistoryService).deleteSearchHistory(testUserId, historyId);
    }

    @Test
    @DisplayName("인증되지 않은 사용자 요청 시 실패")
    void recordSearchFlow_UnauthenticatedUser_Failure() {
        // When
        SearchHistoryFacade.SearchHistoryFlowResult result = 
                searchHistoryFacade.recordSearchFlow(null, "query", 10, "category", "filters");

        // Then
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).contains("검색 기록 저장 실패");
    }

    @Test
    @DisplayName("잘못된 검색어로 요청 시 실패")
    void recordSearchFlow_InvalidQuery_Failure() {
        // When
        SearchHistoryFacade.SearchHistoryFlowResult result = 
                searchHistoryFacade.recordSearchFlow(mockUserDetails, "", 10, "category", "filters");

        // Then
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).contains("검색 기록 저장 실패");
    }

    @Test
    @DisplayName("잘못된 페이징 파라미터로 요청 시 실패")
    void getSearchHistoryFlow_InvalidPaging_Failure() {
        // Given
        Pageable invalidPageable = PageRequest.of(-1, 10);

        // When
        SearchHistoryFacade.SearchHistoryFlowResult result = 
                searchHistoryFacade.getSearchHistoryFlow(mockUserDetails, invalidPageable);

        // Then
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).contains("검색 히스토리 조회 실패");
    }

    @Test
    @DisplayName("잘못된 제한값으로 최근 검색어 요청 시 실패")
    void getRecentQueriesFlow_InvalidLimit_Failure() {
        // When
        SearchHistoryFacade.SearchHistoryFlowResult result = 
                searchHistoryFacade.getRecentQueriesFlow(mockUserDetails, 0);

        // Then
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).contains("최근 검색어 조회 실패");
    }

    @Test
    @DisplayName("잘못된 히스토리 ID로 삭제 요청 시 실패")
    void deleteSearchHistoryFlow_InvalidHistoryId_Failure() {
        // When
        SearchHistoryFacade.SearchHistoryFlowResult result = 
                searchHistoryFacade.deleteSearchHistoryFlow(mockUserDetails, null);

        // Then
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).contains("검색 기록 삭제 실패");
    }
}