# Stripe 결제/구독 모듈 — Agent 작업

## 배경

로드맵 5순위(결제/구독)를 착수한다. 기존 설계는 PG를 **Polar**(Merchant of Record)로 확정해 뒀으나
(`docs/roadmap.md` §5, 기획서 REQ-021), **Stripe 직결로 번복**한다. Stripe sandbox 계정을 이미 발급받았고
이번 스코프는 sandbox(test mode) E2E 검증까지다.

정본 문서(`docs/AT-CREW_서비스기획서_전체_20260728.xlsx`)와 어긋나는 항목이 두 가지 생긴다 — PG(Polar→Stripe),
통화(KRW→USD 단일). 기획서 자체 정정은 사람 몫(`PLAN-HUMAN.md` PH-06)이고, 레포 내 문서 정정은 PA-15에서 한다.

### 착수 시점 코드 현황

- 결제/구독 도메인 없음. `billing`·`payment` 모듈 미존재.
- `RecruitServiceImpl.java:258` 에 `TODO(결제)` — 끌어올리기 개수 확인·차감 훅이 비어 있음.
- 스타터 작품 4개 제한(`마이페이지_작가-R20`) 미구현. `Member.totalSlotCount`는 요금제와 무관한 작업 슬롯이다.
- 공유 포트폴리오·다국어 노출 기능 자체가 없음 → 프로 게이팅 불가(후속 플랜 `plans/260813-pro-plan-gating/`).
- 메일 발송 인프라 없음(다른 워크트리에서 작업 중).
- Flyway 최신 V9. 병렬 워크트리 3개가 모두 V9라 V10 동시 사용 위험이 있다.
- `.env`는 원본 레포 `/Users/danhan/Development/at-crew-backend`에만 존재(gitignore, 워크트리 미복사).

## 결정

| # | 항목 | 결정 |
|---|------|------|
| D1 | PG | Stripe 직결. Polar 폐기. 근거는 글로벌 확장 우선. MoR 부재로 세무는 사업자 몫 |
| D2 | 스코프 | sandbox(test mode) E2E까지. 라이브 전환은 전부 PH |
| D3 | 결제 UI | Stripe Checkout(호스팅 리다이렉트) + Customer Portal. 자체 카드 UI·취소 UI 없음 |
| D4 | 상품 정의 | Stripe Dashboard 수기 생성, price ID를 환경변수로 주입. 코드 부트스트랩 안 함 |
| D5 | 모듈 | `com.atcrew.billing` 신설. 구독 상태·entitlement 잔량 소유. 다른 모듈은 포트로만 접근 |
| D6 | 통화 | **USD 단일**. KRW 미사용. 기획서 원화 표기 전부 무효 |
| D7 | 할인 표기 | Stripe Coupon 미사용. 청구는 할인가 Price 하나, 정가는 카탈로그 표시용 설정값 |
| D8 | 세금 | Stripe Tax 미적용. 표시가 = 최종 청구액(`tax_behavior: inclusive`) |
| D9 | 상품 | 단건 3종(팀원모집글·끌어올리기·**구인글**). 구인글은 기획서 P0인데 로드맵에서 누락됐던 항목 |
| D10 | 게이팅 범위 | recruit 3종 + artwork 스타터 4개 제한까지. 포트폴리오·다국어는 포트만 열고 후속 플랜 |
| D11 | 결제 실패 | 유예 없이 즉시 스타터 전환 + 상태 노출 API. 이메일은 이벤트 발행까지만 |
| D12 | 환불 | 앱 내 환불 API 없음. 대시보드 수동 환불 + `charge.refunded` 웹훅으로 entitlement 회수 |
| D13 | 탈퇴 | 활성 구독 즉시 취소(잔여기간 환불 없음). Stripe Customer는 삭제하지 않고 보존 |
| D14 | 구독 대상 | 프로 구독은 창작자만. 기업 계정은 구독 진입 차단, 단건 상품만 |
| D15 | Flyway | billing은 **V20 대역** 예약. 병렬 워크트리와 번호 충돌 회피 |
| D16 | 웹훅 | 웹훅이 단일 진실 소스. 서명 검증 + `event.id` 멱등 테이블. 복귀 URL에서 상태 변경 금지 |
| D17 | 카탈로그 | 백엔드가 가격·정가·CTA 상태까지 계산해 반환. 프론트 하드코딩 금지 |
| D18 | 이력 | 인보이스 미러링 안 함(Stripe가 정본). entitlement 원장(ledger)만 자체 보관 |
| D19 | 테스트 | 웹훅 픽스처 통합테스트 + REST Docs, test clock은 `@Tag` 분리 + 키 없으면 assume skip |

