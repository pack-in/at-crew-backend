# 앳크루 백엔드 인증 아키텍처 심층 분석: Firebase Authentication과 자체 JWT 이중 구조

## 1. 서론: 왜 Firebase Authentication인가, 그리고 앳크루의 선택

인증(Authentication)은 거의 모든 서비스의 출발점이지만, 직접 구현하기에는 함정이 많은 영역이다. 비밀번호 해싱, 소셜 로그인 OAuth 핸드셰이크, 토큰 발급과 검증, 세션 관리, brute-force 방어까지 — 하나라도 잘못 구현하면 곧바로 보안 사고로 직결된다. 특히 Google, Apple 같은 소셜 로그인은 각 provider의 OAuth 2.0 / OIDC 스펙을 직접 구현하고 유지보수해야 하는 부담이 크다.

**Firebase Authentication**은 이 부담을 상당 부분 덜어준다. 클라이언트(앱)는 Firebase SDK를 통해 Google 로그인을 수행하고, 그 결과로 **Firebase ID Token**(서명된 JWT)을 받는다. 백엔드는 이 토큰을 **Firebase Admin SDK**로 검증하기만 하면 "이 사용자가 정말 그 Google 계정의 소유자다"라는 사실을 신뢰할 수 있다. OAuth redirect, code exchange, provider별 공개키 관리 같은 복잡성이 전부 Firebase 뒤로 숨는다.

앳크루(at-crew)는 기존 서비스 **라이트(Laiteu)** 의 기술 부채를 해소하기 위해 모듈형 모놀리식(Modular Monolith) 아키텍처로 재작성된 프로젝트다. 인증 영역에서 핵심적인 설계 결정은 다음과 같았다:

- **Firebase의 역할을 의도적으로 축소했다.** 라이트 시절에는 이메일 로그인과 Google 로그인 모두 Firebase에 의존했다. 앳크루에서는 **Google 로그인만 Firebase ID Token 검증 방식을 유지**하고, **이메일 로그인은 자체 BCrypt 기반 인증**으로 전환했다.
- 토큰 전략은 **Firebase 토큰을 곧장 세션으로 쓰지 않고**, 검증 후 **앳크루 자체 JWT(access/refresh)를 발급**하는 구조다. 즉 Firebase는 "Google 신원 증명기"로만 쓰고, 서비스 내부 인증의 주권은 우리가 가진다.

이 글은 이 인증 아키텍처를 레이어별로 해부하면서, 각 결정마다 "왜 이렇게 했는가", "다른 선택지는 무엇이었나", "트레이드오프는 무엇인가"를 기술 면접에서 답할 수 있는 수준으로 정리한다.

---

## 2. 전체 아키텍처 흐름

먼저 큰 그림이다. 클라이언트 요청은 Spring Security Filter Chain을 통과한 뒤 Controller, Service로 흘러간다.

```
[클라이언트]
     │
     ▼
[Spring Security Filter Chain]
     │  ← JwtAuthenticationFilter (보호된 엔드포인트는 여기서 인증)
     ▼
[AuthController]
     │
     ▼
[AuthServiceImpl] ──────────────┐
     ├─ FirebaseVerifier        │ (Google 전용 ID Token 검증)
     ├─ MemberService           │ (member 모듈 공개 인터페이스)
     ├─ JwtProvider             │ (자체 access/refresh JWT)
     ├─ LoginAttemptLimiter     │ (MongoDB TTL 기반 rate limit)
     └─ RefreshTokenRepository ─┘ (MongoDB)
```

### 2.1 Google 로그인 흐름

```
1. 클라이언트가 Firebase SDK로 Google 로그인 → Firebase ID Token 획득
2. POST /api/auth/google/login  { firebaseIdToken }
3. FirebaseVerifier.verify(idToken)
     - Firebase Admin SDK가 토큰 서명·만료·issuer 검증
     - sign_in_provider == "google.com" 확인
     - email, email_verified 추출
4. MemberService.findByLoginEmailAndProvider(email, GOOGLE)
     - 없으면 → AuthException(MEMBER_NOT_REGISTERED, 404)
5. MemberService.recordLogin(memberId)  (최근 로그인 시각 갱신)
6. JwtProvider.generateAccessToken(memberId, email)
7. issueRefreshToken(memberId)  (기존 refresh token 삭제 후 새로 발급)
8. AuthInfo(accessToken, refreshToken, member, isNewMember=false) 반환
```

Google **회원가입**(`/api/auth/google/register`)도 거의 동일하다. 차이는 4번에서 회원을 조회하는 대신 `memberService.register(...)`로 새 회원을 만들고, 반환 시 `isNewMember=true`라는 점이다.

### 2.2 이메일 로그인 흐름

이메일 로그인은 Firebase를 전혀 거치지 않는다.

```
1. POST /api/auth/email/login  { email, password }
2. LoginAttemptLimiter.checkBlocked(email)  ← rate limit 선검사
3. MemberService.verifyPassword(email, password)  ← timing-safe 검증
4. 분기:
     - 비밀번호 미설정(마이그레이션 회원) → recordFailure + PASSWORD_RESET_REQUIRED(428)
     - 불일치(미가입·탈퇴·오답 통합)       → recordFailure + AUTHENTICATION_FAILED(401)
     - 일치                               → reset(email)
5. MemberService.recordLogin(memberId)
6. access/refresh token 발급
7. AuthInfo 반환
```

핵심 대비 포인트: **Google 흐름은 "토큰을 신뢰"하는 검증 위주**이고, **이메일 흐름은 "비밀번호를 신뢰하지 않는" 방어 위주**다. 이 차이가 뒤에 나올 에러 코드 설계와 timing attack 방어의 출발점이 된다.

---

## 3. Firebase Admin SDK의 동작 원리

