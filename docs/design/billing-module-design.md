# billing 모듈 설계 — Stripe 결제/구독

기획서 REQ-020·021, 정책 요금제-R01~R06·설정-R03·R11·구인구직-R02, 마이페이지_작가-R20을 구현한다.
플랜 문서는 `plans/260813-stripe-billing/`에 있다.

## 1. 결정 요약

| 항목 | 결정 | 비고 |
|------|------|------|
| PG | **Stripe 직결** | 기존 로드맵의 Polar 결정을 번복. 글로벌 확장 우선 |
| MoR | 없음 | 부가세·해외 VAT 신고, 환불 운영이 전부 사업자 몫 |
| 결제 UI | Stripe Checkout(호스팅) + Customer Portal | 자체 카드·구독관리 UI 없음 |
| 통화 | **USD 단일** | 기획서의 원화 표기는 무효. 금액은 센트 단위 정수 |
| 세금 | Stripe Tax 미적용 | 표시가 = 최종 청구액 |
| 상품 정의 | Stripe Dashboard 수기 생성 + Price ID 주입 | 코드 부트스트랩 없음 |
| 진실 소스 | **웹훅** | Checkout 복귀 URL에서는 상태를 바꾸지 않는다 |
| 이력 | entitlement 원장만 자체 보관 | 인보이스·영수증은 Stripe가 정본 |

## 2. 상품

| 상품 키 | 유형 | 청구가 | 정가 | 설명 |
|---------|------|--------|------|------|
| `PRO_MONTHLY` | 구독(월) | $5.99 | $11.99 | 얼리버드 50% |
| `PRO_YEARLY` | 구독(연) | $59.99 | $119.99 | 2개월 무료 |
| `TEAM_POSTING` | 단건 | $39.99 | — | 팀원 모집글 업로드권한(+끌어올리기 1회) |
| `BOOST` | 단건 | $7.99 | — | 끌어올리기 1회(48시간 상단 고정) |
| `JOB_POSTING` | 단건 | $99.99 | — | 구인글 업로드권한(기업 전용) |

정가는 표시 전용이라 Stripe에 만들지 않고 `billing.products.*.list-amount` 설정으로만 관리한다.
단건 권한은 만료되지 않으며 **게시가 성공했을 때만** 차감한다(요금제-R06).

## 3. 모듈 경계

```
recruit  ──consume()──▶  billing  ──findById()──▶  member
artwork  ──hasProPlan()─▶
company  ──implements──▶  billing.CompanyAccountPort
```

- `BillingService`가 유일한 공개 포트다. 다른 모듈은 구독 테이블·entitlement 테이블을 직접 보지 않는다.
- `Member`에 plan 필드를 두지 않는다 — 플랜의 소유권은 billing에 있다.
- **기업 계정 판별은 의존 역전**으로 처리한다. billing이 company를 직접 참조하면
  `billing → company → recruit → billing` 순환이 생기므로(Modulith 검증에서 실제로 검출됨),
  인터페이스 `CompanyAccountPort`는 billing이 소유하고 어댑터는 company에 둔다.
  company 모듈이 함께 뜨지 않는 컨텍스트(모듈 테스트)를 위해 billing에 "항상 개인 계정" 대체 빈을
  `@ConditionalOnMissingBean`으로 두고 기동 시 WARN을 남긴다.

## 4. API

| 메서드 | 경로 | 인증 | 설명 |
|--------|------|------|------|
| GET | `/api/billing/catalog` | 불필요 | 상품 5종의 가격·정가·통화·CTA 상태 |
| GET | `/api/billing/me` | 필요 | 플랜·구독 상태·다음 결제일·단건 보유 개수 |
| POST | `/api/billing/checkout-sessions` | 필요 | Checkout URL 발급 |
| POST | `/api/billing/portal-sessions` | 필요 | Customer Portal URL 발급 |
| POST | `/internal/billing/stripe/webhook` | 서명 검증 | Stripe 웹훅 수신구 |

CTA 상태는 `AVAILABLE`(구매 가능) / `CURRENT`(이용 중) / `CHANGE`(다른 주기로 변경) /
`UNAVAILABLE`(기업 계정의 프로 플랜)이며, 라벨 문구는 프론트가 매핑한다(요금제-R04).

## 5. 데이터 모델 (Flyway `V20__billing_schema.sql`)

병렬 워크트리와 번호가 겹치지 않도록 billing은 **V20 대역**을 쓴다(V10~V19는 비워 둔다).

| 테이블 | 역할 |
|--------|------|
| `billing_customers` | 회원 ↔ Stripe Customer 1:1. 탈퇴해도 삭제하지 않는다 |
| `billing_subscriptions` | 구독 미러. 구독 1건당 1행(취소 이력 포함), `stripe_updated_at`로 순서 역전 방어 |
| `billing_entitlement_balances` | 단건 상품 보유 개수. 낙관적 락으로 이중 차감 방지 |
| `billing_entitlement_ledgers` | 잔량 변동 원장(append-only). `PURCHASE`/`CONSUME`/`REFUND_REVOKE` |
| `billing_webhook_events` | 웹훅 멱등 테이블. event id 선삽입 |

## 6. 웹훅 처리

