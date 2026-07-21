package com.fptis.intern.server.global.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "travelx.web.cors")
public record WebCorsProperties(List<String> allowedOrigins) {
}
