package com.commonground.be.domain.searchhistory.entity;

import com.commonground.be.global.domain.audit.TimeStamp;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "search_history", indexes = {
    @Index(name = "idx_search_history_user_id", columnList = "user_id"),
    @Index(name = "idx_search_history_user_created", columnList = "user_id, created_at"),
    @Index(name = "idx_search_history_query", columnList = "query")
})
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SearchHistory extends TimeStamp {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "user_id", nullable = false)
    private String userId;
    
    @Column(name = "query", nullable = false)
    private String query;
    
    @Column(name = "result_count")
    private Integer resultCount;
    
    @Column(name = "category")
    private String category;
    
    @Column(name = "filters")
    private String filters;
    
    public static SearchHistory create(String userId, String query, Integer resultCount, String category, String filters) {
        return SearchHistory.builder()
                .userId(userId)
                .query(query)
                .resultCount(resultCount)
                .category(category)
                .filters(filters)
                .build();
    }
}