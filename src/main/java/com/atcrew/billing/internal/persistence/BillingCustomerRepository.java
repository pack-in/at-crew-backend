package com.atcrew.billing.internal.persistence;

import com.atcrew.billing.internal.domain.BillingCustomer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BillingCustomerRepository extends JpaRepository<BillingCustomer, String> {

    Optional<BillingCustomer> findByStripeCustomerId(String stripeCustomerId);
}