면접에서 자주 나오는 질문이다: "Firebase ID Token을 서버에서 검증한다는 게 정확히 무슨 의미인가?"

### 3.1 ID Token이란 무엇인가

Firebase ID Token은 **OIDC 규격을 따르는 서명된 JWT**다. 구조는 `header.payload.signature` 세 부분이며 Base64URL로 인코딩되어 있다. payload(claims)에는 다음과 같은 정보가 들어 있다:

- `iss` (issuer): `https://securetoken.google.com/<project-id>`
- `aud` (audience): Firebase 프로젝트 ID
- `sub` (subject): Firebase UID
- `exp`, `iat`: 만료/발급 시각
- `email`, `email_verified`
- `firebase`: `{ sign_in_provider: "google.com", identities: {...} }`

이 토큰은 **Google의 비공개 키(private key)로 서명**되어 있다. 따라서 누구든 대응하는 **공개 키(public key)** 만 있으면 서명이 진짜인지 검증할 수 있고, 위조는 비공개 키 없이는 불가능하다.

### 3.2 `verifyIdToken()`이 내부에서 하는 일

`FirebaseAuth.getInstance(app).verifyIdToken(idToken)` 호출은 단순해 보이지만 내부적으로 다음을 수행한다:

1. **공개 키 가져오기 및 캐싱**: Google은 토큰 서명용 공개 키를 `https://www.googleapis.com/robot/v1/metadata/x509/...` 엔드포인트로 노출한다. 키는 주기적으로 rotate되므로 Admin SDK는 응답의 `Cache-Control` 헤더(`max-age`)를 보고 키를 메모리에 캐싱하고, 만료되면 다시 가져온다. 덕분에 매 요청마다 네트워크 호출이 발생하지 않는다.
2. **서명 검증**: 토큰 header의 `kid`(key id)로 맞는 공개 키를 골라 RS256 서명을 검증한다.
3. **클레임 검증**: `exp`가 미래인지, `iat`가 과거인지, `aud`가 우리 프로젝트 ID와 일치하는지, `iss`가 올바른 securetoken issuer인지 확인한다.
4. 위 중 하나라도 실패하면 `FirebaseAuthException`을 던진다.

여기서 중요한 통찰: **검증은 stateless하다.** Firebase 서버에 "이 토큰 유효해?"라고 매번 묻는 것이 아니라, 캐싱된 공개 키로 로컬에서 암호학적으로 검증한다. 이것이 JWT 기반 인증의 핵심 장점이자, 동시에 "발급된 토큰을 즉시 무효화하기 어렵다"는 한계의 근원이다.

---

## 4. FirebaseConfig 설계 고민: Conditional Bean과 Fallback 패턴

Firebase Admin SDK를 초기화하려면 service account 인증 정보(JSON 키 파일)가 필요하다. 그런데 이 키 파일은 보안 정보라 로컬 개발 환경이나 CI 테스트 환경에는 보통 없다. 그렇다고 키가 없다고 애플리케이션 부팅이 통째로 실패하면 곤란하다 — 이메일 로그인만 테스트하고 싶은 개발자까지 막히기 때문이다.

이 문제를 두 개의 Conditional Bean으로 우아하게 해결했다.

```java
@Configuration
@ConditionalOnExpression("'${firebase.credentials-path:}'.trim().length() > 0")
class FirebaseConfig {
    @Value("${firebase.credentials-path}")
    private String credentialsPath;

    @Bean
    FirebaseApp firebaseApp() throws IOException {
        if (!FirebaseApp.getApps().isEmpty()) {
            return FirebaseApp.getInstance();   // 중복 초기화 방지
        }
        InputStream source = credentialsPath.startsWith("classpath:")
                ? new ClassPathResource(credentialsPath.substring(10)).getInputStream()
                : new FileInputStream(credentialsPath);
        try (InputStream stream = source) {
            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(stream))
                    .build();
            return FirebaseApp.initializeApp(options);
        }
    }

    @Bean
    FirebaseVerifier firebaseVerifier(FirebaseApp app) {
        return new FirebaseVerifierImpl(FirebaseAuth.getInstance(app));
    }
}
```

```java
@Configuration
class FirebaseFallbackConfig {
    @Bean
    @ConditionalOnMissingBean(FirebaseVerifier.class)
    FirebaseVerifier noOpFirebaseVerifier() {
        return idToken -> {
            throw new AuthException(AuthErrorCode.FIREBASE_NOT_CONFIGURED);
        };
    }
}
```

### 왜 이렇게 했는가

- **`@ConditionalOnExpression`**: `firebase.credentials-path` 프로퍼티가 비어 있지 않을 때만 `FirebaseConfig` 전체를 활성화한다. `:` 뒤의 빈 기본값(`${firebase.credentials-path:}`) 덕분에 프로퍼티 자체가 없어도 SpEL 평가가 깨지지 않고 길이 0으로 평가되어 비활성화된다.
- **`@ConditionalOnMissingBean`**: `FirebaseVerifier` 빈이 컨텍스트에 없을 때만 no-op 구현을 등록한다. 즉, 키 파일이 있으면 진짜 `FirebaseVerifierImpl`이 올라가고, 없으면 호출 즉시 `FIREBASE_NOT_CONFIGURED(503)`를 던지는 더미가 올라간다.

이 조합의 핵심은 **`FirebaseVerifier`라는 인터페이스가 항상 주입 가능하다는 보장**이다. `AuthServiceImpl`은 빈이 진짜인지 더미인지 알 필요 없이 그냥 `FirebaseVerifier`를 의존하면 된다. 의존성 주입 그래프가 환경에 따라 끊기지 않는다.

### 다른 선택지와 트레이드오프

