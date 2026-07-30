# 나라별 데이터 언어 변환 — 국가 필드 설계안 (Phase 1: countryCode만)

> 작성일: 2026-07-30
> 상태: 설계안 → 구현 착수
> 관련 문서: [global-timezone-strategy.md](global-timezone-strategy.md)(§5.1 i18n, §5.2 ActiveRegion을 범위 밖으로 플래그), [community-module-design.md](community-module-design.md), [roadmap.md](../roadmap.md)

## 0. 요약 (TL;DR)

**스코프 축소 경위**: 최초 요구사항은 ① 거주 국가(가입 시, 단일) ② Pro 플랜 회원의 작품 노출 국가(다중) 둘 다였다.
그러나 `docs/roadmap.md`(2026-07-30, "우선순위 확정")에 결제/구독(요금제)이 **5순위**(인증시스템→recruit→
기업프로필→검색 이후)로 이미 확정되어 있는 것을 구현 착수 직전에 발견 — 지금 구독 도메인을 새로 만들면
이 확정 순서와 충돌한다. 사용자 결정으로 **이번 Phase 1은 거주 국가(Member.countryCode)만** 구현하고,
Pro 플랜 게이팅이 필요한 "작품 노출 국가"는 로드맵 5순위(결제/구독)에 도달했을 때 §4를 이어서 구현한다.

| 항목 | 결정 |
|------|------|
| 이번 구현 범위(Phase 1) | 회원가입 시 거주 국가(단일) 수집 + API 노출 |
| 메뉴/UI 번역 | **백엔드 책임 아님** — 프론트엔드가 자체 번역 리소스로 처리. 백엔드는 회원의 countryCode 값만 저장·응답 |
| 포트폴리오 콘텐츠 | 원문 그대로 저장 — 번역/다국어 컬럼 추가 없음 |
| 거주 국가 필드 | `Member.countryCode` (String, ISO 3166-1 alpha-2), timezone과 동일한 "String + 카탈로그 검증" 패턴 |
| Figma 근거 | 설정(모바일) "사용자 정보" 영역에 "거주 국가" 입력 필드 확인 |
| Phase 2 (범위 밖, 로드맵 5순위 도달 후) | `Artwork.targetCountryCodes`(다중, Pro 전용) + 이를 판단할 `subscription` 모듈 — §4에 설계만 남겨둠, 구현은 로드맵 순서 도달 시 |
| 범위 밖 | 실제 PG 연동, 커뮤니티 피드의 국가 기반 필터링, `ActiveRegion`(국내 행정구역) 글로벌화, 서버 사이드 i18n(MessageSource) |

---

## 1. 배경

사용자 요구사항 원문:
- 포트폴리오는 원문 그대로, 메뉴만 언어 변환
- 회원가입 시 거주 국가 설정
- Pro 플랜 회원은 작품 업로드 시 노출할 여러 국가 선택 가능

기존 코드 조사 결과(2026-07-30):
- `Member`에 국가/언어 필드 전무. `timezone`(String, IANA tzdb 카탈로그 검증)만 최근 추가됨 — 동일 패턴 재사용 가능.
- Plan/Subscription/Payment/Billing 도메인 **전무**.
- `Artwork`에 노출 국가/지역 필드 없음. 공개 범위는 `Visibility`(PUBLIC/LINK_ONLY/PRIVATE) 하나뿐, 국가 축 없음.
- i18n(MessageSource/LocaleResolver) 관련 설정 전무 — 이번 작업 스코프에서도 도입하지 않음(사용자 결정).
- Figma "요금제" 페이지(`UI개편_설정` 파일, node `6627:25961` 등)에 월간/연간 토글, 카드형 요금제 UI가 이미 존재 — 프론트는 이 화면을 이미 준비 중.
- Figma 작품 업로드 플로우 중 `마이페이지_작가_유료사용자` 프레임에만 "작품 설정을 선택해주세요" 단계(활동 언어/이미지 나열 형식/연령대)가 있고, `마이페이지_작가_무료 사용자` 프레임에는 해당 단계가 없음 — Pro 전용 게이팅이 디자인 레벨에서 이미 확정.

---

## 2. Member.countryCode 설계

**필드**: `Member.countryCode` (String, nullable 아님 — 가입 시 필수)

