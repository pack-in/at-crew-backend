-- 결제/구독 (plans/260813-stripe-billing) — Stripe 직결.
-- 병렬 워크트리와 마이그레이션 번호가 겹치지 않도록 billing은 V20 대역을 예약해 사용한다(V10~V19는 비워 둔다).
-- member_id는 Member 참조지만 모듈 경계상 FK를 걸지 않는다(다른 모듈 소유).
-- 금액·인보이스·영수증의 정본은 Stripe이며, 여기에는 미러(구독 상태)와 자체 원장(entitlement)만 둔다.

-- 회원 ↔ Stripe Customer 1:1 매핑. 탈퇴해도 Customer는 삭제하지 않으므로 행을 유지한다.
CREATE TABLE billing_customers (
    member_id           VARCHAR(36) CHARACTER SET latin1 COLLATE latin1_bin NOT NULL,
    stripe_customer_id  VARCHAR(64) CHARACTER SET latin1 COLLATE latin1_bin NOT NULL,
    created_at          DATETIME NOT NULL,
    PRIMARY KEY (member_id),
    UNIQUE KEY uk_billing_customers_stripe_id (stripe_customer_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Stripe 구독 미러. 구독 하나당 한 행이며(취소 이력도 남는다), 현재 구독은 status가 ACTIVE/PAST_DUE인 행이다.
-- stripe_updated_at은 Stripe 이벤트의 시각으로, 웹훅 순서가 뒤바뀌어 도착했을 때 과거 이벤트를 무시하는 기준이다.
CREATE TABLE billing_subscriptions (
    id                      VARCHAR(36) CHARACTER SET latin1 COLLATE latin1_bin NOT NULL,
    member_id               VARCHAR(36) CHARACTER SET latin1 COLLATE latin1_bin NOT NULL,
    stripe_subscription_id  VARCHAR(64) CHARACTER SET latin1 COLLATE latin1_bin NOT NULL,
    plan                    VARCHAR(20) NOT NULL,
    status                  VARCHAR(20) NOT NULL,
    current_period_end      DATETIME NULL,
    cancel_at_period_end    TINYINT(1) NOT NULL DEFAULT 0,
    stripe_updated_at       DATETIME NOT NULL,
    version                 BIGINT NULL,
    created_at              DATETIME NOT NULL,
    updated_at              DATETIME NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_billing_subscriptions_stripe_id (stripe_subscription_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 현재 구독 조회(member_id + status) 전용 인덱스
CREATE INDEX idx_billing_subscriptions_member_status
    ON billing_subscriptions (member_id, status);

-- 단건 게시 상품 보유 개수. 구매 +1 / 게시 성공 -1 / 환불 회수 -1이며 만료되지 않는다(요금제-R06).
-- 동시 게시로 인한 이중 차감은 version(낙관적 락)으로 막는다.
CREATE TABLE billing_entitlement_balances (
    member_id   VARCHAR(36) CHARACTER SET latin1 COLLATE latin1_bin NOT NULL,
    product     VARCHAR(30) NOT NULL,
    quantity    INT NOT NULL DEFAULT 0,
    version     BIGINT NULL,
    PRIMARY KEY (member_id, product)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 잔량 변동 원장(append-only). 환불 회수·미차감 정책 검증·분쟁 대응의 근거다.
CREATE TABLE billing_entitlement_ledgers (
    id              VARCHAR(36) CHARACTER SET latin1 COLLATE latin1_bin NOT NULL,
    member_id       VARCHAR(36) CHARACTER SET latin1 COLLATE latin1_bin NOT NULL,
    product         VARCHAR(30) NOT NULL,
    delta           INT NOT NULL,
    reason          VARCHAR(20) NOT NULL,
    stripe_event_id VARCHAR(64) CHARACTER SET latin1 COLLATE latin1_bin NULL,
    ref_id          VARCHAR(64) CHARACTER SET latin1 COLLATE latin1_bin NULL,
    created_at      DATETIME NOT NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_billing_entitlement_ledgers_member
    ON billing_entitlement_ledgers (member_id, created_at);

-- 웹훅 멱등 테이블. Stripe는 같은 이벤트를 재전송하므로 event id 선삽입으로 1회만 처리한다.
-- processed_at이 비어 있으면 처리 중 실패한 이벤트로, Stripe 재시도 또는 대시보드 재발송으로 복구한다.
CREATE TABLE billing_webhook_events (
    stripe_event_id VARCHAR(64) CHARACTER SET latin1 COLLATE latin1_bin NOT NULL,
    type            VARCHAR(80) NOT NULL,
    received_at     DATETIME NOT NULL,
    processed_at    DATETIME NULL,
    PRIMARY KEY (stripe_event_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
