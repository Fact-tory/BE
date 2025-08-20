package com.commonground.be.domain.analysis.service;

import com.commonground.be.domain.analysis.dto.request.AnalysisRestartRequest;
import com.commonground.be.domain.analysis.dto.request.AnalysisStartRequest;
import com.commonground.be.domain.analysis.dto.response.AnalysisResponse;
import com.commonground.be.domain.analysis.dto.response.AnalysisResultResponse;
import com.commonground.be.domain.analysis.dto.response.AnalysisStatusResponse;
import com.commonground.be.domain.analysis.dto.response.MyAnalysesResponse;
import com.commonground.be.domain.analysis.entity.Analysis;
import com.commonground.be.domain.analysis.enums.AnalysisStatus;
import com.commonground.be.domain.analysis.enums.AnalysisType;
import com.commonground.be.domain.analysis.repository.AnalysisRepository;
import com.commonground.be.global.application.exception.CommonException;
import com.commonground.be.global.application.response.ResponseExceptionEnum;
import com.commonground.be.global.infrastructure.concurrency.RedisLock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AnalysisServiceImpl implements AnalysisService {

    private final AnalysisRepository analysisRepository;
    private final BiasAnalysisService biasAnalysisService;
    private final SentimentAnalysisService sentimentAnalysisService;
    private final KeywordExtractionService keywordExtractionService;

    // 사용자별 일일 분석 제한
    private static final int DAILY_ANALYSIS_LIMIT = 50;

    @Override
    @Transactional
    @RedisLock(key = "'analysis_start:' + #userId", waitTime = 5, leaseTime = 30, 
               timeoutMessage = "다른 분석이 진행 중입니다. 잠시 후 다시 시도해주세요.")
    public AnalysisResponse startAnalysis(String userId, AnalysisStartRequest request) {
        log.info("분석 시작 요청 - userId: {}, type: {}", userId, request.getType());

        // 사용량 체크
        if (!canStartNewAnalysis(userId)) {
            throw new CommonException(ResponseExceptionEnum.ANALYSIS_LIMIT_EXCEEDED);
        }

        // 분석 엔티티 생성
        Analysis analysis = createAnalysis(userId, request);
        Analysis savedAnalysis = analysisRepository.save(analysis);

        // 비동기 분석 시작
        startAsyncAnalysis(savedAnalysis);

        log.info("분석 시작됨 - analysisId: {}", savedAnalysis.getId());
        return new AnalysisResponse(savedAnalysis);
    }

    @Override
    public AnalysisResultResponse getAnalysisResult(String userId, Long analysisId) {
        Analysis analysis = findAnalysisByUserAndId(userId, analysisId);
        return new AnalysisResultResponse(analysis);
    }

    @Override
    public AnalysisStatusResponse getAnalysisStatus(String userId, Long analysisId) {
        Analysis analysis = findAnalysisByUserAndId(userId, analysisId);
        return new AnalysisStatusResponse(analysis);
    }

    @Override
    public MyAnalysesResponse getMyAnalyses(String userId, Pageable pageable) {
        Page<Analysis> analysisPage = analysisRepository.findByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(userId, pageable);
        return new MyAnalysesResponse(analysisPage);
    }

    @Override
    @Transactional
    public void cancelAnalysis(String userId, Long analysisId) {
        Analysis analysis = findAnalysisByUserAndId(userId, analysisId);
        
        if (analysis.getStatus() == AnalysisStatus.COMPLETED) {
            throw new CommonException(ResponseExceptionEnum.ANALYSIS_ALREADY_COMPLETED);
        }
        
        analysis.cancel();
        analysisRepository.save(analysis);
        
        log.info("분석 취소됨 - analysisId: {}", analysisId);
    }

    @Override
    @Transactional
    public AnalysisResponse restartAnalysis(String userId, Long analysisId, AnalysisRestartRequest request) {
        Analysis analysis = findAnalysisByUserAndId(userId, analysisId);
        
        if (!analysis.canRestart()) {
            throw new CommonException(ResponseExceptionEnum.ANALYSIS_CANNOT_RESTART);
        }

        // 분석 재시작
        analysis.restart();
        
        // 옵션 업데이트
        if (request.getOptions() != null) {
            updateAnalysisOptions(analysis, request.getOptions());
        }
        
        Analysis savedAnalysis = analysisRepository.save(analysis);
        
        // 비동기 분석 재시작
        startAsyncAnalysis(savedAnalysis);
        
        log.info("분석 재시작됨 - analysisId: {}", analysisId);
        return new AnalysisResponse(savedAnalysis);
    }

    @Override
    public boolean canStartNewAnalysis(String userId) {
        LocalDateTime todayStart = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0).withNano(0);
        long todayAnalysisCount = analysisRepository.countByUserIdAndCreatedAtAfter(userId, todayStart);
        return todayAnalysisCount < DAILY_ANALYSIS_LIMIT;
    }

    private Analysis createAnalysis(String userId, AnalysisStartRequest request) {
        return Analysis.builder()
                .userId(userId)
                .analysisType(request.getType())
                .targetUrl(request.getUrl())
                .targetText(request.getText())
                .targetNewsId(request.getNewsId())
                .title(generateTitle(request))
                .includeBiasAnalysis(request.getOptions() != null ? request.getOptions().getIncludeBiasAnalysis() : true)
                .includeSentimentAnalysis(request.getOptions() != null ? request.getOptions().getIncludeSentimentAnalysis() : true)
                .includeKeywordExtraction(request.getOptions() != null ? request.getOptions().getIncludeKeywordExtraction() : true)
                .includeFactCheck(request.getOptions() != null ? request.getOptions().getIncludeFactCheck() : false)
                .build();
    }

    private String generateTitle(AnalysisStartRequest request) {
        if (request.getTitle() != null && !request.getTitle().trim().isEmpty()) {
            return request.getTitle().trim();
        }

        return switch (request.getType()) {
            case URL_ANALYSIS -> "URL 분석: " + extractDomainFromUrl(request.getUrl());
            case TEXT_ANALYSIS -> "텍스트 분석: " + truncateText(request.getText(), 50);
            case NEWS_ANALYSIS -> "뉴스 분석: " + request.getNewsId();
        };
    }

    private String extractDomainFromUrl(String url) {
        if (url == null) return "Unknown";
        try {
            return new java.net.URL(url).getHost();
        } catch (Exception e) {
            return "Invalid URL";
        }
    }

    private String truncateText(String text, int maxLength) {
        if (text == null) return "Empty Text";
        if (text.length() <= maxLength) return text;
        return text.substring(0, maxLength) + "...";
    }

    private void updateAnalysisOptions(Analysis analysis, AnalysisRestartRequest.AnalysisOptions options) {
        // 필요하다면 분석 옵션 업데이트 로직 구현
        // 현재는 엔티티에 setter가 없으므로 리플렉션이나 빌더 패턴 재적용이 필요
        log.info("분석 옵션 업데이트 요청됨 - analysisId: {}", analysis.getId());
    }

    private Analysis findAnalysisByUserAndId(String userId, Long analysisId) {
        return analysisRepository.findByIdAndUserIdAndDeletedAtIsNull(analysisId, userId)
                .orElseThrow(() -> new CommonException(ResponseExceptionEnum.ANALYSIS_NOT_FOUND));
    }

    private void startAsyncAnalysis(Analysis analysis) {
        CompletableFuture.runAsync(() -> performAnalysis(analysis))
                .exceptionally(throwable -> {
                    log.error("분석 중 오류 발생 - analysisId: {}", analysis.getId(), throwable);
                    handleAnalysisError(analysis.getId(), throwable.getMessage());
                    return null;
                });
    }

    @Transactional
    public void performAnalysis(Analysis analysis) {
        try {
            log.info("분석 시작 - analysisId: {}", analysis.getId());
            
            // 분석 상태를 진행 중으로 변경
            analysis.updateStatus(AnalysisStatus.IN_PROGRESS);
            analysis.updateProgress(10);
            analysisRepository.save(analysis);

            String contentToAnalyze = extractContentForAnalysis(analysis);
            
            // 편향 분석
            if (analysis.getIncludeBiasAnalysis()) {
                log.info("편향 분석 시작 - analysisId: {}", analysis.getId());
                String biasResult = biasAnalysisService.analyzeBias(contentToAnalyze);
                analysis.setBiasAnalysisResult(biasResult);
                analysis.updateProgress(40);
                analysisRepository.save(analysis);
            }

            // 감정 분석
            if (analysis.getIncludeSentimentAnalysis()) {
                log.info("감정 분석 시작 - analysisId: {}", analysis.getId());
                String sentimentResult = sentimentAnalysisService.analyzeSentiment(contentToAnalyze);
                analysis.setSentimentAnalysisResult(sentimentResult);
                analysis.updateProgress(70);
                analysisRepository.save(analysis);
            }

            // 키워드 추출
            if (analysis.getIncludeKeywordExtraction()) {
                log.info("키워드 추출 시작 - analysisId: {}", analysis.getId());
                String keywordResult = keywordExtractionService.extractKeywords(contentToAnalyze);
                analysis.setKeywordExtractionResult(keywordResult);
                analysis.updateProgress(90);
                analysisRepository.save(analysis);
            }

            // 분석 완료
            analysis.complete();
            analysisRepository.save(analysis);
            
            log.info("분석 완료 - analysisId: {}", analysis.getId());

        } catch (Exception e) {
            log.error("분석 실행 중 오류 - analysisId: {}", analysis.getId(), e);
            handleAnalysisError(analysis.getId(), e.getMessage());
        }
    }

    private String extractContentForAnalysis(Analysis analysis) {
        return switch (analysis.getAnalysisType()) {
            case URL_ANALYSIS -> {
                // URL에서 콘텐츠 크롤링 로직 구현
                yield "URL 크롤링된 콘텐츠: " + analysis.getTargetUrl();
            }
            case TEXT_ANALYSIS -> analysis.getTargetText();
            case NEWS_ANALYSIS -> {
                // 뉴스 ID로 뉴스 콘텐츠 조회 로직 구현
                yield "뉴스 콘텐츠: " + analysis.getTargetNewsId();
            }
        };
    }

    @Transactional
    public void handleAnalysisError(Long analysisId, String errorMessage) {
        analysisRepository.findById(analysisId).ifPresent(analysis -> {
            analysis.setError(errorMessage);
            analysisRepository.save(analysis);
        });
    }
}