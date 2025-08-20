package com.commonground.be.domain.analysis.dto.request;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AnalysisRestartRequest {
    
    private AnalysisOptions options;

    @Getter
    @Setter
    public static class AnalysisOptions {
        private Boolean includeBiasAnalysis = true;
        private Boolean includeSentimentAnalysis = true;
        private Boolean includeKeywordExtraction = true;
        private Boolean includeFactCheck = false;
    }
}