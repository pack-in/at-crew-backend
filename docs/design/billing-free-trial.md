# 구독 30일 무료 체험

2026-09-27 FE 요청으로 도입했다. PR #241(dev)·#242(main)로 운영 배포를 마쳤다.
billing 모듈 전반은 [billing-module-design.md](billing-module-design.md), 프론트 계약은
[billing-frontend-integration.md](billing-frontend-integration.md) §1·§7에 있다. 이 문서는 체험 기능의 결정 근거와 검증 기록을 남긴다.

## 1. 요청

FE가 구독 Checkout 세션에 `subscription_data.trial_period_days: 30`을 넣어 달라고 요청했다. 월간이면
30일 뒤 1개월치, 연간이면 30일 뒤 1년치가 청구된다.

FE 예시 코드를 그대로 옮기지 않은 이유는 두 가지다.

- 예시는 **모든** 구독 Checkout에 조건 없이 체험을 붙인다. 이대로면 체험 중 취소하고 재구독하는 식으로 체험을 무한히 반복할 수 있다.
- 예시의 `payment_method_types: ['card']`는 Managed Payments(#239)에서 **금지된 파라미터**다. 넣으면 세션 생성이 실패한다.

## 2. 결정

| 항목 | 결정 |
|------|------|
| 대상 | **구독 이력이 전혀 없는 회원의 첫 구독에만** 붙인다. 취소 이력도 이력으로 본다 |
| 기간 | 월간·연간 공통 30일(`billing.trial-days`) |
| 체험 중 혜택 | 프로 혜택을 그대로 준다. Stripe `trialing` 상태는 `ACTIVE`로 매핑된다 |
| 결제수단 | 체험을 시작할 때 카드를 받는다(Checkout 기본값). 체험이 끝나면 자동 청구된다 |
| 안내 문구 | Stripe 결제창이 "30일 무료 / 그 이후 YYYY-MM-DD부터 매달(매년) 금액"을 자동으로 보여준다. BE가 문구를 따로 내려주지 않는다 |

**조건 없이 매번 체험을 주는 안(B안)은 기각했다.** 체험의 목적은 첫 전환이다. 조건 없는 체험은 사실상
무료 플랜을 하나 더 여는 것과 같다. 체험 대상 판정에 필요한 데이터는 이미 `billing_subscriptions`에
있어서, 첫 구독으로 제한해도 추가 비용이 작았다.

## 3. 구현

| 위치 | 역할 |
|------|------|
| `BillingServiceImpl.isTrialEligible` / `trialDays` | 체험 대상 판정. `SubscriptionRepository.existsByMemberId`로 판정하며, 기존 `(member_id, status)` 인덱스를 탄다. 비로그인은 카탈로그 표시용으로 대상이라고 본다 |
| `BillingServiceImpl.createCheckoutSession` | 구독 상품이면 ① 이 고객의 열린 Checkout 세션을 만료시키고 ② 판정한 체험 일수로 세션을 만든다 |
| `StripeGateway.expireOpenCheckoutSessions` | `status=open` 세션을 조회해 전부 `expire`한다. 그사이 이미 닫힌 세션은 로그만 남기고 건너뛴다 |
| `StripeGateway.checkoutParams` | 체험 일수가 0보다 크고 구독 상품일 때만 `subscription_data.trial_period_days`를 넣는다. 단건 결제 세션에 넣으면 Stripe가 400으로 거절하므로 막는다 |
| `CatalogItemInfo.trialDays` | 카탈로그 응답 필드. `cta`가 `AVAILABLE`이 아니면(이용 중인 플랜, 기업 계정) 항상 0이다 |
| `BillingProperties.trialDays` | `billing.trial-days`. 값이 없거나 0~730 범위를 벗어나면 **기동에 실패한다**. 체험을 끄려면 0을 명시한다 |

### 열린 세션을 만료시키는 이유

체험 대상 판정은 **세션을 만들 때** 끝난다. 그런데 Checkout 세션은 24시간 유효하다. 세션 A와 B를
미리 열어 둔 뒤, A로 체험을 시작하고 곧바로 취소한 다음 B를 결제하면 체험을 한 번 더 받는다.
구독 Checkout 직전에 열린 세션을 전부 닫아 두면 결제 가능한 세션은 회원당 항상 하나뿐이다.
이 우회는 `/code-review`에서 발견됐다.

그 밖에 검토한 대안은 웹훅에서 두 번째 체험을 즉시 끝내는 방식이다. 틈은 완전히 없어지지만, 웹훅
처리 중에 Stripe 쓰기 호출이 들어가고 회원 경험도 나빠진다. 그래서 악용이 실제로 관측될 때까지 보류했다.

## 4. 알려진 제약

- **탈퇴 후 재가입:** 탈퇴한 회원이 다시 가입하면 memberId가 새로 생겨 체험을 다시 받을 수 있다. 탈퇴 정책상 재가입을 허용하기 때문이다. 이를 막으려면 카드 fingerprint를 대조해야 하는데, 현재 규모에서는 도입하지 않는다.
- **결제 직후의 짧은 틈:** 결제를 마친 뒤 웹훅(`customer.subscription.created`)이 도착하기 전 수 초 사이에 새 세션을 열면, 그 세션에도 체험이 붙는다.
- **첫 결제 실패 이력:** 첫 결제가 실패해 `incomplete`로 끝난 구독도 이력으로 센다. 다만 체험을 도입한 뒤로는 새 구독이 결제 없이 `trialing`으로 시작하므로, 새로 해당하는 경우는 생기지 않는다.
- **체험 중 상태 구분 없음:** `GET /api/billing/me`는 "체험 중" 여부를 따로 내려주지 않는다. `status`는 `ACTIVE`이고 `currentPeriodEnd`는 첫 결제 시각이다. FE가 "체험 중" 표시를 원하면 추가 작업이 필요하다.

## 5. 검증 기록

| 일자 | 항목 | 결과 |
|------|------|------|
| 2026-09-27 | 단위·통합 테스트: 대상 판정, Checkout 경로의 체험 일수 전달과 세션 만료 순서, 파라미터 조립, 설정 검증 | billing과 모듈 경계 테스트 35건 통과, CI 통과 |
| 2026-09-27 | **배포 전 live 계정 실호출:** 운영 키로 월간·연간 Price에 `trial_period_days=30`과 `managed_payments`를 함께 넣어 세션 생성 | 오류 없이 생성됨. 결제창에 "30일 무료 / 그 이후 2026-10-27부터 매달(매년)"이 표시됨. 확인 후 세션을 만료시킴 |
| 2026-09-27 | 운영 배포 후 `GET /api/billing/catalog` | 월간·연간 모두 `trialDays: 30` |
| 2026-09-27 | **실카드 E2E(운영, 테스트 계정)** | 통과(사용자 수행) |
| 2026-09-27 | Figma `UI개편_설정`·`UI개편_전체UI`에서 체험 문구 검색 | 체험 문구 없음. "4주/8주 무료" 배지는 연간 할인 배지의 변형 시안이고 체험과 무관하다 |

실카드 E2E를 한 테스트 계정은 이제 구독 이력이 있으므로 이후 체험 대상이 아니다. 체험 흐름을 다시
검증하려면 새 계정을 써야 한다.

## 6. 운영

- **체험 끄기·기간 변경:** `billing.trial-days`를 바꾸고 재배포한다(0이면 체험 없음). 이미 시작된 체험에는 영향이 없다.
- **체험 결제창 수동 검증:** 서버에서 live 키로 세션을 만들어 결제창 표시를 확인할 수 있다. 결제는 하지 않는다. 회원과 연결되지 않은 세션이라, 결제하면 앳크루에는 반영되지 않고 청구만 된다. 확인이 끝나면 `POST /v1/checkout/sessions/{id}/expire`로 닫는다. 결제창 URL은 `#` 뒤 fragment까지 있어야 열린다.
