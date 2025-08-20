package com.commonground.be.domain.analysis.entity;

import com.commonground.be.domain.analysis.enums.AnalysisStatus;
import com.commonground.be.domain.analysis.enums.AnalysisType;
import com.commonground.be.global.domain.audit.SoftDeleteTimeStamp;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
    name = "analysis",
    indexes = {
        @Index(name = "idx_user_id", columnList = "userId"),
        @Index(name = "idx_status", columnList = "status"),
        @Index(name = "idx_analysis_type", columnList = "analysisType"),
        @Index(name = "idx_created_at", columnList = "createdAt")
    }
)
public class Analysis extends SoftDeleteTimeStamp {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String userId;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private AnalysisType analysisType;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private AnalysisStatus status;

    // 분석 대상
    @Column(columnDefinition = "TEXT")
    private String targetUrl;

    @Column(columnDefinition = "TEXT")
    private String targetText;

    @Column
    private String targetNewsId;

    @Column
    private String title;

    // 분석 옵션
    @Column(nullable = false)
    private Boolean includeBiasAnalysis = true;

    @Column(nullable = false)
    private Boolean includeSentimentAnalysis = true;

    @Column(nullable = false)
    private Boolean includeKeywordExtraction = true;

    @Column(nullable = false)
    private Boolean includeFactCheck = false;

    // 분석 결과
    @Column(columnDefinition = "JSON")
    private String biasAnalysisResult;

    @Column(columnDefinition = "JSON")
    private String sentimentAnalysisResult;

    @Column(columnDefinition = "JSON")
    private String keywordExtractionResult;

    @Column(columnDefinition = "JSON")
    private String factCheckResult;

    // 메타데이터
    @Column
    private String errorMessage;

    @Column
    private Integer progressPercentage = 0;

    @Builder
    public Analysis(String userId, AnalysisType analysisType, String targetUrl, 
                   String targetText, String targetNewsId, String title,
                   Boolean includeBiasAnalysis, Boolean includeSentimentAnalysis,
                   Boolean includeKeywordExtraction, Boolean includeFactCheck) {
        this.userId = userId;
        this.analysisType = analysisType;
        this.status = AnalysisStatus.PENDING;
        this.targetUrl = targetUrl;
        this.targetText = targetText;
        this.targetNewsId = targetNewsId;
        this.title = title;
        this.includeBiasAnalysis = includeBiasAnalysis != null ? includeBiasAnalysis : true;
        this.includeSentimentAnalysis = includeSentimentAnalysis != null ? includeSentimentAnalysis : true;
        this.includeKeywordExtraction = includeKeywordExtraction != null ? includeKeywordExtraction : true;
        this.includeFactCheck = includeFactCheck != null ? includeFactCheck : false;
    }

    // 상태 변경 메서드
    public void updateStatus(AnalysisStatus status) {
        this.status = status;
    }

    public void updateProgress(Integer progressPercentage) {
        this.progressPercentage = progressPercentage;
    }

    public void setError(String errorMessage) {
        this.status = AnalysisStatus.FAILED;
        this.errorMessage = errorMessage;
    }

    public void setBiasAnalysisResult(String result) {
        this.biasAnalysisResult = result;
    }

    public void setSentimentAnalysisResult(String result) {
        this.sentimentAnalysisResult = result;
    }

    public void setKeywordExtractionResult(String result) {
        this.keywordExtractionResult = result;
    }

    public void setFactCheckResult(String result) {
        this.factCheckResult = result;
    }

    public void complete() {
        this.status = AnalysisStatus.COMPLETED;
        this.progressPercentage = 100;
    }

    public void cancel() {
        this.status = AnalysisStatus.CANCELLED;
    }

    public void restart() {
        this.status = AnalysisStatus.PENDING;
        this.progressPercentage = 0;
        this.errorMessage = null;
        this.biasAnalysisResult = null;
        this.sentimentAnalysisResult = null;
        this.keywordExtractionResult = null;
        this.factCheckResult = null;
    }

    public boolean isInProgress() {
        return status == AnalysisStatus.IN_PROGRESS;
    }

    public boolean isCompleted() {
        return status == AnalysisStatus.COMPLETED;
    }

    public boolean canRestart() {
        return status == AnalysisStatus.FAILED || status == AnalysisStatus.CANCELLED;
    }
}