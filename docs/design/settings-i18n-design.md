# 설정 · i18n · 인증 확장 설계

> 작성일: 2026-08-10
> 상태: 설계안 (구현 전)
> 범위: 설정 API 전체(계정 정보·로그아웃·비밀번호 변경·마케팅 동의·성인 콘텐츠 토글·게시물 언어 선택), 비밀번호 재설정(이메일 발송 인프라 포함), 계정 유형 필드, 언어 세그먼트 필터, 성인 콘텐츠 표시 필터
> 범위 밖: 서버 에러/검증 메시지 현지화(4개 언어 `MessageSource`), 본인 인증(PASS) 연동, 관리자 콘솔
> 정본 근거: `docs/AT-CREW_서비스기획서_전체_20260728.xlsx` REQ-001·REQ-002·REQ-018·REQ-019·REQ-022, 정책 로그인-R04·R08·R12·R16, 설정-R04~R14

---

## 0. 설계 요약 (TL;DR)

| 항목 | 결정 |
|---|---|
| 메일 발송 | 신규 `com.atcrew.notification` 모듈(common 아님, §1). Resend HTTP API 어댑터 + 로컬/테스트용 로깅 어댑터, 프로파일로 스위칭 |
| 계정 유형 | `Member.accountType`(CREATOR\|COMPANY) 신설, 가입 시 필수·이후 읽기 전용 |
| 언어 | `Member.primaryLanguage`(가입 시 필수, 변경 불가) + `postLanguages`(복수, 설정에서 변경 가능, 주 언어는 해제 불가) |
| 마케팅 동의 | 신규 컬럼을 만들지 않고 기존 `TermsAgreement.marketingNotification`(컬럼 `terms_marketing`)을 토글 대상으로 재사용 |
| 로그아웃/세션 | 현재 refresh token이 **회원당 1개**뿐이라는 사실이 설계를 가른다(§4). 로그아웃=전체 삭제, 비밀번호 변경=전체 삭제 후 현재 기기용 재발급 |
| access token 무효화 | 즉시 무효화 메커니즘이 없음(stateless JWT) → **TTL을 1시간→15분으로 단축**해 노출 창을 줄이는 것으로 이번 마일스톤은 대응(§4.3) |
| 비밀번호 재설정 토큰 | SHA-256 해시 저장, 원문은 메일에만, 만료 1시간(로그인-R08), 1회성, 계정 열거 방지를 위해 요청 API는 항상 202 |
| 언어 세그먼트 판정 단위 | **게시물이 아니라 작성자 계정** — `artworks`에 언어 컬럼을 새로 만들지 않고 작성자의 `primary_language`를 비정규화해 조인 없이 필터 |

---

## 1. notification 모듈 — 왜 common이 아닌가

`common.*`(response/security/persistence/exception/logging/id/config/web)는 `@NamedInterface`로 전 모듈에 개방된 shared kernel이다. 여기에 "비밀번호 재설정 메일 본문", "결제 실패 안내 문구" 같은 **도메인 문구**가 들어가면 common이 도메인 지식을 갖게 되고 템플릿이 늘어날수록 shared kernel이 비대해진다. 그래서 인프라 포트는 얇게 common 밖에 독립 모듈로 둔다.

```
com.atcrew.notification/
  EmailSender.java              -- 포트 (public interface): sendRaw(EmailMessage)
  EmailMessage.java              -- record: to, subject, html, text
  NotificationService.java       -- 의미 단위 public API (다른 모듈이 호출)
  internal/
    application/NotificationServiceImpl.java
    infra/resend/ResendEmailAdapter.java   -- @Profile("!local & !test"), RestClient 사용
    infra/log/LoggingEmailAdapter.java     -- @Profile("local | test")
    infra/ResendProperties.java
    template/EmailTemplates.java           -- 텍스트 블록 기반 HTML, i18n은 범위 밖(한국어 고정)
```

공개 API(의미 단위로 노출 — 다른 모듈이 "메일 보내줘"가 아니라 "무슨 일이 일어났는지"를 전달):

```java
public interface NotificationService {
    void sendPasswordResetLink(String toEmail, String memberName, String resetUrl);
    void sendPaymentFailed(String toEmail, String memberName, String billingPortalUrl);
    void sendSubscriptionChanged(String toEmail, String memberName, PlanChangeKind kind);
}
```

Resend는 HTTP API라 `spring-boot-starter-mail`/`JavaMailSender`가 필요 없다 — 기존 `RestClient`(spring-web)로 충분하므로 **build.gradle에 신규 의존성이 없다**.

