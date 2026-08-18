# 결제/구독 프론트 연동 요청서

백엔드는 `https://api.at-crew.com`, 프론트는 `https://at-crew.com` 기준이다.
백엔드 설계는 [billing-module-design.md](billing-module-design.md)에 있다.

## 0. 먼저 알아야 할 것

- **결제 화면을 직접 만들지 않는다.** 카드 입력은 Stripe Checkout(호스팅), 구독 취소·주기 변경·결제수단
  변경·영수증 조회는 Stripe Customer Portal(호스팅)이 전부 처리한다. Stripe.js·publishable key도 필요 없다.
- **통화는 USD 단일이다.** 기획서 요금제 화면의 원화 표기("0원/월", "7,500원/월 정가 15,000원", "VAT 별도")는
  전부 무효다. 금액은 백엔드가 내려주는 값을 쓴다.
- **금액은 센트 단위 정수**다. `599` → `$5.99`. 표시할 때 100으로 나눈다.
- **결제 완료는 웹훅 기준**이라 Checkout에서 돌아온 직후에는 아직 반영 전일 수 있다. 폴링이 필요하다.

## 1. 요금제 페이지 (`PLAN-P01`, 비로그인 포함)

```
GET /api/billing/catalog
```

```json
{
  "data": [
    {"product": "PRO_MONTHLY", "amount": 599, "listAmount": 1199, "currency": "USD", "cta": "AVAILABLE"},
    {"product": "PRO_YEARLY", "amount": 5999, "listAmount": 11999, "currency": "USD", "cta": "AVAILABLE"},
    {"product": "TEAM_POSTING", "amount": 3999, "listAmount": null, "currency": "USD", "cta": "AVAILABLE"},
    {"product": "BOOST", "amount": 799, "listAmount": null, "currency": "USD", "cta": "AVAILABLE"},
    {"product": "JOB_POSTING", "amount": 9999, "listAmount": null, "currency": "USD", "cta": "AVAILABLE"}
  ]
}
```

- `listAmount`가 있으면 취소선 정가로 표기한다.
- 로그인 상태로 호출하면 현재 구독이 반영된다. `cta` 매핑:

| cta | 버튼 |
|-----|------|
| `AVAILABLE` | "시작하기" / "연간 플랜 시작하기" / "구매하기" |
| `CURRENT` | "이용 중인 플랜" (비활성, 취소선 미노출) |
| `CHANGE` | "월간 플랜으로 변경하기" / "연간 플랜으로 변경하기" |
| `UNAVAILABLE` | 기업 계정 — 프로 카드 구매 불가 처리 |

- 혜택 문구·"2개월 무료" 배지 같은 마케팅 카피는 프론트가 갖는다.
- 스타터 카드는 비로그인 요금제 페이지에서만 노출하고, 설정 탭에는 프로·단건 카드만 노출한다(요금제-R03).

## 2. 결제 진입

```
POST /api/billing/checkout-sessions
{ "product": "PRO_MONTHLY" }
→ { "data": { "checkoutUrl": "https://checkout.stripe.com/..." } }
```

받은 URL로 이동시킨다(`window.location.href`). 새 창·iframe은 권장하지 않는다.

주요 실패 응답:

| 상태 | 코드 | 처리 |
|------|------|------|
| 403 | `SUBSCRIPTION_NOT_ALLOWED` | 기업 계정 안내 |
| 409 | `ALREADY_SUBSCRIBED` | 이미 이용 중 |
| 409 | `SUBSCRIPTION_CHANGE_VIA_PORTAL` | 포털로 유도(§4) |
| 503 | `PRICE_NOT_CONFIGURED` | 상품 준비 중 안내 |

## 3. 복귀 페이지 2개 (프론트에서 신규 제작)

- `/billing/success?session_id={CHECKOUT_SESSION_ID}`
- `/billing/cancel`

success 페이지는 **결제 완료를 즉시 확정하지 말고** 아래를 1초 간격으로 최대 10초 폴링한다.

```
GET /api/billing/me
→ { "data": {
      "plan": "PRO_MONTHLY",            // STARTER | PRO_MONTHLY | PRO_YEARLY
      "status": "ACTIVE",               // ACTIVE | PAST_DUE | null(구독 이력 없음·취소 완료)
      "currentPeriodEnd": "2026-09-13T00:00:00Z",
      "cancelAtPeriodEnd": false,
      "balances": { "TEAM_POSTING": 1, "BOOST": 1, "JOB_POSTING": 0 }
  }}
```

플랜 또는 보유 개수가 기대대로 바뀌면 완료 처리하고, 타임아웃이면 "처리 중" 안내를 노출한다
(결제는 이미 성공했고 반영만 늦는 상황이다).

## 4. 구독 관리 · 결제 내역

```
POST /api/billing/portal-sessions
→ { "data": { "portalUrl": "https://billing.stripe.com/..." } }
```

- 설정 > 요금제 및 결제 탭의 **[결제 내역 바로가기]**, **구독 취소**, **월↔연 변경**, **결제수단 변경**은
  전부 이 URL로 보낸다.
- 포털에서 돌아오는 주소는 백엔드가 `/settings/billing`으로 지정한다. 프론트 라우트가 다르면 알려주면 바꾼다.
- 결제 이력이 전혀 없는 회원은 404 `CUSTOMER_NOT_FOUND` — 버튼을 숨기거나 안내한다.

## 5. 게이팅 모달 (403 기반)

버튼·카드는 **항상 노출**하고, 눌렀을 때 오는 403으로 모달을 띄운다(마이페이지_작가-R20, 구인구직-R02).
보유 개수로 프론트가 미리 숨기지 않는다.

| API | 코드 | 모달 |
|-----|------|------|
| 팀원모집글 생성 / 구인글 제출 / 끌어올리기 | `ENTITLEMENT_REQUIRED` | "게시 권한이 필요해요" + [취소]/[요금제 바로가기] |
| 작품 업로드 · 휴지통 복구 | `STARTER_ARTWORK_LIMIT_EXCEEDED` | 스타터 4개 제한 안내 + 프로 전환 유도 |

단건 상품 구매 전에는 환불 안내 모달이 필수다(요금제-R06): "미사용 권한은 환불 가능, 사용 후 환불 불가",
[취소]/[구매하기] → §2의 Checkout으로 이동.

## 6. 결제 실패 배너

`GET /api/billing/me`의 `status`가 `PAST_DUE`면 설정 탭 상단에 `프로 플랜 월간, 결제 실패` 형식으로
노출하고 [결제 내역 바로가기](§4)를 함께 둔다(설정-R03). 이때 `plan`은 이미 `STARTER`로 내려오므로
프로 기능은 잠긴다 — 유예 기간은 없다.

## 7. 알려진 제약

- 환불 신청 화면은 없다. 환불은 문의를 받아 운영자가 Stripe 대시보드에서 처리하고, 권한 회수는
  웹훅으로 자동 반영된다.
- 구인글 업로드 권한은 기업 전용 상품이지만 카탈로그에서는 모든 계정에 노출된다 —
  기업 인증·게시 단계에서 걸린다.
