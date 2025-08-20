package com.commonground.be.config;

import org.opensearch.client.opensearch.OpenSearchClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.mockito.Mockito;

@Configuration
public class TestSecurityConfig {

    @Bean
    @ConditionalOnMissingBean(OpenSearchClient.class)
    public OpenSearchClient openSearchClient() {
        return Mockito.mock(OpenSearchClient.class);
    }
}
