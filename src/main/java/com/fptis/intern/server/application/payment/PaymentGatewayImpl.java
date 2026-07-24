package com.fptis.intern.server.application.payment;

import com.fptis.intern.server.global.exception.BusinessErrorCode;
import com.fptis.intern.server.global.exception.BusinessException;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.net.RequestOptions;
import com.stripe.param.PaymentIntentCreateParams;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class PaymentGatewayImpl implements PaymentGateway {

    @Override
    public PaymentIntentResult createIntent(String idempotencyKey, long amountMinorUnits, String currency,
                                             Map<String, String> metadata) {
        PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
                .setAmount(amountMinorUnits)
                .setCurrency(currency.toLowerCase())
                .putAllMetadata(metadata)
                .setAutomaticPaymentMethods(
                        PaymentIntentCreateParams.AutomaticPaymentMethods.builder().setEnabled(true).build())
                .build();
        RequestOptions options = RequestOptions.builder()
                .setIdempotencyKey(idempotencyKey)
                .build();
        try {
            PaymentIntent intent = PaymentIntent.create(params, options);
            return new PaymentIntentResult(intent.getId(), intent.getClientSecret());
        } catch (StripeException e) {
            log.warn("[PaymentGatewayImpl] Stripe PaymentIntent 생성 실패: idempotencyKey={}, message={}",
                    idempotencyKey, e.getMessage());
            throw new BusinessException(BusinessErrorCode.PAYMENT_INTENT_CREATE_FAILED);
        }
    }
}
