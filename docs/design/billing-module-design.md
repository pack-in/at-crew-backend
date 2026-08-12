# billing 모듈 설계

> 작성일: 2026-08-10
> 상태: 설계안 (구현 전)
> 범위: 프로 플랜(월간/연간) 구독 Checkout·Customer Portal·Webhook 연동, 플랜 게이팅 조회 API
> 범위 밖: 단건 게시 상품(팀원모집글 업로드권한·끌어올리기) — 이번 마일스톤에 recruit이 없어 소비처가 없다. 결제 실패 이메일 발송은 `notification` 모듈에 위임
> ⚠️ **PG 변경**: 정본 기획서(REQ-021, 요금제-R02~R06, 설정-R01~R03)와 `docs/roadmap.md` 5번 항목은 전부 **Polar**로 확정 기재돼 있다. 이번 마일스톤은 사용자 결정에 따라 **Stripe로 대체**한다. §7에 근거와 문서 정정 필요 항목을 정리했다.

---

## 0. 설계 요약 (TL;DR)

| 항목 | 결정 |
|---|---|
| 모듈 경계 | 신규 `com.atcrew.billing` 모듈. member에 얹지 않는다 — Stripe 연동은 `Subscription`·`WebhookEvent` 두 애그리게잇을 가진 독립 도메인이고, member에 넣으면 결제 인프라를 흡수하게 된다 |
| 의존 방향 | 단방향 — `billing`은 어느 모듈도 참조하지 않는다. 다른 모듈은 `PlanService` 공개 인터페이스로만 플랜을 조회한다 |
| 플랜 캐시 | **Member에 플랜 컬럼을 두지 않는다.** 이중 진실 소스가 되고 웹훅 반영 실패 시 영구 불일치가 남는다. `subscriptions` 테이블 PK(member_id) 조회 1회로 충분 |
| plan/status 분리 | `plan`(STARTER/PRO_MONTHLY/PRO_YEARLY)과 `status`(ACTIVE/PAST_DUE/...)를 별개 컬럼으로 둔다 — 결제 실패 시 "무슨 플랜이 실패했는지" 표기(설정-R03 "프로 플랜 월간, 결제 실패")를 유지하면서 게이팅은 스타터로 즉시 전환하기 위함(§4.2) |
| 웹훅 정합성 | 서명 검증 + `event_id` 멱등 + **재조회(re-fetch) 기반 갱신**으로 순서 역전 방어(§4.3) — `ArtworkSearchIndexer`가 이미 쓰는 검증된 패턴 |
| PG | Polar → **Stripe로 변경**(§7). VAT 처리 주체가 앳크루로 이동한다는 점이 가장 큰 실무 영향 |

---

## 1. 모듈 분리 결정 근거

Stripe SDK 호출·웹훅 수신·구독 상태머신은 도메인 규칙(플랜 전환·결제 실패·게이팅)을 가진 독립 바운디드 컨텍스트다. `member`나 `portfolio`에 넣으면 각 모듈이 결제 상태 전이 로직까지 알아야 한다.

게이팅 소비는 공개 인터페이스 하나로 좁힌다:

```java
// com.atcrew.billing.PlanService
public interface PlanService {
    PlanInfo getPlan(String memberId);   // row 없으면 STARTER 기본값 반환 — null 없이 항상 안전
    boolean isPro(String memberId);
    void assertPro(String memberId);     // 아니면 BillingException(PRO_PLAN_REQUIRED)
    int artworkLimit(String memberId);   // STARTER=4, PRO=Integer.MAX_VALUE
}
```

탈퇴 시 구독 취소는 `member`가 이미 발행하는 `MemberDeactivatedEvent`를 `billing`이 구독해서 처리한다 — `member → billing` 역방향 의존이 생기지 않는다.

---

## 2. 도메인 모델

### 2.1 Subscription (테이블: `subscriptions`)

