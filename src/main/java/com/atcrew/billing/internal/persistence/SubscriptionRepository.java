package com.atcrew.billing.internal.persistence;

import com.atcrew.billing.internal.domain.Subscription;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SubscriptionRepository extends JpaRepository<Subscription, String> {

    Optional<Subscription> findByMemberId(String memberId);
}
