package com.commonground.be.global.infrastructure.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 📅 스케줄링 설정
 * 
 * 책임:
 * - Spring 스케줄링 기능 활성화
 * - 네이버 뉴스 정기 크롤링 스케줄 관리
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
    // Spring Boot가 자동으로 TaskScheduler를 설정하므로 별도 Bean 설정 불필요
}