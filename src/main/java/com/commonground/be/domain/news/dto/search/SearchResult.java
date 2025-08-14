package com.commonground.be.domain.news.dto.search;

import com.commonground.be.domain.news.entity.News;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SearchResult {
    private List<News> news;
    private long totalHits;
    private int page;
    private int size;
    private boolean hasNext;
}