### 가격표 (USD, D6·D7)

| 상품 키 | 상품 | 청구가 | 정가(취소선) | 비고 |
|---|------|------|------|------|
| `PRO_MONTHLY` | 프로 월간 | $5.99 | $11.99 | 얼리버드 50% |
| `PRO_YEARLY` | 프로 연간 | $59.99 | $119.99 | 2개월 무료 배지 |
| `TEAM_POSTING` | 팀원모집글 업로드권한 | $39.99 | — | 끌어올리기 1회 포함 |
| `BOOST` | 끌어올리기 | $7.99 | — | 48시간 상단 고정 |
| `JOB_POSTING` | 구인글 업로드권한 | $99.99 | — | 기업 전용 |

USD는 소수점 통화이므로 Stripe amount는 **센트 단위 정수**($5.99 → `599`)로 다룬다.

## 금지 범위

- 자체 카드 입력 UI, 결제수단 등록·변경 API, 구독 취소 API를 만들지 않는다(전부 Customer Portal 위임).
- 앱 내 환불 신청·심사 플로우를 만들지 않는다.
- Stripe Product/Price를 코드에서 생성·동기화하지 않는다.
- `Member`에 plan 필드를 추가하지 않는다. 플랜은 billing이 소유하고 포트로만 노출한다.
- 인보이스·영수증을 자체 테이블에 미러링하지 않는다.
- 공유 포트폴리오·다국어 게이팅을 구현하지 않는다(기능 자체가 없음, 후속 플랜).
- 메일 발송 코드를 작성하지 않는다(다른 워크트리 작업과 충돌).
- 다통화·Stripe Tax·구독 유예기간(dunning)을 구현하지 않는다.
- V10~V19 마이그레이션 번호를 사용하지 않는다.

## 검증

- `./gradlew test` — billing 관련 신규/기존 테스트 전부 통과. 전체 스위트에는 **기준 커밋(299ba5b)에서도 동일하게 실패하는
  5건**(`SearchApiDocTest` 2건, `EventPublicationRegistryTest` 3건)이 남아 있다. 별도 워크트리에서 기준 커밋 전체 스위트를
  돌려 실패 목록이 정확히 같음을 확인했다 — 공유 Testcontainer가 컨텍스트 종료를 따라 먼저 닫히는 기존 구조 문제이며
  이번 변경과 무관하다(단독 실행 시에는 통과).
- 웹훅 픽스처 통합테스트 5종(구독 생성/갱신/실패/취소/환불) 통과.
- 동일 웹훅 event를 2회 전달해도 잔량·구독 상태가 1회분만 반영된다(멱등).
- 동시 게시 2건이 잔량 1개를 놓고 경합할 때 정확히 1건만 성공한다.
- 게시 실패 시 잔량이 차감되지 않는다(`요금제-R06`).
- Spring Modulith 모듈 경계 테스트 통과 — billing이 recruit·artwork를 참조하지 않는다.
- 애플리케이션이 Stripe 키 없이도 기동된다(로컬 더미 기본값).

---

## PA-01. billing 모듈 스캐폴딩 및 설정 바인딩

