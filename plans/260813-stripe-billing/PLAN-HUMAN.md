# Stripe 결제/구독 모듈 — 사람 작업

## 배경

Stripe sandbox 계정(`acct_1U118N0AYNC5GQth`)은 발급 완료됐다. 여기서는 시크릿 발급·대시보드 설정·실계정 심사 등
코드로 대신할 수 없는 작업만 다룬다. 결정 사항과 코드 작업은 `PLAN-AGENT.md`에 있다.

## 금지 범위

- 라이브(live) 모드 키를 `.env`에 넣지 않는다. 이번 스코프는 sandbox(test mode) 전용이다.
- 실키를 커밋하지 않는다. `.env`는 gitignore돼 있고 gitleaks pre-commit이 걸려 있지만, `.env.example`에는 값이 아닌 양식만 남긴다.

## 검증

- 로컬에서 `stripe listen` 연결 상태로 Checkout 결제를 완료하면 웹훅이 도착하고 플랜/보유 개수가 반영된다.
- 앱을 Stripe 키 없이 기동해도 부팅에 실패하지 않는다.

---

## PH-01. 워크트리에 `.env` 심볼릭 링크 연결

`.env`는 gitignore 대상이라 git worktree 생성 시 복사되지 않는다. 현재 원본 레포에만 있고
orca 워크트리 3개(torpedo·turban·mvp)에는 없다. 링크로 연결하면 키를 한 곳에서만 관리하게 된다.

```
ln -s /Users/danhan/Development/at-crew-backend/.env \
      /Users/danhan/orca/workspaces/at-crew-backend/torpedo/.env
```

- [ ] torpedo 워크트리에 링크 생성
- [ ] 필요하면 turban·mvp 워크트리에도 동일하게 연결

## PH-02. Stripe Dashboard에서 Product·Price 생성

depends on: PH-01

