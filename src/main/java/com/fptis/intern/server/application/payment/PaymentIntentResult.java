package com.fptis.intern.server.application.payment;

public record PaymentIntentResult(String paymentIntentId, String clientSecret) {
}
