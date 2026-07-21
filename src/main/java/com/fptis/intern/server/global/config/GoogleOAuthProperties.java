package com.fptis.intern.server.global.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * redirect-uri는 구글 인가 요청(authorize)과 code 교환(token exchange) 양쪽에
 * 반드시 동일한 문자열로 쓰여야 한다 — 여기 한 곳에서만 읽어 드리프트를 막는다.
 */
@ConfigurationProperties(prefix = "travelx.oauth.google")
public record GoogleOAuthProperties(String clientId, String clientSecret, String redirectUri) {
}
