package com.fptis.intern.server.global.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
@EnableConfigurationProperties(AiServerProperties.class)
public class AiClientConfig {

    @Bean
    public WebClient aiWebClient(AiServerProperties props) {
        return WebClient.builder()
                .baseUrl(props.serverUrl())
                .build();
    }
}