- **선택지 A: 키 없으면 `@Profile`로 분기.** 가능하지만 프로파일이 늘어날수록 조합 폭발이 생기고, "Firebase 설정 여부"라는 런타임 사실을 정적 프로파일로 표현하는 게 부자연스럽다.
- **선택지 B: `FirebaseVerifier`를 nullable로 두고 `@Autowired(required=false)`.** 호출부마다 null 체크가 흩어진다. no-op 패턴(Null Object Pattern)이 훨씬 깔끔하다.
- **트레이드오프**: no-op 패턴은 "Firebase가 없는데 Google 로그인을 시도"하는 상황을 **부팅 시점이 아니라 런타임에** `503`으로 드러낸다. 빠른 실패(fail-fast)를 선호한다면 단점일 수 있으나, "이메일 로그인은 되고 Google만 안 되는" 부분 가용성을 얻는 대가로는 합리적이다.

`if (!FirebaseApp.getApps().isEmpty())` 가드는 테스트 컨텍스트가 여러 번 뜰 때 `FirebaseApp.initializeApp`이 중복 호출되어 `IllegalStateException`이 나는 것을 막는 방어 코드라는 점도 짚어둘 만하다.

---

## 5. FirebaseVerifierImpl 분석: Google 전용 강제

```java
class FirebaseVerifierImpl implements FirebaseVerifier {
    private final FirebaseAuth firebaseAuth;

    @Override
    public FirebaseUser verify(String idToken) {
        try {
            FirebaseToken decoded = firebaseAuth.verifyIdToken(idToken);
            String email = decoded.getEmail();
            if (email == null) {
                throw new AuthException(AuthErrorCode.INVALID_FIREBASE_TOKEN);
            }
            return new FirebaseUser(email, extractProvider(decoded), decoded.isEmailVerified());
        } catch (FirebaseAuthException e) {
            throw new AuthException(AuthErrorCode.INVALID_FIREBASE_TOKEN, e);
        }
    }

    private AuthProvider extractProvider(FirebaseToken decoded) {
        Map<String, Object> firebaseClaim = (Map<String, Object>) decoded.getClaims().get("firebase");
        if (firebaseClaim == null) {
            throw new AuthException(AuthErrorCode.INVALID_FIREBASE_TOKEN);
        }
        String signInProvider = (String) firebaseClaim.get("sign_in_provider");
        if (!"google.com".equals(signInProvider)) {
            throw new AuthException(AuthErrorCode.UNSUPPORTED_AUTH_PROVIDER);
        }
        return AuthProvider.GOOGLE;
    }
}
```

이 클래스가 하는 일은 세 가지 방어 레이어로 읽힌다.

### 5.1 `firebase` claim 파싱과 `sign_in_provider` 필터링

Firebase ID Token은 Google, Apple, 익명 로그인, **그리고 Firebase 자체 이메일/비밀번호 로그인**으로도 발급될 수 있다. 즉, 단순히 "유효한 Firebase 토큰"이라는 사실만으로는 "이 사람이 Google로 로그인했다"를 보장하지 못한다.

앳크루는 이메일 인증을 자체 BCrypt로 옮겼기 때문에, **Firebase 경로는 반드시 Google이어야 한다**는 불변식(invariant)을 코드로 강제해야 한다. `firebase.sign_in_provider`가 `"google.com"`이 아니면 `UNSUPPORTED_AUTH_PROVIDER(400)`로 거부한다. 이로써 "Firebase 이메일 로그인으로 받은 토큰을 Google 엔드포인트에 던져 우회"하는 시나리오를 차단한다.

### 5.2 `email == null` 체크의 의미

소셜 provider 중에는 이메일을 제공하지 않거나(예: 익명 로그인), 사용자가 이메일 공유를 거부하는 경우가 있다. 앳크루는 **`(loginEmail, authProvider)`를 계정 식별 단위로 삼기 때문에 이메일이 없으면 계정을 식별할 수 없다.** 그래서 이메일이 null이면 즉시 `INVALID_FIREBASE_TOKEN`으로 끊는다. 식별 불가능한 신원을 시스템 안쪽으로 흘려보내지 않겠다는 의도다.

### 5.3 예외 변환

`FirebaseAuthException`(SDK 예외)을 잡아 우리 도메인 예외인 `AuthException`으로 변환한다. 이것은 **모듈 경계 보호**의 한 형태다 — Firebase SDK의 예외 타입이 상위 레이어로 새어나가지 않게 막고, 일관된 에러 코드 체계 안으로 흡수한다. 원인 예외 `e`를 함께 넘겨 로깅 시 스택트레이스는 보존한다.

> **면접 포인트**: "유효한 토큰인데 왜 거부될 수 있나?"에 대해 (1) email 없음, (2) Google 외 provider, 두 가지를 들 수 있어야 한다. "토큰 검증 성공 == 로그인 허용"이 아니라는 점이 핵심이다.

---

## 6. 계정 식별 단위 설계: `loginEmail` 단독 → `(loginEmail, authProvider)` 복합 키

라이트 시절에는 `loginEmail`이 단독 unique 키였다. 즉 "한 이메일 = 한 계정"이었다. 앳크루는 이를 **`(loginEmail, authProvider)` 복합 unique**로 바꿨다.

### 왜 바꿨는가

같은 사람이 같은 이메일 주소로 **이메일 가입**도 하고 **Google 가입**도 할 수 있다(예: 회사 Google Workspace 이메일). 단독 unique 구조에서는 둘 중 하나만 존재할 수 있어 다음과 같은 충돌이 생긴다:

- 이메일로 가입한 사용자가 나중에 Google 로그인을 시도 → 같은 이메일이 이미 있으니 가입 거부 or 강제 병합
- 그 반대 경우도 마찬가지

복합 키로 바꾸면 **`a@x.com / EMAIL`과 `a@x.com / GOOGLE`이 별개 계정으로 공존**할 수 있다. 인증 방식이 다르면 본질적으로 다른 자격 증명이므로, 별개 계정으로 보는 것이 데이터 모델상 더 정직하다.

