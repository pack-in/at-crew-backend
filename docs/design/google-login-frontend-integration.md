# Google 로그인 프론트 연동 요청서

백엔드는 `https://api.at-crew.com`, 프론트는 `https://at-crew.com` 기준이다.

## 0. 먼저 알아야 할 것

- **백엔드가 Firebase Admin SDK 검증 방식에서 Google ID Token 직접 검증 방식으로 전환됐다.**
  프론트도 맞춰서 **Firebase JS SDK(`firebase/auth`)를 쓰지 않는다.** 대신 **Google Identity
  Services(GIS)** 스크립트 하나로 Google ID Token(JWT)을 직접 받아 백엔드로 넘긴다.
- GIS는 npm 패키지 설치가 필요 없다. `<script src="https://accounts.google.com/gsi/client">`
  한 줄만 로드하면 `window.google.accounts.id` 전역 객체가 생긴다.
- **공개 Client ID는 아직 발급 전이다.** dev/prod 각각의 Client ID는 백엔드 팀이 별도로
  전달할 예정이다. 아래 예시의 `{GOOGLE_CLIENT_ID}`는 자리표시자이며, 실제 값이 나올 때까지
  환경변수 등으로 비워둔다.
- 프론트는 ID Token의 내용(이메일·이름 등)을 직접 파싱해서 쓸 필요가 없다 — 검증과 클레임
  추출은 전부 백엔드가 한다. 프론트는 GIS 콜백에서 받은 `credential`(JWT 문자열)을 그대로
  `googleIdToken` 필드에 담아 아래 API로 전달하면 된다.
- 로그인과 회원가입 모두 **같은 방식으로 ID Token을 받고**, 로그인 API를 먼저 호출해 404가
  오면 회원가입 API로 전환하는 흐름은 기존과 동일하다(§4).

## 1. GIS 스크립트 로딩 & ID Token 받기

```html
<script src="https://accounts.google.com/gsi/client" async defer></script>
```

```js
window.google.accounts.id.initialize({
  client_id: '{GOOGLE_CLIENT_ID}', // dev/prod 값은 백엔드 팀 전달 예정
  callback: handleGoogleCredential,
});

// 버튼을 직접 렌더링하는 방식
window.google.accounts.id.renderButton(
  document.getElementById('google-login-button'),
  { theme: 'outline', size: 'large', text: 'continue_with' },
);

function handleGoogleCredential(response) {
  const googleIdToken = response.credential; // JWT 문자열 — 이 값을 그대로 백엔드에 전달
  // 로그인 먼저 시도 → 404면 회원가입 화면으로 전환(§4)
  loginWithGoogle(googleIdToken);
}
```

- `renderButton` 대신 커스텀 버튼에서 `google.accounts.id.prompt()`(One Tap)를 쓸 수도 있다.
  두 방식 모두 콜백으로 같은 형태의 `credential`(ID Token)을 받으므로 이후 API 연동은 동일하다.
- ID Token은 발급 후 유효 시간이 짧다(수 분~1시간 내외). 화면에 오래 머물다 로그인 버튼을
  누르면 만료된 토큰이 넘어갈 수 있으니, 버튼 클릭 시점에 새로 발급받은 `credential`을
  바로 전송하는 흐름을 권장한다(캐싱·재사용 금지).

## 2. Google 로그인

```
POST /api/auth/google/login
{ "googleIdToken": "eyJhbGciOi..." }
```

성공 시(200):

```json
{
  "code": "SUCCESS",
  "message": null,
  "data": {
    "accessToken": "eyJ...",
    "refreshToken": "eyJ...",
    "member": { "id": "...", "handle": "...", "name": "...", "...": "회원 정보 전체(설정 API 문서 참고)" },
    "isNewUser": false
  }
}
```

주요 실패 응답:

| 상태 | 코드 | 처리 |
|------|------|------|
| 401 | `INVALID_GOOGLE_TOKEN` | Google 토큰 검증 실패 — "로그인에 실패했어요" 등 일반 안내, 재시도 유도 |
| 404 | `MEMBER_NOT_REGISTERED` | **미가입 계정** — 같은 `googleIdToken`을 들고 회원가입 화면(§3)으로 이동시킨다 |
| 503 | `GOOGLE_LOGIN_NOT_CONFIGURED` | 서버에 Google Client ID가 설정되지 않은 상태 — "Google 로그인을 준비 중이에요" 안내(dev 환경에서 Client ID 미배포 시 발생 가능) |
| 429 | `TOO_MANY_ATTEMPTS` | 같은 IP에서 토큰 검증 실패가 반복됨(10분 내 30회) — "잠시 후 다시 시도해주세요" 안내, 재시도 버튼 비활성화 권장 |