메일 발송은 트랜잭션 커밋 이후에만 실행한다(`@ApplicationModuleListener` 또는 `TransactionSynchronizationManager.afterCommit`) — 롤백된 요청에 대해 유효하지 않은 링크가 발송되는 것을 막는다. `ArtworkEventListener`의 `@Async @EventListener` 패턴을 따른다. 발송 실패는 예외를 삼키지 않고 로깅 후 반환(재시도는 이번 스코프에 넣지 않는다 — 사용자가 "재발송" 버튼으로 우회 가능).

---

## 2. Member 확장

### 2.1 신규 필드

```java
// member/internal/domain/Member.java 확장
@Enumerated(EnumType.STRING)
private AccountType accountType;         // CREATOR | COMPANY — 가입 시 필수, setter 없음(읽기 전용)

@Enumerated(EnumType.STRING)
private Language primaryLanguage;        // KO | JA | ZH | EN — 가입 시 필수, setter 없음

@ElementCollection(fetch = FetchType.EAGER)
@CollectionTable(name = "member_post_languages", joinColumns = @JoinColumn(name = "member_id"))
@Enumerated(EnumType.STRING) @Column(name = "value")
private Set<Language> postLanguages;     // 기본값 = {primaryLanguage}

private boolean adultContentVisible = true;   // 설정-R10 기본 ON
```

- `AccountType`·`Language`는 `com.atcrew.member` 패키지의 공개 enum(기존 `ActivityField`/`ActiveRegion`과 동일 위치).
- `postLanguages`는 `activeRegions`와 동일한 `@ElementCollection` 패턴(`member_active_regions` 테이블 선례).
- `Member.updatePostLanguages(Set<Language>)` — 내부에서 `primaryLanguage` 미포함 시 `MemberException(PRIMARY_LANGUAGE_CANNOT_BE_REMOVED)`(설정-R14 "주 사용 언어 칩 해제 불가").
- `Member.updateMarketingConsent(boolean)` — `TermsAgreement`는 불변 embeddable이라 새 인스턴스로 교체하되 `agreedAt`은 보존.
- `Member.updateAdultContentVisible(boolean)`.

**기존 회원 마이그레이션**: `primary_language`는 NULL 허용 컬럼으로 추가하고, 신규 가입만 필수로 강제한다. NULL인 기존 회원은 언어 필터에서 "전체 노출"로 폴백한다(§5). `account_type`은 `companies` 테이블에 행이 있으면 `COMPANY`로 백필하고 나머지는 `CREATOR` 기본값.

### 2.2 Flyway — V14

```sql
ALTER TABLE members
  ADD COLUMN account_type          VARCHAR(20) NOT NULL DEFAULT 'CREATOR' AFTER creator_role,
  ADD COLUMN primary_language      VARCHAR(10) NULL AFTER country_code,
  ADD COLUMN adult_content_visible TINYINT(1)  NOT NULL DEFAULT 1 AFTER primary_language;

UPDATE members m JOIN companies c ON c.member_id = m.id SET m.account_type = 'COMPANY';

CREATE TABLE member_post_languages (
    member_id VARCHAR(36) CHARACTER SET latin1 COLLATE latin1_bin NOT NULL,
    value     VARCHAR(10) NOT NULL,
    PRIMARY KEY (member_id, value)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE INDEX idx_members_lang_search ON members (is_active, primary_language, employment_status, updated_at DESC);
```

---

## 3. REST API

### 3.1 가입 API 계약 변경 (⚠️ 프론트 동시 배포 필요 — 하위 호환 깨짐)

`POST /api/auth/email/register`, `POST /api/auth/google/register` 요청에 `accountType`(필수), `primaryLanguage`(필수) 추가.

### 3.2 신규 — auth

| 메서드 | 경로 | 인증 | 요청 | 응답 | 에러 |
|---|---|---|---|---|---|
| POST | `/api/auth/logout` | 필요 | `{refreshToken?}` | 204 | 401 |
| POST | `/api/auth/email/password-reset/request` | 불필요 (**`SecurityConfig`에 이미 permitAll 선등록돼 있음**, 컨트롤러만 만들면 됨) | `{email}` | 204(항상) | 429 `TOO_MANY_ATTEMPTS` |
| POST | `/api/auth/email/password-reset/confirm` | 불필요(동일) | `{token, newPassword, newPasswordConfirm}` | 204 | 400 `PASSWORD_MISMATCH`, 400 `INVALID_RESET_TOKEN`, 410 `RESET_TOKEN_EXPIRED` |

### 3.3 신규 — member / 설정

