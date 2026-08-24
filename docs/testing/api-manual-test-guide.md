# API 수동 테스트 가이드

본 문서는 앳크루 백엔드의 모든 공개 API(컨트롤러 8종)를 Swagger UI 기준으로 수동 테스트하기 위한 가이드입니다.
실제 소스 코드(Controller, Request DTO, ErrorCode enum, SecurityConfig)에서 추출한 필드·제약조건·에러 코드를 그대로 사용합니다.

---

## 사전 준비

- **Swagger UI 접속**: http://localhost:8080/swagger-ui.html
- **OpenAPI 문서**: http://localhost:8080/v3/api-docs
- **프로파일**: `prod`가 아닌 프로파일(예: `local`, `dev`)로 실행해야 합니다.
  - `prod`에서는 Swagger UI, 개발용 회원가입(`POST /api/members`)이 비활성화됩니다.
- **인증 방식**: JWT Bearer Token
  - 로그인/회원가입 응답의 `data.accessToken`을 복사하여 Swagger의 `Authorize` 버튼에 `Bearer {accessToken}` 형태로 입력합니다.
  - Access Token 만료 시 `POST /api/auth/refresh`로 갱신합니다.
- **공통 응답 봉투** (`ApiResponse<T>`):
  ```json
  { "code": "SUCCESS", "message": null, "data": { ... } }
  ```
  - 실패 시 `code`에 에러 코드(예: `AUTHENTICATION_FAILED`), `message`에 사용자 메시지, `data`는 null.
  - 에러 코드는 ErrorCode enum의 **name() 값을 그대로** 사용합니다 (접두사 없음).
- **공통 에러 코드** (GlobalExceptionHandler):
  | 코드 | 상황 | HTTP |
  |------|------|------|
  | `COMMON_INVALID_INPUT` | `@Valid` Bean Validation 실패 / JSON 파싱 실패 / PathVariable·RequestParam 제약 위반 | 400 |
  | `UNAUTHENTICATED` | 유효한 인증 정보 없이 보호된 API 호출 | 401 |
  | `HTTP_404` | 존재하지 않는 URL | 404 |
  | `HTTP_405` | 허용되지 않는 HTTP 메서드 | 405 |
  | `HTTP_415` | 지원하지 않는 미디어 타입 | 415 |
  | `COMMON_INTERNAL_SERVER_ERROR` | 처리되지 않은 서버 예외 | 500 |

---

## 인증 필요 여부 요약 (SecurityConfig 기준)

**인증 불필요 (permitAll)**:
- `POST /api/auth/email/login`, `POST /api/auth/email/register`
- `POST /api/auth/google/login`, `POST /api/auth/google/register`
- `POST /api/auth/refresh`
- `POST /api/members` (prod 제외 — 개발용)
- `GET /api/members/{handle}`
- `GET /api/artworks/{artworkId}`
- `GET /api/community/artworks`
- `POST /internal/artwork/images/processed` (별도 `X-Internal-Secret` 헤더 검증)

**인증 필요 (그 외 전부)**: 회원 프로필 수정, 경력, 작품 업로드/수정/삭제, 북마크, 휴지통 등.

---

## 테스트 흐름 개요

```
[1단계] 회원가입 & 인증
  ├─ (개발용) POST /api/members  → memberId·handle 확보
  ├─ POST /api/auth/email/register  → accessToken·refreshToken 확보 (실서비스 가입 경로)
  ├─ POST /api/auth/email/login
  ├─ POST /api/auth/google/register / login (Firebase 토큰 필요)
  └─ POST /api/auth/refresh
        │  accessToken 을 Swagger Authorize 에 등록
        ▼
[2단계] 회원 프로필 (인증)
  ├─ GET /api/members/{handle}        (공개)
  ├─ PATCH /api/members/me/name
  ├─ PATCH /api/members/me/info
  ├─ POST /api/members/me/careers     → careerId 확보
  ├─ DELETE /api/members/me/careers/{careerId}
  └─ DELETE /api/members/me           (탈퇴 — 다른 테스트 끝난 뒤 마지막에)
        ▼
[3단계] 작품 업로드 & 조회
  ├─ POST /api/artwork/images/presign → imageKeys 확보(R2 업로드 가정)
  ├─ POST /api/artworks               → artworkId 확보 (PROCESSING 상태)
  ├─ POST /internal/artwork/images/processed → READY 전환(Worker 시뮬레이션)
  ├─ GET /api/artworks/{artworkId}    (공개)
  ├─ GET /api/artworks/{artworkId}/status
  ├─ GET /api/members/me/artworks
  ├─ PATCH /api/artworks/{artworkId}
  ├─ PATCH /api/artworks/{artworkId}/publication
  └─ GET /api/community/artworks      (공개)
        ▼
[4단계] 북마크
  ├─ POST /api/bookmarks/folders      → folderId 확보
  ├─ GET /api/bookmarks/folders
  ├─ POST /api/bookmarks              → 작품 북마크
  ├─ GET /api/bookmarks
  ├─ PATCH /api/bookmarks/move
  ├─ DELETE /api/bookmarks/{artworkId}
  └─ DELETE /api/bookmarks/folders/{folderId}
        ▼
[5단계] 휴지통
  ├─ DELETE /api/artworks/{artworkId} (휴지통 이동)
  ├─ GET /api/trash/artworks
  ├─ POST /api/trash/artworks/restore
  └─ DELETE /api/trash/artworks       (영구 삭제)
```

> **의존성 핵심**: 토큰이 필요한 모든 API는 1단계에서 발급한 accessToken을 Authorize에 등록한 뒤 호출합니다.
> 작품 ID는 3단계에서, 폴더 ID는 4단계에서, 경력 ID는 2단계에서 각각 확보해 후속 단계에 사용합니다.

---

## 참고: enum 값 목록

코드에서 정의된 enum 값입니다. 그 외 값을 보내면 `COMMON_INVALID_INPUT`(JSON 파싱 실패)이 발생합니다.