- [x] `com.atcrew.billing` 패키지 생성(`internal/{web,application,domain,persistence,exception}` 구조는 기존 모듈과 동일)
- [x] `build.gradle`에 Stripe Java SDK 의존성 추가
- [x] ~~`application.yml`에 `spring.config.import: optional:file:.env[.properties]` 추가~~ — **철회**. 이 설정을 넣으면 테스트에서 Testcontainer 접속 정보가 어긋나 `SearchApiDocTest`·`EventPublicationRegistryTest`가 깨지는 것을 확인했다(기준 커밋에서는 통과, 설정 제거 시 복구). `.env`는 기존 R2·JWT 키와 동일하게 실행 환경이 환경변수로 주입한다
- [x] `application.yml`에 `stripe:` 블록 추가(secret-key / publishable-key / webhook-secret / price ID 5종 / 정가 5종 / `billing.frontend-base-url`), 전부 더미 기본값으로 키 없이 기동 가능하게 한다
- [x] `.env.example` 신규 커밋 — `PLAN-HUMAN.md` PH-03의 양식과 동일하게
- [x] `StripeProperties` `@ConfigurationProperties` 바인딩

## PA-02. Flyway V20 billing 스키마

depends on: PA-01

- [x] `V20__billing_schema.sql` 작성 (V10~V19는 비워 둔다 — D15)
- [x] `billing_customer` — member_id(PK), stripe_customer_id(UNIQUE), created_at
- [x] `billing_subscription` — id, member_id, stripe_subscription_id(UNIQUE), plan, status, current_period_end, cancel_at_period_end, created_at, updated_at, version
- [x] `billing_entitlement_balance` — member_id + product_key(복합 PK), quantity, version
- [x] `billing_entitlement_ledger` — id, member_id, product_key, delta, reason(PURCHASE/CONSUME/REFUND_REVOKE), stripe_event_id, ref_id, created_at
- [x] `billing_webhook_event` — stripe_event_id(PK), type, received_at, processed_at
- [x] ID 전략은 `docs/design/mariadb-migration-design.md`의 String/UUIDv7 규약을 따른다
- [x] 시각 컬럼은 전부 UTC 저장(`Instant`)

## PA-03. 도메인·리포지토리

depends on: PA-02

- [x] `Subscription` 엔티티 — 상태 전이(ACTIVE/PAST_DUE/CANCELED)를 도메인 메서드로 캡슐화
- [x] `EntitlementBalance` 엔티티 — `consume()`은 잔량 부족 시 도메인 예외, 낙관적 락(`@Version`)
- [x] `EntitlementLedger` 엔티티 — append-only, 수정 메서드 없음
- [x] `BillingErrorCode` enum — 프로젝트 에러코드 규약을 따른다(권한 부족, 기업 구독 불가, 중복 구독 등)
- [x] 리포지토리 3종

## PA-04. 카탈로그·상태 조회 API

depends on: PA-03

- [x] `GET /api/billing/catalog` — 비로그인 허용. 상품 5종의 키·표시명·청구가(센트)·정가·통화(USD)·할인배지 반환
- [x] 로그인 상태면 현재 플랜 기준 CTA 상태를 함께 계산(시작하기 / 이용 중인 플랜 / 월간·연간 변경하기 — `요금제-R04`)
- [x] 기업 계정에는 프로 플랜 카드의 CTA를 구매 불가 상태로 반환(D14)
- [x] `GET /api/billing/me` — 인증 필요. 플랜(STARTER/PRO_MONTHLY/PRO_YEARLY), 구독 상태, 다음 결제일, 단건 상품 3종 보유 개수 반환
- [x] Swagger 문서화 — 비2xx `@ApiResponse.description`에 에러코드 명시(프로젝트 규약)

## PA-05. Checkout Session 생성 API

depends on: PA-03

