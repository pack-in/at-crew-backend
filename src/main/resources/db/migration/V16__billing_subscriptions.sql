-- 구독(프로 플랜) 상태 (docs/design/billing-module-design.md §2.1, §2.3).
-- member_id는 Member 참조지만 모듈 경계상 FK를 걸지 않는다(V1 FK 정책과 동일).
-- Stripe 관련 컬럼(stripe_*, last_event_*)은 스키마에만 두고 이번 단계에서는 채우지 않는다 —
-- Checkout/Webhook 연동은 후속 작업 범위다. 현재는 PlanService 게이팅 조회만 이 테이블을 읽는다.
-- plan(가입한 상품)과 status(결제 상태)를 분리한다 — 결제 실패 시 게이팅은 스타터로 떨어지지만
-- "프로 플랜 월간, 결제 실패" 표기를 유지해야 하기 때문이다(§2.1).

CREATE TABLE subscriptions (
    id                     VARCHAR(36) CHARACTER SET latin1 COLLATE latin1_bin NOT NULL,
    member_id              VARCHAR(36) CHARACTER SET latin1 COLLATE latin1_bin NOT NULL,
    plan                   VARCHAR(20)  NOT NULL DEFAULT 'STARTER',
    status                 VARCHAR(30)  NOT NULL DEFAULT 'NONE',
    stripe_customer_id     VARCHAR(64)  NULL,
    stripe_subscription_id VARCHAR(64)  NULL,
    current_period_end     DATETIME(6)  NULL,
    cancel_at_period_end   TINYINT(1)   NOT NULL DEFAULT 0,
    pending_plan           VARCHAR(20)  NULL,
    last_event_at          DATETIME(6)  NULL,
    last_event_id          VARCHAR(64)  NULL,
    version                BIGINT       NOT NULL DEFAULT 0,
    created_at             DATETIME(6)  NOT NULL,
    updated_at             DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_sub_member (member_id),
    UNIQUE KEY uk_sub_stripe_sub (stripe_subscription_id),
    KEY idx_sub_customer (stripe_customer_id)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