- **CreatorRole**: `WEBTOON`, `ILLUSTRATOR`, `WEB_NOVELIST`, (기타) — 웹툰작가/일러스트작가/웹소설작가
- **EmploymentStatus**: `PREPARING`, `AVAILABLE`, `NEGOTIABLE`
- **ActivityField**: `ILLUSTRATION`, `WEBTOON`, `PRINT_COMIC`, `ANIMATION` (작가는 복수 선택, 기업은 단일 선택)
- **ExperienceLevel**: `NEWCOMER`, `ONE_TO_TWO`, `THREE_TO_FOUR`, `FIVE_TO_NINE`, `TEN_PLUS`
- **ActiveRegion**: `SEOUL`, `GYEONGGI`, `DAEJEON`, `DAEGU`, `GWANGJU`, `BUSAN`, `OTHER`
- **TeamExperience**: `NONE`, `SHORT_TERM`, `DIVISION`, `REGULAR_DEADLINE`
- **ArtworkField**: `ILLUSTRATION`, `WEBTOON`, `PRINT_COMIC`, `ANIMATION`, `ETC`
- **CreativeType**: `ORIGINAL`, `SECONDARY`, `FAN_ART`, `OC`, `COMMISSION`
- **ArtworkRole**: `TOTAL_ARTWORK`, `ADAPTATION_STORYBOARD`, `STORYBOARD`, `DIRECTION`, `LINEART`, `SKETCH`, `COLORING`, `BASE_COLOR`, `TONE_WORK`, `POST_PROCESSING`, `FULL_COLOR`, `PANEL_DECORATION`, `THREE_D_MODELING`, `MATERIAL_MAKING`, `MATERIAL_PLACEMENT`, `BACKGROUND`, `WEBNOVEL_COVER`, `CHARACTER_DESIGN`, `CHARACTER_SHEET`, `TYPOGRAPHY`, `BROADCAST_THUMBNAIL`
- **AgeRating**: `ALL`, `R18`, `G18`
- **노출 위치**: `publishToFeed`(피드 공개 ON/OFF) × `portfolioIds`(담을 라이브 포트폴리오) 조합 — 공개 상태값을 직접 고르는 필드는 없다(업로드-R09)
- **ImageLayoutType**: `VERTICAL_SCROLL`, `HORIZONTAL_SWIPE`
- **ArtworkStatus**: `PROCESSING`, `READY`, `DELETED`
- **ImageProcessingStatus**: `PENDING`, `DONE`, `FAILED`
- **WorkDuration**: 객체 `{ "months": int, "days": int, "hours": int, "minutes": int }` (전부 nullable)

---

## 1단계: 회원가입 & 인증

### 1-0. 회원 가입 (개발용) [POST /api/members]

**목적**: Firebase/이메일 인증 없이 회원을 직접 생성. **prod 프로파일에서는 비활성화**.
**인증**: 불필요

**Request Body**:
```json
{
  "loginEmail": "tester@example.com",
  "handle": "creator_kim",
  "name": "김창작",
  "creatorRole": "WEBTOON"
}
```
**필드 제약**:
- `loginEmail`: `@NotBlank @Email`
- `handle`: `@NotBlank`, 정규식 `^[a-zA-Z0-9_-]{3,30}$` (영문·숫자·_·- 3~30자)
- `name`: `@NotBlank @Size(max=16)`
- `creatorRole`: `@NotNull` (CreatorRole enum)

**정상 응답 (201)**: `data`에 MemberInfo (id, handle, name, creatorRole 등).

**예외 케이스**:
| 케이스 | 변경 값 | 예상 에러 코드 | HTTP |
|--------|---------|--------------|------|
| 이메일 형식 오류 | loginEmail: "notEmail" | COMMON_INVALID_INPUT | 400 |
| 핸들 형식 오류 | handle: "ab" (3자 미만) | COMMON_INVALID_INPUT | 400 |
| 이름 길이 초과 | name: 17자 이상 | COMMON_INVALID_INPUT | 400 |
| creatorRole 누락 | creatorRole 생략 | COMMON_INVALID_INPUT | 400 |
| 이메일 중복 | 기존 loginEmail 재사용 | DUPLICATE_EMAIL | 409 |
| 핸들 중복 | 기존 handle 재사용 | DUPLICATE_HANDLE | 409 |

---

### 1-1. 이메일 회원가입 [POST /api/auth/email/register]

**목적**: 이메일·비밀번호로 가입. 가입 즉시 활성화되며 토큰을 발급받습니다.
**인증**: 불필요

**Request Body**:
```json
{
  "email": "user1@example.com",
  "password": "Passw0rd!",
  "passwordConfirm": "Passw0rd!",
  "name": "사용자1",
  "agreeService": true,
  "agreePrivacy": true,
  "agreeThirdParty": false,
  "agreeMarketing": false
}
```
**필드 제약**:
- `email`: `@NotBlank @Email`
- `password`: `@NotBlank`, 정규식 `^(?=.*[A-Za-z])(?=.*\d)(?=.*[^A-Za-z\d\s]).{8,64}$` (영문·숫자·특수문자 포함 8~64자)
- `passwordConfirm`: `password`와 일치해야 함 (`@AssertTrue isPasswordConfirmed`)
- `name`: `@NotBlank @Size(max=16)`
- `agreeService`/`agreePrivacy`/`agreeThirdParty`/`agreeMarketing`: boolean (필수 약관 미동의 시 서비스단에서 거부)

**정상 응답 (201)**:
```json
{
  "code": "SUCCESS",
  "message": null,
  "data": {
    "accessToken": "eyJ...",
    "refreshToken": "eyJ...",
    "member": { "id": "...", "handle": "...", "name": "사용자1", "...": "..." },
    "isNewUser": true
  }
}
```
> **확보할 값**: `data.accessToken`(Authorize 등록), `data.refreshToken`(1-5에서 사용).

**예외 케이스**:
| 케이스 | 변경 값 | 예상 에러 코드 | HTTP |
|--------|---------|--------------|------|
| 이메일 형식 오류 | email: "notEmail" | COMMON_INVALID_INPUT | 400 |
| 비밀번호 규칙 위반 | password: "12345678" (특수문자·영문 없음) | COMMON_INVALID_INPUT | 400 |
| 비밀번호 불일치 | passwordConfirm을 다르게 | COMMON_INVALID_INPUT | 400 |
| 이름 길이 초과 | name: 17자 이상 | COMMON_INVALID_INPUT | 400 |
| 필수 약관 미동의 | agreeService: false 등 | TERMS_NOT_AGREED | 400 |
| 이미 가입된 이메일 | 기존 email 재사용 | DUPLICATE_EMAIL | 409 |