```java
@Entity
@Table(name = "subscriptions")
public class Subscription implements Persistable<String> {

    @Id
    private String id;                     // UUIDv7

    private String memberId;               // UNIQUE — 회원당 구독 레코드 1개

    @Enumerated(EnumType.STRING)
    private Plan plan;                     // STARTER | PRO_MONTHLY | PRO_YEARLY

    @Enumerated(EnumType.STRING)
    private SubscriptionStatus status;     // NONE | ACTIVE | PAST_DUE | CANCELED | INCOMPLETE | TRIALING | UNPAID

    private String stripeCustomerId;
    private String stripeSubscriptionId;   // UNIQUE, nullable(스타터는 없을 수 있음)

    private Instant currentPeriodEnd;
    private boolean cancelAtPeriodEnd;
    private Plan pendingPlan;              // 월↔연 주기 변경 예약(설정-R02)

    private Instant lastEventAt;           // Stripe event.created — 순서 역전 방어용
    private String lastEventId;

    @Version
    private Long version;

    private Instant createdAt;
    private Instant updatedAt;

    @Transient
    private boolean isNew;
}
```

**`plan`과 `status`를 분리하는 이유**: 결제 실패 시 즉시 스타터로 전환해야 하는데(설정-R03) `plan`을 `STARTER`로 덮어버리면 "무슨 플랜이 실패했는지"가 사라져 UI가 요구하는 `"프로 플랜 월간, 결제 실패"` 문구를 만들 수 없다. 대신:

```java
public boolean isPro() {
    return plan != Plan.STARTER && (status == SubscriptionStatus.ACTIVE || status == SubscriptionStatus.TRIALING);
}
```

로 판정하면 실패 시 `plan=PRO_MONTHLY, status=PAST_DUE` → 게이팅은 스타터로 떨어지고, 표기는 "프로 월간, 결제 실패"가 **동시에** 성립한다.

### 2.2 WebhookEvent (테이블: `billing_webhook_events`)

```
event_id(PK, Stripe evt_...), event_type, stripe_created_at, received_at, processed_at,
status(RECEIVED|PROCESSED|IGNORED|FAILED), fail_reason
```

멱등성 체크와 관측(어떤 이벤트가 언제 왔는지)을 겸한다.

### 2.3 Flyway — V16

```sql
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

CREATE TABLE billing_webhook_events (
    event_id          VARCHAR(64) CHARACTER SET latin1 COLLATE latin1_bin NOT NULL,
    event_type        VARCHAR(64)  NOT NULL,
    stripe_created_at DATETIME(6)  NOT NULL,
    received_at       DATETIME(6)  NOT NULL,
    processed_at       DATETIME(6)  NULL,
    status             VARCHAR(20)  NOT NULL,
    fail_reason        VARCHAR(500) NULL,
    PRIMARY KEY (event_id),
    KEY idx_bwe_received (received_at),
    KEY idx_bwe_status (status, received_at)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

---

## 3. REST API

| 메서드 | 경로 | 인증 | 요청 | 응답 | 에러 |
|---|---|---|---|---|---|
| GET | `/api/billing/me` | 필요 | — | `{plan, status, currentPeriodEnd, cancelAtPeriodEnd, pendingPlan}` | 401 |
| GET | `/api/billing/plans` | 불필요 | — | 요금제 페이지용 상품 목록(가격은 서버 상수, Stripe Price ID 매핑) | — |
| POST | `/api/billing/checkout-session` | 필요 | `{plan: PRO_MONTHLY\|PRO_YEARLY, successUrl?, cancelUrl?}` | 200 `{checkoutUrl}` | 409 `ALREADY_SUBSCRIBED`, 502 `STRIPE_ERROR`, 400 `INVALID_RETURN_URL` |
| POST | `/api/billing/portal-session` | 필요 | — | 200 `{portalUrl}` | 409 `NO_STRIPE_CUSTOMER` |
| POST | `/internal/billing/stripe/webhook` | Stripe-Signature 헤더 검증 | raw JSON | 200(항상, 서명 실패만 400) | 400 `INVALID_WEBHOOK_SIGNATURE` |

- `successUrl`/`cancelUrl`은 `cors.allowed-origins` 기준 허용 목록 검증을 반드시 거친다 — 오픈 리다이렉트 방지.
- 웹훅은 raw body 그대로 받는다 — `@RequestBody String`으로 받아야 서명 검증이 성립한다(DTO 바인딩 금지).
- `SecurityConfig`에 웹훅 경로 permitAll 추가.

에러 코드(`BillingErrorCode`, `billing/internal/exception/`):
```
PRO_PLAN_REQUIRED(403), ALREADY_SUBSCRIBED(409), NO_STRIPE_CUSTOMER(409),
STRIPE_ERROR(502), INVALID_RETURN_URL(400), INVALID_WEBHOOK_SIGNATURE(400)
```

---

## 4. 핵심 로직

### 4.1 Checkout / Portal

```
POST /api/billing/checkout-session
1. 이미 ACTIVE/TRIALING 프로 구독이 있으면 409 ALREADY_SUBSCRIBED (Portal에서 주기 변경 유도)
2. Stripe Customer가 없으면 생성(email=member.loginEmail) 후 subscriptions.stripe_customer_id 저장
3. Checkout Session 생성 — client_reference_id = memberId, metadata.memberId = memberId (매칭 이중화)
4. 200 {checkoutUrl}

