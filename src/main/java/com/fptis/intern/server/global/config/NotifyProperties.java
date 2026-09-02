package com.fptis.intern.server.global.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "travelx.notify")
public record NotifyProperties(String discordWebhookUrl) {
}
