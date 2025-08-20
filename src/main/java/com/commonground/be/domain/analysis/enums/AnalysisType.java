package com.commonground.be.domain.analysis.enums;

public enum AnalysisType {
    URL_ANALYSIS("URL 기반 분석"),
    TEXT_ANALYSIS("텍스트 기반 분석"),
    NEWS_ANALYSIS("뉴스 ID 기반 분석");

    private final String description;

    AnalysisType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}