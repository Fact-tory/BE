package com.commonground.be.domain.news.enums;

import lombok.Getter;

@Getter
public enum CategoryEnum {
    POLITICS("정치"),
    ECONOMY("경제"), 
    SOCIETY("사회"),
    CULTURE("문화");
    
    private final String koreanName;
    
    CategoryEnum(String koreanName) {
        this.koreanName = koreanName;
    }

}