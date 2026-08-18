package com.atcrew.billing.internal.web;

import com.atcrew.billing.internal.application.BillingWebhookService;
import com.atcrew.billing.internal.config.StripeProperties;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.net.Webhook;
import io.swagger.v3.oas.annotations.Hidden;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Stripe 웹훅 수신구. 인증 없이 열려 있으므로 서명 검증이 유일한 신뢰 근거다.
 *
 * <p>본문은 서명 계산에 쓰이므로 역직렬화하지 않고 원문 문자열 그대로 받는다.
 * 처리 중 예외가 나면 5xx가 나가고 Stripe가 재시도한다.
 *
 * <p>로컬 검증: {@code stripe listen --forward-to localhost:8080/internal/billing/stripe/webhook}
 */
@Hidden
@RestController
@RequestMapping("/internal/billing/stripe")
class BillingWebhookController {

    private static final Logger log = LoggerFactory.getLogger(BillingWebhookController.class);

    private final BillingWebhookService webhookService;
    private final StripeProperties stripeProperties;

    BillingWebhookController(BillingWebhookService webhookService, StripeProperties stripeProperties) {
        this.webhookService = webhookService;
        this.stripeProperties = stripeProperties;
    }

    @PostMapping("/webhook")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void handle(@RequestHeader("Stripe-Signature") String signature,
            @RequestBody String payload) {
        String secret = stripeProperties.webhookSecret();
        if (secret == null || secret.isBlank()) {
            log.error("STRIPE_WEBHOOK_SECRET이 설정되지 않아 웹훅을 처리할 수 없습니다");
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE);
        }

        Event event;
        try {
            event = Webhook.constructEvent(payload, signature, secret);
        } catch (SignatureVerificationException e) {
            log.warn("웹훅 서명 검증 실패");
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        }

        webhookService.handle(event);
    }
}
