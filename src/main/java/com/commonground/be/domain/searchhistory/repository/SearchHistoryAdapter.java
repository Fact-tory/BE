package com.commonground.be.domain.searchhistory.repository;

import com.commonground.be.domain.searchhistory.entity.SearchHistory;
import com.commonground.be.global.application.exception.SearchHistoryExceptions;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class SearchHistoryAdapter implements SearchHistoryRepositoryInterface {
    
    private final SearchHistoryRepository searchHistoryRepository;
    
    @Override
    public void save(SearchHistory searchHistory) {
        try {
            searchHistoryRepository.save(searchHistory);
            log.debug("검색 기록 저장 완료: userId={}, query={}", 
                    searchHistory.getUserId(), searchHistory.getQuery());
        } catch (Exception e) {
            log.error("검색 기록 저장 실패: userId={}, query={}", 
                    searchHistory.getUserId(), searchHistory.getQuery(), e);
            throw SearchHistoryExceptions.searchHistorySaveFailed();
        }
    }
    
    @Override
    public SearchHistory findById(Long id) {
        return searchHistoryRepository.findById(id)
                .orElseThrow(SearchHistoryExceptions::searchHistoryNotFound);
    }
    
    @Override
    public Page<SearchHistory> findByUserIdOrderByCreatedAtDesc(String userId, Pageable pageable) {
        try {
            return searchHistoryRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
        } catch (Exception e) {
            log.error("사용자 검색 히스토리 조회 실패: userId={}", userId, e);
            throw SearchHistoryExceptions.searchHistoryFailed();
        }
    }
    
    @Override
    public long countByUserId(String userId) {
        try {
            return searchHistoryRepository.countByUserId(userId);
        } catch (Exception e) {
            log.error("사용자 검색 수 조회 실패: userId={}", userId, e);
            return 0L;
        }
    }
    
    @Override
    public long countByUserIdAndCreatedAtAfter(String userId, LocalDateTime since) {
        try {
            return searchHistoryRepository.countByUserIdAndCreatedAtAfter(userId, since);
        } catch (Exception e) {
            log.error("기간별 검색 수 조회 실패: userId={}, since={}", userId, since, e);
            return 0L;
        }
    }
    
    @Override
    public List<String> findDistinctQueriesByUserId(String userId, Pageable pageable) {
        try {
            return searchHistoryRepository.findDistinctQueriesByUserId(userId, pageable);
        } catch (Exception e) {
            log.error("최근 검색어 조회 실패: userId={}", userId, e);
            return List.of();
        }
    }
    
    @Override
    public List<SearchHistory> findTop10ByUserIdOrderByCreatedAtDesc(String userId) {
        try {
            return searchHistoryRepository.findTop10ByUserIdOrderByCreatedAtDesc(userId);
        } catch (Exception e) {
            log.error("최근 검색 히스토리 조회 실패: userId={}", userId, e);
            return List.of();
        }
    }
    
    @Override
    public void delete(SearchHistory searchHistory) {
        try {
            searchHistoryRepository.delete(searchHistory);
            log.debug("검색 기록 삭제 완료: id={}, userId={}", 
                    searchHistory.getId(), searchHistory.getUserId());
        } catch (Exception e) {
            log.error("검색 기록 삭제 실패: id={}, userId={}", 
                    searchHistory.getId(), searchHistory.getUserId(), e);
            throw SearchHistoryExceptions.searchHistoryDeleteFailed();
        }
    }
    
    @Override
    public void deleteAll(List<SearchHistory> searchHistories) {
        try {
            searchHistoryRepository.deleteAll(searchHistories);
            log.debug("검색 기록 일괄 삭제 완료: 총 {}건", searchHistories.size());
        } catch (Exception e) {
            log.error("검색 기록 일괄 삭제 실패: 총 {}건", searchHistories.size(), e);
            throw SearchHistoryExceptions.searchHistoryDeleteFailed();
        }
    }
}