- [x] `POST /api/billing/checkout-sessions` — body는 상품 키 하나
- [x] Stripe Customer 없으면 생성 후 `billing_customer`에 저장(member_id ↔ customer 1:1)
- [x] 구독 상품은 `mode=subscription`, 단건 상품은 `mode=payment`
- [x] `client_reference_id`/`metadata`에 memberId·productKey를 실어 웹훅에서 복원 가능하게 한다
- [x] `success_url` = `{frontend-base-url}/billing/success?session_id={CHECKOUT_SESSION_ID}`, `cancel_url` = `{frontend-base-url}/billing/cancel`
- [x] 기업 계정의 프로 구독 요청은 403 + 전용 에러코드(D14)
- [x] 이미 동일 플랜 구독 중이면 거부, 월↔연 변경은 Portal로 유도

## PA-06. Customer Portal Session 생성 API

depends on: PA-05

- [x] `POST /api/billing/portal-sessions` — `return_url`은 설정 > 요금제 및 결제 탭
- [x] Stripe Customer가 없는 회원(구매 이력 없음)은 404 + 전용 에러코드

## PA-07. 웹훅 수신·처리

depends on: PA-03

- [x] `POST /internal/billing/stripe/webhook` — permitAll + Stripe 서명 검증(raw body 필요, `@RequestBody String`)
- [x] `billing_webhook_event`에 `stripe_event_id` 선삽입으로 멱등 보장(중복은 200 반환 후 무시)
- [x] `checkout.session.completed` — `mode=payment`면 entitlement +1(ledger PURCHASE). `TEAM_POSTING`은 BOOST도 +1(`요금제-R06`)
- [x] `customer.subscription.created|updated|deleted` — 구독 미러 갱신, 취소 시 즉시 스타터
- [x] `invoice.payment_failed` — 유예 없이 PAST_DUE + 즉시 스타터 전환(D11)
- [x] `invoice.payment_succeeded` — 플랜 복원
- [x] `charge.refunded` — entitlement 회수(ledger REFUND_REVOKE). 잔량 부족(이미 사용)이면 음수로 두지 않고 WARN 로그만 남긴다(D12)
- [x] 순서 역전 방어 — 구독 이벤트는 Stripe가 준 타임스탬프/기간 값 기준으로만 갱신하고 과거 이벤트는 무시
- [x] 처리 실패 시 5xx를 반환해 Stripe 재시도를 유도, 성공 시 `processed_at` 기록

## PA-08. 공개 포트 `BillingService`

depends on: PA-03

- [x] `hasProPlan(memberId)` — artwork·포트폴리오·다국어 게이팅 공용 진입점
- [x] `getBalance(memberId, productKey)` — 조회 전용
- [x] `consume(memberId, productKey, refId)` — 조건부 차감, 실패 시 도메인 예외
- [x] billing은 recruit·artwork를 참조하지 않는다. Modulith 경계 테스트로 검증

## PA-09. recruit 연동 — 단건 상품 차감

depends on: PA-08

- [x] 팀원모집글 게시 성공 시 `TEAM_POSTING` 차감, 실패 시 미차감(`요금제-R06`)
- [x] 구인글 게시 성공 시 `JOB_POSTING` 차감 (D9, `구인구직-R02`)
- [x] 끌어올리기 적용 성공 시 `BOOST` 차감 — `RecruitServiceImpl.java:258`의 `TODO(결제)` 제거
- [x] 권한 없음은 403 + 에러코드로 반환(프론트가 모달 분기)
- [x] 차감은 게시 트랜잭션 안에서 수행해 성공·차감이 원자적으로 묶이게 한다

## PA-10. artwork 스타터 작품 4개 제한

depends on: PA-08

- [x] 작품 업로드 시 스타터 플랜이면 보유 작품 4개 초과 차단(`마이페이지_작가-R20`)
- [x] 휴지통 복구에도 동일 제한 적용(`휴지통-R03`)
- [x] 프로 플랜은 제한 없음. 다운그레이드 시 기존 작품은 유지하고 신규 생성만 막는다(`요금제-R01`)
- [x] 초과 상태를 전용 에러코드로 구분해 프론트가 안내 모달을 띄울 수 있게 한다

## PA-11. 탈퇴 시 구독 취소

depends on: PA-05

