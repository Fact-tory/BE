package com.commonground.be.domain.analysis.service;

import com.commonground.be.domain.analysis.dto.request.AnalysisRestartRequest;
import com.commonground.be.domain.analysis.dto.request.AnalysisStartRequest;
import com.commonground.be.domain.analysis.dto.response.AnalysisResponse;
import com.commonground.be.domain.analysis.dto.response.AnalysisResultResponse;
import com.commonground.be.domain.analysis.dto.response.AnalysisStatusResponse;
import com.commonground.be.domain.analysis.dto.response.MyAnalysesResponse;
import org.springframework.data.domain.Pageable;

public interface AnalysisService {

    /**
     * 분석 시작
     */
    AnalysisResponse startAnalysis(String userId, AnalysisStartRequest request);

    /**
     * 분석 결과 조회
     */
    AnalysisResultResponse getAnalysisResult(String userId, Long analysisId);

    /**
     * 분석 상태 조회
     */
    AnalysisStatusResponse getAnalysisStatus(String userId, Long analysisId);

    /**
     * 내 분석 목록 조회
     */
    MyAnalysesResponse getMyAnalyses(String userId, Pageable pageable);

    /**
     * 분석 취소
     */
    void cancelAnalysis(String userId, Long analysisId);

    /**
     * 분석 재시작
     */
    AnalysisResponse restartAnalysis(String userId, Long analysisId, AnalysisRestartRequest request);

    /**
     * 사용자 분석 사용량 체크
     */
    boolean canStartNewAnalysis(String userId);
}