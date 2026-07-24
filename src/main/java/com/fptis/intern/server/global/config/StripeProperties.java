package com.fptis.intern.server.global.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "travelx.payment.stripe")
public record StripeProperties(String secretKey, String webhookSecret) {
}