- [x] `MemberDeactivatedEvent`를 `@ApplicationModuleListener`로 구독(기존 패턴)
- [x] 활성 구독이 있으면 Stripe 구독 즉시 취소. 잔여기간 환불 없음
- [x] Stripe Customer는 삭제하지 않는다(D13)
- [x] Stripe 호출 실패 시 이벤트가 재발행되도록 예외를 삼키지 않는다(`republish-outstanding-events-on-restart` 활용)

## PA-12. 결제 실패 이벤트 발행

depends on: PA-07

- [x] `SubscriptionPaymentFailedEvent(memberId, planType, failedAt)` 공개 이벤트 정의
- [x] `invoice.payment_failed` 처리 시 발행. billing은 메일을 직접 보내지 않는다(D11)
- [x] 메일 모듈이 다른 규약을 이미 정했다면 그 이름·페이로드에 맞춘다

## PA-13. 테스트

depends on: PA-09, PA-10, PA-11, PA-12

- [x] 웹훅 픽스처 통합테스트 5종(구독 생성/갱신/실패/취소/환불) — 실제 Stripe 호출 없음
- [x] 동일 event 2회 전달 시 멱등 검증
- [x] entitlement 동시 차감 경합 테스트(잔량 1, 동시 2건 → 1건만 성공)
- [x] 게시 실패 시 미차감 검증
- [x] 기업 계정 프로 구독 차단, 스타터 4개 제한, 다운그레이드 후 기존 작품 유지 검증
- [x] REST Docs — 카탈로그·내 상태·포털 진입 404·기업 계정 403. **Checkout·Portal 성공 응답은 문서화하지 않는다** — 빈을 대역으로 바꾸면 REST Docs 계열에 컨텍스트가 하나 더 생기고 공유 Testcontainer가 먼저 닫히는 컨텍스트를 따라 종료돼 다른 문서화 테스트가 연쇄로 깨진다(실측). 응답 형태는 프론트 연동 문서에 예시로 남긴다
- [x] 설정 바인딩 회귀 테스트 — `src/test/resources/application.yml`이 테스트 클래스패스에서 main 설정을 통째로 대체하므로, 상품 카탈로그 5종이 비면 컨테이너 없이 즉시 실패하도록 한다

## PA-14. Stripe test clock 시뮬레이션 테스트

depends on: PA-13

- [x] `@Tag("stripe-sandbox")`로 분리해 기본 `./gradlew test`에서 제외
- [x] 키 환경변수가 없으면 `Assumptions.assumeTrue`로 자동 skip (D19)
- [x] 연간 구독 갱신, 결제 실패 → 즉시 다운그레이드 시나리오
- [x] CI에는 sandbox 키를 주입하지 않는다

## PA-15. 문서화

depends on: PA-13

- [x] `docs/design/billing-module-design.md` 신규 — 도메인·API·웹훅 이벤트 매핑·멱등·entitlement 원장·에러코드
- [x] `docs/design/billing-frontend-integration.md` 신규 — 프론트 요청서(카탈로그 렌더링, checkout 리다이렉트, `/billing/success`·`/billing/cancel` 페이지, 폴링 규약, Portal 위임, 403 기반 모달 분기, PAST_DUE 배너). 프론트 배포는 `https://at-crew.com`, API는 `https://api.at-crew.com`
- [x] `docs/roadmap.md` §5 정정 — PG Polar→Stripe, 통화 KRW→USD, 단건 상품 3종(구인글 추가), 탈퇴 처리 변경
- [x] `docs/roadmap.md` 포트폴리오·다국어 항목에 "구현 시 billing 프로 게이팅 필수"를 선행조건으로 명시
- [x] `CLAUDE.md` 문서 목록에 신규 문서 2개 추가
- [x] 웹훅 운영 규약을 설계 문서에 명시 — 로컬은 `stripe listen`, prod는 `https://api.at-crew.com/internal/billing/stripe/webhook` 등록

포트폴리오·다국어 게이팅 잔여분(D10)은 `plans/260813-pro-plan-gating/`에 별도 플랜으로 분리해 두었다.
