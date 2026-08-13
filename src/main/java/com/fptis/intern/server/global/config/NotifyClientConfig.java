package com.fptis.intern.server.global.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
@EnableConfigurationProperties(NotifyProperties.class)
public class NotifyClientConfig {

    @Bean
    public WebClient notifyWebClient() {
        return WebClient.builder().build();
    }
}
