package com.evrouting.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

    @Value("${python.engine.url}")
    private String pythonEngineUrl;

    @Bean
    public WebClient orsWebClient() {
        return WebClient.builder()
                .baseUrl("https://api.openrouteservice.org")
                .defaultHeader("Accept", "application/json, application/geo+json")
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    @Bean
    public WebClient pythonWebClient() {
        return WebClient.builder()
                .baseUrl(pythonEngineUrl)
                .defaultHeader("Content-Type", "application/json")
                .build();
    }
}
