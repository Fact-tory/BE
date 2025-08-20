package com.commonground.be.domain.analysis.dto.request;

import com.commonground.be.domain.analysis.enums.AnalysisType;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AnalysisStartRequest {
    
    private String url;
    private String text;
    private String newsId;
    private String title;
    private AnalysisType type;
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