### 파급효과

1. 조회 API가 `findByLoginEmail` → `findByLoginEmailAndProvider`로 바뀐다. `AuthServiceImpl.loginWithGoogle`이 `findByLoginEmailAndProvider(email, GOOGLE)`을 호출하는 이유다.
2. DB unique index가 단일 컬럼에서 복합 컬럼으로 변경된다.
3. **마이그레이션 호환성**: 라이트 → 앳크루 무중단 마이그레이션이 제약 조건이므로, 기존 데이터에 `authProvider`를 채워 넣는 백필(backfill)이 필요하다. 라이트 데이터의 provider를 추론해 채우는 작업이 동반된다.

### 트레이드오프

- **단점**: 사용자 입장에서 "같은 이메일인데 왜 계정이 두 개냐"는 혼란이 생길 수 있다. 이건 UX/제품 정책으로 풀어야 하며, 향후 account linking(15장)의 필요성을 낳는다.
- **대안**: "이메일 = 글로벌 식별자, provider는 attribute"로 두고 한 계정에 여러 인증 방식을 매다는 모델. 더 사용자 친화적이지만 자격 증명 병합 로직과 보안 검증(소유권 증명)이 복잡해진다. 앳크루는 1차 출시에서 단순성을 택했다.

---

## 7. 에러 코드 통합 vs 구분의 트레이드오프

이 부분이 인증 보안에서 가장 미묘하고, 면접에서 변별력이 높은 주제다.

### 7.1 Enumeration Attack이란

**계정 열거 공격(account enumeration)** 은 시스템의 응답 차이를 이용해 "어떤 이메일이 가입되어 있는지"를 알아내는 공격이다. 예를 들어:

- 미가입 이메일로 로그인 → "가입되지 않은 계정입니다"(404)
- 가입된 이메일 + 틀린 비번 → "비밀번호가 틀렸습니다"(401)

이 두 응답이 다르면 공격자는 임의의 이메일 리스트를 던져 보고 404/401 패턴만으로 **가입 회원 목록을 수집**할 수 있다. 수집된 목록은 피싱, credential stuffing의 표적이 된다.

### 7.2 이메일 로그인: 실패를 단일 응답으로 통합

그래서 이메일 로그인은 **미가입·탈퇴·비밀번호 오답을 모두 `AUTHENTICATION_FAILED(401)` 하나로 통합**한다.

```java
if (verification.isMismatched()) {
    loginAttemptLimiter.recordFailure(command.email());
    throw new AuthException(AuthErrorCode.AUTHENTICATION_FAILED); // 미가입/탈퇴/오답 구분 안 함
}
```

응답 코드도, 메시지("이메일 또는 비밀번호를 다시 확인해주세요")도 동일하다. 공격자는 응답만으로 계정 존재 여부를 구분할 수 없다.

### 7.3 Google 로그인에서만 404를 허용하는 이유

반면 Google 로그인은 미가입 시 `MEMBER_NOT_REGISTERED(404)`를 **별도로** 내려준다. 일관성이 깨진 것처럼 보이지만 의도된 결정이다.

핵심 논리: **Google 로그인은 이미 Firebase ID Token으로 "요청자가 그 이메일의 소유자임"을 증명한 상태다.** 공격자가 임의의 이메일에 대해 "가입됐는지" 알아내려면 먼저 그 이메일의 Google 계정에 실제로 로그인해 유효한 ID Token을 받아야 한다. 자기가 소유한 이메일이 우리 서비스에 가입돼 있는지 아는 것은 enumeration이 아니다 — **타인의 가입 여부를 캐낼 수 없으므로** 정보 노출 위험이 없다.

오히려 여기서 404를 주는 것이 UX상 유리하다. 클라이언트는 "이 Google 계정은 미가입이니 회원가입 화면으로 보내자"는 분기를 명확히 할 수 있다. 401로 뭉뚱그리면 신규/오류 구분이 안 된다.

> **면접 포인트**: "보안과 UX의 트레이드오프를 어떻게 판단했나?"에 대해 — *"공격자가 정보를 얻을 수 있는 경로가 닫혀 있다면 구체적인 에러를 줘도 안전하다. Google은 토큰이 소유권을 증명하므로 안전하고, 이메일은 그렇지 않으므로 통합한다"* 가 정답이다.

---

## 8. Timing Attack 방어: 응답 시간으로 계정 존재를 추측하는 공격

에러 코드를 통합해도 한 가지 측면 채널(side channel)이 남는다: **응답 시간**.

순진하게 구현하면 이렇게 된다:

```java
// 안티패턴
Member m = repo.findByEmail(email);
if (m == null) return fail();                           // DB 조회만, 매우 빠름
if (!bcrypt.matches(password, m.hash)) return fail();  // BCrypt 연산, 느림
```

BCrypt는 의도적으로 느린 해시 함수다(수십~수백 ms). 회원이 없으면 BCrypt를 돌리지 않으니 응답이 빠르고, 회원이 있으면 BCrypt를 돌리니 느리다. 공격자는 **응답 시간 차이만으로** 에러 코드가 같아도 계정 존재 여부를 추론할 수 있다. 7장에서 막은 enumeration이 timing으로 되살아나는 셈이다.

### 방어: 더미 BCrypt 연산

앳크루는 `MemberService.verifyPassword` 내부에서 **회원이 없을 때도 더미 BCrypt 연산을 수행**해 응답 시간을 균일화한다. 그 결과를 `PasswordVerification`이라는 타입으로 추상화해 반환한다(상태: `matched` / `mismatched` / `notSet`).

```java
PasswordVerification verification = memberService.verifyPassword(command.email(), command.password());
```

