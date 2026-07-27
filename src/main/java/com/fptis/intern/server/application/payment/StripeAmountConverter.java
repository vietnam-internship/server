package com.fptis.intern.server.application.payment;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Set;

/**
 * Stripe는 통화별 최소 단위(minor unit)로 금액을 받는다 — 대부분 통화는 소수점 2자리(×100)지만
 * KRW/VND/JPY 등 "zero-decimal" 통화는 정수 그대로 보내야 한다. 잘못 곱하면 100배 과다 청구로
 * 직결되므로 반드시 이 목록을 거쳐야 한다.
 * 목록 출처: https://docs.stripe.com/currencies#zero-decimal
 */
public final class StripeAmountConverter {

    private static final Set<String> ZERO_DECIMAL_CURRENCIES = Set.of(
            "BIF", "CLP", "DJF", "GNF", "JPY", "KMF", "KRW", "MGA",
            "PYG", "RWF", "UGX", "VND", "VUV", "XAF", "XOF", "XPF");

    private StripeAmountConverter() {
    }

    public static long toMinorUnits(double amount, String currencyCode) {
        BigDecimal value = BigDecimal.valueOf(amount);
        if (ZERO_DECIMAL_CURRENCIES.contains(currencyCode.toUpperCase())) {
            return value.setScale(0, RoundingMode.HALF_UP).longValueExact();
        }
        return value.movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact();
    }
}