---

### 1-2. 이메일 로그인 [POST /api/auth/email/login]

**목적**: 이메일·비밀번호 로그인. 실패 사유(미가입·탈퇴·비밀번호 오류)는 보안상 단일 코드로 통합됩니다.
**인증**: 불필요

**Request Body**:
```json
{ "email": "user1@example.com", "password": "Passw0rd!" }
```
**필드 제약**: `email` `@NotBlank @Email`, `password` `@NotBlank` + 비밀번호 정규식.

**정상 응답 (200)**: 1-1과 동일 구조의 AuthInfo (`isNewUser`는 false 예상).

**예외 케이스**:
| 케이스 | 변경 값 | 예상 에러 코드 | HTTP |
|--------|---------|--------------|------|
| 이메일 형식 오류 | email: "notEmail" | COMMON_INVALID_INPUT | 400 |
| 비밀번호 형식 위반 | password: "short" | COMMON_INVALID_INPUT | 400 |
| 미가입 / 비밀번호 오류 / 탈퇴 회원 | 잘못된 자격 증명 | AUTHENTICATION_FAILED | 401 |
| 마이그레이션 회원(비밀번호 미설정) | 마이그레이션 계정 로그인 | PASSWORD_RESET_REQUIRED | 428 |
| 로그인 시도 횟수 초과 | 단시간 반복 실패 | TOO_MANY_ATTEMPTS | 429 |

---

### 1-3. Google 로그인 [POST /api/auth/google/login]

**목적**: Firebase ID Token으로 Google 로그인. 미가입 시 404를 반환해 프론트가 가입 화면으로 이동합니다.
**인증**: 불필요 (Firebase ID Token이 본문에 포함)

**Request Body**:
```json
{ "firebaseIdToken": "<Firebase ID Token>" }
```
**필드 제약**: `firebaseIdToken` `@NotBlank`.

**정상 응답 (200)**: AuthInfo.

**예외 케이스**:
| 케이스 | 변경 값 | 예상 에러 코드 | HTTP |
|--------|---------|--------------|------|
| 토큰 누락 | firebaseIdToken: "" | COMMON_INVALID_INPUT | 400 |
| 토큰 검증 실패 | 위조·만료 토큰 | INVALID_FIREBASE_TOKEN | 401 |
| 미가입 계정 | 가입 안 된 Google 계정 | MEMBER_NOT_REGISTERED | 404 |
| Firebase 미설정 | 환경설정 누락 | FIREBASE_NOT_CONFIGURED | 503 |

---

### 1-4. Google 회원가입 [POST /api/auth/google/register]

**목적**: Firebase ID Token으로 Google 계정 가입.
**인증**: 불필요

**Request Body**:
```json
{
  "firebaseIdToken": "<Firebase ID Token>",
  "name": "구글유저",
  "agreeService": true,
  "agreePrivacy": true,
  "agreeThirdParty": false,
  "agreeMarketing": false
}
```
**필드 제약**: `firebaseIdToken` `@NotBlank`, `name` `@NotBlank @Size(max=16)`, 약관 boolean.

**정상 응답 (201)**: AuthInfo (`isNewUser`: true).

**예외 케이스**:
| 케이스 | 변경 값 | 예상 에러 코드 | HTTP |
|--------|---------|--------------|------|
| 이름 누락 | name: "" | COMMON_INVALID_INPUT | 400 |
| 이름 길이 초과 | name: 17자 이상 | COMMON_INVALID_INPUT | 400 |
| 필수 약관 미동의 | agreeService: false | TERMS_NOT_AGREED | 400 |
| 토큰 검증 실패 | 위조·만료 토큰 | INVALID_FIREBASE_TOKEN | 401 |
| 이미 가입된 Google 계정 | 기존 계정 재가입 | DUPLICATE_EMAIL | 409 |

---

### 1-5. 토큰 갱신 [POST /api/auth/refresh]

**목적**: Refresh Token으로 새 Access/Refresh Token 발급.
**인증**: 불필요 (Refresh Token이 본문에 포함)

**Request Body**:
```json
{ "refreshToken": "<refreshToken>" }
```
**필드 제약**: `refreshToken` `@NotBlank`.

**정상 응답 (200)**: AuthInfo (새 토큰 쌍).

**예외 케이스**:
| 케이스 | 변경 값 | 예상 에러 코드 | HTTP |
|--------|---------|--------------|------|
| 토큰 누락 | refreshToken: "" | COMMON_INVALID_INPUT | 400 |
| 만료·위조·재사용된 토큰 | 잘못된 refreshToken | INVALID_REFRESH_TOKEN | 401 |

---

## 2단계: 회원 프로필 (인증 필요, 단 핸들 조회는 공개)

> 2단계부터는 Swagger Authorize에 `Bearer {accessToken}`을 등록한 상태로 진행합니다.

### 2-1. 핸들로 회원 조회 [GET /api/members/{handle}]

**목적**: @핸들로 회원 프로필 조회.
**인증**: 불필요 (공개)
**Path Variable**: `handle` — 정규식 `^[a-zA-Z0-9_-]{3,30}$` (예: `creator_kim`)

**정상 응답 (200)**: `data`에 MemberProfileInfo (공개 프로필).

**예외 케이스**:
| 케이스 | 변경 값 | 예상 에러 코드 | HTTP |
|--------|---------|--------------|------|
| 핸들 형식 오류 | handle: "ab" | COMMON_INVALID_INPUT | 400 |
| 존재하지 않는 회원 | 없는 handle | MEMBER_NOT_FOUND | 404 |

---

### 2-2. 이름 수정 [PATCH /api/members/me/name]

**목적**: 내 이름·작가명 수정 (최대 16자).
**인증**: 필요

**Request Body**:
```json
{ "name": "새이름" }
```
**필드 제약**: `name` `@NotBlank @Size(max=16)`.

**정상 응답 (204)**: 본문 없음.

**예외 케이스**:
| 케이스 | 변경 값 | 예상 에러 코드 | HTTP |
|--------|---------|--------------|------|
| 이름 공백 | name: "" | COMMON_INVALID_INPUT | 400 |
| 이름 길이 초과 | name: 17자 이상 | COMMON_INVALID_INPUT | 400 |
| 토큰 없음 | Authorize 미등록 | UNAUTHENTICATED | 401 |

---

