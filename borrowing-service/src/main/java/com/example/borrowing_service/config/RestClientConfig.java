package com.example.borrowing_service.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class RestClientConfig {

    @Bean
    public RestClient memberServiceRestClient(@Value("${member-service.url}") String baseUrl) {
        return RestClient.builder().baseUrl(baseUrl).build();
    }

    @Bean
    public RestClient catalogServiceRestClient(@Value("${catalog-service.url}") String baseUrl) {
        return RestClient.builder().baseUrl(baseUrl).build();
    }
}
