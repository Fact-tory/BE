package com.commonground.be.domain.analysis.dto.response;

import com.commonground.be.domain.analysis.entity.Analysis;
import com.commonground.be.domain.analysis.enums.AnalysisStatus;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class AnalysisStatusResponse {
    
    private final Long analysisId;
    private final AnalysisStatus status;
    private final Integer progressPercentage;
    private final String errorMessage;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;
    private final Boolean canRestart;

    public AnalysisStatusResponse(Analysis analysis) {
        this.analysisId = analysis.getId();
        this.status = analysis.getStatus();
        this.progressPercentage = analysis.getProgressPercentage();
        this.errorMessage = analysis.getErrorMessage();
        this.createdAt = analysis.getCreatedAt();
        this.updatedAt = analysis.getUpdatedAt();
        this.canRestart = analysis.canRestart();
    }
}