### 2-3. 프로필 정보 수정 [PATCH /api/members/me/info]

**목적**: 구인구직 상태·활동 분야·경력·지역·슬롯·연락처·SNS·툴 등 프로필 전체 수정. 각 필드 `null`이면 변경 없음.
**인증**: 필요

**Request Body** (전 필드 nullable):
```json
{
  "creatorRole": "ILLUSTRATOR",
  "employmentStatus": "AVAILABLE",
  "activityFields": ["ILLUSTRATION", "WEBTOON"],
  "experienceLevel": "THREE_TO_FOUR",
  "activeRegion": "SEOUL",
  "totalSlotCount": 3,
  "availableSlotCount": 2,
  "teamExperiences": ["SHORT_TERM"],
  "contact": "010-1234-5678",
  "sns": "https://instagram.com/me",
  "tools": "Procreate, Photoshop"
}
```
**필드 제약**:
- `activityFields`: `@Size(max=4)`, 원소 `@NotNull` (`[]` 전송 시 전체 삭제)
- `activeRegion`: 단일 선택 (SEOUL·GYEONGGI·GANGWON·CHUNGBUK·CHUNGNAM·JEONBUK·JEONNAM·GYEONGBUK·GYEONGNAM·JEJU)
- `teamExperiences`: `@Size(max=4)`, 원소 `@NotNull`
- `totalSlotCount`: `@Min(1) @Max(5)`
- `availableSlotCount`: `@Min(0) @Max(5)`, `totalSlotCount` 이하여야 함(서비스단 검증)
- `contact`: `@Size(max=100)`, 정규식 `^$|^(01[016789]-\d{3,4}-\d{4}|이메일형식)$` (빈 문자열 전송 시 삭제)
- `sns`: `@Size(max=200)`, `tools`: `@Size(max=200)`

**정상 응답 (204)**: 본문 없음.

**예외 케이스**:
| 케이스 | 변경 값 | 예상 에러 코드 | HTTP |
|--------|---------|--------------|------|
| 활동 분야 5개 초과 | activityFields 5개 | COMMON_INVALID_INPUT | 400 |
| 슬롯 범위 위반 | totalSlotCount: 6 | COMMON_INVALID_INPUT | 400 |
| 연락처 형식 오류 | contact: "abc" | COMMON_INVALID_INPUT | 400 |
| 잘못된 enum 값 | employmentStatus: "FOO" | COMMON_INVALID_INPUT | 400 |
| 가용 슬롯 > 전체 슬롯 | total 2, available 5 | INVALID_SLOT_COUNT | 400 |
| 토큰 없음 | Authorize 미등록 | UNAUTHENTICATED | 401 |

---

### 2-4. 경력 추가 [POST /api/members/me/careers]

**목적**: 참여작 정보를 경력으로 추가.
**인증**: 필요

**Request Body**:
```json
{
  "workTitle": "어느 봄날의 이야기",
  "role": "총작화",
  "startDate": "2023.01.01",
  "endDate": "2024.06.30",
  "ongoing": false,
  "description": "메인 작화 담당"
}
```
**필드 제약**:
- `workTitle`: `@NotBlank @Size(max=100)`
- `role`: `@Size(max=100)`
- `startDate`: `@NotNull @PastOrPresent`, 포맷 `yyyy.MM.dd`
- `endDate`: `@PastOrPresent`, 포맷 `yyyy.MM.dd` (연재중이면 null)
- `ongoing`: boolean
- `description`: `@Size(max=200)`

**정상 응답 (201)**: `data`에 CareerEntryInfo (id 포함).
> **확보할 값**: `data.id` → 2-5에서 사용할 careerId.

**예외 케이스**:
| 케이스 | 변경 값 | 예상 에러 코드 | HTTP |
|--------|---------|--------------|------|
| 작품명 누락 | workTitle: "" | COMMON_INVALID_INPUT | 400 |
| 시작일 누락 | startDate 생략 | COMMON_INVALID_INPUT | 400 |
| 시작일 미래 날짜 | startDate: "2099.01.01" | COMMON_INVALID_INPUT | 400 |
| 날짜 포맷 오류 | startDate: "2023-01-01" | COMMON_INVALID_INPUT | 400 |
| 종료일이 시작일보다 앞섬 | end < start | INVALID_CAREER_PERIOD | 400 |
| 경력 50개 초과 | 51번째 추가 | CAREER_LIMIT_EXCEEDED | 400 |
| 토큰 없음 | Authorize 미등록 | UNAUTHENTICATED | 401 |

---

### 2-5. 경력 삭제 [DELETE /api/members/me/careers/{careerId}]

**목적**: 등록된 경력 항목 삭제.
**인증**: 필요
**Path Variable**: `careerId` (2-4에서 확보).

**정상 응답 (204)**: 본문 없음.

**예외 케이스**:
| 케이스 | 변경 값 | 예상 에러 코드 | HTTP |
|--------|---------|--------------|------|
| 존재하지 않는 경력 | 없는 careerId | CAREER_NOT_FOUND | 404 |
| 토큰 없음 | Authorize 미등록 | UNAUTHENTICATED | 401 |

---

### 2-6. 회원 탈퇴 [DELETE /api/members/me]

**목적**: 내 계정을 소프트 딜리트(비활성화).
**인증**: 필요
> **주의**: 탈퇴 후에는 동일 토큰으로 다른 API 호출이 불가하므로 **모든 테스트 마지막에** 수행하세요.

**정상 응답 (204)**: 본문 없음. 이후 동일 계정 이메일 로그인 시 `AUTHENTICATION_FAILED`(401).

**예외 케이스**:
| 케이스 | 변경 값 | 예상 에러 코드 | HTTP |
|--------|---------|--------------|------|
| 토큰 없음 | Authorize 미등록 | UNAUTHENTICATED | 401 |

---

## 3단계: 작품 업로드 & 조회

### 3-1. 이미지 Presigned URL 발급 [POST /api/artwork/images/presign]

**목적**: R2 직접 업로드용 Presigned PUT URL 발급.
**인증**: 필요

**Request Body**:
```json
{ "count": 2, "contentTypes": ["image/jpeg", "image/png"] }
```
**필드 제약**:
- `count`: `@Min(1) @Max(20)`
- `contentTypes`: `@NotEmpty @Size(max=20)` (허용: jpeg, png, webp)

