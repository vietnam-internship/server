package com.fptis.intern.server.global.config;

import com.stripe.Stripe;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Stripe SDK는 API 키를 인스턴스가 아닌 전역 정적 필드({@link Stripe#apiKey})로 관리한다 —
 * 앱 시작 시 한 번만 세팅하면 이후 모든 Stripe.* 호출에 자동으로 실린다.
 */
@Configuration
@EnableConfigurationProperties(StripeProperties.class)
@RequiredArgsConstructor
public class StripeConfig {

    private final StripeProperties stripeProperties;

    @PostConstruct
    void configureApiKey() {
        Stripe.apiKey = stripeProperties.secretKey();
    }
}
