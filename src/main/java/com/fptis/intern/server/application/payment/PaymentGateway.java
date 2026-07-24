package com.fptis.intern.server.application.payment;

import java.util.Map;

/**
 * Stripe PaymentIntents API 호출을 이 인터페이스 뒤에 격리한다 — GoogleAuthorizationCodeExchanger와
 * 같은 이유로, PaymentService가 특정 PG SDK에 직접 의존하지 않게 하고 테스트에서는 목으로 대체한다.
 * Stripe는 결제 승인을 동기 API로 확인해주지 않는다 — {@link #createIntent}는 결제창을 띄우기 위한
 * PaymentIntent만 만들고, 실제 승인 여부는 웹훅(payment_intent.succeeded/payment_intent.payment_failed)
 * 으로만 확정된다(PaymentService.handlePaymentSucceeded/handlePaymentFailed).
 */
public interface PaymentGateway {

    PaymentIntentResult createIntent(String idempotencyKey, long amountMinorUnits, String currency,
                                      Map<String, String> metadata);
}
