package com.fptis.intern.server.global.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "travelx.jwt")
public record JwtProperties(int accessTokenExpireMinutes, int refreshTokenExpireDays) {
}