**검증**: `timezone`과 동일한 "실재 카탈로그 대조" 패턴을 따른다.
```java
private static void validateCountryCode(String countryCode) {
    if (!Set.of(Locale.getISOCountries()).contains(countryCode)) {
        throw new MemberException(MemberErrorCode.INVALID_COUNTRY, countryCode);
    }
}
```
`Locale.getISOCountries()`가 timezone 검증의 `ZoneId.getAvailableZoneIds()`와 동일한 역할 — JDK 내장 ISO 3166-1 alpha-2 카탈로그로 임의 문자열("ZZ" 등)을 원천 차단한다.

**적용 지점** (timezone 필드 추가 시 손댄 지점과 동일):
- `Member` 생성자(`registerWithEmail`/`registerWithGoogle`), `updateInfo()` — 검증 후 저장, 설정 화면에서 재변경 가능
- `RegisterMemberCommand`, `EmailRegisterCommand`, `GoogleRegisterCommand` — `timezone` 다음에 `countryCode` 필드 추가
- `UpdateInfoCommand` — 마지막에 `countryCode` 추가
- `MemberErrorCode.INVALID_COUNTRY` 신규 추가
- `MemberInfo` 응답에 `countryCode` 노출
- Swagger: `EmailRegisterRequest`/`GoogleRegisterRequest`/`UpdateInfoRequest`에 필드+예시(`KR`) 추가

**Figma 근거**: 설정 페이지(모바일 370px, `UI개편_설정`) "사용자 정보" 영역에 "거주 국가" Text_input_box 행 확인(`7026:112438`). 데스크톱 레이아웃엔 아직 반영 안 되어 있으나 값 자체는 설정에서 재변경 가능해야 하므로 API는 처음부터 지원.

**countryCode vs ActiveRegion**: `ActiveRegion`(SEOUL/GYEONGGI/...)은 한국 국내 행정구역 전용 enum으로 완전히 별개 축이다. 이번 작업에서 재사용하거나 확장하지 않는다 — 혼용 시 "국내 지역"과 "국가"가 같은 enum에 섞이는 의미 오염이 생긴다([[global-timezone-strategy]] §5.2가 이미 지적한 문제와 동일). `ActiveRegion` 글로벌화는 범위 밖으로 유지.

---

## 3. `subscription` 모듈 신설 (Phase 2 — 로드맵 5순위 도달 후 구현, 설계만 선기록)

### 3.1 모듈 구조

```
subscription/
  Plan.java                    (enum: FREE, PRO)
  BillingCycle.java             (enum: MONTHLY, YEARLY)
  SubscriptionInfo.java         (공개 Info record)
  SubscriptionService.java      (공개 인터페이스)
  SubscribeCommand.java         (공개 Command record)
  internal/
    domain/Subscription.java    (@Document 애그리게잇)
    domain/SubscriptionStatus.java (enum: ACTIVE, CANCELED)
    application/SubscriptionServiceImpl.java
    exception/SubscriptionErrorCode.java, SubscriptionException.java
    persistence/SubscriptionRepository.java
    web/SubscriptionController.java, dto/*
```

`artwork` 모듈이 `subscription.SubscriptionService`(공개 인터페이스)를 의존해 플랜을 조회한다 — `community`가 `member.MemberService`를 참조하는 것과 동일한 패턴(Modulith 허용 범위).

### 3.2 도메인 모델

**`Subscription`** (member 1명당 활성 구독은 최대 1건):
- `id`, `memberId`, `plan`(현재는 PRO만 실제로 레코드 생성 — FREE는 레코드 부재로 암묵 표현), `billingCycle`, `status`(ACTIVE/CANCELED), `startedAt`, `currentPeriodEnd`, `canceledAt`(nullable), `createdAt`, `updatedAt`

**Free 회원을 위한 레코드를 만들지 않는 이유**: 전 회원에 대해 FREE 구독 문서를 미리 만들면 가입 시점마다 불필요한 쓰기가 늘고, "구독 이력"이라는 개념과도 안 맞는다. `getActivePlan(memberId)`가 활성 `Subscription` 부재 시 `Plan.FREE`를 반환하는 것으로 충분하다.

**플랜 판정 로직** (`getActivePlan`):
```
활성 Subscription 존재
  AND status IN (ACTIVE, CANCELED)   // 해지해도 currentPeriodEnd까지는 Pro 유지
  AND currentPeriodEnd > now
  → PRO
그 외 → FREE
```

