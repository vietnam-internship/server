package com.fptis.intern.server.domain.payment;

/**
 * PENDING -(PaymentIntent 생성 완료, 아직 승인 전)- PENDING 유지 -(웹훅 payment_intent.succeeded)->
 * APPROVED, -(웹훅 payment_intent.payment_failed)-> FAILED. Stripe는 승인 여부를 동기 API로
 * 알려주지 않으므로 전이는 전부 웹훅 핸들러(PaymentService.handlePaymentSucceeded/handlePaymentFailed)
 * 에서만 일어난다 — 그 핸들러는 예외를 던지지 않고 항상 정상 처리로 응답해야 한다(Stripe가 실패
 * 응답을 보면 같은 이벤트를 계속 재전송한다).
 */
public enum PaymentStatus {
    PENDING,
    APPROVED,
    FAILED
}
