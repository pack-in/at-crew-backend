# 이메일 자체 인증 재설계 (Firebase → Custom)

> 작성일: 2026-06-12 (rev.2 — 동일자 개정)
> 상태: 설계안 (구현 전)
> 범위: 이메일 로그인·회원가입을 Firebase 의존에서 자체 인증(BCrypt)으로 전환. Google 로그인은 Firebase ID Token 방식 유지.

> **rev.2 개정 요약**
> 1. §5 결정 번복 — 에러 구분 노출(Figma 우선) → **보안 우선 통합 401**로 복구. 피그마의 구체적 에러 메시지는 "피그마가 아직 반영 못 한 것"으로 확인됨.
> 2. 계정 식별 단위 변경 — `loginEmail` 단독 → **`(loginEmail, authProvider)` 복합 키**. 같은 이메일이라도 provider가 다르면 완전히 별개의 계정. provider 불일치 에러 자체가 소멸.
> 3. 약관에 `thirdPartyProvision`([필수] 개인정보 제3자 제공) 추가 확정.
> 4. **§10 보안 구현 체크리스트** 신규 — 자체 이메일 인증 최초 구현에서 놓치기 쉬운 실무 보안 항목 전수 정리.

---

## 0. 설계 요약 (TL;DR)

| 항목 | 결정 |
|------|------|
| 엔드포인트 | 이메일/Google **분리** — `/api/auth/email/*`, `/api/auth/google/*` |
| **계정 식별 단위** | **`(loginEmail, authProvider)` 복합 키** — 같은 이메일 + 다른 provider = 다른 계정 (rev.2 변경) |
| 비밀번호 저장 | `Member` 문서에 `passwordHash` 필드 추가 (EMAIL 전용, nullable) — 별도 credential 문서 분리하지 않음 |
| 비밀번호 검증 위치 | member 모듈이 해싱·매칭 담당 (`MemberService.verifyPassword`), auth 모듈은 오케스트레이션 |
| PasswordEncoder | `common.security.SecurityConfig`에 BCrypt 빈 등록, 사용은 member.internal |
| **보안 vs UX 충돌** | **보안 우선** — 로그인 실패 원인(미가입·탈퇴·비밀번호 오류)을 `AUTHENTICATION_FAILED(401)` 단일 코드·단일 메시지로 통합. 더미 BCrypt로 timing attack 방지 (rev.2 변경) |
| 이메일 인증 | 가입 시 **즉시 가입 허용** (인증 단계 없음, Figma 의도 준수) |
| 약관 | 필수 3종 — 이용약관·개인정보처리방침·**개인정보 제3자 제공(`thirdPartyProvision`, 신규)** + 선택 마케팅 |
| 비밀번호 재설정 | 토큰 링크 방식 초안 — **별도 설계 필요** 플래그 (이메일 발송 인프라 부재, Figma 화면 미확인) |
| 기존 Firebase 이메일 회원 | `passwordHash == null` → 첫 로그인 시 비밀번호 재설정 유도 (옵션 B: scrypt 해시 이관도 검토) |
| FirebaseVerifier | Google 전용으로 축소 (`sign_in_provider == "google.com"`만 허용) |
| 보안 체크리스트 | §10 — 해싱·timing·rate limit·토큰·전송·로깅·Security 설정 등 14개 항목, 구현 PR에서 전수 점검 |

> 참고: CLAUDE.md에는 JPA로 표기되어 있으나 실제 저장소는 **MongoDB**(`spring-data-mongodb`)이다. 본 문서는 실제 코드 기준(MongoDB Document)으로 작성한다.

---

## 1. API 엔드포인트 재설계

### 1.1 결정: 이메일/Google 분리 엔드포인트

**기존**

| 메서드 | 경로 | 비고 |
|--------|------|------|
| POST | `/api/auth/login` | firebaseIdToken 단일 입력 (이메일·Google 공용) |
| POST | `/api/auth/register` | firebaseIdToken + 약관 |
| POST | `/api/auth/refresh` | 유지 |

**신규**

| 메서드 | 경로 | 설명 |
|--------|------|------|
| POST | `/api/auth/email/login` | 이메일+비밀번호 로그인 |
| POST | `/api/auth/email/register` | 이메일 회원가입 |
| POST | `/api/auth/google/login` | Google 로그인 (Firebase ID Token) |
| POST | `/api/auth/google/register` | Google 회원가입 (Firebase ID Token + 약관) |
| POST | `/api/auth/refresh` | 토큰 갱신 (변경 없음) |
| POST | `/api/auth/email/password-reset/request` | 재설정 메일 요청 (§7, 별도 설계 필요) |
| POST | `/api/auth/email/password-reset/confirm` | 재설정 확정 (§7, 별도 설계 필요) |

**근거**
- 두 방식의 Request Body 스키마가 완전히 다름 (`email+password` vs `firebaseIdToken`). 통합 엔드포인트는 `oneOf` 스키마·조건부 validation이 필요해 Swagger 문서와 Bean Validation이 모두 지저분해진다.
- 에러 코드 집합도 다름 (이메일: 인증 실패 통합 401 / Google: 토큰 검증 오류).
- 향후 카카오·네이버 등 provider 확장 시 `/api/auth/{provider}/login` 패턴으로 자연 확장. **rev.2의 복합 키 식별(§3.5)과도 정합** — provider가 URL 경로에서 결정되므로 요청마다 조회 키가 명확하다.

**트레이드오프**
- 기존 `/api/auth/login`, `/api/auth/register`를 사용하는 클라이언트가 있다면 깨짐. → 현재 정식 출시 전이므로 **하위 호환 없이 교체**한다. (운영 중이었다면 기존 경로를 Google 전용으로 deprecate 유지하는 안을 택했을 것)

### 1.2 Request / Response / 에러 정의

#### POST /api/auth/email/login

```json
// Request
{ "email": "user@example.com", "password": "abcd123!" }
```

