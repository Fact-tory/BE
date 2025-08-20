package com.commonground.be.domain.analysis.dto.response;

import com.commonground.be.domain.analysis.entity.Analysis;
import com.commonground.be.domain.analysis.enums.AnalysisStatus;
import com.commonground.be.domain.analysis.enums.AnalysisType;
import lombok.Getter;
import org.springframework.data.domain.Page;

import java.time.LocalDateTime;
import java.util.List;

@Getter
public class MyAnalysesResponse {
    
    private final List<AnalysisItem> analyses;
    private final PageInfo pageInfo;

    public MyAnalysesResponse(Page<Analysis> analysisPage) {
        this.analyses = analysisPage.getContent().stream()
                .map(AnalysisItem::new)
                .toList();
        this.pageInfo = new PageInfo(analysisPage);
    }

    @Getter
    public static class AnalysisItem {
        private final Long analysisId;
        private final AnalysisType type;
        private final AnalysisStatus status;
        private final String title;
        private final Integer progressPercentage;
        private final LocalDateTime createdAt;
        private final LocalDateTime updatedAt;

        public AnalysisItem(Analysis analysis) {
            this.analysisId = analysis.getId();
            this.type = analysis.getAnalysisType();
            this.status = analysis.getStatus();
            this.title = analysis.getTitle();
            this.progressPercentage = analysis.getProgressPercentage();
            this.createdAt = analysis.getCreatedAt();
            this.updatedAt = analysis.getUpdatedAt();
        }
    }

    @Getter
    public static class PageInfo {
        private final int currentPage;
        private final int totalPages;
        private final long totalElements;
        private final int size;
        private final boolean hasNext;
        private final boolean hasPrevious;

        public PageInfo(Page<Analysis> page) {
            this.currentPage = page.getNumber() + 1; // 0-based to 1-based
            this.totalPages = page.getTotalPages();
            this.totalElements = page.getTotalElements();
            this.size = page.getSize();
            this.hasNext = page.hasNext();
            this.hasPrevious = page.hasPrevious();
        }
    }
}