**정상 응답 (200)**: `data`에 PresignedUrlInfo 리스트 (각 항목에 업로드 URL과 imageKey). imageKey를 R2 업로드 후 3-2에서 사용.

**예외 케이스**:
| 케이스 | 변경 값 | 예상 에러 코드 | HTTP |
|--------|---------|--------------|------|
| count 0 또는 21 | count: 0 | COMMON_INVALID_INPUT | 400 |
| contentTypes 비어있음 | contentTypes: [] | COMMON_INVALID_INPUT | 400 |
| 허용되지 않는 형식 | contentTypes: ["image/gif"] | INVALID_CONTENT_TYPE | 400 |
| URL 생성 실패 | R2 연동 오류 | PRESIGN_FAILED | 500 |
| 토큰 없음 | Authorize 미등록 | UNAUTHENTICATED | 401 |

---

### 3-2. 작품 업로드 [POST /api/artworks]

**목적**: R2 업로드 완료 후 작품 정보 저장. `PROCESSING` 상태로 시작.
**인증**: 필요

**Request Body**:
```json
{
  "imageKeys": ["uploads/abc.jpg", "uploads/def.png"],
  "representativeImageIndex": 0,
  "thumbnailKey": "uploads/thumb.jpg",
  "imageLayoutType": "VERTICAL_SCROLL",
  "title": "첫 번째 작품",
  "description": "설명 텍스트",
  "artworkField": "ILLUSTRATION",
  "creativeType": "ORIGINAL",
  "roles": ["TOTAL_ARTWORK", "COLORING"],
  "genres": ["판타지"],
  "tags": ["오리지널", "캐릭터"],
  "ageRating": "ALL",
  "publishToFeed": true,
  "portfolioIds": ["4c8c0d5e-1b2a-7c3d-8e4f-5a6b7c8d9e0f"],
  "tools": ["Procreate"],
  "workDuration": { "months": 0, "days": 3, "hours": 5, "minutes": 0 },
  "cutCount": 12,
  "videoLinks": ["https://youtube.com/watch?v=xxx"],
  "materials": [
    {
      "name": "브러시 세트",
      "targets": ["채색"],
      "attachmentKeys": ["uploads/brush.zip"],
      "links": ["https://example.com/brush"]
    }
  ]
}
```
**필드 제약**:
- `imageKeys`: `@NotEmpty @Size(max=20)`
- `representativeImageIndex`: `@Min(0)` (imageKeys 범위 내여야 함 — 서비스단 검증)
- `thumbnailKey`: 선택
- `imageLayoutType`: `@NotNull`
- `title`: `@NotBlank @Size(max=100)`
- `description`: `@Size(max=500)`
- `artworkField`/`creativeType`/`ageRating`/`publishToFeed`: `@NotNull`
- `portfolioIds`: 선택. 본인 소유의 작가 페이지·최신 반영형 포트폴리오만 지정할 수 있다(고정형은 409, 타인 소유는 403, 스타터가 공유 포트폴리오를 지정하면 403 — 이때 작품도 생성되지 않는다)
- `roles`: `@NotEmpty`
- `tags`: `@Size(max=7)`
- `videoLinks`: `@Size(max=5)`
- `materials[].name`: `@NotBlank`
- `genres`/`tools`/`cutCount`/`workDuration`: 선택

**정상 응답 (201)**: `data`에 ArtworkInfo (artworkId, status=PROCESSING).
> **확보할 값**: `data.id`(artworkId) → 이후 조회/수정/북마크/삭제에 사용.

**예외 케이스**:
| 케이스 | 변경 값 | 예상 에러 코드 | HTTP |
|--------|---------|--------------|------|
| 이미지 0개 | imageKeys: [] | COMMON_INVALID_INPUT | 400 |
| 이미지 21개 초과 | imageKeys 21개 | COMMON_INVALID_INPUT | 400 |
| 제목 누락 | title: "" | COMMON_INVALID_INPUT | 400 |
| 제목 길이 초과 | title: 101자 | COMMON_INVALID_INPUT | 400 |
| roles 비어있음 | roles: [] | COMMON_INVALID_INPUT | 400 |
| 태그 8개 이상 | tags 8개 | COMMON_INVALID_INPUT | 400 |
| 필수 값 누락 | publishToFeed 생략 | COMMON_INVALID_INPUT | 400 |
| 고정형 포트폴리오 지정 | portfolioIds에 고정형 ID | SNAPSHOT_PORTFOLIO_IMMUTABLE | 409 |
| 타인 포트폴리오 지정 | portfolioIds에 타인 ID | PORTFOLIO_ACCESS_DENIED | 403 |
| 대표 인덱스 범위 초과 | representativeImageIndex: 99 | INVALID_REPRESENTATIVE_INDEX | 400 |
| 토큰 없음 | Authorize 미등록 | UNAUTHENTICATED | 401 |

---

### 3-3. 이미지 처리 콜백 (내부용) [POST /internal/artwork/images/processed]

**목적**: 이미지 Worker가 처리 완료 후 호출하는 내부 콜백. 작품을 `READY`로 전환. Swagger에 `@Hidden`이라 UI에 미노출 → curl/Postman으로 직접 호출.
**인증**: SecurityConfig상 permitAll이나 **`X-Internal-Secret` 헤더 검증** 필수.

**Headers**: `X-Internal-Secret: <artwork.internal.secret 설정값>`

**Request Body**:
```json
{
  "artworkId": "<3-2의 artworkId>",
  "imageKey": "uploads/abc.jpg",
  "thumbKey": "processed/thumb.webp",
  "thumbAdultKey": "processed/thumb_adult.webp",
  "originalAvifKey": "processed/orig.avif",
  "status": "DONE"
}
```
**필드 제약**: `artworkId` `@NotBlank`, `imageKey` `@NotBlank`, `status` `@NotNull` (ImageProcessingStatus: PENDING/DONE/FAILED). `thumbKey`/`thumbAdultKey`/`originalAvifKey` 선택.

**정상 응답 (204)**: 본문 없음.

**예외 케이스**:
| 케이스 | 변경 값 | 예상 에러 코드 | HTTP |
|--------|---------|--------------|------|
| 시크릿 헤더 누락/불일치 | X-Internal-Secret 잘못 | INTERNAL_SECRET_INVALID | 401 |
| artworkId 누락 | artworkId: "" | COMMON_INVALID_INPUT | 400 |
| status 누락 | status 생략 | COMMON_INVALID_INPUT | 400 |

