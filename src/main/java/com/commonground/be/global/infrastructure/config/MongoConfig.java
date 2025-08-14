package com.commonground.be.global.infrastructure.config;

import com.commonground.be.domain.news.entity.News;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.config.AbstractMongoClientConfiguration;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.TextIndexDefinition;

/**
 * MongoDB 설정 클래스
 * 
 * MongoDB 연결 및 레포지토리 스캔 설정을 담당합니다.
 * Custom Repository 구현체를 사용하므로 자동 레포지토리 생성은 비활성화합니다.
 */
@Configuration
public class MongoConfig extends AbstractMongoClientConfiguration {

    @Override
    protected String getDatabaseName() {
        return "commonground";
    }
    
    @Override
    protected boolean autoIndexCreation() {
        return true; // @Indexed 어노테이션 기반 인덱스 자동 생성
    }
    
    /**
     * News 컬렉션에 텍스트 검색 인덱스 생성
     */
    @Bean
    public MongoTemplate mongoTemplate() throws Exception {
        MongoTemplate mongoTemplate = new MongoTemplate(mongoClient(), getDatabaseName());
        
        // News 컬렉션에 텍스트 인덱스 생성 (제목, 내용 검색용)
        TextIndexDefinition textIndex = new TextIndexDefinition.TextIndexDefinitionBuilder()
            .onField("title")
            .onField("content")
            .build();
            
        mongoTemplate.indexOps(News.class).createIndex(textIndex);
        
        return mongoTemplate;
    }
}