### 3.3 결제 게이트웨이 미연동 — 임시 자가-구독 패턴

코드베이스에 결제 도메인이 전혀 없고 PG 연동은 이번 스코프 밖이다. `community` 모듈의 `BannerController`가 RBAC 부재 상태에서 "인증 회원 누구나 호출 가능 + TODO 명시"로 임시 처리한 선례를 그대로 따른다:

```java
// TODO: 결제 게이트웨이(PG) 연동 전까지 구독 API는 실제 결제 검증 없이
// 인증된 회원이 직접 호출하면 즉시 활성화된다. PG 연동 시 웹훅 기반으로 교체 필요.
```

- `POST /api/subscriptions/subscribe` — `SubscribeCommand(plan=PRO, billingCycle)`를 받아 즉시 `Subscription` 생성/갱신(`currentPeriodEnd` = now + 1개월 또는 1년)
- `POST /api/subscriptions/cancel` — `status=CANCELED`(즉시 FREE 전환이 아니라 `currentPeriodEnd`까지는 PRO 유지 — 일반적인 SaaS 해지 UX)
- `GET /api/subscriptions/me` — 내 구독 상태 조회 (Figma 요금제 페이지의 "요금제 및 결제 정보" 영역에 대응)

결제 실패/카드 만료/환불 같은 PG 연동 이후에나 의미 있는 상태(PAST_DUE 등)는 이번 상태 머신에 넣지 않는다 — 나중에 실제 PG를 붙일 때 상태 enum에 추가한다.

### 3.4 에러 코드

`SubscriptionErrorCode`: `SUBSCRIPTION_NOT_FOUND`(구독 내역 없음, cancel 호출 시), `INVALID_BILLING_CYCLE`

---

## 4. Artwork.targetCountryCodes 설계 (Phase 2 — 구현 보류)

**필드**: `Artwork.targetCountryCodes` (List\<String\>, ISO 3166-1 alpha-2, 최대 10개 — `Member.activityFields`류 리스트 제약과 동일하게 상한 설정)

**게이팅**: `UploadArtworkCommand`/`UpdateArtworkCommand`에 `targetCountryCodes` 필드가 비어있지 않으면 `ArtworkServiceImpl`이 `subscriptionService.getActivePlan(authorId)`를 조회해 `PRO`가 아니면 명시적 에러(`ArtworkErrorCode.PRO_PLAN_REQUIRED`)를 던진다. 조용히 무시(silent drop)하지 않는다 — 프론트가 업그레이드 유도 UX를 보여줄 수 있어야 한다.

**Free 회원 기본값**: `targetCountryCodes = []` (빈 리스트). 별도의 "본인 거주국가만 노출" 같은 암묵 규칙을 만들지 않는다 — 실제 피드 필터링 자체가 이번 스코프 밖이라 지금은 순수 메타데이터다.

**검증**: 개별 원소는 Member.countryCode와 동일한 `Locale.getISOCountries()` 카탈로그 검증 재사용.

---

## 5. 범위 밖 (후속 작업)

1. **커뮤니티 피드 국가 필터링 강제 적용** — `targetCountryCodes`를 실제로 피드 조회 쿼리에 반영(뷰어의 countryCode와 매칭)하는 작업은 `community` 모듈의 피드 API 확장이 필요한 별도 작업이다. 이번엔 저장·응답까지만 하고, 필터링은 값이 준비된 뒤 후속 PR로 분리한다.
2. **실제 PG 연동** — Toss Payments 등 PG사 선정, 웹훅 처리, 결제 실패/환불 상태 머신은 별도 설계 필요.
3. **서버 사이드 i18n** — `GlobalExceptionHandler`, 검증 메시지 등은 계속 한국어 하드코딩 유지(사용자 결정: 프론트 자체 번역).
4. **`ActiveRegion` 글로벌화** — [[global-timezone-strategy]] §5.2와 동일 사유로 범위 밖.
5. **Figma "활동 언어" 단일선택 vs 다중선택 불일치** — 이번 구현은 다중 선택으로 진행(사용자 결정). 프론트 실제 구현 시 Figma mock이 갱신되지 않았다면 디자인팀 확인 필요.
