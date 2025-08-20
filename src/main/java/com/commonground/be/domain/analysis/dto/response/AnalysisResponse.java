package com.commonground.be.domain.analysis.dto.response;

import com.commonground.be.domain.analysis.entity.Analysis;
import com.commonground.be.domain.analysis.enums.AnalysisStatus;
import com.commonground.be.domain.analysis.enums.AnalysisType;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class AnalysisResponse {
    
    private final Long analysisId;
    private final AnalysisType type;
    private final AnalysisStatus status;
    private final String title;
    private final Integer progressPercentage;
    private final LocalDateTime createdAt;
    private final String message;

    public AnalysisResponse(Analysis analysis) {
        this.analysisId = analysis.getId();
        this.type = analysis.getAnalysisType();
        this.status = analysis.getStatus();
        this.title = analysis.getTitle();
        this.progressPercentage = analysis.getProgressPercentage();
        this.createdAt = analysis.getCreatedAt();
        this.message = generateMessage(analysis.getStatus());
    }

    private String generateMessage(AnalysisStatus status) {
        return switch (status) {
            case PENDING -> "분석이 대기열에 추가되었습니다.";
            case IN_PROGRESS -> "분석이 진행 중입니다.";
            case COMPLETED -> "분석이 완료되었습니다.";
            case FAILED -> "분석이 실패했습니다.";
            case CANCELLED -> "분석이 취소되었습니다.";
        };
    }
}