- Google 토큰이 곧 이메일 소유 증명이라, 이메일 로그인과 달리 미가입 여부를 404로 그대로
  노출해도 계정 존재 열거(enumeration) 문제가 되지 않는다(백엔드 판단, 변경 요청 대상 아님).
- 이메일 로그인의 이메일 단위 카운터(5회/10분)는 Google 로그인에 없다 — 토큰 검증 전에는
  이메일을 알 수 없기 때문이다. 대신 IP 단위 카운터(10분 내 30회)가 로그인·회원가입 양쪽에
  적용된다.

## 3. Google 회원가입

```
POST /api/auth/google/register
{
  "googleIdToken": "eyJhbGciOi...",
  "name": "홍길동",
  "agreeService": true,
  "agreePrivacy": true,
  "agreeThirdParty": false,
  "agreeMarketing": false,
  "timezone": "Asia/Seoul",
  "countryCode": "KR",
  "primaryLanguage": "KO"
}
```

| 필드 | 설명 |
|------|------|
| `googleIdToken` | §1에서 받은 ID Token(JWT). 필수 |
| `name` | 사용자 이름·작가명, 최대 16자. 필수 |
| `agreeService` / `agreePrivacy` / `agreeThirdParty` / `agreeMarketing` | 약관 동의 여부(불리언) |
| `timezone` | IANA 시간대 ID, 클라이언트 자동감지값. 예: `Asia/Seoul`. 최대 64자, 필수 |
| `countryCode` | 거주 국가, ISO 3166-1 alpha-2(대문자 2자). 예: `KR`. 필수 |
| `primaryLanguage` | 주 사용 언어 코드(예: `KO`). **가입 후 변경 불가**(로그인-R19). 필수 |

성공 시(201) 응답 형태는 §2와 동일하고 `isNewUser: true`가 내려온다.

주요 실패 응답:

| 상태 | 코드 | 처리 |
|------|------|------|
| 400 | `PRIMARY_LANGUAGE_REQUIRED` | 주 사용 언어 미선택 — 가입 폼에서 재입력 요구 |
| 401 | `INVALID_GOOGLE_TOKEN` | Google 토큰 검증 실패 — §1부터 다시 진행(구글 로그인 버튼 재클릭) |
| 409 | `DUPLICATE_EMAIL`(메시지: "이미 가입된 이메일입니다") | 이미 가입된 계정 — 로그인 화면으로 유도 |
| 503 | `GOOGLE_LOGIN_NOT_CONFIGURED` | §2와 동일 |
| 429 | `TOO_MANY_ATTEMPTS` | §2와 동일 |

- 그 외 `@Valid` 검증 실패(빈 이름·형식 오류 등)는 공통 `COMMON_INVALID_INPUT` 400으로 온다.

## 4. 로그인 ↔ 회원가입 전환 흐름

1. GIS 콜백으로 받은 `googleIdToken`으로 먼저 §2 로그인을 호출한다.
2. 404 `MEMBER_NOT_REGISTERED`가 오면 **같은 `googleIdToken`을 유지한 채** 회원가입 화면(약관
   동의·시간대·국가·주 사용 언어 입력)으로 이동시키고, 입력이 끝나면 §3 회원가입을 호출한다.
3. 그 사이 ID Token이 만료될 수 있으므로(§1), 회원가입 화면 진입이 오래 지연되면 로그인
   버튼을 다시 눌러 새 `credential`을 받는 것을 권장한다.
4. 401 `INVALID_GOOGLE_TOKEN`은 로그인·회원가입 어느 단계에서 나와도 처리는 동일하다 —
   GIS 로그인 버튼을 다시 눌러 새 ID Token을 받게 한다.

## 5. 알려진 제약

- Client ID 실제 값은 이 문서 작성 시점(2026-09-14)에 아직 미발급 상태다. dev/prod 값이
  나오는 대로 백엔드 팀이 별도 공지한다.
- Firebase JS SDK 관련 기존 연동 코드(Firebase 프로젝트 설정, `firebase/auth` import 등)는
  이번 전환으로 더 이상 쓰지 않는다 — 제거 대상이다.