호출하는 `AuthServiceImpl` 입장에서는 회원 존재 여부를 알 수 없고, 오직 검증 결과 상태만 받는다. timing 균일화 책임이 member 모듈 안에 캡슐화되어 있다는 점이 중요하다.

### 트레이드오프

- 더미 연산은 CPU를 낭비한다. 하지만 BCrypt strength 10 기준 수십 ms로, rate limit과 결합하면 실질 비용은 작다.
- 완벽한 상수 시간(constant-time)을 보장하긴 어렵다. DB 조회 시간, JIT, GC 등의 노이즈가 있다. 다만 공격자가 통계적으로 의미 있는 차이를 뽑아내기 어렵게 만드는 것이 목표이며, **rate limiting과 함께** 다층 방어로 작동한다.

---

## 9. JWT 이중 타입 구조: access vs refresh

앳크루는 Firebase 토큰을 검증한 뒤 **자체 JWT**를 발급한다. 그리고 access token과 refresh token에 각각 `type` claim을 박아 넣는다.

```java
public String generateAccessToken(String memberId, String email) {
    return Jwts.builder()
            .subject(memberId)
            .claim("email", email)
            .claim("type", "access")          // ← 타입 표식
            .issuedAt(new Date())
            .expiration(new Date(System.currentTimeMillis() + accessTokenExpiration))
            .signWith(secretKey, Jwts.SIG.HS512)
            .compact();
}

public boolean validateAccessToken(String token) {
    try {
        Claims claims = getClaims(token);
        return "access".equals(claims.get("type", String.class));  // ← 타입 강제
    } catch (JwtException | IllegalArgumentException e) {
        return false;
    }
}
```

### 왜 type claim이 필요한가

access token과 refresh token이 같은 secret key로 서명되면, **서명만으로는 둘을 구분할 수 없다.** 만약 type 검증이 없다면 공격자(혹은 버그)가 refresh token을 `Authorization` 헤더에 넣어 보호된 API에 접근할 수 있다. refresh token은 보통 수명이 길기 때문에(예: 2주), 짧은 수명을 전제로 한 access token 자리에 끼어들면 보안 가정이 무너진다.

`type: "access"` claim과 `validateAccessToken`의 `"access".equals(...)` 체크는 **"이 토큰은 access 용도로만 쓰여야 한다"는 의도를 토큰 안에 못박는다.** refresh 엔드포인트는 반대로 refresh 타입만 받아들인다. 토큰 오용(token confusion)을 차단하는 저렴하고 효과적인 방어다.

### 서명 알고리즘 선택

`HS512`(HMAC-SHA512, 대칭키)를 사용한다. 단일 서비스가 토큰을 발급하고 검증하므로 비대칭키(RS256)의 키 분배 이점이 필요 없다. 대칭키는 빠르고 단순하다. 대신 secret key 유출이 곧 위조 가능성이므로 키 관리가 중요하다. (Firebase 토큰은 RS256인데, 이는 발급자(Google)와 검증자(우리)가 분리되어 있어 공개키 검증이 필수이기 때문 — 대비해서 설명하면 좋은 포인트다.)

---

## 10. Refresh Token 회전과 TOCTOU 방어

### 10.1 단일 보유 정책

앳크루는 **회원당 refresh token을 하나만 유지**한다. 새 로그인이나 토큰 갱신 시 기존 토큰을 전부 삭제하고 새로 발급한다(rotation). 이렇게 하면:

- 탈취된 토큰의 수명이 자연스럽게 짧아진다(다음 갱신 때 무효화).
- "한 계정 = 한 세션"에 가까운 모델이 되어 동시 세션 추적이 단순해진다.

### 10.2 TOCTOU와 `findAndRemove`의 원자성

토큰 갱신(refresh)에서 가장 위험한 경쟁 조건은 **TOCTOU(Time-Of-Check to Time-Of-Use)** 다. 순진한 구현:

```java
// 안티패턴 — 경쟁 조건
RefreshToken t = repo.findByTokenValue(value);   // 1. 조회(check)
if (t == null) throw ...;
repo.delete(t);                                  // 2. 삭제
issueNew();                                      // 3. 새 토큰 발급
```

같은 refresh token으로 **두 요청이 동시에** 들어오면 둘 다 1번 조회를 통과해 둘 다 새 토큰을 발급받을 수 있다. 토큰이 복제되어 rotation이 무력화된다.

앳크루는 MongoDB의 원자적 `findAndRemove`로 이를 막는다:

```java
public Optional<RefreshToken> findAndDeleteByTokenValue(String tokenValue) {
    Query query = Query.query(Criteria.where("tokenValue").is(tokenValue));
    return Optional.ofNullable(mongoOperations.findAndRemove(query, RefreshToken.class));
}
```

`findAndRemove`는 **조회와 삭제를 단일 원자 연산**으로 수행한다(MongoDB의 `findAndModify` 계열). 동시에 두 요청이 와도 **정확히 하나만 비어 있지 않은 결과**를 받고, 나머지는 빈 `Optional`을 받는다. 빈 `Optional`을 받은 쪽은 `INVALID_REFRESH_TOKEN(401)`로 거부된다. 결과적으로 새 토큰은 정확히 한 번만 발급된다.

### 10.3 TTL Index로 만료 자동화

```java
@Document(collection = "refresh_tokens")
public class RefreshToken {
    @Indexed private String memberId;
    @Indexed(unique = true) private String tokenValue;
    @Indexed(expireAfterSeconds = 0) private Instant expiresAt; // TTL index
    @CreatedDate private Instant createdAt;
}
```

`expiresAt`에 `expireAfterSeconds = 0` TTL index를 걸면 MongoDB가 `expiresAt`이 지난 문서를 백그라운드 스레드로 자동 삭제한다. 만료 토큰을 애플리케이션이 직접 청소할 필요가 없다.