---

### 3-4. 작품 상세 조회 [GET /api/artworks/{artworkId}]

**목적**: 작품 상세 조회.
**인증**: 불필요 (공개) — 단, 토큰이 있으면 viewer 권한(비공개/링크전용 접근)에 반영.
**Path Variable**: `artworkId`.

**정상 응답 (200)**: `data`에 ArtworkInfo.

**예외 케이스**:
| 케이스 | 변경 값 | 예상 에러 코드 | HTTP |
|--------|---------|--------------|------|
| 존재하지 않는 작품 | 없는 artworkId | ARTWORK_NOT_FOUND | 404 |
| 비공개 작품을 비소유자가 조회 | PRIVATE 작품, 타인 토큰 | ARTWORK_ACCESS_DENIED | 403 |

---

### 3-5. 작품 처리 상태 폴링 [GET /api/artworks/{artworkId}/status]

**목적**: 이미지 Worker 처리 완료 여부 확인.
**인증**: 필요 (소유자)
**Path Variable**: `artworkId`.

**정상 응답 (200)**: `data`에 ArtworkStatus (`PROCESSING` / `READY` / `DELETED`).

**예외 케이스**:
| 케이스 | 변경 값 | 예상 에러 코드 | HTTP |
|--------|---------|--------------|------|
| 존재하지 않는 작품 | 없는 artworkId | ARTWORK_NOT_FOUND | 404 |
| 타인 작품 | 비소유자 토큰 | ARTWORK_ACCESS_DENIED | 403 |
| 토큰 없음 | Authorize 미등록 | UNAUTHENTICATED | 401 |

---

### 3-6. 내 작품 목록 [GET /api/members/me/artworks]

**목적**: 내 작품을 커서 페이지네이션으로 조회.
**인증**: 필요
**Query Parameters**:
- `cursor` (선택): 마지막 작품 createdAt millis
- `size` (선택): 페이지 크기 (기본 20, 최대 50)

**정상 응답 (200)**: `data`에 CursorPage<ArtworkSummaryInfo> (`items`, `nextCursor`, `hasNext`).

**예외 케이스**:
| 케이스 | 변경 값 | 예상 에러 코드 | HTTP |
|--------|---------|--------------|------|
| 잘못된 커서 | cursor: "abc" | INVALID_CURSOR | 400 |
| 토큰 없음 | Authorize 미등록 | UNAUTHENTICATED | 401 |

---

### 3-7. 작품 수정 [PATCH /api/artworks/{artworkId}]

**목적**: 작품 정보 수정. 전 필드 nullable (부분 수정).
**인증**: 필요 (소유자)
**Path Variable**: `artworkId`.

**Request Body** (예시 — 일부만):
```json
{ "title": "수정된 제목", "description": "수정된 설명", "노출 위치 제외 모든 필드 부분 전송 가능": true }
```
> 참고: 수정 요청에는 노출 위치 필드가 없습니다 (3-8 전용 API로 재선언). 제약은 업로드와 동일하되 모두 선택(nullable).

**필드 제약**: `imageKeys` `@Size(max=20)`, `representativeImageIndex` `@Min(0)`, `title` `@Size(max=100)`, `description` `@Size(max=500)`, `tags` `@Size(max=7)`, `videoLinks` `@Size(max=5)`, `materials[].name` `@NotBlank`.

**정상 응답 (200)**: `data`에 수정된 ArtworkInfo.

**예외 케이스**:
| 케이스 | 변경 값 | 예상 에러 코드 | HTTP |
|--------|---------|--------------|------|
| 제목 길이 초과 | title: 101자 | COMMON_INVALID_INPUT | 400 |
| 처리 중인 작품 수정 | status=PROCESSING 작품 | ARTWORK_NOT_READY | 400 |
| 대표 인덱스 범위 초과 | representativeImageIndex 초과 | INVALID_REPRESENTATIVE_INDEX | 400 |
| 존재하지 않는 작품 | 없는 artworkId | ARTWORK_NOT_FOUND | 404 |
| 타인 작품 | 비소유자 토큰 | ARTWORK_ACCESS_DENIED | 403 |
| 토큰 없음 | Authorize 미등록 | UNAUTHENTICATED | 401 |

---

### 3-8. 노출 위치 재선언 [PATCH /api/artworks/{artworkId}/publication]

**목적**: 작품 피드 공개 여부와 담을 포트폴리오를 재선언한다. 공개 상태는 이 조합으로 서버가 계산한다(업로드-R09).
**인증**: 필요 (소유자)
**Path Variable**: `artworkId`.

**Request Body**:
```json
{ "publishToFeed": false, "portfolioIds": ["4c8c0d5e-1b2a-7c3d-8e4f-5a6b7c8d9e0f"] }
```
**필드 제약**: `publishToFeed` `@NotNull`. `portfolioIds`는 증분이 아니라 **전체 목록**이라 빠진 포트폴리오에서는 제외된다(빈 배열 = 전부 제외).

**정상 응답 (204)**: 본문 없음.

**예외 케이스**:
| 케이스 | 변경 값 | 예상 에러 코드 | HTTP |
|--------|---------|--------------|------|
| publishToFeed 누락 | publishToFeed 생략 | COMMON_INVALID_INPUT | 400 |
| 고정형 포트폴리오 지정 | portfolioIds에 고정형 ID | SNAPSHOT_PORTFOLIO_IMMUTABLE | 409 |
| 처리 중인 작품 | status=PROCESSING | (허용 — 업로드 시와 같은 조합을 그대로 받는다) | 204 |
| 휴지통 작품 | status=DELETED | ARTWORK_DELETED | 410 |
| 존재하지 않는 작품 | 없는 artworkId | ARTWORK_NOT_FOUND | 404 |
| 타인 작품 | 비소유자 토큰 | ARTWORK_ACCESS_DENIED | 403 |
| 토큰 없음 | Authorize 미등록 | UNAUTHENTICATED | 401 |

---

### 3-9. 커뮤니티 작품 목록 [GET /api/community/artworks]

