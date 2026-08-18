package com.atcrew.support;

import com.atcrew.billing.BillingProduct;
import com.atcrew.billing.PlanType;
import com.atcrew.billing.SubscriptionStatus;
import com.atcrew.billing.internal.domain.EntitlementBalance;
import com.atcrew.billing.internal.domain.MemberProductId;
import com.atcrew.billing.internal.domain.Subscription;
import com.atcrew.billing.internal.persistence.EntitlementBalanceRepository;
import com.atcrew.billing.internal.persistence.SubscriptionRepository;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * 테스트에서 단건 게시 상품 보유 개수를 직접 채워 넣기 위한 헬퍼.
 *
 * <p>실제 지급은 Stripe 웹훅으로만 이뤄지므로(BillingWebhookService), 결제와 무관한 게시·끌어올리기
 * 테스트에서는 이 헬퍼로 사전 조건만 만든다.
 */
public final class BillingTestSupport {

    private BillingTestSupport() {
    }

    /**
     * 단건 게시 상품 3종을 넉넉히 지급한다. 게시·끌어올리기가 결제와 무관하게 동작해야 하는
     * recruit·search 테스트에서 사전 조건으로 쓴다.
     */
    public static void grantAllPostingProducts(EntitlementBalanceRepository repository, String memberId) {
        for (BillingProduct product : BillingProduct.values()) {
            if (!product.isSubscription()) {
                grant(repository, memberId, product, 20);
            }
        }
    }

    /** 결제 없이 프로 플랜 구독 상태를 만든다. 웹훅이 반영했을 때와 같은 행을 직접 넣는다. */
    public static String grantProPlan(SubscriptionRepository repository, String memberId) {
        Subscription subscription = Subscription.create(memberId, "sub_test_" + memberId,
                PlanType.PRO_MONTHLY, SubscriptionStatus.ACTIVE,
                Instant.now().plus(30, ChronoUnit.DAYS), false, Instant.now());
        return repository.save(subscription).getId();
    }

    /** 구독을 취소 상태로 바꿔 스타터로 내린다(다운그레이드 재현). */
    public static void cancelPlan(SubscriptionRepository repository, String subscriptionId) {
        Subscription subscription = repository.findById(subscriptionId).orElseThrow();
        subscription.sync(subscription.getPlan(), SubscriptionStatus.CANCELED,
                subscription.getCurrentPeriodEnd(), false, Instant.now());
        repository.save(subscription);
    }

    /** 보유 개수를 0으로 만든다 — 권한 없음 상태를 재현할 때 쓴다. */
    public static void clear(EntitlementBalanceRepository repository, String memberId,
            BillingProduct product) {
        repository.findById(new MemberProductId(memberId, product))
                .ifPresent(balance -> {
                    balance.add(-balance.getQuantity());
                    repository.save(balance);
                });
    }

    public static void grant(EntitlementBalanceRepository repository, String memberId,
            BillingProduct product, int quantity) {
        EntitlementBalance balance = repository.findById(new MemberProductId(memberId, product))
                .orElseGet(() -> EntitlementBalance.create(memberId, product));
        balance.add(quantity);
        repository.save(balance);
    }
}
