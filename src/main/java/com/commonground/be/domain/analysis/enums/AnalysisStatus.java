package com.commonground.be.domain.analysis.enums;

public enum AnalysisStatus {
    PENDING("대기 중"),
    IN_PROGRESS("진행 중"),
    COMPLETED("완료"),
    FAILED("실패"),
    CANCELLED("취소됨");

    private final String description;

    AnalysisStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}