> **면접 포인트**: TTL index의 삭제는 즉시가 아니라 **약 60초 주기**의 백그라운드 작업이다. 따라서 만료 직후 잠깐 문서가 남아 있을 수 있는데, `expiresAt` 비교를 애플리케이션에서도 확인하거나 검증 로직이 만료를 함께 체크해야 한다는 점을 알면 좋다.

---

## 11. Rate Limiting with MongoDB: Redis 없이 만든 brute-force 방어

```java
@Service
class LoginAttemptLimiter {
    private static final int EMAIL_LIMIT = 5;
    private static final int IP_LIMIT = 30;
    private static final int WINDOW_SECONDS = 600; // 10분

    void checkBlocked(String email) { ... }
    void recordFailure(String email) { ... increment email + IP }
    void reset(String email) { ... }
}
```

### 왜 Redis가 아니라 MongoDB인가

rate limiting의 교과서적 정답은 Redis다(원자적 `INCR`, 자동 만료 `EXPIRE`, 인메모리 속도). 하지만 앳크루는 **현재 인프라에 이미 MongoDB가 있고 Redis는 없다.** 새 인프라 컴포넌트(Redis) 도입은 운영 비용, 장애 지점, 배포 복잡도를 늘린다. 초기 트래픽 규모에서 로그인 시도 빈도는 Redis급 처리량을 요구하지 않으므로, **MongoDB의 TTL 컬렉션으로 충분히 구현**할 수 있다고 판단했다.

### 구조

- **이메일 단위 카운터(5회/10분)**: 특정 계정에 대한 집중 brute-force를 막는다.
- **IP 단위 카운터(30회/10분)**: 여러 계정을 돌려가며 시도하는 분산 공격(credential stuffing의 한 형태)을 막는다. 이메일 한도보다 높게 잡아 동일 IP의 정상 다중 사용자(예: 회사/카페 NAT)를 과도하게 막지 않도록 균형을 맞췄다.
- 각 카운터 문서에 TTL을 걸어 10분 윈도우가 지나면 자동 소멸한다.
- `checkBlocked`는 비밀번호 검증 **이전에** 호출되어, 한도를 넘으면 BCrypt 연산조차 시작하지 않고 `TOO_MANY_ATTEMPTS(429)`로 끊는다. 비용이 큰 연산을 게이트 뒤로 미루는 것이다.
- 로그인 성공 시 `reset(email)`로 카운터를 비운다.

### 한계와 트레이드오프

- **원자성**: MongoDB의 `$inc`는 단일 문서에 대해 원자적이라 동시 increment에 안전하다. 다만 "조회 후 증가" 같은 분리 연산을 쓰면 경쟁이 생기므로, atomic update 연산자를 써야 한다.
- **고정 윈도우의 burst 문제**: 단순 고정 윈도우(fixed window)는 경계에서 burst를 허용한다(윈도우 끝 5회 + 다음 윈도우 시작 5회). sliding window나 token bucket이 더 정교하지만 구현 복잡도가 올라간다. 현재 위협 모델에서는 고정 윈도우로 충분하다고 본 트레이드오프다.
- **TTL 삭제 지연**: 10장과 동일하게 TTL은 즉시가 아니다. 윈도우 경계 정확도에 약간의 느슨함이 있다.
- 트래픽이 커지면 MongoDB write 부하가 핫스팟이 될 수 있어, 그 시점에 Redis로 이전하는 것이 자연스러운 진화 경로다(15장).

---

## 12. Spring Security Filter Chain 통합

```java
@Bean
SecurityFilterChain filterChain(...) throws Exception {
    http
        .csrf(AbstractHttpConfigurer::disable)        // stateless JWT → CSRF 토큰 불필요
        .cors(cors -> cors.configurationSource(corsConfigurationSource()))
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS));

    http.headers(headers -> headers
        .httpStrictTransportSecurity(hsts -> hsts
            .includeSubDomains(true)
            .maxAgeInSeconds(31536000)));

    if (isProd()) {
        http.requiresChannel(channel -> channel.anyRequest().requiresSecure());
    }

    return http
        .authorizeHttpRequests(auth -> {
            auth.requestMatchers(HttpMethod.POST,
                    "/api/auth/email/login",
                    "/api/auth/email/register",
                    "/api/auth/google/login",
                    "/api/auth/google/register",
                    "/api/auth/refresh").permitAll();
            auth.anyRequest().authenticated();
        })
        .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
        .build();
}
```

### 12.1 `SessionCreationPolicy.STATELESS`

서버가 `HttpSession`을 만들지도, 참조하지도 않게 한다. 모든 요청은 JWT로 자기 자신을 증명해야 한다. 이것이 **CSRF를 비활성화할 수 있는 근거**다 — CSRF는 브라우저가 쿠키(세션)를 자동 전송하는 것을 악용하는 공격인데, 인증을 `Authorization` 헤더의 Bearer 토큰으로 받으면 브라우저가 자동으로 붙여주지 않으므로 CSRF 표면이 사라진다.

### 12.2 `OncePerRequestFilter`와 JwtAuthenticationFilter

```java
class JwtAuthenticationFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(...) {
        String token = extractToken(request);
        if (token != null && jwtProvider.validateAccessToken(token)) {
            String memberId = jwtProvider.getMemberId(token);
            MDC.put("memberId", memberId);
            MemberPrincipal principal = new MemberPrincipal(memberId);
            UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                    principal, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
            SecurityContextHolder.getContext().setAuthentication(auth);
        }
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove("memberId");   // 스레드 재사용 시 누수 방지
        }
    }
}
```

