package com.atcrew.billing.internal.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.domain.Persistable;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

/**
 * 웹훅 멱등 레코드. Stripe는 같은 이벤트를 재전송하므로 event id를 선삽입해 1회만 처리한다(D16).
 */
@Entity
@Table(name = "billing_webhook_events")
@EntityListeners(AuditingEntityListener.class)
public class WebhookEvent implements Persistable<String> {

    @Id
    @Column(name = "stripe_event_id", length = 64)
    private String stripeEventId;

    @Column(nullable = false, length = 80)
    private String type;

    @CreatedDate
    @Column(name = "received_at", nullable = false, updatable = false)
    private Instant receivedAt;

    @Column(name = "processed_at")
    private Instant processedAt;

    @Transient
    private boolean isNew = false;

    protected WebhookEvent() {
    }

    public static WebhookEvent received(String stripeEventId, String type) {
        WebhookEvent event = new WebhookEvent();
        event.stripeEventId = stripeEventId;
        event.type = type;
        event.isNew = true;
        return event;
    }

    public void markProcessed(Instant processedAt) {
        this.processedAt = processedAt;
    }

    @Override
    public String getId() { return stripeEventId; }
    public String getType() { return type; }
    public Instant getReceivedAt() { return receivedAt; }
    public Instant getProcessedAt() { return processedAt; }

    @Override
    public boolean isNew() { return isNew; }

    @PostPersist
    @PostLoad
    void markNotNew() {
        this.isNew = false;
    }
}
