package com.atcrew.billing.internal.persistence;

import com.atcrew.billing.SubscriptionStatus;
import com.atcrew.billing.internal.domain.Subscription;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface SubscriptionRepository extends JpaRepository<Subscription, String> {

    Optional<Subscription> findByStripeSubscriptionId(String stripeSubscriptionId);

    /**
     * 회원의 현재 구독. 취소 이력이 함께 남으므로 살아 있는 상태(ACTIVE·PAST_DUE)만 조회한다.
     * 정상 흐름에서는 0~1건이며, 여러 건이면 가장 최근 이벤트를 기준으로 삼는다.
     */
    List<Subscription> findByMemberIdAndStatusInOrderByStripeUpdatedAtDesc(
            String memberId, Collection<SubscriptionStatus> statuses);
}
