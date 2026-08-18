package com.atcrew.billing.internal.persistence;

import com.atcrew.billing.internal.domain.WebhookEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WebhookEventRepository extends JpaRepository<WebhookEvent, String> {
}