POST /api/billing/portal-session
1. stripe_customer_id 없으면 409 NO_STRIPE_CUSTOMER
2. Billing Portal Session 생성 → 200 {portalUrl}
```

### 4.2 Stripe 웹훅 — 멱등성 · 서명 · 순서 역전

```java
@PostMapping(value = "/internal/billing/stripe/webhook", consumes = MediaType.APPLICATION_JSON_VALUE)
public ResponseEntity<Void> handle(@RequestHeader("Stripe-Signature") String sigHeader,
                                    @RequestBody String rawPayload) { ... }
```

1. **서명 검증**: `Webhook.constructEvent(rawPayload, sigHeader, endpointSecret)`. 실패 시 400.
2. **멱등성**: `billing_webhook_events`에 `event_id`(PK)를 `saveAndFlush`로 선삽입 — 유니크 위반이면 이미 처리된 이벤트이므로 즉시 200 반환(`MemberServiceImpl.register()`가 동일하게 `saveAndFlush`로 유니크 위반을 동기적으로 잡는 패턴).
3. **순서 역전 방어(2중)**:
   - **주 방어— 재조회**: 이벤트 페이로드 값을 신뢰하지 않고 `Subscription.retrieve(subscriptionId)`로 Stripe에서 현재 상태를 다시 읽어 반영한다. 어떤 순서로 이벤트가 오든 최종 상태는 항상 "조회 시점의 진실"로 수렴한다. `ArtworkSearchIndexer`가 이미 이 패턴(재조회 기반 멱등 색인)을 쓰고 있다.
   - **보조 방어**: `subscriptions.last_event_at`에 `event.created`를 저장하고 더 오래된 이벤트는 갱신을 건너뛴다(재조회 API 호출도 절약).
4. **처리 대상 이벤트**:

| 이벤트 | 처리 |
|---|---|
| `checkout.session.completed` | `client_reference_id`로 매칭, customerId/subscriptionId 저장 후 재조회 반영 |
| `customer.subscription.created`/`.updated` | 재조회 → plan/status/currentPeriodEnd/cancelAtPeriodEnd/pendingPlan 갱신 |
| `customer.subscription.deleted` | plan=STARTER, status=CANCELED |
| `invoice.payment_failed` | status=PAST_DUE(plan 유지) + `notificationService.sendPaymentFailed(...)`(설정-R03) |
| `invoice.paid`/`invoice.payment_succeeded` | 재조회 → status=ACTIVE 복원 |

5. **항상 200**(서명 실패 제외). 처리 중 예외는 `billing_webhook_events.status=FAILED` + `fail_reason` 기록 후에도 200을 반환한다 — Stripe의 재시도 폭주를 막고, 실패분은 별도 배치/알림으로 회수한다.

### 4.3 탈퇴 시 구독 취소

```java
// billing/internal/application/BillingMemberEventListener.java
@ApplicationModuleListener
void on(MemberDeactivatedEvent event) {
    // 1) Subscription.cancel(immediately=true)  2) Customer.delete()
    // 실패 시 예외를 던져 event_publication에 미완료로 남긴다
}
```

⚠️ `spring.modulith.events.republish-outstanding-events-on-restart: true`는 현재 `application-prod.yml`에만 설정돼 있다. local/test 환경에서는 미완료 이벤트가 재기동 시 자동 재발행되지 않으므로 테스트 시 유의한다.

### 4.4 스타터 4개 제한 소비처

`artwork` 모듈이 `PlanService.artworkLimit(memberId)`를 업로드·휴지통 복구 시점에 조회한다. 상세 로직은 `docs/design/portfolio-module-design.md`가 아니라 이번 마일스톤의 artwork 계약 변경 작업(§계획 W5)에서 구현한다 — billing은 조회 인터페이스만 제공한다.

---

## 5. 가격 정책 (요금제-R03~R05)

| 상품 | 가격 | 비고 |
|---|---|---|
| 스타터 | 0원/월, 기본 적용 | 비로그인 요금제 페이지에서만 카드 노출 |
| 프로 월간 | 7,500원/월(정가 15,000원) | 로그인 후 설정 탭에는 프로 카드만 노출 |
| 프로 연간 | 75,000원/년(정가 150,000원, 2개월 무료) | 동일 |

Stripe Price는 `application.yml`에 상수로 등록(`billing.stripe.price.pro-monthly`, `billing.stripe.price.pro-yearly`) — 이번 스코프는 단건 상품이 없어 Price 2개만 관리하면 된다.

---

## 6. 작업 분할 참고

Flyway V16, `billing` 코어(스키마·Stripe 어댑터·Checkout/Portal·웹훅)와 `PlanService` 공개 + 탈퇴 이벤트 구독을 별도 PR로 나눈다. 상세는 상위 실행 계획(`.claude/plans/at-crew-calm-zephyr.md`) §5 참조. Stripe 클라이언트는 인터페이스로 감싸 테스트에서 스텁을 주입할 수 있게 한다(`StripeClient` 포트 + `LiveStripeClient`/`StubStripeClient`).

로컬 웹훅 검증에는 Stripe CLI가 필요하다(`brew install stripe/stripe-cli/stripe`, 현재 미설치): `stripe listen --forward-to localhost:8080/internal/billing/stripe/webhook`.

---

## 7. PG 결정 변경 — Polar → Stripe

### 7.1 정본과의 충돌

- `docs/AT-CREW_서비스기획서_전체_20260728.xlsx` REQ-021, 요금제-R02·R06, 설정-R01~R03, 설정-R11(탈퇴 시 "Polar Delete Customer API")이 전부 Polar를 전제로 작성돼 있다.
- `docs/roadmap.md` 5번 항목 "PG 확정(2026-07-31): Polar"도 동일.

이번 마일스톤은 **사용자 결정에 따라 Stripe로 진행**하며, 위 두 문서는 이 설계 문서 반영과 함께 정정한다(`docs/roadmap.md` 갱신 별도 커밋).

### 7.2 실무 영향 — VAT/인보이스

Polar는 Merchant of Record(MoR)로 부가세·세금계산서를 대행한다. **Stripe는 MoR이 아니다** — 표준 Stripe Checkout/Billing만으로는 부가세 원천징수·인보이스 발행 주체가 앳크루(가맹점)가 된다. 옵션:

1. 가격에 VAT를 포함해 표기하고 별도 정산 없이 매출로 처리(간이 접근, 사업자 규모에 따라 세무 검토 필요)
2. Stripe Tax를 활성화해 자동 계산·징수(별도 구성·비용 발생)

기획서의 "VAT 별도 표기"(요금제-R04) 문구는 Polar 전제였으므로, Stripe로 갈 경우 표기 방식 자체를 재검토해야 한다. **이번 마일스톤 코드에서는 1번(가격 내 포함 표기, VAT 별도 문구 제거)으로 잠정 진행**하고 최종 확정은 사업자 세무 검토 이후로 남긴다.

### 7.3 대체 매핑

| Polar 개념(기획서 원문) | Stripe 대응 |
|---|---|
| Polar Checkout | Stripe Checkout Session |
| Polar 결제 내역·고객 포털 | Stripe Customer Portal |
| Polar 웹훅 | Stripe 웹훅(`checkout.session.completed` 등) |
| Polar Delete Customer API(탈퇴) | `Subscription.cancel()` + `Customer.delete()` |