| 메서드 | 경로 | 인증 | 요청 | 응답 | 기획서 |
|---|---|---|---|---|---|
| GET | `/api/members/me` | 필요 | — | `MemberInfo` | 기존 부재 |
| GET | `/api/members/me/account` | 필요 | — | `AccountInfo{loginEmail, authProvider, accountType, primaryLanguage, postLanguages, marketingAgreed, adultContentVisible}` | 설정-R04·R05 |
| PATCH | `/api/members/me/password` | 필요 | `{currentPassword, newPassword, newPasswordConfirm}` | 200 `{accessToken, refreshToken}` | 설정-R13 |
| PATCH | `/api/members/me/settings/marketing` | 필요 | `{agreed}` | 204 | 설정-R09 |
| PATCH | `/api/members/me/settings/adult-content` | 필요 | `{visible}` | 204 | 설정-R10 |
| PATCH | `/api/members/me/settings/post-languages` | 필요 | `{languages: [KO, JA]}` | 204 | 설정-R14 |
| GET | `/api/settings/support` | 불필요 | — | `{kakaoOpenChatUrl, operatingHours}` | 설정-R12 |

- `PATCH /me/password`가 200 + 토큰 재발급인 이유는 §4.2. `MemberInfo.loginEmail`은 `@JsonIgnore`라 재사용 불가 → `AccountInfo` 별도 record 신설.
- `/api/settings/support`는 `application.yml`의 `support.kakao-open-chat-url`을 읽어 반환하는 정적 엔드포인트. 서버는 값만 노출하고 실제 오픈채팅 연결은 프론트가 처리(기획서 비고: "오픈채팅방명 변경 확인 필요" — 프론트 확정 값 필요).
- `SecurityConfig`에 `/api/settings/support` permitAll 추가.

에러 코드:
```
MemberErrorCode  += PRIMARY_LANGUAGE_REQUIRED(400), PRIMARY_LANGUAGE_CANNOT_BE_REMOVED(400),
                    PASSWORD_NOT_SUPPORTED_FOR_PROVIDER(400), CURRENT_PASSWORD_MISMATCH(400),
                    SAME_AS_CURRENT_PASSWORD(400), INVALID_LANGUAGE(400)
AuthErrorCode    += INVALID_RESET_TOKEN(400), RESET_TOKEN_EXPIRED(410), RESET_TOKEN_ALREADY_USED(410)
```

### 3.4 필터가 추가되는 기존 API

`GET /api/community/artworks`, `GET /api/community/authors`, `GET /api/search` — 로그인 뷰어의 `postLanguages`·`adultContentVisible` 기준 서버측 필터 적용. 비로그인은 언어 필터 미적용(§5.1), 성인 콘텐츠는 기본 ON 취급.

---

## 4. 핵심 로직

### 4.1 비밀번호 재설정 토큰

