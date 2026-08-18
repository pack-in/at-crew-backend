package com.atcrew.billing.internal.infra;

import com.atcrew.billing.BillingProduct;
import com.atcrew.billing.internal.config.BillingProperties;
import com.atcrew.billing.internal.exception.BillingErrorCode;
import com.atcrew.billing.internal.exception.BillingException;
import com.stripe.StripeClient;
import com.stripe.exception.StripeException;
import com.stripe.model.Customer;
import com.stripe.param.CustomerCreateParams;
import com.stripe.param.checkout.SessionCreateParams;
import org.springframework.stereotype.Component;

/**
 * Stripe API 호출을 한곳에 모은 어댑터. 애플리케이션 계층이 Stripe SDK 예외를 직접 다루지 않도록
 * 전부 {@link BillingException}으로 변환한다.
 */
@Component
public class StripeGateway {

    private final StripeClient client;
    private final BillingProperties properties;

    StripeGateway(StripeClient client, BillingProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    public String createCustomer(String memberId, String email) {
        CustomerCreateParams.Builder params = CustomerCreateParams.builder()
                .putMetadata("memberId", memberId);
        if (email != null && !email.isBlank()) {
            params.setEmail(email);
        }
        try {
            Customer customer = client.customers().create(params.build());
            return customer.getId();
        } catch (StripeException e) {
            throw new BillingException(BillingErrorCode.STRIPE_REQUEST_FAILED, e);
        }
    }

    /**
     * Checkout 세션을 만들고 결제 페이지 URL을 반환한다.
     *
     * <p>단건 결제는 PaymentIntent ID를 원장의 refId로 남겨 환불 시 회수 대상을 역추적하므로,
     * 메타데이터에 의존하지 않고도 환불 처리가 가능하다.
     */
    public String createCheckoutSession(String memberId, BillingProduct product, String customerId) {
        BillingProperties.Product config = properties.product(product);
        if (config.priceId() == null || config.priceId().isBlank()) {
            throw new BillingException(BillingErrorCode.PRICE_NOT_CONFIGURED, "product=" + product);
        }

        String base = properties.frontendBaseUrl();
        SessionCreateParams params = SessionCreateParams.builder()
                .setMode(product.isSubscription()
                        ? SessionCreateParams.Mode.SUBSCRIPTION
                        : SessionCreateParams.Mode.PAYMENT)
                .setCustomer(customerId)
                .addLineItem(SessionCreateParams.LineItem.builder()
                        .setPrice(config.priceId())
                        .setQuantity(1L)
                        .build())
                // {CHECKOUT_SESSION_ID}는 Stripe가 치환하는 자리표시자다 — URL 인코딩하면 안 된다.
                .setSuccessUrl(base + "/billing/success?session_id={CHECKOUT_SESSION_ID}")
                .setCancelUrl(base + "/billing/cancel")
                .setClientReferenceId(memberId)
                .putMetadata("memberId", memberId)
                .putMetadata("product", product.name())
                .build();

        try {
            return client.checkout().sessions().create(params).getUrl();
        } catch (StripeException e) {
            throw new BillingException(BillingErrorCode.STRIPE_REQUEST_FAILED, e);
        }
    }

    /** 구독 취소·결제수단 변경·영수증 조회를 담당하는 Stripe 호스팅 페이지 URL. */
    public String createPortalSession(String customerId, String returnPath) {
        com.stripe.param.billingportal.SessionCreateParams params =
                com.stripe.param.billingportal.SessionCreateParams.builder()
                        .setCustomer(customerId)
                        .setReturnUrl(properties.frontendBaseUrl() + returnPath)
                        .build();
        try {
            return client.billingPortal().sessions().create(params).getUrl();
        } catch (StripeException e) {
            throw new BillingException(BillingErrorCode.STRIPE_REQUEST_FAILED, e);
        }
    }

    /** 구독을 즉시 취소한다(잔여 기간 환불 없음, 설정-R11). */
    public void cancelSubscription(String stripeSubscriptionId) {
        try {
            client.subscriptions().cancel(stripeSubscriptionId);
        } catch (StripeException e) {
            throw new BillingException(BillingErrorCode.STRIPE_REQUEST_FAILED, e);
        }
    }
}