**목적**: 공개 작품을 최신순으로 조회.
**인증**: 불필요
**Query Parameters**:
- `artworkField` (선택): ArtworkField enum 필터
- `ageRating` (선택): AgeRating enum 필터 (기본 ALL)
- `cursor` (선택): 마지막 작품 createdAt millis
- `size` (선택): 페이지 크기 (기본 20, 최대 50)

**정상 응답 (200)**: `data`에 CursorPage<ArtworkSummaryInfo>.

**예외 케이스**:
| 케이스 | 변경 값 | 예상 에러 코드 | HTTP |
|--------|---------|--------------|------|
| 잘못된 enum 필터 | artworkField: "FOO" | COMMON_INVALID_INPUT | 400 |
| 잘못된 커서 | cursor: "abc" | INVALID_CURSOR | 400 |

---

## 4단계: 북마크 (전부 인증 필요)

### 4-1. 북마크 폴더 생성 [POST /api/bookmarks/folders]

**목적**: 북마크 폴더 생성.
**인증**: 필요

**Request Body**:
```json
{ "name": "관심 작품" }
```
**필드 제약**: `name` `@NotBlank @Size(max=20)`.

**정상 응답 (201)**: `data`에 BookmarkFolderInfo.
> **확보할 값**: `data.id` → folderId (4-2, 4-6, 4-7에서 사용).

**예외 케이스**:
| 케이스 | 변경 값 | 예상 에러 코드 | HTTP |
|--------|---------|--------------|------|
| 폴더명 공백 | name: "" | COMMON_INVALID_INPUT | 400 |
| 폴더명 길이 초과 | name: 21자 | COMMON_INVALID_INPUT | 400 |
| 공백만 입력 | name: "   " | BOOKMARK_FOLDER_NAME_BLANK | 400 |
| 폴더명 중복 | 기존 폴더명 재사용 | BOOKMARK_FOLDER_DUPLICATE_NAME | 409 |
| 토큰 없음 | Authorize 미등록 | UNAUTHENTICATED | 401 |

---

### 4-2. 북마크 폴더 목록 [GET /api/bookmarks/folders]

**목적**: 내 북마크 폴더 목록 조회.
**인증**: 필요

**정상 응답 (200)**: `data`에 BookmarkFolderInfo 리스트.

**예외 케이스**:
| 케이스 | 변경 값 | 예상 에러 코드 | HTTP |
|--------|---------|--------------|------|
| 토큰 없음 | Authorize 미등록 | UNAUTHENTICATED | 401 |

---

### 4-3. 북마크 저장 [POST /api/bookmarks]

**목적**: 작품을 북마크에 저장. `folderId` 미지정 시 기본 폴더에 저장.
**인증**: 필요

**Request Body**:
```json
{ "artworkId": "<3-2의 artworkId>", "folderId": "<4-1의 folderId 또는 생략>" }
```
**필드 제약**: `artworkId` `@NotBlank`, `folderId` 선택.

**정상 응답 (201)**: `data`에 BookmarkEntryInfo.

**예외 케이스**:
| 케이스 | 변경 값 | 예상 에러 코드 | HTTP |
|--------|---------|--------------|------|
| artworkId 누락 | artworkId: "" | COMMON_INVALID_INPUT | 400 |
| 존재하지 않는 작품 | 없는 artworkId | ARTWORK_NOT_FOUND | 404 |
| 존재하지 않는 폴더 | 없는 folderId | BOOKMARK_FOLDER_NOT_FOUND | 404 |
| 이미 북마크한 작품 | 동일 작품 재저장 | BOOKMARK_ALREADY_EXISTS | 409 |
| 토큰 없음 | Authorize 미등록 | UNAUTHENTICATED | 401 |

---

### 4-4. 북마크 목록 [GET /api/bookmarks]

**목적**: 북마크 목록 조회. `folderId` 미지정 시 기본 폴더. 삭제/비공개 작품은 미노출.
**인증**: 필요
**Query Parameters**: `folderId`(선택), `cursor`(선택), `size`(선택, 기본 20·최대 50).

**정상 응답 (200)**: `data`에 CursorPage<BookmarkEntryInfo>.

**예외 케이스**:
| 케이스 | 변경 값 | 예상 에러 코드 | HTTP |
|--------|---------|--------------|------|
| 존재하지 않는 폴더 | 없는 folderId | BOOKMARK_FOLDER_NOT_FOUND | 404 |
| 잘못된 커서 | cursor: "abc" | INVALID_CURSOR | 400 |
| 토큰 없음 | Authorize 미등록 | UNAUTHENTICATED | 401 |

---

### 4-5. 북마크 폴더 이동 [PATCH /api/bookmarks/move]

**목적**: 선택한 작품들의 북마크를 다른 폴더로 이동. `targetFolderId` 미지정 시 기본 폴더.
**인증**: 필요

**Request Body**:
```json
{ "artworkIds": ["<artworkId1>", "<artworkId2>"], "targetFolderId": "<folderId 또는 생략>" }
```
**필드 제약**: `artworkIds` `@NotEmpty`, `targetFolderId` 선택.

**정상 응답 (204)**: 본문 없음.

**예외 케이스**:
| 케이스 | 변경 값 | 예상 에러 코드 | HTTP |
|--------|---------|--------------|------|
| artworkIds 비어있음 | artworkIds: [] | COMMON_INVALID_INPUT | 400 |
| 존재하지 않는 대상 폴더 | 없는 targetFolderId | BOOKMARK_FOLDER_NOT_FOUND | 404 |
| 북마크되지 않은 작품 | 미북마크 artworkId | BOOKMARK_NOT_FOUND | 404 |
| 토큰 없음 | Authorize 미등록 | UNAUTHENTICATED | 401 |

---

### 4-6. 북마크 해제 [DELETE /api/bookmarks/{artworkId}]

**목적**: 작품 북마크 해제.
**인증**: 필요
**Path Variable**: `artworkId`.

**정상 응답 (204)**: 본문 없음.

**예외 케이스**:
| 케이스 | 변경 값 | 예상 에러 코드 | HTTP |
|--------|---------|--------------|------|
| 북마크되지 않은 작품 | 미북마크 artworkId | BOOKMARK_NOT_FOUND | 404 |
| 토큰 없음 | Authorize 미등록 | UNAUTHENTICATED | 401 |

---

### 4-7. 북마크 폴더 삭제 [DELETE /api/bookmarks/folders/{folderId}]