[Dashboard](https://dashboard.stripe.com/acct_1U118N0AYNC5GQth/test/dashboard) **test mode**에서 상품 5종을 만든다.
통화는 전부 **USD**, 세금은 **Stripe Tax 미사용**(가격 = 최종 청구액, `tax_behavior: inclusive`).

| 상품 | 유형 | 가격 |
|------|------|------|
| 프로 월간 | Recurring / monthly | $5.99 |
| 프로 연간 | Recurring / yearly | $59.99 |
| 팀원모집글 업로드권한 | One-time | $39.99 |
| 끌어올리기 | One-time | $7.99 |
| 구인글 업로드권한 | One-time | $99.99 |

정가($11.99 / $119.99) 취소선은 Stripe에 만들지 않는다 — 표시 전용이라 백엔드 설정값으로 관리한다.

- [ ] 상품 5종 생성
- [ ] 각 상품의 `price_...` ID 5개 확보

## PH-03. `.env`에 Stripe 키 붙여넣기

depends on: PH-02

**원본 레포** `/Users/danhan/Development/at-crew-backend/.env` 하단에 아래를 추가한다(PH-01 링크로 모든 워크트리에 반영됨).
`STRIPE_WEBHOOK_SECRET`은 PH-04에서 얻으므로 지금은 비워도 된다.

```
# --- Stripe (test mode) ---
STRIPE_SECRET_KEY=sk_test_
STRIPE_PUBLISHABLE_KEY=pk_test_
STRIPE_WEBHOOK_SECRET=whsec_
STRIPE_PRICE_PRO_MONTHLY=price_
STRIPE_PRICE_PRO_YEARLY=price_
STRIPE_PRICE_TEAM_POSTING=price_
STRIPE_PRICE_BOOST=price_
STRIPE_PRICE_JOB_POSTING=price_
FRONTEND_BASE_URL=http://localhost:3000
```

Secret key와 Publishable key는 Dashboard > Developers > API keys에서 복사한다.
Publishable key는 현재 구성(호스팅 Checkout)에서 백엔드가 쓰지 않지만, 프론트 전달용으로 함께 보관한다.

- [ ] Secret key 입력
- [ ] Publishable key 입력
- [ ] price ID 5개 입력

## PH-04. Stripe CLI 설치 및 웹훅 시크릿 확보

depends on: PH-03

```
brew install stripe/stripe-cli/stripe
stripe login
stripe listen --forward-to localhost:8080/internal/billing/stripe/webhook
```

`stripe listen` 실행 시 출력되는 `whsec_...`를 `.env`의 `STRIPE_WEBHOOK_SECRET`에 넣는다.
이 값은 세션마다 바뀔 수 있으므로 검증할 때마다 확인한다.

- [ ] Stripe CLI 설치·로그인
- [ ] `stripe listen`으로 `whsec_` 확보 후 `.env` 반영

## PH-05. sandbox 수동 E2E 검증

depends on: PH-04

자동 테스트는 실제 Stripe를 호출하지 않으므로(`PLAN-AGENT.md` D19) 아래는 손으로 확인한다.
테스트 카드는 `4242 4242 4242 4242`(성공), `4000 0000 0000 0341`(결제 실패)을 쓴다.

- [ ] 프로 월간 구독 결제 → `GET /api/billing/me`가 PRO_MONTHLY로 바뀐다
- [ ] 월간 → 연간 변경을 Customer Portal에서 수행 → 플랜이 반영된다
- [ ] Portal에서 구독 취소 → 즉시 스타터로 전환된다
- [ ] 단건 상품 3종 각각 결제 → 보유 개수 +1 (팀원모집글은 끌어올리기도 +1)
- [ ] 팀원모집글·구인글 게시 → 보유 개수 -1, 게시 실패 시 미차감
- [ ] Dashboard에서 단건 결제 환불 → 보유 개수가 회수된다
- [ ] 결제 실패 카드로 구독 → PAST_DUE + 즉시 스타터 전환
- [ ] 탈퇴 → 활성 구독이 Stripe에서 즉시 취소된다

## PH-06. 기획서 정정

정본 문서(`docs/AT-CREW_서비스기획서_전체_20260728.xlsx`)와 어긋난 항목이다. 코드·레포 문서는 agent가 맞추지만
정본 자체는 사람이 고쳐야 이후 대조에서 혼선이 없다.

- [ ] REQ-021, 요금제-R02, 설정-R03, 설정-R11의 "Polar" → "Stripe"
- [ ] 요금제-R03~R05의 원화 가격·"VAT 별도/포함" 표기 → USD 가격표로 교체
- [ ] 구인글 업로드 권한 가격($99.99)을 요금제-R05에 명시 — 현재 "유료 게시 상품"이라고만 적혀 있고 값이 없다
- [ ] 설정-R11의 "Polar Delete Customer API" → "Stripe 구독 즉시 취소, Customer는 보존"

## PH-07. 라이브 전환 준비 (이번 스코프 밖)

sandbox 검증이 끝난 뒤 별도로 판단한다. 여기 항목이 끝나기 전에는 실결제를 열지 않는다.

- [ ] Stripe 라이브 계정 사업자 심사 통과
- [ ] 국내 카드(BC·국내전용) 실카드 승인률 검증 — 해외 결제망 경유라 국내 PG보다 낮게 나올 수 있다
- [ ] Stripe Tax 적용 여부 및 해외 소비지 과세(EU VAT·일본 소비세 등) 등록 의무 검토 — MoR이 없어 전부 사업자 몫이다
- [ ] 부가세·전자세금계산서 처리 방식 확정
- [ ] prod 웹훅 엔드포인트 등록 — `https://api.at-crew.com/internal/billing/stripe/webhook`
- [ ] 라이브 키를 prod 환경변수에 주입(`.env` 아님)

## PH-08. MVP 단일 요금제(Portfolio Pro $8) 결정 — 미해결

2026-08-18 논의에서 "MVP는 $8짜리 Portfolio Pro 요금제 하나만 쓴다"로 방향이 나왔으나, 아래 세 가지가
정해지지 않아 착수하지 않았다. 상품 5종은 현재 카탈로그에 모두 노출된 상태다.

구현 방향은 정리돼 있다 — 상품을 지우지 않고 `billing.products.*`에 `enabled` 플래그를 추가해 카탈로그에서
거르고 비활성 상품의 Checkout 생성을 막는다(코드 20줄 안쪽).

- [ ] **단건 상품 3종 처리** — 팀원모집글·끌어올리기·구인글을 비활성화하면 구매 경로가 사라져 게시 자체가
      불가능해진다(게시 시 보유 개수를 차감하므로). (a) 판매 중단 + 게이팅 해제(무료 게시) (b) 판매 중단 +
      게이팅 유지(게시 봉인) (c) 현행 유지 중 택일
- [ ] **Portfolio Pro 가격 구조 확정** — 월 $8 단일이 맞는지, 연간 요금제와 정가 취소선(할인 표기)이 있는지
- [ ] **Portfolio Pro 혜택 확정** — 현재 코드에 걸린 프로 혜택은 작품 4개 제한 해제 하나뿐이다(공유
      포트폴리오·다국어는 기능 미구현). 이름상 포트폴리오 기능이 핵심 혜택일 텐데 MVP 포함 여부에 따라
      `plans/260813-pro-plan-gating/` 우선순위가 바뀐다
- [ ] Figma 요금제 화면(`UI개편_설정` 5154:41400 내 6230:47908)과 대조 — 세션에 Figma 접근 수단이 없어
      확인하지 못했다
