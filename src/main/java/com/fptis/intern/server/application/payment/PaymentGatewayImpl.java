package com.fptis.intern.server.application.payment;

import com.fptis.intern.server.global.exception.BusinessErrorCode;
import com.fptis.intern.server.global.exception.BusinessException;
import com.stripe.exception.ApiConnectionException;
import com.stripe.exception.ApiException;
import com.stripe.exception.AuthenticationException;
import com.stripe.exception.CardException;
import com.stripe.exception.IdempotencyException;
import com.stripe.exception.InvalidRequestException;
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
        } catch (CardException | InvalidRequestException | AuthenticationException e) {
            // RateLimitException은 InvalidRequestException의, PermissionException은
            // AuthenticationException의 하위 타입이라 별도로 나열하지 않아도 여기서 잡힌다.
            // Stripe가 요청을 받고 명확히 거절함 — PaymentIntent가 생성되지 않았다고 확신할 수 있는
            // 케이스만 여기서 분류한다. 호출부(ReservationService)가 이 경우에만 즉시 보상(홀드 취소
            // + 재고/슬롯 복원)을 태운다.
            log.warn("[PaymentGatewayImpl] Stripe PaymentIntent 생성 확정 실패: idempotencyKey={}, message={}",
                    idempotencyKey, e.getMessage());
            throw new BusinessException(BusinessErrorCode.PAYMENT_INTENT_CREATE_FAILED);
        } catch (ApiConnectionException | ApiException | IdempotencyException e) {
            // 네트워크 타임아웃(ApiConnectionException), Stripe 서버 오류(ApiException), 동일
            // idempotencyKey 재사용 충돌(IdempotencyException) — 이 응답들은 요청이 Stripe 쪽에서
            // 실제로 처리(PaymentIntent 생성)됐는지 우리가 확정할 수 없다. 여기서 홀드를 취소하면
            // Stripe엔 살아있는 PaymentIntent가 있는데 우리 예약만 취소된 상태로 어긋날 수 있으므로,
            // 호출부는 보상하지 않고 결제 TTL 스윕러(expireOnePendingPayment)에 정리를 맡긴다.
            log.warn("[PaymentGatewayImpl] Stripe PaymentIntent 생성 결과 불확실: idempotencyKey={}, message={}",
                    idempotencyKey, e.getMessage());
            throw new BusinessException(BusinessErrorCode.PAYMENT_INTENT_CREATE_OUTCOME_UNKNOWN);
        } catch (StripeException e) {
            // 위에서 분류하지 않은 나머지 StripeException 하위 타입 — 확정 실패인지 알 수 없으므로
            // 보수적으로 "결과 불확실" 쪽으로 분류해 잘못된 보상(실제로는 살아있는 intent를 우리
            // 쪽만 취소)을 피한다.
            log.warn("[PaymentGatewayImpl] Stripe PaymentIntent 생성 중 미분류 예외: idempotencyKey={}, type={}, message={}",
                    idempotencyKey, e.getClass().getSimpleName(), e.getMessage());
            throw new BusinessException(BusinessErrorCode.PAYMENT_INTENT_CREATE_OUTCOME_UNKNOWN);
        }
    }
}