| 필드 | 검증 | 실패 메시지 (Figma #1~#4) |
|------|------|---------------------------|
| email | `@NotBlank` | "이메일을 입력해주세요" |
| email | `@Email` | "올바른 이메일 형식으로 입력해주세요" |
| password | `@NotBlank` | "비밀번호를 입력해주세요" |
| password | `@Pattern` (§1.3) | "비밀번호는 영문, 숫자, 특수문자를 포함해 8자 이상 입력해주세요" |

- 200: `ApiResponse<AuthInfo>` (accessToken, refreshToken, member, isNewMember=false)
- 400 `COMMON_INVALID_INPUT`: Bean Validation 실패 (기존 `GlobalExceptionHandler` 정책 유지 — 응답 메시지는 `"필드명: 메시지"` 형식). **rev.2에서도 유지** — 입력 형식 오류는 계정 존재 여부와 무관하므로 노출해도 보안 손실이 없다.
- 401 `AUTHENTICATION_FAILED`: "이메일 또는 비밀번호를 다시 확인해주세요" — **미가입·탈퇴·비밀번호 오류를 모두 이 하나로 통합** (rev.2, §5)
- 428 `PASSWORD_RESET_REQUIRED`: 마이그레이션 회원 — "비밀번호 재설정이 필요해요" (§8)
- 429 `TOO_MANY_ATTEMPTS`: rate limit 초과 (§4.4, §10.3)

> **rev.1과의 차이**: 404 `MEMBER_NOT_REGISTERED`(#5), 409 `PROVIDER_MISMATCH_GOOGLE`(#7), 403 `ACCOUNT_DEACTIVATED`(#8)를 모두 제거하고 401 하나로 통합했다. 근거는 §5. 또한 #7은 코드 차원에서도 소멸 — 복합 키 식별(§3.5)에서는 "Google로 가입된 같은 이메일"이 단순히 '다른 계정'이므로 EMAIL 조회 시 "회원 없음"일 뿐이다.
>
> **428의 예외적 노출**: `PASSWORD_RESET_REQUIRED`는 계정 존재를 노출하지만, (1) 대상이 마이그레이션 회원이라는 한정된 집단이고 (2) 이 응답 없이는 해당 회원이 영원히 로그인 불가하므로 기능상 불가피하다. rate limiting(§4.4)이 동일하게 적용되어 대량 탐색 비용을 올린다. 4단계(§8.3) 완료 후 마이그레이션 회원이 소진되면 이 코드도 제거한다.

#### POST /api/auth/email/register

```json
// Request
{
  "email": "user@example.com",
  "password": "abcd123!",
  "passwordConfirm": "abcd123!",
  "name": "활동명",
  "agreeService": true,
  "agreePrivacy": true,
  "agreeThirdParty": true,
  "agreeMarketing": false
}
```

| 필드 | 검증 |
|------|------|
| email | `@NotBlank`, `@Email` |
| password | `@NotBlank`, `@Pattern` (§1.3), `@Size(max=64)` |
| passwordConfirm | password와 일치 (record-level `@AssertTrue` 또는 커스텀 검증) |
| name | `@NotBlank`, `@Size(max=16)` (기존 유지) |
| agreeService / agreePrivacy / agreeThirdParty | 필수 — 미동의 시 400 `TERMS_NOT_AGREED` (도메인 검증, 기존 패턴 유지) |

- 201: `ApiResponse<AuthInfo>` (isNewMember=true)
- 409 `DUPLICATE_EMAIL`: "이미 가입된 이메일입니다" — **중복 판정 기준이 `(email, EMAIL)` 복합 키로 변경** (rev.2). 같은 이메일의 GOOGLE 계정이 있어도 이메일 가입은 허용된다.
- 400 `COMMON_INVALID_INPUT`: Bean Validation 실패

> `passwordConfirm`은 서버에서도 검증한다. 클라이언트 검증만 믿으면 API 직접 호출 시 오타 비밀번호로 가입될 수 있음. 단 DB에는 당연히 저장하지 않는다.

> **가입 409와 enumeration**: 가입 API의 `DUPLICATE_EMAIL`은 "해당 이메일의 EMAIL 계정 존재"를 노출한다. 이는 가입 UX상 포기할 수 없는 항목(Figma·업계 표준 동일)이므로 유지하되, 가입 엔드포인트에도 rate limiting을 적용해 대량 탐색을 차단한다(§10.14). 로그인의 통합 401 정책(§5)과 보호 수준이 다른 것은 의도된 차등이다 — 로그인은 credential stuffing의 직접 표적이므로 더 엄격하게 막는다.

> **약관 항목 변경 확정** (rev.2): Figma 기준 필수 약관 3종(이용약관·개인정보처리방침·**개인정보 제3자 제공**). 현재 `TermsAgreement`는 2종+마케팅만 보유 → `thirdPartyProvision` 필드 추가 (§3.4).

#### POST /api/auth/google/login

```json
{ "firebaseIdToken": "eyJ..." }
```

- 200: `ApiResponse<AuthInfo>`
- 401 `INVALID_FIREBASE_TOKEN`: 토큰 검증 실패
- 404 `MEMBER_NOT_REGISTERED`: `(email, GOOGLE)` 계정 없음 → 프론트가 가입 화면으로 라우팅

> **Google 로그인에서 404를 유지하는 이유 (통합 401 정책의 의도적 예외)**: 유효한 Firebase ID Token은 해당 Google 계정(=이메일)의 소유를 이미 증명한다. 소유자 본인에게 "이 이메일로 가입된 계정 없음"을 알려주는 것은 enumeration이 아니며, 프론트가 가입 플로우로 자연 전환하기 위해 이 신호가 필수다. §5의 통합 401은 **비밀번호 기반 로그인**(토큰 없이 누구나 임의 이메일을 시도 가능)에 적용되는 정책이다.

> **rev.1과의 차이**: 409 `PROVIDER_MISMATCH_EMAIL` 제거. 복합 키 식별에서 "이메일로 가입한 같은 주소"는 별개 계정이므로, GOOGLE 조회 결과 없음 → 404 → Google 가입 진행이 정상 흐름이다. 403 `ACCOUNT_DEACTIVATED`도 제거 — 탈퇴 시 `loginEmail`이 null로 클리어되므로 조회 자체가 안 되고, 탈퇴 정책상 재가입이 허용되므로 404 → 재가입이 올바른 동선이다.

#### POST /api/auth/google/register

```json
{ "firebaseIdToken": "eyJ...", "name": "활동명",
  "agreeService": true, "agreePrivacy": true, "agreeThirdParty": true, "agreeMarketing": false }
```

- 기존 `register`와 동일하되 firebaseIdToken은 Google 전용으로 검증. Google 계정은 이메일 소유가 보증되므로 `emailVerified` 체크는 방어적으로 유지하되 사실상 항상 통과.
- 409 `DUPLICATE_EMAIL` 판정 기준: `(email, GOOGLE)` 복합 키.

### 1.3 비밀번호 정책

```
^(?=.*[A-Za-z])(?=.*\d)(?=.*[^A-Za-z\d\s]).{8,64}$
```

- 영문·숫자·특수문자 각 1자 이상, 8자 이상 (Figma placeholder 기준)
- **상한 64자**: BCrypt는 72바이트 초과분을 무시하므로 상한을 명시해 silent truncation을 방지 (§10.1)
- 공백 문자는 특수문자로 인정하지 않음

> 로그인 시에도 동일 `@Pattern`을 적용한다(Figma #4가 로그인 화면 에러로 명시됨). 정책을 만족하지 않는 비밀번호는 어차피 가입이 불가능하므로 DB 조회 전에 차단해도 정보 노출이 없고, 불필요한 BCrypt 연산을 줄인다(§10.4). 단 §8의 해시 이관(옵션 B)을 채택해 라이트 시절 약한 비밀번호가 유입될 경우 이 검증은 로그인에서 제거해야 한다.

---

## 2. AuthService 공개 인터페이스 변경

```java
public interface AuthService {

    AuthInfo loginWithEmail(EmailLoginCommand command);

    AuthInfo loginWithGoogle(String firebaseIdToken);

    AuthInfo registerWithEmail(EmailRegisterCommand command);

    AuthInfo registerWithGoogle(GoogleRegisterCommand command);

    AuthInfo refresh(String refreshToken);   // 변경 없음
}
```

```java
// auth 공개 패키지 (com.atcrew.auth)
public record EmailLoginCommand(String email, String password) {}

public record EmailRegisterCommand(
        String email,
        String password,          // raw — 해싱은 member 모듈에서 (§4.1)
        String name,
        boolean agreeService,
        boolean agreePrivacy,
        boolean agreeThirdParty,  // 신규 (rev.2 확정) — [필수] 개인정보 제3자 제공
        boolean agreeMarketing
) {}

public record GoogleRegisterCommand(
        String firebaseIdToken,
        String name,
        boolean agreeService,
        boolean agreePrivacy,
        boolean agreeThirdParty,  // 신규 (rev.2 확정)
        boolean agreeMarketing
) {}

// 기존 RegisterCommand는 삭제
```

**근거**
- `login(String firebaseIdToken)`처럼 의미가 모호한 단일 String 파라미터를 없애고, 메서드명으로 인증 수단을 드러냄.
- 통합 `login(LoginCommand)` + 내부 분기 안도 가능하지만, 커맨드에 nullable 필드(`password` 또는 `firebaseIdToken` 중 하나만 채움)가 생겨 호출자가 잘못 조합할 여지가 있음. 컴파일 타임에 구분되는 분리 메서드가 안전.
- `EmailLoginCommand`·`EmailRegisterCommand`는 `toString()`을 오버라이드해 password를 마스킹한다 (§10.8).

**트레이드오프**: 메서드 수 증가. provider가 늘면 인터페이스가 비대해질 수 있으나, provider별 입력·에러가 본질적으로 다르므로 허용한다.

### 2.1 이메일 로그인 검증 순서 (AuthServiceImpl) — rev.2 전면 재작성

```
1. (컨트롤러) Bean Validation — Figma #1~#4 → 400
2. (필터/인터셉터) LoginAttemptLimiter 선검사 — 이메일+IP 기준 차단 중이면 429 (§4.4)
3. result = memberService.verifyPassword(email, password)
   — member.internal에서 (loginEmail=email, authProvider=EMAIL) 복합 키로 활성 회원 조회
   — 회원 부재 시: ★ 더미 BCrypt 연산을 반드시 수행한 뒤 MISMATCHED 반환 ★
     (회원 존재+비밀번호 오답 경로와 응답 시간이 같아야 timing attack으로
      계정 존재 여부를 추측할 수 없다 — §10.2 코드 패턴 참조)
   — 회원 존재 + passwordHash == null: PASSWORD_NOT_SET 반환 (마이그레이션 회원)
   — 회원 존재 + 해시 매칭 실패: MISMATCHED 반환
4. result == PASSWORD_NOT_SET → 428 PASSWORD_RESET_REQUIRED (§8)
5. result == MISMATCHED → 실패 기록(LoginAttemptLimiter) 후 401 AUTHENTICATION_FAILED
   — 미가입·탈퇴·비밀번호 오답 모두 이 경로. 응답으로는 구분 불가, 서버 로그로만 구분(§10.8)
6. result == MATCHED → member = memberService.findByLoginEmailAndProvider(email, EMAIL)
7. 성공 → recordLogin, 실패 카운터 리셋, JWT 발급, refresh token 회전 (기존 로직 유지)
```

> **rev.1과의 차이 및 근거**
> - rev.1은 `existsByLoginEmail` → 탈퇴 분기 → provider 분기 → 비밀번호 검증의 4단 분기로 각기 다른 에러를 내렸다. rev.2는 §5의 보안 우선 결정에 따라 **분기 자체를 제거**한다 — 응답 경로가 적을수록 사이드채널도 적다.
> - 탈퇴 여부 확인(`isDeactivatedEmail`)은 로그인 **응답 분기에서 제거**. 탈퇴 회원은 `loginEmail`이 null 클리어되어 있어 3단계 조회에서 자연히 "부재"가 되고, 응답은 동일한 401이다. 탈퇴 계정 로그인 시도 감지가 필요하면 `deletedLoginEmail` 기반 **비동기 감사 로깅**으로만 수행한다 — 응답 경로의 쿼리 수를 바꾸면 timing 균일화가 깨지므로 동기 분기에 넣지 않는다.
> - provider 불일치 분기는 **개념적으로 소멸** (§3.5). `(email, EMAIL)` 조회에 GOOGLE 계정은 걸리지 않는다.
> - rev.1 §2.1 말미의 "더미 해시 비교는 불필요" 판단은 구분 노출 정책의 부속이었다. 통합 401로 복구했으므로 **더미 BCrypt는 필수**로 되돌린다.

```java
// member 공개 패키지 — verifyPassword 결과 타입
public enum PasswordVerificationResult {
    MATCHED,           // 해시 매칭 성공
    MISMATCHED,        // 회원 부재(더미 연산 수행됨) 또는 해시 불일치 — 호출자는 구분 불가
    PASSWORD_NOT_SET   // 활성 EMAIL 회원이나 passwordHash == null (마이그레이션, §8)
}
```

> `MISMATCHED`가 회원 부재와 비밀번호 오답을 **의도적으로 합쳐서** 반환하는 것이 이 설계의 핵심이다. auth 모듈조차 둘을 구분할 수 없게 만들어, 실수로 구분 응답을 내릴 가능성을 타입 수준에서 차단한다. (부재/오답 구분이 필요한 감사 로그는 member.internal에서 직접 기록)

---

## 3. Member 도메인 변경

### 3.1 결정: `Member` 문서에 `passwordHash` 직접 추가 (별도 credential 문서 분리 안 함)

```java
@Document(collection = "members")
public class Member {
    // ... 기존 필드 ...

    /**
     * BCrypt 해시. EMAIL provider 전용, GOOGLE 회원·마이그레이션 미전환 회원은 null.
     * 절대 MemberInfo 등 공개 레코드로 노출하지 않는다. toString 제외 (§10.8).
     */
    private String passwordHash;
}
```

**대안 비교**

| | A. Member.passwordHash (채택) | B. auth.internal에 EmailCredential 문서 분리 |
|---|---|---|
| 모듈 순수성 | member가 자격증명 일부 보유 | auth가 자격증명 완전 소유 (이상적) |
| 쓰기 원자성 | 단일 문서 — MongoDB 단일 문서 원자성으로 충분 | 가입 시 2개 문서 저장 — standalone MongoDB에서 멀티 도큐먼트 트랜잭션 불가, 보상 로직 필요 |
| 탈퇴 처리 | `deactivate()`에서 `passwordHash = null` 한 줄 | `MemberDeactivatedEvent` 리스너로 credential 삭제 — 이벤트 유실 시 고아 자격증명 |
| 라이트 마이그레이션 | 회원 1문서 = 1레코드, 이관 단순 | 회원당 2문서 이관 |

**근거**: 모듈 경계 원칙은 "직접 의존 금지·공개 인터페이스 통신"이지 "인증 데이터의 물리 분리"가 아니다. `passwordHash`를 member 내부 도메인에 숨기고 공개 인터페이스로는 `verifyPassword`(결과 enum)만 노출하면 캡슐화는 유지된다. 반면 B안은 MongoDB 환경에서 일관성 비용이 실질적이다.

**트레이드오프**: 비밀번호 변경 이력·잠금 횟수 등 자격증명 메타데이터가 늘어나면 Member가 비대해진다. 그 시점에 B안으로 분리하는 리팩토링 여지를 남긴다 (공개 인터페이스 시그니처는 그대로 유지 가능하므로 분리 비용 낮음).

### 3.2 Member 도메인 메서드

```java
public static Member registerWithEmail(String loginEmail, String handle, String name,
        String passwordHash, TermsAgreement termsAgreement) { ... } // authProvider = EMAIL 고정

// 기존 register(... AuthProvider ...)는 Google 전용으로 유지하거나 registerWithGoogle로 rename

public boolean matchesPassword(String rawPassword, PasswordEncoder encoder) {
    return passwordHash != null && encoder.matches(rawPassword, passwordHash);
}

public void changePassword(String newPasswordHash) { assertActive(); this.passwordHash = newPasswordHash; }

public boolean hasPassword() { return passwordHash != null; }

public void deactivate() {
    // 기존 로직 + passwordHash = null (탈퇴 계정에 해시를 남길 이유 없음 — 개인정보 최소 보유)
    // 기존과 동일: loginEmail → deletedLoginEmail 백업 후 null 클리어
}
```

### 3.3 RegisterMemberCommand / MemberService 변경 — rev.2 복합 키 반영

```java
// member 공개 패키지
public record RegisterMemberCommand(
        String loginEmail,
        String name,
        AuthProvider authProvider,
        String rawPassword,        // EMAIL일 때 필수, GOOGLE이면 null — 해싱은 member.internal에서 수행
        boolean agreeService,
        boolean agreePrivacy,
        boolean agreeThirdParty,   // 신규 (rev.2 확정)
        boolean agreeMarketing
) {}
```

```java
public interface MemberService {
    // ── rev.2: loginEmail 단독 조회 메서드를 복합 키 메서드로 교체 ──
    // 삭제: existsByLoginEmail(String) / findByLoginEmail(String) / isDeactivatedEmail(String)

    /** (loginEmail, authProvider) 복합 키로 활성 회원 존재 확인. 가입 중복 검사용. */
    boolean existsByLoginEmailAndProvider(String loginEmail, AuthProvider authProvider);

    /** (loginEmail, authProvider) 복합 키로 활성 회원 조회. */
    MemberInfo findByLoginEmailAndProvider(String loginEmail, AuthProvider authProvider);

    /** 탈퇴 회원 여부 — (deletedLoginEmail, authProvider) 기준 (감사 로깅·재가입 정책용). */
    boolean isDeactivatedEmail(String loginEmail, AuthProvider authProvider);

    /**
     * EMAIL 활성 회원의 비밀번호 매칭. 내부적으로 (loginEmail, EMAIL) 복합 키 조회.
     * 회원 부재 시 더미 BCrypt 연산 후 MISMATCHED 반환 — timing-safe 보장 (§10.2).
     */
    PasswordVerificationResult verifyPassword(String loginEmail, String rawPassword);

    /** 비밀번호 재설정 확정 시 사용 (§7). 변경 성공 시 호출자(auth)가 전 refresh token 폐기 (§10.6). */
    void changePassword(String memberId, String rawNewPassword);
}
```

> **rev.1과의 차이**: `existsByLoginEmail`·`findByLoginEmail`은 "이메일 1개 = 계정 1개" 전제의 메서드였다. rev.2에서 같은 이메일로 EMAIL/GOOGLE 두 계정이 공존할 수 있으므로(§3.5) 단독 이메일 조회는 **결과가 모호해져서 위험**하다(어느 계정?). 모호한 메서드를 남겨두면 반드시 누군가 잘못 쓰므로 시그니처에서 제거한다. `hasPassword`는 `verifyPassword`의 `PASSWORD_NOT_SET` 결과로 흡수되어 별도 메서드가 불필요해졌다 (쿼리 1회 절감 + 분기 누락 방지).
>
> `MemberRepository` 파생 쿼리도 동일하게 교체: `findByLoginEmailAndAuthProvider`, `existsByLoginEmailAndAuthProvider`, `existsByDeletedLoginEmailAndAuthProvider`.

**raw password를 모듈 경계로 넘기는 이유**: 해싱·매칭을 한 모듈(member)에 모아 인코더 파라미터(BCrypt strength) 변경·재해싱 정책을 한 곳에서 관리하기 위함. auth가 해싱하고 member가 매칭하면 인코딩 책임이 분산된다. 인프로세스 호출이므로 raw password 전달 자체의 추가 노출면은 없다 (단 커맨드 객체의 `toString()` 마스킹 필수 — §10.8).

### 3.4 TermsAgreement 변경

```java
public record TermsAgreement(
        boolean serviceTerms,
        boolean privacyPolicy,
        boolean thirdPartyProvision,   // 신규 — [필수] 개인정보 제3자 제공 동의
        boolean marketingNotification,
        Instant agreedAt
) { ... }
```

- `Member.register` / `registerWithEmail`의 **필수 약관 검증에 `thirdPartyProvision` 포함** — `serviceTerms && privacyPolicy && thirdPartyProvision`이 모두 true가 아니면 `TERMS_NOT_AGREED`.
- 기존 가입 데이터(필드 부재)는 MongoDB 특성상 `false`로 역직렬화됨 → 마이그레이션 시 기존 회원의 제3자 제공 동의를 어떻게 볼지 **정책 확인 필요** (라이트 약관에 해당 조항이 있었는지에 따라 backfill 여부 결정).

### 3.5 계정 식별 단위 변경: `(loginEmail, authProvider)` 복합 키 — rev.2 신규

**결정**: `john@gmail.com`으로 이메일 가입한 사용자와 `john@gmail.com`으로 Google 가입한 사용자는 **완전히 별개의 계정**이다.

**인덱스 변경**

```java
@Document(collection = "members")
@CompoundIndex(name = "idx_login_email_provider",
        def = "{'loginEmail': 1, 'authProvider': 1}",
        unique = true, sparse = true)   // 탈퇴 회원(loginEmail=null) 다수 공존 허용
public class Member {

    private String loginEmail;          // 기존 @Indexed(unique=true, sparse=true) 단독 인덱스 제거

    private AuthProvider authProvider;
}
```

- 기존: `loginEmail` 단독 unique(sparse) → **신규: `(loginEmail, authProvider)` 복합 unique(sparse)**.
- compound index의 sparse는 "인덱스 필드가 모두 부재"일 때만 제외하므로, `authProvider`는 항상 존재하는 현 스키마에서는 **partial index가 더 정확**하다. Spring 어노테이션 대신 마이그레이션 스크립트로 생성 권장:

```javascript
db.members.createIndex(
  { loginEmail: 1, authProvider: 1 },
  { unique: true,
    partialFilterExpression: { loginEmail: { $type: "string" } },  // 탈퇴(loginEmail=null) 문서 제외
    name: "idx_login_email_provider" }
)
db.members.dropIndex("loginEmail")   // 기존 단독 unique 인덱스 제거 — 새 인덱스 생성 성공 확인 후
```

**파급 효과**

| 영역 | 변화 |
|------|------|
| 로그인 | `(email, 경로의 provider)` 조회 — 다른 provider 계정은 "없는 회원"과 동일 취급 |
| provider 불일치 에러 | **개념 자체가 소멸** — `PROVIDER_MISMATCH_GOOGLE`/`PROVIDER_MISMATCH_EMAIL` 코드 삭제 (§5.4) |
| 가입 중복 검사 | `existsByLoginEmailAndProvider(email, provider)` — 같은 이메일 타 provider 계정이 있어도 가입 허용 |
| 탈퇴 조회 | `isDeactivatedEmail(email, provider)` — `(deletedLoginEmail, authProvider)` 기준 |
| 알림·연락 이메일 | 같은 주소로 두 계정의 메일이 갈 수 있음 — 메일 템플릿에 계정 구분(가입 방식) 표기 권장 |

**근거**
- provider 불일치 분기는 rev.1에서 에러 코드 2종 + Figma 미정의 문구 확인 + 검증 순서 분기를 유발하는 **복잡도의 근원**이었다. 식별 단위를 바꾸면 이 분기가 통째로 사라진다.
- §5의 보안 정책과 정합: "Google로 가입하셨네요"라는 응답 자체가 가입 경로 노출이었다. 복합 키에서는 노출할 정보가 애초에 없다.
- provider 확장(카카오·네이버) 시에도 계정 모델이 그대로 확장된다 — 이메일 충돌 걱정 없이 provider별 독립 계정.

**트레이드오프**
- 동일인이 이메일·Google로 두 계정을 만들 수 있다 — 사용자가 "내 계정이 두 개"임을 인지하지 못하면 혼란 가능. 계정 통합(account linking) 기능이 필요해지면 별도 설계 필요. 현 단계에서는 출시 전 단순성을 우선한다.
- 라이트 마이그레이션 매핑 확인 필요: 라이트에서 동일 이메일이 단일 계정이었다면 이관 시 provider 1개로 매핑되므로 충돌 없음 (§8.4).

---

## 4. 신규 컴포넌트

### 4.1 PasswordEncoder (BCrypt)

- **빈 등록**: `common.security.SecurityConfig`에 `@Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }`
  - 근거: JwtProvider 등 인증 인프라가 이미 `common.security`에 있고, common은 모든 모듈이 의존 가능한 공유 계층. `spring-boot-starter-security`가 이미 의존성에 존재하므로 추가 라이브러리 불필요.
- **사용처**: member.internal (해싱·매칭·더미 연산), 추후 §7의 재설정 토큰 해싱에 auth.internal도 사용 가능.
- strength 선택 근거는 §10.1.

### 4.2 EmailCredentialService — **불필요 (현 시점)**

§3.1에서 A안을 채택했으므로 별도 내부 컴포넌트는 만들지 않는다. `MemberService.verifyPassword/changePassword`가 그 역할을 흡수한다. 자격증명 메타데이터(잠금·이력)가 생기는 시점에 도입을 재검토.

### 4.3 FirebaseVerifier → Google 전용 축소

```java
// auth.internal.infra.firebase
interface GoogleTokenVerifier {           // FirebaseVerifier에서 rename
    GoogleUser verify(String idToken);    // FirebaseUser → GoogleUser (provider 필드 제거)
}

record GoogleUser(String email, boolean emailVerified) {}
```

- `extractProvider`에서 `"password"` 분기 **제거** — `"google.com"`이 아니면 `UNSUPPORTED_AUTH_PROVIDER`. 이로써 Firebase 이메일 계정 토큰으로 로그인하는 우회 경로를 차단.
- `FirebaseConfig`/`FirebaseFallbackConfig`는 유지 (Google 로그인이 계속 Firebase Admin SDK를 사용).
- rename은 선택 사항이나, "Firebase = Google 로그인 인프라"라는 축소된 역할을 이름에 반영하는 것을 권장.

### 4.4 LoginAttemptLimiter (auth.internal)

- 이메일+IP 기준 실패 횟수 추적, 임계치 초과 시 일시 차단(429). 상세 파라미터는 §10.3.
- 저장소: MongoDB TTL 컬렉션 (refresh token과 동일 패턴) — Redis 도입 전까지의 현실적 선택.
- **rev.1과의 차이**: rev.1에서는 "에러 구분 노출의 필수 보완책"이라는 조건부 위상이었으나, rev.2에서는 **정책과 무관한 기본 방어선**이다. 통합 401로 enumeration oracle을 없애도 credential stuffing(유출 계정 목록 대입)은 rate limiting 없이는 막을 수 없다. 이메일 로그인 첫 배포 PR에 반드시 포함.

---

## 5. 에러 코드 재설계 — 보안 우선 통합 (rev.2 결정 번복)

### 5.1 충돌 내용과 결정 변경 경위

현재 코드는 미가입·탈퇴·provider 불일치를 모두 `AUTHENTICATION_FAILED(401)`로 통합해 계정 존재 여부를 숨긴다 (enumeration 방어). Figma는 #5(미가입)·#7(provider 불일치)·#8(탈퇴)을 구분 노출하도록 그려져 있어, **rev.1은 Figma UX를 우선해 구분 노출을 택했다**.

**rev.2에서 번복**: Figma의 구체적 에러 메시지는 **"피그마가 아직 반영 못 한 것"**으로 확인됐다. 실제 정책은 보안 우선이다. 따라서 현재 코드의 통합 401 정책을 **유지·강화**한다.

### 5.2 결정: 로그인 실패는 `AUTHENTICATION_FAILED(401)` 단일 코드·단일 메시지

- 미가입 · 탈퇴 · 잘못된 비밀번호 · (개념상) provider 불일치 → 전부 **401 `AUTHENTICATION_FAILED`**
- 클라이언트 노출 메시지는 단 하나: **"이메일 또는 비밀번호를 다시 확인해주세요"**
- Bean Validation 400(#1~#4)은 유지 — 입력 형식 오류는 계정 정보와 무관
- `MEMBER_NOT_REGISTERED`·`ACCOUNT_DEACTIVATED`는 **이메일 로그인용으로 사용 금지** (Google 로그인의 404, 가입 API 등 enumeration 위험이 없는 맥락에서는 사용 가능 — §1.2)
- `PROVIDER_MISMATCH_GOOGLE`·`PROVIDER_MISMATCH_EMAIL`은 복합 키 식별(§3.5)로 **개념 자체가 소멸 — 코드 삭제**

**근거**
1. **로그인 엔드포인트는 credential stuffing의 직접 표적**이다. "가입 안 된 이메일"을 알려주면 공격자는 유효 계정 목록을 선별한 뒤 비밀번호 대입에 집중할 수 있다. 통합 401은 이 선별 단계를 차단한다.
2. rev.1의 핵심 논거였던 "가입 409가 어차피 노출하므로 로그인만 숨겨도 무의미"는 **절반만 맞다**. 가입 API는 CAPTCHA·rate limit 등으로 별도 통제 가능하고, 호출 패턴이 달라 탐지가 쉽다. 로그인까지 oracle을 제공하면 공격면이 두 배가 될 뿐 "일관성"의 실익이 없다.
3. UX 손실의 실제 크기: 정당 사용자가 미가입 상태에서 로그인을 시도하는 경우, 통합 메시지를 보고 비밀번호 재시도 → 재설정 시도(§7에서 발송 메일로 미가입 안내 가능) → 가입으로 흐른다. 마찰은 있으나 계정 안전과의 교환으로 수용한다.
4. **timing attack까지 막아야 정책이 완성된다**: 메시지를 통일해도 "회원 없음 = BCrypt 생략 = 빠른 응답"이면 응답 시간으로 구분된다. 회원 부재 시 더미 BCrypt 연산이 필수 (§2.1, §10.2).

**Figma 후속 조치**: #5·#7·#8 화면 문구는 디자인 수정 대상이다. 로그인 실패 시 단일 문구("이메일 또는 비밀번호를 다시 확인해주세요")로 통일하도록 **디자인팀에 변경 요청** — 확인 필요 항목 (§9).

**보완 장치**
1. `LoginAttemptLimiter` (§4.4) — brute-force·stuffing의 실질 비용 인상.
2. 비밀번호 재설정 요청(§7)은 가입 여부 무관 동일 200 응답 (§10.14).
3. 실패 사유(부재/탈퇴/오답)는 서버 로그에 `LogMask` 적용해 기록 — 감사 추적은 유지하되 응답에는 싣지 않는다 (§10.8).

### 5.3 Figma 메시지 매핑표 (rev.2)

| # | Figma 메시지 | 처리 | 코드 | HTTP |
|---|--------------|------|------|------|
| 1 | 이메일을 입력해주세요 | Bean Validation `@NotBlank` | `COMMON_INVALID_INPUT` | 400 |
| 2 | 비밀번호를 입력해주세요 | Bean Validation `@NotBlank` | `COMMON_INVALID_INPUT` | 400 |
| 3 | 올바른 이메일 형식으로 입력해주세요 | Bean Validation `@Email` | `COMMON_INVALID_INPUT` | 400 |
| 4 | 비밀번호는 영문, 숫자, 특수문자를 포함해 8자 이상 입력해주세요 | Bean Validation `@Pattern` | `COMMON_INVALID_INPUT` | 400 |
| 5 | ~~가입되지 않은 이메일이에요~~ | **폐기 — 디자인 수정 요청** (#6으로 통합) | `AUTHENTICATION_FAILED` | 401 |
| 6 | 이메일 또는 비밀번호를 다시 확인해주세요 | **로그인 실패 유일 메시지** | `AUTHENTICATION_FAILED` | 401 |
| 7 | ~~Google 계정으로 가입한 회원은...~~ | **폐기 — 복합 키로 개념 소멸 (§3.5) + 디자인 수정 요청** | `AUTHENTICATION_FAILED` | 401 |
| 8 | ~~탈퇴한 계정이에요~~ | **폐기 — 디자인 수정 요청** (#6으로 통합) | `AUTHENTICATION_FAILED` | 401 |

> #1~#4는 1차적으로 클라이언트 인라인 검증으로 처리되고, 서버 400은 방어선이다. 400 응답의 field-error 메시지가 Figma 문구와 일치하도록 어노테이션 `message`를 지정한다. (현행 핸들러는 첫 번째 필드 오류만 `"필드명: 메시지"`로 내려줌 — 프론트가 서버 메시지를 그대로 노출할 계획이면 필드명 prefix 제거 여부 협의 필요)

### 5.4 AuthErrorCode 개편안 (rev.2)

```java
public enum AuthErrorCode {
    // 이메일 로그인 — 단일 실패 코드 (기존 코드 유지, 메시지만 Figma #6으로 교체)
    AUTHENTICATION_FAILED(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호를 다시 확인해주세요"),
    PASSWORD_RESET_REQUIRED(HttpStatus.PRECONDITION_REQUIRED, "비밀번호 재설정이 필요해요"), // 마이그레이션 (§8)
    TOO_MANY_ATTEMPTS(HttpStatus.TOO_MANY_REQUESTS, "로그인 시도가 너무 많아요. 잠시 후 다시 시도해주세요"), // §4.4

    // Google 로그인
    MEMBER_NOT_REGISTERED(HttpStatus.NOT_FOUND, "가입되지 않은 계정이에요. 회원가입을 진행해주세요"), // Google 전용 (§1.2)
    INVALID_FIREBASE_TOKEN(HttpStatus.UNAUTHORIZED, "Firebase 토큰이 유효하지 않습니다"),
    FIREBASE_NOT_CONFIGURED(HttpStatus.SERVICE_UNAVAILABLE, "Firebase가 설정되지 않았습니다"),
    UNSUPPORTED_AUTH_PROVIDER(HttpStatus.BAD_REQUEST, "지원하지 않는 로그인 방식입니다"),

    // 토큰 (유지)
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "Refresh Token이 유효하지 않거나 만료되었습니다");

    // rev.1에서 추가 예정이었으나 rev.2에서 폐기:
    //   LOGIN_FAILED            — AUTHENTICATION_FAILED로 흡수 (별도 코드 불필요)
    //   PROVIDER_MISMATCH_GOOGLE / PROVIDER_MISMATCH_EMAIL — 복합 키 식별로 개념 소멸 (§3.5)
    //   ACCOUNT_DEACTIVATED     — 로그인 응답에서 탈퇴 여부 노출 금지 (§5.2)
    // 삭제 유지: EMAIL_NOT_VERIFIED (이메일 즉시 가입 정책 — §6)
}
```

`MemberErrorCode`는 변경 최소화: `DUPLICATE_EMAIL(409)` 그대로 사용하되 중복 판정은 `(email, provider)` 복합 키 (§1.2). `MEMBER_DEACTIVATED(403)`는 인증 이후 컨텍스트(본인 API 호출)용으로만 유지하고 로그인 흐름에서는 사용하지 않는다.

---

## 6. 이메일 인증 방식

### 결정: 즉시 가입 허용 (가입 시 이메일 인증 단계 없음)

**Figma 해석**: 가입 폼 → "회원가입 완료하기" → 완료. 인증 메일 대기 화면·OTP 입력 화면이 존재하지 않음. 이는 "가입 전환율을 위해 인증 마찰을 제거한다"는 기획 의도로 해석한다. Firebase 시절의 `EMAIL_NOT_VERIFIED(422)` 검증은 이메일 플로우에서 **제거**한다.

**대안 비교**

| | 즉시 가입 (채택) | 인증 링크 후 활성화 | 가입 중 OTP |
|---|---|---|---|
| Figma 부합 | O | X (화면 없음) | X (화면 없음) |
| 가입 전환율 | 최고 | 메일 미도착·스팸함 이탈 | 입력 마찰 |
| 타인 이메일 도용 가입 | 가능 | 차단 | 차단 |
| 구현 비용 | 0 (이메일 발송 인프라 불필요) | 발송 인프라 + 토큰 관리 | 발송 인프라 + OTP 관리 |

**도용 리스크 완화**
- 진짜 소유자는 비밀번호 재설정(§7, 이메일 수신 = 소유 증명)으로 계정을 회수할 수 있다.
- 데이터 모델에 `emailVerified`(boolean, 기본 false / Google 가입은 true) 필드를 **지금 추가해 둔다**. 추후 "미인증 회원 기능 제한"이나 인증 캠페인을 무마이그레이션으로 도입 가능. (필드만 추가, 현 시점 로직 분기는 없음)

**트레이드오프**: 오타·도용 이메일로 가입된 계정에 알림 메일이 가는 등 부작용 가능. 마케팅 수신 동의 메일 발송 전에는 인증 도입을 재검토할 것 (법적 요건 확인 필요 항목으로 플래그).

---

## 7. 비밀번호 재설정 플로우 (초안 — **별도 설계 필요**)

> Figma에 "비밀번호 재설정" 링크는 존재하나 상세 화면 미확인. 이메일 발송 인프라(SMTP/SES 등)가 현재 코드베이스에 없음. 본 절은 방향 합의용 초안이며, 화면 확정 후 별도 설계 문서로 분리한다. 보안 요건은 §10.7·§10.14에 정리.

### 7.1 방식 비교: 토큰 링크 vs OTP

| | 토큰 링크 (권장) | 6자리 OTP |
|---|---|---|
| 흐름 | 메일의 링크 클릭 → 새 비밀번호 입력 | 메일의 코드를 화면에 입력 → 새 비밀번호 입력 |
| 보안 | 토큰 엔트로피 높음(128bit+), brute-force 비현실적 | 10^6 조합 — 시도 횟수 제한 필수 |
| UX | 메일 앱 ↔ 브라우저 전환 1회 | 같은 화면 유지, 모바일 친화 |
| 구현 | 단순 (토큰 1개 검증) | 시도 카운터·재발송 쿨다운 추가 필요 |

웹 중심 서비스이므로 **토큰 링크 방식 권장**. 단 Figma 재설정 화면이 코드 입력형이면 OTP로 전환 (설계 골격은 동일).

### 7.2 엔드포인트 초안

```
POST /api/auth/email/password-reset/request   { "email": "..." }
  → 200 (가입 여부 무관 동일 응답: "메일이 발송되었어요" — enumeration 방지, §10.14)
  → (email, EMAIL) 활성 회원일 때만 실제 발송. 같은 이메일의 GOOGLE 계정만 있으면
    "Google로 가입된 계정" 안내 메일 발송 고려 (응답은 동일 200 — 메일 내용으로만 분기).

POST /api/auth/email/password-reset/confirm   { "token": "...", "newPassword": "..." }
  → 200 / 401 INVALID_RESET_TOKEN(만료·재사용) / 400(비밀번호 정책 위반)
```

> rev.2 반영: 재설정 대상 조회도 `(loginEmail, EMAIL)` 복합 키. GOOGLE 계정에는 재설정할 비밀번호가 없다.

### 7.3 토큰 설계

- `SecureRandom` 128bit+ → URL-safe 인코딩. DB에는 **SHA-256 해시만 저장** (DB 유출 시 토큰 직접 사용 방지, §10.7).
- TTL 30분, **단발성** (confirm 성공 시 즉시 삭제 — refresh token의 findAndDelete 패턴 재사용).
- 재설정 성공 시: 해당 회원의 **모든 refresh token 폐기(전 기기 로그아웃, §10.6)** + `passwordHash` 교체. 마이그레이션 회원(§8)의 비밀번호 최초 설정 경로로도 이 플로우를 그대로 사용.
- 저장소: auth.internal에 `PasswordResetToken` 문서 + TTL 인덱스 (RefreshToken과 동일 패턴).
- 요청 rate limit: 이메일당 예: 5분 3회.

**별도 설계 필요 항목**: 메일 발송 인프라 선정(SES/SendGrid 등), 메일 템플릿, 재설정 페이지 URL 체계(프론트 라우트), Figma 화면 확정.

---

## 8. 마이그레이션 고려사항

### 8.1 문제

기존 Firebase 이메일 가입 회원은 비밀번호가 **Firebase에만**(scrypt 변형 해시) 존재한다. 전환 후 자체 로그인 시 대조할 `passwordHash`가 없다.

### 8.2 전략 비교

| | 옵션 A: 전원 비밀번호 재설정 (권장) | 옵션 B: Firebase 해시 이관 + lazy rehash |
|---|---|---|
| 방법 | `passwordHash=null` 회원이 이메일 로그인 시 `PASSWORD_RESET_REQUIRED(428)` 응답 → 프론트가 재설정 플로우(§7)로 유도 | Firebase CLI `auth:export`로 scrypt 해시·파라미터 이관 → 로그인 시 scrypt 검증, 성공하면 BCrypt로 재해싱해 저장 |
| 사용자 경험 | 첫 로그인 시 1회 재설정 마찰 | 무감각 (기존 비밀번호 그대로) |
| 구현 비용 | 낮음 — §7 플로우 재사용, 에러 코드 1개 | 높음 — firebase-scrypt 검증기 구현, scrypt 파라미터(시크릿 키 포함) 안전 보관, 이중 검증 경로 유지 |
| 보안 | 재설정 시점에 이메일 소유 재증명 (부수 효과로 휴면·도용 계정 정리) | Firebase의 scrypt signer key를 자체 DB로 가져와야 함 — 비밀 관리 부담 |
| 리스크 | 재설정 귀찮아서 이탈 가능 | 라이트 시절 약한 비밀번호 정책이 그대로 유입 (§1.3 로그인 패턴 검증과 충돌) |

**권장: 옵션 A.** 근거 — (1) §7 재설정 플로우가 어차피 필수 구현이므로 한계 비용이 거의 0, (2) 이메일 가입 비중·활성 사용자 규모가 크지 않은 현 단계에서 옵션 B의 복잡도(이중 해시 검증 경로, scrypt 키 관리)는 과투자, (3) 라이트 → 앳크루는 서비스 리브랜딩 전환이라 "새 서비스에서 비밀번호를 다시 설정해 주세요"가 사용자에게 자연스럽게 수용된다. 단, 활성 이메일 회원 수가 예상보다 많다면(예: 수천 명 이상) 옵션 B 재검토.

### 8.3 점진적 전환 단계

```
1단계  데이터 준비
  - Member에 passwordHash(null)·emailVerified·thirdPartyProvision 필드 추가 (Mongo라 스키마 변경 무비용)
  - (loginEmail, authProvider) 복합 unique 인덱스 생성 → 기존 loginEmail 단독 인덱스 제거 (§3.5)
  - 라이트 회원 이관 시 authProvider 매핑: Firebase password → EMAIL, google.com → GOOGLE

2단계  신규 API 배포
  - /api/auth/email/*, /api/auth/google/* 활성화, 기존 /login·/register 제거
  - LoginAttemptLimiter 동시 배포 (§4.4 — 기본 방어선)
  - §10 보안 체크리스트 전수 점검 후 배포
  - 프론트 동시 전환 (출시 전이므로 클라이언트 버전 분기 불필요)

3단계  기존 이메일 회원 전환
  - passwordHash == null인 EMAIL 회원 로그인 → 428 PASSWORD_RESET_REQUIRED → 재설정 유도
  - (선택) 마이그레이션 공지 메일로 사전 재설정 유도

4단계  Firebase 정리
  - Firebase 이메일 가입 경로 차단 (Google OAuth만 잔존)
  - 전환률 모니터링 후 Firebase 이메일 사용자 풀 삭제
  - 마이그레이션 회원 소진 시 PASSWORD_RESET_REQUIRED 코드 제거 (§1.2 — 계정 존재 노출면 축소)
```

### 8.4 데이터 호환성 메모 (라이트 마이그레이션 제약)

- `passwordHash`는 nullable 신규 필드이므로 기존 문서와 충돌 없음.
- `TermsAgreement.thirdPartyProvision`은 기존 문서에서 false로 읽힘 — 라이트 약관 범위 확인 후 backfill 여부 결정 (§3.4).
- `AuthProvider` enum 값(EMAIL/GOOGLE)은 변경 없음 → 마이그레이션 매핑 영향 없음.
- **복합 unique 인덱스 사전 검증** (rev.2): 라이트 데이터에 `(loginEmail, authProvider)` 중복이 있는지 이관 전 점검 필요. 라이트가 이메일 단일 계정 체계였다면 중복 불가능하지만, Firebase에서 동일 이메일이 password·google.com 두 provider로 존재했던 케이스가 있다면 **두 개의 앳크루 계정으로 분리 이관**된다 — 이것이 rev.2 정책상 올바른 결과임을 마이그레이션 스크립트 주석에 명시.

---

## 9. 영향 범위 및 후속 작업

| 영역 | 작업 |
|------|------|
| auth 공개 | `AuthService` 시그니처 교체, `EmailLoginCommand`·`EmailRegisterCommand`·`GoogleRegisterCommand`(각 `agreeThirdParty` 포함) 추가, `RegisterCommand` 삭제 |
| auth.internal | `AuthController` 엔드포인트 분리, DTO 신설(`agreeThirdParty` 포함), `AuthServiceImpl` 통합 401 + 더미 BCrypt 흐름으로 재작성(§2.1), `AuthErrorCode` 개편(§5.4), `FirebaseVerifier` → Google 전용, `LoginAttemptLimiter` 신설 |
| member 공개 | `existsByLoginEmail`·`findByLoginEmail`·`isDeactivatedEmail` → 복합 키 시그니처로 교체(§3.3), `verifyPassword(PasswordVerificationResult)`·`changePassword` 추가, `RegisterMemberCommand` 확장(rawPassword·agreeThirdParty) |
| member.internal | `Member.passwordHash`·`emailVerified` 추가, `TermsAgreement.thirdPartyProvision` 추가 + 필수 약관 검증 포함, `MemberRepository` 복합 키 파생 쿼리 교체, 복합 unique 인덱스(§3.5), 더미 BCrypt 포함 `verifyPassword` 구현 |
| common.security | `PasswordEncoder` 빈 등록, CORS·세션 정책·비인증 엔드포인트 허용 목록 점검 (§10.10·§10.13) |
| 인프라/운영 | HTTPS 강제·HSTS (§10.9), 복합 인덱스 마이그레이션 스크립트 (§3.5, §8.3) |
| 테스트 | auth/member 단위·통합 테스트 전면 재작성, SecurityIntegrationTest 시나리오 추가 (통합 401 — 미가입/탈퇴/오답/타 provider 4경로 동일 응답 검증, timing 더미 연산, rate limit 429, 428 분기, 복합 키 중복 가입), 인증·보안 체크리스트(§10) 재실행 |
| 확인 필요 (외부) | ① Figma #5·#7·#8 에러 문구를 통합 메시지로 **디자인 수정 요청** (§5.2) ② Figma 재설정 화면 확정 ③ 라이트 약관의 제3자 제공 조항 여부 (backfill 판단) ④ 메일 발송 인프라 선정 ⑤ 라이트 데이터의 동일 이메일·복수 provider 케이스 존재 여부 (§8.4) |

---

## 10. 보안 구현 체크리스트 (rev.2 신규)

> 자체 이메일 인증을 처음 구현하면서 놓치기 쉬운 항목의 전수 목록. **구현 PR 리뷰 시 본 절을 체크리스트로 사용**하고, 각 항목의 테스트를 SecurityIntegrationTest에 반영한다.

### 10.1 비밀번호 해싱 — BCrypt

- [ ] **strength 10 (기본값) 사용**. 근거: strength 10 ≈ 해시당 50~100ms — 공격자에게는 brute-force 비용, 사용자에게는 무감각한 지연. 12로 올리면 보안은 4배지만 로그인 p95가 200ms+ 증가하고 rate limiting(10.3)이 이미 온라인 공격을 막으므로, 오프라인 공격(DB 유출) 대비로는 10이면 충분. 추후 상향 시 로그인 성공 시점 lazy rehash(`encoder.upgradeEncoding`)로 무중단 전환 가능.
- [ ] **72-byte 상한 인지**: BCrypt는 입력 72바이트 초과분을 **조용히 무시**한다. §1.3의 `@Size(max=64)`가 이를 선제 차단 (UTF-8 한글 포함 가능성까지 고려하면 64자 ≤ 72바이트 보장은 아니므로, 인코딩 후 바이트 길이도 검증하거나 영문·숫자·특수문자 정책상 1자=1바이트임을 주석으로 명시).
- [ ] **timing-safe 비교**: 직접 `hash.equals()` 금지. `BCryptPasswordEncoder.matches()`만 사용 — 내부적으로 상수 시간 비교를 수행한다.
- [ ] 해시 결과를 로그·예외 메시지·API 응답 어디에도 싣지 않는다 (10.8).

### 10.2 Timing Attack 방지 — 더미 BCrypt 연산

- [ ] 회원 부재 시에도 **반드시 BCrypt 연산을 수행**해 회원 존재 시와 응답 시간을 균일화한다. 통합 401(§5)은 이것 없이는 불완전하다.

```java
// MemberServiceImpl — 기동 시 1회 생성하는 더미 해시
private final String dummyHash; // 생성자에서: passwordEncoder.encode("dummy-" + UUID.randomUUID())

@Override
public PasswordVerificationResult verifyPassword(String loginEmail, String rawPassword) {
    Optional<Member> found = memberRepository
            .findByLoginEmailAndAuthProvider(loginEmail, AuthProvider.EMAIL);

    if (found.isEmpty()) {
        passwordEncoder.matches(rawPassword, dummyHash);   // 결과 폐기 — 시간 균일화 목적
        return PasswordVerificationResult.MISMATCHED;
    }
    Member member = found.get();
    if (!member.hasPassword()) {
        return PasswordVerificationResult.PASSWORD_NOT_SET; // 428 — 별도 응답이므로 더미 불필요
    }
    return member.matchesPassword(rawPassword, passwordEncoder)
            ? PasswordVerificationResult.MATCHED
            : PasswordVerificationResult.MISMATCHED;
}
```

- [ ] 더미 해시는 **고정 문자열 하드코딩 금지** — 기동 시 랜덤 생성 (코드 유출 시에도 더미 입력값 예측 불가).
- [ ] 401 응답 경로의 DB 쿼리 수도 균일하게 유지 — 탈퇴 감사 로깅 등 추가 조회는 비동기로 (§2.1).
- [ ] 테스트: 회원 부재 vs 비밀번호 오답의 응답 시간 분포가 유의미하게 다르지 않은지 측정 (정밀 검증은 어려우므로 "더미 연산이 호출되는지" 단위 테스트로 대체 가능).

### 10.3 Rate Limiting / 계정 잠금 — LoginAttemptLimiter

- [ ] **키 설계**: `이메일` 기준과 `IP` 기준 **이중 추적**. 이메일 기준은 특정 계정 표적 공격(IP 분산) 방어, IP 기준은 광범위 stuffing(이메일 분산) 방어 — 한쪽만으로는 우회된다.
- [ ] **임계치(초안)**: 이메일 기준 10분 내 5회 실패 → 10분 차단. IP 기준 10분 내 30회 실패 → 10분 차단 (공유 IP·NAT 고려해 이메일보다 느슨하게). 운영 지표 보며 조정.
- [ ] **구현**: MongoDB TTL 컬렉션 `login_attempts` — `{ key, failCount, firstFailedAt }`, `firstFailedAt`에 TTL 인덱스(`expireAfterSeconds=600`). `findAndModify`(upsert + `$inc`)로 원자적 증가. 로그인 성공 시 해당 이메일 키 삭제.
- [ ] **응답**: 차단 중이면 비밀번호 검증 **전에** 429 `TOO_MANY_ATTEMPTS` — 차단 중에도 BCrypt를 돌리면 DoS 증폭이 된다.
- [ ] 영구 잠금(admin 해제 필요)은 도입하지 않음 — 공격자가 임의 계정을 잠그는 DoS 수단이 된다. 시간 기반 자동 해제만 사용.
- [ ] TTL 인덱스 삭제는 약 60초 주기라는 점 인지 — 차단 해제가 최대 1분 늦을 수 있음 (허용).

### 10.4 비밀번호 정책

- [ ] 정규식 `^(?=.*[A-Za-z])(?=.*\d)(?=.*[^A-Za-z\d\s]).{8,64}$` — 영문·숫자·특수문자 각 1+, 8자 이상 (§1.3).
- [ ] **상한 64자** — BCrypt 72바이트 silent truncation 방지 (10.1).
- [ ] **로그인 시에도 형식 검증을 BCrypt 연산보다 선행** — 정책 위반 비밀번호는 가입 자체가 불가하므로 조기 400 차단이 정보 노출 없이 BCrypt 비용을 절약하고, 초장문 입력으로 해싱 비용을 유발하는 DoS도 차단한다.
- [ ] 자주 쓰는 비밀번호(top-10k) 차단·HIBP 연동은 현 단계 미도입 — 추후 검토 항목으로만 기록.

### 10.5 Refresh Token 보안

- [ ] **기구현 유지**: rotation(refresh 시 신규 발급), 단일 사용(`findAndDelete`로 원자적 소비), TTL 인덱스 만료.
- [ ] **추가 — 재사용 탐지(token family 개념)**: 이미 소비된(=DB에 없는) refresh token이 다시 제시되면 단순 401로 끝내지 말 것 — rotation 체계에서 "사용된 토큰의 재등장"은 **탈취의 강한 신호**다 (정상 클라이언트는 구 토큰을 다시 쓸 일이 없음). 대응: 해당 회원의 **모든 refresh token 즉시 폐기**(전 기기 로그아웃) + 경고 로그. 완전한 family-id 추적(토큰마다 가계 ID 부여)은 차기 과제로 하되, "DB 부재 토큰이 서명·만료는 유효할 때 전체 폐기" 휴리스틱은 이번에 구현한다.
- [ ] refresh token 원문을 DB에 평문 저장하지 않는다 — SHA-256 해시로 저장·조회 (재설정 토큰과 동일 원칙, 10.7). 현 구현이 평문 저장이라면 이번 PR에서 전환.
- [ ] 클라이언트 저장 위치(웹이라면 httpOnly·Secure·SameSite 쿠키 vs localStorage)는 프론트와 협의 항목으로 플래그 — XSS 시 탈취면이 달라진다.

### 10.6 비밀번호 변경 시 전 기기 로그아웃

- [ ] `changePassword` 성공 시(재설정 §7 포함) 해당 회원의 **모든 refresh token 삭제** — `refreshTokenRepository.deleteAllByMemberId(memberId)`.
- [ ] 근거: 비밀번호를 바꾸는 가장 흔한 이유가 "탈취 의심"이다. 기존 세션을 살려두면 공격자의 refresh token이 계속 유효해 변경이 무의미해진다.
- [ ] access token은 짧은 만료(10.11)로 자연 소멸 — 별도 블랙리스트는 도입하지 않음 (만료 15분 이내 잔존 위험은 수용).
- [ ] 호출 순서: `passwordHash` 교체 → 토큰 전체 삭제 → (멀티 도큐먼트 트랜잭션 불가하므로) 토큰 삭제 실패 시 재시도 로그. 둘 다 멱등 연산이라 재실행 안전.

### 10.7 비밀번호 재설정 토큰 (§7.3 요건의 체크리스트화)

- [ ] `SecureRandom` 128bit 이상 → Base64 URL-safe 인코딩.
- [ ] **DB에는 SHA-256 해시만 저장** — raw 토큰은 메일로만 전송되고 서버는 보관하지 않는다. DB가 유출돼도 토큰을 역산해 재설정 링크를 만들 수 없다. (BCrypt가 아닌 SHA-256인 이유: 토큰은 고엔트로피 랜덤값이라 brute-force가 무의미하므로 느린 해시가 불필요)
- [ ] **단발성**: confirm 성공 시 `findAndDelete`로 원자적 소비. 같은 토큰 재제시 → 401.
- [ ] **TTL 30분**: TTL 인덱스로 자동 만료.
- [ ] 회원당 미사용 토큰 1개 정책(신규 요청 시 기존 토큰 무효화) — 메일 여러 통이 떠돌 때 구 링크가 살아있는 창을 줄인다.
- [ ] raw 토큰을 로그에 남기지 않는다 (메일 발송 라이브러리의 디버그 로그 포함 — 10.8).

### 10.8 로그·디버그에서 비밀번호 노출 방지

- [ ] **`toString()` 마스킹**: `EmailLoginCommand`·`EmailRegisterCommand`·`RegisterMemberCommand`·Request DTO 등 raw password를 담는 record는 전부 `toString()` 오버라이드로 password 필드를 `"****"` 처리. record 기본 `toString()`은 전 필드를 노출하므로 예외 메시지·디버그 로그에 평문이 찍힐 수 있다.
- [ ] `LogMask` 적용 대상: 로그인 시도 로그의 이메일(부분 마스킹), 실패 사유 기록(§5.2 — 응답에는 없지만 로그에는 부재/탈퇴/오답 구분 기록), 재설정 요청 이메일.
- [ ] **요청 본문 로깅 필터 점검**: 요청/응답 로깅 필터(logging-policy)가 있다면 `/api/auth/email/*` 경로의 body 로깅을 제외하거나 password·passwordConfirm 필드를 마스킹하는지 확인 — 이 경로가 가장 흔한 평문 유출 지점.
- [ ] 예외 스택·Bean Validation 실패 메시지에 입력값이 echo되지 않는지 확인 (`@Pattern` 실패 시 Hibernate Validator는 기본적으로 값을 메시지에 넣지 않지만, 커스텀 메시지에서 `${validatedValue}` 사용 금지).
- [ ] `passwordHash` 필드는 `MemberInfo` 등 공개 레코드·Swagger 스키마·`toString()` 어디에도 포함 금지.

### 10.9 HTTPS / 전송 보안

- [ ] **운영 환경 HTTP→HTTPS 강제**: LB/리버스 프록시에서 301 리다이렉트 또는 앱 레벨 `requiresChannel().anyRequest().requiresSecure()` (프록시 뒤라면 `server.forward-headers-strategy=framework` 설정 필수 — 아니면 `X-Forwarded-Proto`를 못 읽어 무한 리다이렉트).
- [ ] **HSTS 헤더**: `Strict-Transport-Security: max-age=31536000; includeSubDomains` — Spring Security 기본 활성이나 HTTPS 응답에서만 내려가므로 운영 환경 검증 필요. preload 등록은 도메인 정책 확정 후.
- [ ] **비밀번호 평문 전송은 HTTPS가 유일한 방어선**이다 — 클라이언트 측 해싱은 도입하지 않는다(해시가 곧 패스워드가 되어 무의미). 따라서 HTTPS 미적용 환경(스테이징 포함)에서 실계정 비밀번호 사용 금지를 운영 수칙으로 명시.
- [ ] 로컬/테스트 프로파일에서만 HTTP 허용 — 프로파일 분기 설정 확인.

### 10.10 CORS 설정

- [ ] **화이트리스트 origin만 허용**: 운영 프론트 도메인(+ 스테이징·로컬 dev 도메인은 프로파일 분기). `allowedOrigins("*")` 또는 패턴 전체 허용 금지.
- [ ] **`allowCredentials(true)`는 refresh token을 쿠키로 보낼 때만** 필요 — `*` origin과 조합 불가(스펙상 거부)이므로 화이트리스트가 전제. Authorization 헤더 방식만 쓴다면 credentials 허용 자체를 끈다.
- [ ] 허용 메서드·헤더 최소화: `POST, GET, PATCH, DELETE` + `Authorization, Content-Type`. `maxAge`로 preflight 캐싱(예: 1시간).
- [ ] CORS는 브라우저 한정 방어임을 인지 — 서버 간 호출·curl에는 무력하므로 인증·rate limit의 대체재가 아니다.

### 10.11 Access Token 만료

- [ ] **access token 만료 15분 권장** (현 설정값 확인 후 조정). 근거: access token은 무상태 검증이라 **발급 후 회수 불가** — 탈취되면 만료까지 막을 방법이 없다. 만료를 짧게 잡고 refresh rotation(10.5)으로 세션을 잇는 것이 "탈취 시 피해 창 최소화 + UX 유지"의 표준 조합이다.
- [ ] refresh token 만료(예: 14일)와의 조합으로 "15분마다 갱신, 14일 미접속 시 재로그인" 동작을 프론트와 합의.
- [ ] JWT claim 최소화 — 이메일 등 PII를 payload에 넣지 않는다 (JWT는 서명만 될 뿐 암호화되지 않음).

### 10.12 비밀번호 평문 전송 방지

- [ ] §10.9와 동일 결론의 재확인: 로그인·가입·재설정 API는 **HTTPS 필수**. GET 쿼리스트링으로 자격증명을 받는 엔드포인트가 없는지 확인 (전부 POST body — 쿼리스트링은 액세스 로그·브라우저 히스토리에 남는다).
- [ ] Swagger UI에서 password 필드는 `format: password` 지정 (화면 에코 방지 — 보안이라기보다 shoulder-surfing 방어).

### 10.13 Spring Security 설정 점검

- [ ] **CSRF disable**: stateless JWT + Authorization 헤더 방식이므로 `csrf(AbstractHttpConfigurer::disable)` — CSRF는 브라우저가 자동 첨부하는 자격증명(쿠키)을 악용하는 공격이라 헤더 토큰 방식에는 해당 없음. **단, refresh token을 쿠키로 전환하면(10.5) 이 결정을 재검토해야 한다** — 결정 의존성을 주석으로 남길 것.
- [ ] **세션 정책 STATELESS**: `sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))` — JSESSIONID 발급·세션 고정 공격면 제거.
- [ ] **비인증 엔드포인트 명시적 허용**: `/api/auth/email/login`, `/api/auth/email/register`, `/api/auth/google/login`, `/api/auth/google/register`, `/api/auth/refresh`, `/api/auth/email/password-reset/**` 를 `permitAll`로 **개별 나열** — 와일드카드 `/api/auth/**` 전체 허용은 향후 auth 하위에 인증 필요 엔드포인트(예: 로그아웃, 세션 목록)가 생길 때 사고가 된다.
- [ ] 그 외 전부 `anyRequest().authenticated()` — 기본 거부(deny-by-default) 원칙.
- [ ] 인증 실패(401)·인가 실패(403) 핸들러가 `ApiResponse` 포맷으로 응답하는지 확인 (Spring 기본 HTML 에러 페이지 노출 금지).
- [ ] actuator·swagger-ui 노출 범위 점검: 운영 프로파일에서 swagger 비활성 또는 접근 제한, actuator는 health만 공개.

### 10.14 이메일 존재 여부 노출 방지 — 엔드포인트별 일람

| 엔드포인트 | 존재 노출 | 정책 |
|------------|-----------|------|
| `POST /email/login` | **차단** | 통합 401 + 더미 BCrypt (§5, 10.2) |
| `POST /email/password-reset/request` | **차단** | 가입 여부 무관 **동일 200** ("메일이 발송되었어요"). 미가입이면 발송만 생략. 발송 소요 시간 차이가 응답에 드러나지 않도록 **메일 발송은 비동기 처리** 필수 |
| `POST /email/register` | 허용 (409) | 가입 UX상 불가피 — rate limiting으로 대량 탐색 차단, 필요 시 CAPTCHA 추가 (§1.2) |
| `POST /google/login` | 허용 (404) | Firebase 토큰이 이메일 소유를 증명하므로 enumeration 아님 (§1.2) |
| `POST /email/login` (428) | 허용 (한정) | 마이그레이션 회원 한정·기간 한정 — 4단계 후 제거 (§1.2, §8.3) |

- [ ] 재설정 request의 응답 시간 균일화: 회원 존재 시에만 토큰 생성+메일 발송을 동기로 하면 시간 차로 노출된다 — 발송 큐잉(비동기)으로 응답 경로를 동일하게.
- [ ] 휴리스틱 점검: 새 엔드포인트 추가 시 "이 응답(코드·메시지·시간)이 미가입자와 가입자에서 다른가?"를 리뷰 질문으로 고정.

---

### §10 요약 — 구현 PR 게이트

| 분류 | 필수(배포 전) | 후속 가능 |
|------|----------------|-----------|
| 해싱·timing | 10.1, 10.2, 10.4 | strength 상향 + lazy rehash |
| 남용 방지 | 10.3, 10.14 | CAPTCHA, HIBP 연동 |
| 토큰 | 10.5(재사용 탐지·해시 저장), 10.6, 10.11 | family-id 완전 추적, 쿠키 전환 검토 |
| 재설정 | 10.7 | (§7 자체가 별도 설계) |
| 노출 방지 | 10.8, 10.12 | — |
| 인프라 | 10.9, 10.10, 10.13 | HSTS preload |