- **`OncePerRequestFilter`**: 한 요청 처리 동안 forward/include 등으로 필터 체인이 여러 번 도는 상황에서도 **정확히 한 번만** 실행되도록 보장하는 베이스 클래스다. 인증을 중복 수행하지 않게 한다.
- **`UsernamePasswordAuthenticationToken`**: 이름과 달리 비밀번호 인증 전용이 아니라, Spring Security에서 "인증 완료된 주체"를 표현하는 표준 `Authentication` 구현이다. 여기서는 principal로 `MemberPrincipal`, credentials는 `null`(토큰 기반이라 비밀번호 보관 안 함), 권한으로 `ROLE_USER`를 넣어 만든다. 이걸 `SecurityContextHolder`에 담으면 이후 컨트롤러에서 `@AuthenticationPrincipal`이나 `SecurityUtils`로 현재 사용자를 꺼낼 수 있다.
- **필터 위치**: `addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)`로 JWT 필터를 표준 폼 로그인 필터 앞에 끼운다. 표준 폼 로그인을 쓰지 않으니 그 자리에서 우리 인증을 먼저 끝내겠다는 의미다.
- **MDC 정리**: `memberId`를 MDC에 넣어 로그에 사용자 컨텍스트를 남기되, **`finally`에서 반드시 제거**한다. 톰캣은 스레드를 풀에서 재사용하므로, 제거하지 않으면 다음 요청 로그에 이전 사용자 ID가 새어 들어가는 심각한 로그 오염/정보 누출이 생긴다.

### 12.3 토큰이 없거나 무효일 때

`validateAccessToken`이 false면 인증을 `SecurityContext`에 넣지 않고 그대로 체인을 진행시킨다. 그러면 `authorizeHttpRequests`의 `anyRequest().authenticated()`에 걸려 인증 실패로 처리된다. 즉 **필터는 인증을 "거부"하지 않고 "설정만" 한다.** 거부는 authorization 단계에 위임하는 것이 Spring Security의 책임 분리 원칙이다.

### 12.4 운영 보안 헤더

- **HSTS**: `max-age=31536000; includeSubDomains`로 1년간 HTTPS 강제를 브라우저에 지시한다.
- **`requiresChannel().requiresSecure()`**: 운영 환경에서만 HTTP 요청을 HTTPS로 강제한다. 로컬/테스트는 HTTP를 허용해 개발 편의를 지킨다(`isProd()` 분기).
- permitAll 목록은 로그인/가입/refresh 등 **인증 없이 들어와야 하는 입구**만 정밀하게 열고 나머지는 전부 닫는다(allowlist 방식 — denylist보다 안전).

---

## 13. 모듈 경계와 의존성 방향

앳크루는 모듈형 모놀리식이다. 핵심 규칙: **도메인 모듈 간 직접 의존 금지, 명시적 공개 인터페이스를 통해서만 통신.**

```
com.atcrew.auth            (공개: AuthService, AuthInfo, *Command)
com.atcrew.auth.internal   (구현: AuthServiceImpl, FirebaseVerifierImpl ...)
com.atcrew.member          (공개: MemberService, MemberInfo, PasswordVerification, AuthProvider)
com.atcrew.member.internal (구현, 외부 접근 불가)
```

### 왜 auth가 member 내부에 직접 접근하지 않는가

`AuthServiceImpl`은 `MemberService` 인터페이스와 `MemberInfo`, `PasswordVerification` 같은 공개 DTO만 의존한다. member의 엔티티, repository, passwordHash 필드에는 손댈 수 없다.

- **변경 격리**: member 내부 스키마가 바뀌어도 공개 계약만 유지되면 auth는 영향받지 않는다.
- **응집도**: 비밀번호 해싱/검증, timing 균일화 같은 "회원 자격 증명" 로직이 member 안에 모인다. auth는 "결과"만 받는다.
- **테스트 용이성**: auth 테스트에서 `MemberService`를 mock으로 갈아끼우면 된다.

이 경계는 Spring Modulith 같은 도구로 컴파일/테스트 타임에 검증할 수 있어, 실수로 internal 패키지를 import하면 빌드가 깨지게 만들 수 있다.

### `PasswordVerification`이 의도적으로 구분 불가하게 설계된 이유

`verifyPassword`는 boolean을 반환하지 않고 `PasswordVerification`이라는 타입을 반환한다. 이 타입은 `matched / mismatched / notSet` 같은 **결과 상태**만 노출하고, **"회원이 존재하는가"는 노출하지 않는다.**

이것은 단순한 캡슐화가 아니라 **보안 설계**다. 만약 member가 "회원 없음"과 "비밀번호 틀림"을 구분해서 반환하면, auth 레이어가 그 차이를 실수로 다른 에러 코드로 노출할 위험이 생긴다(7장 enumeration 재발). 반환 타입 자체에서 그 구분을 지워버리면 **상위 레이어가 정보를 흘리고 싶어도 흘릴 수 없다.** 타입 시스템으로 보안 불변식을 강제하는 좋은 예다.

마이그레이션 회원만 예외적으로 `notSet`(비밀번호 미설정)을 구분하는데, 이는 enumeration 위험보다 "비밀번호 재설정 안내"라는 UX 필요가 큰 케이스이고, 비밀번호를 한 번도 설정하지 않은 상태는 공격자가 추측해도 별 가치가 없는 정보라 허용했다.

---

## 14. BCrypt 설계 결정들

이메일 인증을 자체 구현하기로 한 순간, 비밀번호 해싱은 직접 책임지게 됐다. 선택지는 BCrypt, scrypt, Argon2 등인데 앳크루는 Spring Security가 기본 제공하고 검증이 충분한 **BCrypt**를 택했다.

### 14.1 strength 10 선택 근거

BCrypt의 `strength`(cost factor)는 해싱 반복 횟수를 `2^strength`로 결정한다. strength 10이면 `2^10 = 1024` 라운드다.