```sql
-- V15
CREATE TABLE password_reset_tokens (
    id         VARCHAR(36) CHARACTER SET latin1 COLLATE latin1_bin NOT NULL,
    member_id  VARCHAR(36) CHARACTER SET latin1 COLLATE latin1_bin NOT NULL,
    token_hash CHAR(64) CHARACTER SET latin1 COLLATE latin1_bin NOT NULL,   -- SHA-256 hex
    expires_at DATETIME(6) NOT NULL,
    used_at    DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_prt_hash (token_hash),
    KEY idx_prt_member (member_id),
    KEY idx_prt_expires (expires_at)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

- **원문 토큰**: `Base64URL(SecureRandom 32바이트)` = 43자.
- **저장**: `SHA-256(원문)` hex 64자. BCrypt를 쓰지 않는 이유는 (1) 256비트 고엔트로피라 브루트포스가 무의미하고 (2) **조회를 위해 결정적 해시가 필요**하기 때문(BCrypt는 salt 때문에 인덱스 조회 불가).
- **만료 1시간, 1회성**(로그인-R08). `used_at`으로 소비 표시.
- **재요청 시 기존 미사용 토큰 전부 무효화**: `UPDATE ... SET used_at = now WHERE member_id = ? AND used_at IS NULL`.
- **소비의 원자성**: `AuthServiceImpl.consumeRefreshToken`의 검증된 패턴을 재사용 — 조건부 UPDATE의 영향 행 수로 승자를 결정한다.
  ```java
  @Modifying
  @Query("UPDATE PasswordResetToken t SET t.usedAt = :now " +
         "WHERE t.tokenHash = :hash AND t.usedAt IS NULL AND t.expiresAt > :now")
  int consume(String hash, Instant now);   // 0이면 만료/이미사용/없음
  ```
- **계정 열거(enumeration) 방지**: `request`는 계정 존재 여부·가입 방식과 무관하게 **항상 204**. Google 가입 계정에는 재설정 메일 대신 "Google 계정에서 진행하세요" 안내 메일을 보낸다(REQ-002 — 화면에도 상시 안내 문구가 있음).
- **rate limit**: 기존 `login_attempts` 테이블(`attempt_key VARCHAR(350)`)을 `pwreset:{email}` 키 프리픽스로 재사용한다. 신규 테이블 불필요, `LoginAttemptLimiter`를 일반화하거나 동일 리포지토리를 쓰는 `PasswordResetLimiter`를 신설.
- **성공 시**: 비밀번호 변경 + `refreshTokenRepository.deleteAllByMemberId(memberId)`(전 기기 로그아웃) → 로그인 화면으로 유도(로그인-R14).
- **메일 발송은 커밋 이후**(§1).

### 4.2 로그아웃 / 다른 기기 세션 만료 — 현재 구조가 설계를 가른다

**현재 구조의 정확한 실태** (`AuthServiceImpl.issueRefreshToken`):
```java
private String issueRefreshToken(String memberId) {
    refreshTokenRepository.deleteAllByMemberId(memberId);   // 기존 토큰 전부 삭제 후 신규 1개 발급
    ...
}
```
즉 **refresh token은 회원당 항상 1개**다. 기기 A에서 로그인 중 기기 B로 로그인하면 A의 토큰이 즉시 무효화된다 — 사실상 단일 세션 모델이다. 이는 "다른 기기 만료"(설정-R13) 구현을 오히려 쉽게 만든다.

| 요구 | 구현 |
|---|---|
| 로그아웃(설정-R06) | `POST /api/auth/logout` → `deleteAllByMemberId(memberId)`. body의 `refreshToken`은 선택(있으면 값 일치 검증 후 삭제, 없으면 memberId 기준 전삭제) → 204 |
| 비밀번호 변경 시 다른 기기 만료(설정-R13) | ① `deleteAllByMemberId` ② 현재 기기용 access/refresh 재발급 ③ **200 응답 바디로 새 토큰 반환** — "현재 기기 세션 유지"의 유일한 무결한 구현(기존 refresh를 남기면 "전부 만료"가 깨짐) |
| 비밀번호 재설정 완료 | 전 기기 만료, 재발급 없음 → 로그인 화면 |
| 탈퇴 | 기존 `AuthEventListener`가 `MemberDeactivatedEvent`로 이미 처리 중 — 변경 불필요 |

**멀티 디바이스 지원 여부는 이번 스코프에서 다루지 않는다** — 확장하려면 `refresh_tokens`에 `device_id`를 추가해 회원당 다중 행을 허용해야 하는데, 그 경우 "다른 기기만 로그아웃"이 `WHERE id <> :currentTokenId` 조건으로 바뀌는 별도 설계가 필요하다(§6 D1).

### 4.3 access token 즉시 무효화 문제

access token은 stateless JWT(TTL 1시간, `jwt.access-token-expiration: 3600000`)이며 `JwtAuthenticationFilter`가 DB를 조회하지 않는다. 따라서 로그아웃·비밀번호 변경 이후에도 **최대 1시간 동안 기존 access token이 유효**할 수 있다.

검토한 선택지:
1. **`jwt.access-token-expiration`을 15분(900000)으로 단축** — 코드 변경 0줄, 노출 창을 1/4로 줄임. refresh 흐름이 이미 있어 UX 영향 미미. **이번 마일스톤 채택.**
2. `members.token_epoch` + JWT `ep` 클레임 → 필터에서 대조. 정확하지만 요청당 DB 조회가 1회 추가된다. 후속 이슈로 등록.
3. Redis/DB 블랙리스트 — 인프라 추가(Redis 미보유). 비추천.

REST Docs·인계 문서에 "로그아웃 후 최대 15분간 기존 access token이 유효할 수 있음"을 명시한다.

---

## 5. 언어 세그먼트 · 성인 콘텐츠 필터

### 5.1 언어 필터 — 판정 단위는 "게시물"이 아니라 "계정"

로그인-R16 원문: "주 사용 언어가 다른 사용자 간에는 **계정·게시글**이 기본 노출되지 않는다." 세그먼트의 단위는 게시물이 아니라 **작성자 계정**이다. 그래서 `artworks`에 독립적인 언어 컬럼을 두지 않고 **작성자의 `primary_language`를 비정규화**한다.

- `GET /api/community/authors` → `MemberServiceImpl` 검색 조건에 `primaryLanguage IN (:viewerPostLanguages)` 추가. `idx_members_lang_search`가 받는다.
- `GET /api/community/artworks`, `GET /api/search` → 작품 조회는 작가 조인을 피하기 위해 `artworks.author_language`를 비정규화 컬럼으로 둔다. `primary_language`는 가입 후 변경 불가이므로 **작품 생성 시점에 한 번 복사하면 동기화 문제가 원천적으로 없다.**
  ```sql
  -- V18 (artwork 계약 변경 작업에 포함)
  ALTER TABLE artworks ADD COLUMN author_language VARCHAR(10) NULL AFTER author_id;
  CREATE INDEX idx_aw_feed_lang ON artworks (status, visibility, author_language, created_at DESC, id);
  ```
  ES `ArtworkSearchDocument`에도 `authorLanguage` keyword 필드를 추가하고 `terms` 필터로 반영한다.

**폴백 규칙**(반드시 인계 문서에 명시):
- 뷰어가 비로그인이거나 `postLanguages`가 비어 있으면 **필터 미적용**(전체 노출).
- 작성자 `primary_language`가 NULL(마이그레이션 이전 기존 회원)이면 **항상 노출**.
- 두 규칙 모두 "필터 도입으로 기존 콘텐츠가 갑자기 사라지는 것"을 막기 위한 안전장치다.

**딥링크 예외**(로그인-R16 "직접 URL 접근은 허용"): `GET /api/artworks/{id}`, `GET /api/portfolios/shared/{id}`, `GET /api/members/{handle}`에는 언어 필터를 적용하지 않는다.

### 5.2 성인 콘텐츠 표시 필터 (설정-R10, 마이페이지_작가-R21)

- **표시 OFF**: 목록 API에서 `NOT (ageRating IN (R18, G18) AND authorId <> viewerId)` — **본인 업로드분은 항상 노출**(R21 명시, 표시 설정과 무관).
- **표시 ON**: 필터 없음. 서버는 이미 `thumbAdultKey`를 응답에 포함하므로 blur 여부 판단은 클라이언트가 한다.
- **비로그인**: 기본 ON 취급(설정 기본값 ON).
- 적용 API: `/api/community/artworks`, `/api/search`, `/api/members/{handle}` 작품 그리드, `/api/portfolios/shared/*`.
- **상세**(`GET /api/artworks/{id}`)는 표시 설정과 무관하며 로그인 사용자에게 허용한다 — 본인 인증(PASS) 기반 열람 제한은 이번 범위 밖(§6 R5).

---

## 6. 미확정 항목

| # | 항목 | 상세 |
|---|---|---|
| R5 | 성인 콘텐츠 3단계 vs 2단계 | 설정-R10은 "OFF / ON+미인증 blur / ON+인증 원본" 3단계와 상세 열람 시 본인 인증을 전제하지만, 이번 스코프는 PASS 없이 "전원 미인증"으로 동작해 원본 노출 경로가 없다. 인계 문서에 이 축소를 명시해야 한다 |
| D1 | 멀티 디바이스 세션 | 현재 refresh token은 회원당 1개(§4.2). 유지 vs `device_id` 도입해 다중 세션 지원 — 이번 스코프는 유지로 결정, 확장은 후속 |
| D2 | access token 즉시 무효화 | TTL 15분 단축(채택) vs `token_epoch` 클레임(요청당 DB 조회 1회, 후속 이슈) |
| D5 | 언어 필터 비로그인 정책 | 전체 노출(채택, 딥링크·SEO 관점) vs `Accept-Language` 기반 추정 |

---

## 7. 구현 함정 (착수 전 공유)

1. **`SecurityConfig` 경로 매처 순서** — 리터럴 경로(`/api/members/me`, `/api/members/me/account`)를 `/api/members/{handle}` 같은 템플릿보다 먼저 선언해야 한다. 기존 코드에 recruit `/trash`·`/me` 관련 동일 함정 주석이 있다.
2. **`@ApplicationModuleListener`는 AFTER_COMMIT** — 트랜잭션 없이 발행한 이벤트는 버려진다. 메일 발송·비밀번호 재설정 이벤트 발행부는 반드시 `@Transactional` 안에서 발행한다.
3. **`src/test/resources/application.yml`이 main `application.yml`을 완전 대체**한다. `resend.*`, `support.*` 신규 설정키를 테스트 yml에도 반드시 추가해야 컨텍스트가 뜬다.
4. **assigned String ID + `Persistable`** — `PasswordResetToken` 신규 엔티티는 `RefreshToken`과 동일하게 `Persistable<String>` + `@Transient boolean isNew` 패턴을 따른다. 빠뜨리면 `save()`가 매번 `merge()`(선행 SELECT)로 동작한다.
