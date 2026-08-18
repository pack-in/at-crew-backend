package com.atcrew.billing.internal.application;

import com.atcrew.billing.SubscriptionStatus;
import com.atcrew.billing.internal.domain.Subscription;
import com.atcrew.billing.internal.infra.StripeGateway;
import com.atcrew.billing.internal.persistence.SubscriptionRepository;
import com.atcrew.member.MemberDeactivatedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 탈퇴 시 활성 구독을 즉시 취소한다(설정-R11). 잔여 기간 환불은 없다.
 *
 * <p>Stripe Customer는 삭제하지 않는다 — 환불·분쟁 대응에 결제 이력이 필요하고,
 * 회원이 soft delete로 남는 것과도 정합적이다(D13).
 *
 * <p>실제 구독 상태(CANCELED) 반영은 Stripe가 보내는 customer.subscription.deleted 웹훅이 담당한다.
 */
@Component
class BillingMemberEventListener {

    private static final Logger log = LoggerFactory.getLogger(BillingMemberEventListener.class);

    private final SubscriptionRepository subscriptionRepository;
    private final StripeGateway stripeGateway;

    BillingMemberEventListener(SubscriptionRepository subscriptionRepository, StripeGateway stripeGateway) {
        this.subscriptionRepository = subscriptionRepository;
        this.stripeGateway = stripeGateway;
    }

    @ApplicationModuleListener
    void onMemberDeactivated(MemberDeactivatedEvent event) {
        List<Subscription> subscriptions = subscriptionRepository
                .findByMemberIdAndStatusInOrderByStripeUpdatedAtDesc(event.memberId(),
                        List.of(SubscriptionStatus.ACTIVE, SubscriptionStatus.PAST_DUE));
        for (Subscription subscription : subscriptions) {
            // 실패하면 예외를 그대로 던져 이벤트가 미완료로 남고 재발행 대상이 된다.
            stripeGateway.cancelSubscription(subscription.getStripeSubscriptionId());
            log.info("탈퇴로 구독을 취소했습니다 memberId={}, subscriptionId={}",
                    event.memberId(), subscription.getStripeSubscriptionId());
        }
    }
}
