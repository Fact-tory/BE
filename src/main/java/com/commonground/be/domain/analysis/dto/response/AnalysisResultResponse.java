package com.commonground.be.domain.analysis.dto.response;

import com.commonground.be.domain.analysis.entity.Analysis;
import com.commonground.be.domain.analysis.enums.AnalysisStatus;
import com.commonground.be.domain.analysis.enums.AnalysisType;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.Map;

@Getter
public class AnalysisResultResponse {
    
    private final Long analysisId;
    private final AnalysisType type;
    private final AnalysisStatus status;
    private final String title;
    private final Integer progressPercentage;
    private final LocalDateTime createdAt;
    private final LocalDateTime completedAt;
    private final String errorMessage;
    
    // 분석 결과
    private final Object biasAnalysisResult;
    private final Object sentimentAnalysisResult;
    private final Object keywordExtractionResult;
    private final Object factCheckResult;

    public AnalysisResultResponse(Analysis analysis) {
        this.analysisId = analysis.getId();
        this.type = analysis.getAnalysisType();
        this.status = analysis.getStatus();
        this.title = analysis.getTitle();
        this.progressPercentage = analysis.getProgressPercentage();
        this.createdAt = analysis.getCreatedAt();
        this.completedAt = analysis.getStatus() == AnalysisStatus.COMPLETED ? analysis.getUpdatedAt() : null;
        this.errorMessage = analysis.getErrorMessage();
        
        // JSON 문자열을 객체로 변환
        ObjectMapper objectMapper = new ObjectMapper();
        this.biasAnalysisResult = parseJsonResult(objectMapper, analysis.getBiasAnalysisResult());
        this.sentimentAnalysisResult = parseJsonResult(objectMapper, analysis.getSentimentAnalysisResult());
        this.keywordExtractionResult = parseJsonResult(objectMapper, analysis.getKeywordExtractionResult());
        this.factCheckResult = parseJsonResult(objectMapper, analysis.getFactCheckResult());
    }

    private Object parseJsonResult(ObjectMapper objectMapper, String jsonResult) {
        if (jsonResult == null || jsonResult.trim().isEmpty()) {
            return null;
        }
        try {
            return objectMapper.readValue(jsonResult, Map.class);
        } catch (JsonProcessingException e) {
            return jsonResult; // JSON 파싱 실패시 원본 문자열 반환
        }
    }
}