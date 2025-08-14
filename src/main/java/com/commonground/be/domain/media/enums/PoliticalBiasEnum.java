package com.commonground.be.domain.media.enums;


import lombok.Getter;

@Getter
public enum PoliticalBiasEnum {
	PROGRESSIVE("진보"),
	NEUTRAL("중립"),
	CONSERVATIVE("보수");

	private final String koreanName;

	PoliticalBiasEnum(String koreanName) {
		this.koreanName = koreanName;
	}

}