- **너무 낮으면**: brute-force가 빨라져 보안이 약해진다.
- **너무 높으면**: 로그인마다 CPU를 오래 점유해 처리량이 떨어지고, 9장의 timing 균일화용 더미 연산 비용도 같이 커진다.
- strength 10은 현대 서버에서 **대략 수십~100ms 수준**으로, 사용자 체감 지연은 미미하면서 공격 비용은 충분히 높이는 업계 표준 절충점이다. (Spring Security의 기본값도 10이다.) 하드웨어가 빨라지면 12 등으로 올리는 것을 고려할 수 있다.

### 14.2 72바이트 상한 이슈

BCrypt는 **입력 비밀번호를 최대 72바이트까지만 사용**하고 그 이후는 잘라버린다. 이를 인지하지 못하면 두 가지 문제가 생긴다:

1. **보안 착시**: 72바이트를 넘는 매우 긴 비밀번호의 뒷부분이 무시되어, 사용자가 생각하는 만큼 엔트로피가 반영되지 않는다.
2. **멀티바이트 함정**: UTF-8에서 한글은 3바이트라, 한글 24자만 넘어가도 72바이트를 초과할 수 있다. 문자 수 기준 검증만 하면 예상과 다르게 잘린다.

대응은 **회원가입 시 비밀번호 길이를 바이트 기준으로 검증**하거나 적정 상한(예: 64자)을 강제해, 72바이트 절단이 silent하게 일어나지 않게 막는 것이다. (Argon2를 쓰면 이 상한이 없지만, BCrypt를 쓰는 한 이 이슈는 명시적으로 다뤄야 한다.)

### 14.3 `passwordHash`를 member 모듈에 둔 이유

passwordHash를 auth가 아니라 **member 모듈**에 저장한 것은 의도적이다.

- **단일 문서 원자성**: 회원 정보와 비밀번호 해시가 같은 MongoDB 문서에 있으면, 회원 생성·비밀번호 변경이 단일 문서 쓰기로 원자적으로 처리된다. auth/member로 쪼개면 두 저장소 간 일관성(분산 트랜잭션 비슷한 문제)을 신경 써야 한다.
- **해싱 책임 집중**: "비밀번호를 해싱하고 검증한다"는 책임이 member 한 곳에 모인다(13장의 `PasswordVerification`과 한 묶음). auth는 자격 증명의 평문이나 해시를 절대 만지지 않는다 — 민감 데이터 노출 표면을 좁힌다.
- **개념적 소속**: 비밀번호는 "회원의 속성"이지 "인증 절차의 속성"이 아니다. 인증(auth)은 "검증을 요청"하는 주체이고, 자격 증명의 소유·보관은 member의 책임이라는 도메인 모델이 더 자연스럽다.

> **면접 포인트**: "왜 비밀번호를 인증 모듈이 아니라 회원 모듈에 두었나?"는 모듈 경계 이해를 보는 좋은 질문이다. 원자성 + 책임 집중 + 도메인 소속, 세 축으로 답하면 된다.

---

## 15. 결론 및 향후 개선 방향

앳크루의 인증 아키텍처를 관통하는 설계 철학은 세 가지로 요약된다:

1. **외부 의존을 최소 표면으로 가두기.** Firebase는 "Google 신원 증명"이라는 단일 책임으로 축소하고, 검증 결과를 자체 JWT로 변환해 인증 주권을 내부에 둔다. `FirebaseVerifier` 인터페이스 뒤에 SDK를 숨기고, Conditional Bean으로 환경 의존성을 끊는다.
2. **타입과 원자 연산으로 보안 불변식을 강제하기.** `PasswordVerification`은 정보 노출을 타입 레벨에서 막고, `findAndRemove`는 TOCTOU를 연산 레벨에서 막고, JWT `type` claim은 토큰 오용을 막는다. "규율로 지키는 보안"이 아니라 "구조로 못 깨게 하는 보안"이다.
3. **위협 모델에 맞춘 실용적 절충.** enumeration이 불가능한 Google에는 친절한 404를, 가능한 이메일에는 통합 401을 준다. Redis 대신 MongoDB TTL로 rate limit을 시작한다. 완벽 대신 "현재 위협과 규모에 맞는 합리적 방어"를 택했다.

향후 개선 방향:

- **이메일 자체 인증 보강**: 이메일 인증 코드/링크 기반 소유권 검증을 추가해, 가입과 비밀번호 재설정의 신뢰 수준을 높인다(현재 설계 문서로 진행 중인 영역).
- **Redis 도입**: 트래픽이 커지면 rate limiting과 토큰 블랙리스트를 Redis로 이전한다. sliding window rate limiter, access token 즉시 무효화(로그아웃/탈취 대응) 같은 기능이 가능해진다.
- **Account Linking(계정 통합)**: 같은 이메일의 EMAIL/GOOGLE 계정을 하나로 묶는 기능. 소유권 증명을 동반한 안전한 병합 플로우가 필요하다(6장에서 미룬 숙제).
- **Refresh Token 다중 디바이스 지원**: 현재의 단일 보유 정책은 "한 기기" 모델에 가깝다. 디바이스별 세션을 허용하려면 토큰에 device 식별자를 붙여 회전 정책을 디바이스 단위로 분리하는 진화가 필요하다.
- **Apple 로그인 등 provider 확장**: `extractProvider`의 `google.com` 강제를 provider별 분기로 확장하고, `AuthProvider` enum에 케이스를 추가한다.

---

### 한 줄 요약

> 앳크루 인증은 **Firebase를 Google 신원 증명기로만 쓰고**, 검증 후 **자체 access/refresh JWT 이중 구조**로 세션을 운영한다. enumeration·timing·TOCTOU·token confusion 같은 고전적 인증 공격을 **에러 통합, 더미 BCrypt, 원자 연산, type claim**으로 다층 방어하며, 모듈형 모놀리식 경계를 통해 자격 증명 책임을 member 모듈에 집중시켜 보안 표면을 좁혔다.
