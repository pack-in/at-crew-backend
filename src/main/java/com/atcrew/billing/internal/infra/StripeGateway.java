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
import com.stripe.param.checkout.SessionListParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Stripe API 호출을 한곳에 모은 어댑터. 애플리케이션 계층이 Stripe SDK 예외를 직접 다루지 않도록
 * 전부 {@link BillingException}으로 변환한다.
 */
@Component
public class StripeGateway {

    private static final Logger log = LoggerFactory.getLogger(StripeGateway.class);

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
     *
     * @param trialDays 구독 무료 체험 일수. 0이면 체험 없이 즉시 청구한다
     */
    public String createCheckoutSession(String memberId, BillingProduct product, String customerId,
            int trialDays) {
        try {
            return client.checkout().sessions()
                    .create(checkoutParams(memberId, product, customerId, trialDays)).getUrl();
        } catch (StripeException e) {
            throw new BillingException(BillingErrorCode.STRIPE_REQUEST_FAILED, e);
        }
    }

    SessionCreateParams checkoutParams(String memberId, BillingProduct product, String customerId,
            int trialDays) {
        BillingProperties.Product config = properties.product(product);
        if (config.priceId() == null || config.priceId().isBlank()) {
            throw new BillingException(BillingErrorCode.PRICE_NOT_CONFIGURED, "product=" + product);
        }

        String base = properties.frontendBaseUrl();
        SessionCreateParams.Builder params = SessionCreateParams.builder()
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
                // 대시보드에서 발급한 쿠폰을 결제창에서 입력할 수 있게 한다.
                .setAllowPromotionCodes(true)
                // Stripe가 판매 주체(MoR)로 세금을 처리한다. SDK 고정 API 버전(2025-03-31.basil 이상)에서만 받는다.
                .setManagedPayments(SessionCreateParams.ManagedPayments.builder().setEnabled(true).build());
        if (product.isSubscription() && trialDays > 0) {
            // 체험 기간에도 결제수단은 받는다(Checkout 기본값) — 체험이 끝나면 첫 주기 금액이 자동 청구된다.
            params.setSubscriptionData(SessionCreateParams.SubscriptionData.builder()
                    .setTrialPeriodDays((long) trialDays)
                    .build());
        }
        return params.build();
    }

    /**
     * 고객의 열려 있는 Checkout 세션을 전부 만료시킨다. 세션은 24시간 유효하고 무료 체험 자격은 생성 시점에
     * 판정되므로, 미리 열어 둔 세션으로 체험을 반복하지 못하게 새 세션을 만들기 전에 닫는다.
     *
     * <p>목록 조회와 만료 사이에 결제가 끝나거나 스스로 만료된 세션은 만료 요청이 실패하는데, 이미 닫힌
     * 것이므로 건너뛴다.
     */
    public void expireOpenCheckoutSessions(String customerId) {
        SessionListParams params = SessionListParams.builder()
                .setCustomer(customerId)
                .setStatus(SessionListParams.Status.OPEN)
                .build();
        try {
            for (com.stripe.model.checkout.Session session :
                    client.checkout().sessions().list(params).autoPagingIterable()) {
                expireQuietly(session.getId());
            }
        } catch (StripeException e) {
            throw new BillingException(BillingErrorCode.STRIPE_REQUEST_FAILED, e);
        }
    }

    private void expireQuietly(String sessionId) {
        try {
            client.checkout().sessions().expire(sessionId);
        } catch (StripeException e) {
            log.info("Checkout 세션 만료 건너뜀 sessionId={}, reason={}", sessionId, e.getMessage());
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