**목적**: 폴더 삭제. 폴더 내 북마크는 기본 폴더로 이동.
**인증**: 필요
**Path Variable**: `folderId`.

**정상 응답 (204)**: 본문 없음.

**예외 케이스**:
| 케이스 | 변경 값 | 예상 에러 코드 | HTTP |
|--------|---------|--------------|------|
| 존재하지 않는 폴더 | 없는 folderId | BOOKMARK_FOLDER_NOT_FOUND | 404 |
| 토큰 없음 | Authorize 미등록 | UNAUTHENTICATED | 401 |

---

## 5단계: 휴지통 (전부 인증 필요)

### 5-1. 작품 삭제 (휴지통 이동) [DELETE /api/artworks/{artworkId}]

> ArtworkController 소속이지만 휴지통 흐름의 시작점이므로 5단계에서 다룹니다.

**목적**: 작품을 휴지통으로 이동(소프트 딜리트).
**인증**: 필요 (소유자)
**Path Variable**: `artworkId`.

**정상 응답 (204)**: 본문 없음. status가 `DELETED`로 전환.

**예외 케이스**:
| 케이스 | 변경 값 | 예상 에러 코드 | HTTP |
|--------|---------|--------------|------|
| 존재하지 않는 작품 | 없는 artworkId | ARTWORK_NOT_FOUND | 404 |
| 타인 작품 | 비소유자 토큰 | ARTWORK_ACCESS_DENIED | 403 |
| 토큰 없음 | Authorize 미등록 | UNAUTHENTICATED | 401 |

---

### 5-2. 휴지통 목록 [GET /api/trash/artworks]

**목적**: 휴지통에 있는 내 작품 목록 조회.
**인증**: 필요
**Query Parameters**: `cursor`(선택), `size`(선택, 기본 20·최대 50).

**정상 응답 (200)**: `data`에 CursorPage<ArtworkSummaryInfo>.

**예외 케이스**:
| 케이스 | 변경 값 | 예상 에러 코드 | HTTP |
|--------|---------|--------------|------|
| 잘못된 커서 | cursor: "abc" | INVALID_CURSOR | 400 |
| 토큰 없음 | Authorize 미등록 | UNAUTHENTICATED | 401 |

---

### 5-3. 작품 복구 [POST /api/trash/artworks/restore]

**목적**: 선택 작품을 휴지통에서 복구. 삭제 전 공개 상태 복원.
**인증**: 필요

**Request Body**:
```json
{ "artworkIds": ["<artworkId>"] }
```
**필드 제약**: `artworkIds` `@NotEmpty`.

**정상 응답 (204)**: 본문 없음.

**예외 케이스**:
| 케이스 | 변경 값 | 예상 에러 코드 | HTTP |
|--------|---------|--------------|------|
| artworkIds 비어있음 | artworkIds: [] | COMMON_INVALID_INPUT | 400 |
| 휴지통에 없는 작품 | 미삭제 artworkId | ARTWORK_NOT_DELETED | 400 |
| 존재하지 않는 작품 | 없는 artworkId | ARTWORK_NOT_FOUND | 404 |
| 타인 작품 | 비소유자 artworkId | ARTWORK_ACCESS_DENIED | 403 |
| 토큰 없음 | Authorize 미등록 | UNAUTHENTICATED | 401 |

---

### 5-4. 작품 영구 삭제 [DELETE /api/trash/artworks]

**목적**: 휴지통 작품을 영구 삭제. R2 원본 파일도 삭제.
**인증**: 필요

**Request Body**:
```json
{ "artworkIds": ["<artworkId>"] }
```
**필드 제약**: `artworkIds` `@NotEmpty`.

**정상 응답 (204)**: 본문 없음.

**예외 케이스**:
| 케이스 | 변경 값 | 예상 에러 코드 | HTTP |
|--------|---------|--------------|------|
| artworkIds 비어있음 | artworkIds: [] | COMMON_INVALID_INPUT | 400 |
| 휴지통에 없는 작품 | 미삭제 artworkId | ARTWORK_NOT_DELETED | 400 |
| 존재하지 않는 작품 | 없는 artworkId | ARTWORK_NOT_FOUND | 404 |
| 타인 작품 | 비소유자 artworkId | ARTWORK_ACCESS_DENIED | 403 |
| 토큰 없음 | Authorize 미등록 | UNAUTHENTICATED | 401 |

---

## 부록: 전체 엔드포인트 체크리스트 (전수 23개)

### 인증 (AuthController) — 5개
- [ ] POST /api/auth/email/login
- [ ] POST /api/auth/email/register
- [ ] POST /api/auth/google/login
- [ ] POST /api/auth/google/register
- [ ] POST /api/auth/refresh

### 회원 (MemberController) — 5개
- [ ] GET /api/members/{handle}
- [ ] PATCH /api/members/me/name
- [ ] PATCH /api/members/me/info
- [ ] POST /api/members/me/careers
- [ ] DELETE /api/members/me/careers/{careerId}
- [ ] DELETE /api/members/me

### 회원 (DevMemberController, prod 제외) — 1개
- [ ] POST /api/members

### 작품 (ArtworkController) — 8개
- [ ] POST /api/artwork/images/presign
- [ ] POST /api/artworks
- [ ] GET /api/artworks/{artworkId}
- [ ] GET /api/artworks/{artworkId}/status
- [ ] PATCH /api/artworks/{artworkId}
- [ ] PATCH /api/artworks/{artworkId}/publication
- [ ] DELETE /api/artworks/{artworkId}
- [ ] GET /api/members/me/artworks

### 커뮤니티 (CommunityController) — 1개
- [ ] GET /api/community/artworks

### 북마크 (BookmarkController) — 7개
- [ ] GET /api/bookmarks/folders
- [ ] POST /api/bookmarks/folders
- [ ] DELETE /api/bookmarks/folders/{folderId}
- [ ] GET /api/bookmarks
- [ ] POST /api/bookmarks
- [ ] DELETE /api/bookmarks/{artworkId}
- [ ] PATCH /api/bookmarks/move

### 휴지통 (TrashController) — 3개
- [ ] GET /api/trash/artworks
- [ ] POST /api/trash/artworks/restore
- [ ] DELETE /api/trash/artworks

### 내부용 (ArtworkInternalController, Swagger @Hidden) — 1개
- [ ] POST /internal/artwork/images/processed
