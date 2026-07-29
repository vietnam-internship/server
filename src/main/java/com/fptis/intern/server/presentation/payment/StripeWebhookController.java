package com.fptis.intern.server.presentation.payment;

import com.fptis.intern.server.application.payment.PaymentService;
import com.fptis.intern.server.global.annotation.PublicApi;
import com.fptis.intern.server.global.config.StripeProperties;
import com.stripe.exception.EventDataObjectDeserializationException;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.model.PaymentIntent;
import com.stripe.model.StripeObject;
import com.stripe.net.Webhook;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Stripe가 서버 대 서버로 호출하는 엔드포인트다 — 우리 프론트가 호출하는 API가 아니므로 응답을
 * ApiResponse로 감싸지 않고 Stripe가 기대하는 형태(2xx=수신 확인, 그 외=재전송)만 지킨다.
 * 인증은 사용자 로그인이 아니라 서명 검증(Stripe-Signature)으로 이뤄지므로 @PublicApi로 명시한다.
 */
@Slf4j
@RestController
@RequestMapping("/webhooks/stripe")
@RequiredArgsConstructor
public class StripeWebhookController {

    private final PaymentService paymentService;
    private final StripeProperties stripeProperties;

    @PublicApi
    @PostMapping
    public ResponseEntity<Void> handle(@RequestBody String payload,
                                        @RequestHeader("Stripe-Signature") String signatureHeader) {
        Event event;
        try {
            event = Webhook.constructEvent(payload, signatureHeader, stripeProperties.webhookSecret());
        } catch (SignatureVerificationException e) {
            log.warn("[StripeWebhookController] 서명 검증 실패: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }

        // getObject()는 이벤트에 찍힌 API 버전이 SDK 컴파일 버전과 다르면 조용히 empty를 반환한다
        // (stripe-java의 잘 알려진 동작) — 그러면 결제 승인 이벤트가 와도 아무 처리 없이 200만
        // 리턴하고 넘어가 버린다. deserializeUnsafe()는 버전 불일치와 무관하게 역직렬화하므로
        // 폴백으로 사용한다.
        StripeObject stripeObject = event.getDataObjectDeserializer().getObject().orElse(null);
        if (stripeObject == null) {
            try {
                stripeObject = event.getDataObjectDeserializer().deserializeUnsafe();
            } catch (EventDataObjectDeserializationException e) {
                log.warn("[StripeWebhookController] 이벤트 역직렬화 실패: type={}, message={}",
                        event.getType(), e.getMessage());
                return ResponseEntity.ok().build();
            }
        }
        if (stripeObject instanceof PaymentIntent paymentIntent) {
            switch (event.getType()) {
                case "payment_intent.succeeded" -> paymentService.handlePaymentSucceeded(paymentIntent.getId());
                case "payment_intent.payment_failed" -> paymentService.handlePaymentFailed(paymentIntent.getId());
                default -> {
                    // 이 흐름과 무관한 이벤트 타입은 무시한다.
                }
            }
        }
        return ResponseEntity.ok().build();
    }
}