| 이벤트 | 처리 |
|--------|------|
| `checkout.session.completed` | `mode=payment`일 때만 지급. `TEAM_POSTING`은 `BOOST`도 함께 +1 |
| `customer.subscription.created/updated/deleted` | 구독 미러 갱신. Price ID로 플랜 판별, 기간 종료 시각은 **구독 아이템**에서 읽는다 |
| `invoice.payment_failed` | 유예 없이 `PAST_DUE` + 즉시 스타터 혜택 회수, `SubscriptionPaymentFailedEvent` 발행 |
| `invoice.payment_succeeded` | `PAST_DUE`였다면 `ACTIVE`로 복원 |
| `charge.refunded` | 전액 환불일 때만 권한 회수. 부분 환불은 지급 단위와 대응하지 않아 무시 |

- **멱등**: `billing_webhook_events`에 event id를 선삽입하고, 이미 있으면 그대로 종료한다.
- **재시도**: 처리 중 예외가 나면 트랜잭션이 통째로 롤백돼 5xx가 나가고 Stripe가 재전송한다.
- **순서 역전**: `Subscription.sync()`는 이미 반영된 이벤트 시각보다 오래된 이벤트를 무시한다.
- **환불 역추적**: 지급 원장에 PaymentIntent ID를 남겨 두고, 환불 시 그 ID로 회수 대상을 찾는다 —
  Stripe 메타데이터 전파에 의존하지 않는다.
- 이미 사용한 권한은 환불되어도 회수하지 않는다(잔량을 음수로 만들면 이후 구매분이 소급 차감된다).

## 7. 게이팅

| 대상 | 조건 | 위치 |
|------|------|------|
| 구인글 게시 | `JOB_POSTING` 1개 차감 | 최초 제출(DRAFT→PENDING) 시점. 반려 후 재제출은 미차감 |
| 팀원모집글 게시 | `TEAM_POSTING` 1개 차감 | 생성 성공 시 |
| 끌어올리기 | `BOOST` 1개 차감 | `boost()` 성공 후(쿨다운 위반이면 미차감) |
| 작품 업로드·휴지통 복구 | 스타터는 보유 4개 초과 불가 | `ArtworkServiceImpl.assertArtworkQuota` |
| 프로 구독 구매 | 기업 계정 불가 | `BillingServiceImpl.assertSubscribable` |

차감은 게시 트랜잭션 안에서 수행하므로 게시가 실패하면 차감도 함께 롤백된다.
공유 포트폴리오·다국어 노출 게이팅은 해당 기능이 아직 없어 `plans/260813-pro-plan-gating/`으로 분리했다.

## 8. 에러 코드

| 코드 | 상태 | 상황 |
|------|------|------|
| `ENTITLEMENT_REQUIRED` | 403 | 단건 상품 보유 없음 → 구매 유도 모달 |
| `PRO_PLAN_REQUIRED` | 403 | 프로 전용 기능 |
| `SUBSCRIPTION_NOT_ALLOWED` | 403 | 기업 계정의 구독 시도 |
| `ALREADY_SUBSCRIBED` | 409 | 같은 플랜 중복 구매 |
| `SUBSCRIPTION_CHANGE_VIA_PORTAL` | 409 | 월↔연 변경은 포털에서 |
| `CUSTOMER_NOT_FOUND` | 404 | 결제 이력 없는 회원의 포털 진입 |
| `PRICE_NOT_CONFIGURED` | 503 | Price ID 미설정 |
| `STRIPE_REQUEST_FAILED` | 502 | Stripe API 호출 실패 |
| `INVALID_PRODUCT` | 400 | 구독 상품에 단건 API 사용 |
| `STARTER_ARTWORK_LIMIT_EXCEEDED` | 403 | 스타터 작품 4개 초과(artwork 모듈 코드) |

## 9. 운영

- **로컬**: `stripe listen --forward-to localhost:8080/internal/billing/stripe/webhook`
  실행 시 출력되는 `whsec_`를 `STRIPE_WEBHOOK_SECRET`에 넣는다.
- **prod**: Stripe Dashboard에 `https://api.at-crew.com/internal/billing/stripe/webhook`를 등록한다.
- 키가 없어도 애플리케이션은 기동되며, 결제 API를 호출할 때만 실패한다.
- `.env`는 실행 환경이 환경변수로 주입한다. `application.yml`에 `spring.config.import`로 `.env`를
  직접 읽게 하면 테스트에서 Testcontainer 접속 정보가 어긋나 검색·이벤트 레지스트리 테스트가 깨진다.

## 10. 테스트

- 웹훅 픽스처 기반 통합 테스트(`BillingModuleTests`) — 지급·멱등·구독 생성/실패/복원/취소·순서 역전·환불·동시 차감.
  실제 Stripe 서버는 호출하지 않는다.
- Stripe test clock을 쓰는 sandbox 실호출 테스트는 `@Tag("stripe-sandbox")`로 분리하고,
  키가 없으면 자동으로 건너뛴다. CI에는 sandbox 키를 주입하지 않는다.
- 결제와 무관한 recruit·search 테스트는 `BillingTestSupport.grantAllPostingProducts()`로 사전 조건을 만든다.
