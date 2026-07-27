package com.fptis.intern.server.application.payment;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class StripeAmountConverterTest {

    @Test
    void nonZeroDecimalCurrencyMultipliesByHundred() {
        assertThat(StripeAmountConverter.toMinorUnits(19.99, "USD")).isEqualTo(1999);
    }

    @Test
    void zeroDecimalCurrencyKrwKeepsWholeUnits() {
        assertThat(StripeAmountConverter.toMinorUnits(50000, "KRW")).isEqualTo(50000);
    }

    @Test
    void zeroDecimalCurrencyVndKeepsWholeUnits() {
        assertThat(StripeAmountConverter.toMinorUnits(1200000, "VND")).isEqualTo(1200000);
    }

    @Test
    void isCaseInsensitive() {
        assertThat(StripeAmountConverter.toMinorUnits(100, "krw")).isEqualTo(100);
    }

    @Test
    void roundsHalfUpForFractionalMinorUnits() {
        assertThat(StripeAmountConverter.toMinorUnits(19.995, "USD")).isEqualTo(2000);
    }
}
