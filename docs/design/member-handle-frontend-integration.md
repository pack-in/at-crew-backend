# 사용자 아이디(handle) 프론트 연동 요청서

백엔드는 `https://api.at-crew.com`, 프론트는 `https://at-crew.com` 기준이다. 설계 배경은
`docs/design/member-handle-design.md`(구현 차이는 §7), 정본 명세는 노션 설정-R17·로그인-R19다.

## 0. 먼저 알아야 할 것

- **공개 주소 체계가 바뀐다.** 작가 페이지·작품·공유 포트폴리오 주소가 모두 사용자 아이디(handle)를 앞세운
  `/{handle}/...` 형태가 되고, 공유 URL에서 **언어 세그먼트가 없어진다.** 옛 주소는 새 주소로 308 리다이렉트해야 한다.
- **handle은 이제 바뀔 수 있다.** 설정에서 변경하면(30일에 1회) 옛 handle은 영구히 그 회원 소유로 남아 새 주소로
  이어진다. 그래서 FE는 URL의 handle이 아니라 **API 응답의 현재 handle**을 기준으로 링크를 만들고 리다이렉트한다.
- BE는 운영에 배포됐다(2026-10-16, PR #252·#258). API 문서는 `https://at-crew-api-docs.pages.dev/`에 있고, 응답
  예시의 필드명은 실제 DTO 그대로다.
- BE가 먼저 나가도 **옛 API는 그대로 동작**한다(옛 `/api/portfolios/shared/{identifier}`·`/api/artworks/{id}` 유지).
  FE 가입 화면 배포 후 가입 요청의 `handle`은 **필수**가 됐고(이메일·Google 가입 모두, §6), 새 공유 포트폴리오의
  `shareSlug` 발급도 중단했다(PA-14, 2026-10-21). 이미 발급된 slug는 옛 링크 해석용으로 계속 동작한다.

## 1. 새 URL 체계

| 화면 | 새 주소 | 비고 |
|---|---|---|
| 작가 페이지 | `/{handle}` | 옛 `/portfolio/{handle}`의 대체 |
| 작품 상세 | `/{handle}/artwork/{code}` | 옛 `/artwork/{UUID}`의 대체. `code`는 영문 소문자·숫자 6자 |
| 공유 포트폴리오 | `/{handle}/portfolio/{code}` | 옛 `/portfolio/{slug}`의 대체. 언어 세그먼트 없음 |

- 작가 페이지 포트폴리오는 코드가 없다(주소가 `/{handle}`).
- 작품 코드와 포트폴리오 코드는 경로(`artwork`/`portfolio`)로 구분되므로 같은 값이 쓰일 수 있다. 코드는 **작가 안에서만**
  유일하다.
- `handle` 형식: 영문·숫자·밑줄(`_`) 4~14자, 대소문자 무관 중복 불가. 표시는 저장된 대소문자 그대로 한다.

### 1.1 예약어와 FE 최상위 라우트

`/{handle}`은 최상위 경로 한 칸을 handle이 차지하므로, FE의 최상위 라우트와 같은 이름의 handle을 BE가 막고 있다.

현재 예약어(대소문자 무관, 서버 상수 `HandleValidator.RESERVED`):

```
artwork bookmark community login portfolio pricing privacy profile search setting
terms trash upload billing _next admin help about signup settings company
```

- `api`·`me`(3자 이하)와 `tag-search`·`refund-policy`·`third-party-privacy`(하이픈 포함)는 handle 형식 검사에서 이미
  걸려 목록에 없다.
- **FE가 새 최상위 라우트(`/something`)를 추가할 때는 BE에 예약어 갱신을 요청해 달라.** 요청 전에 누군가 그 이름을
  handle로 가져가면(원장은 삭제되지 않는다) 그 이름은 영구히 그 사람 소유가 되고, 되돌리려면 운영자 강제 변경이 필요하며 그동안 라우트와 충돌한다.
  신규 라우트를 배포하기 **전에** BE 예약어 반영을 먼저 확인할 것.
- `company`는 기업 페이지 `/company/{companyId}`와의 충돌을 막으려고 예약어에 넣었다(2026-10-15).
  FE가 현재 쓰는 최상위 라우트 전체 목록을 주면 BE 목록과 대조하겠다.

## 2. 화면별 호출 API와 리다이렉트 규칙

### 2.1 공통 규칙

모든 응답에는 **현재 handle**이 들어 있다(`MemberProfileInfo.handle` / `ArtworkInfo.authorHandle` /
`PortfolioSharedInfo.ownerHandle`). URL의 handle 세그먼트와 비교한다.

1. 두 값을 **대소문자 무시**로 비교해 **다르면** 현재 handle로 URL을 다시 만들어 **308** 리다이렉트한다
   (나머지 경로는 그대로 유지).
2. **대소문자만 다르면 리다이렉트하지 않는다.** (`/pack_1n`과 `/Pack_1n`은 같은 사람이다. 화면에는 응답의 표기를 쓴다.)
3. SSR을 쓰는 화면이면 응답을 받은 직후 서버에서 처리해야 308이 의미를 가진다.

오류 응답은 `{ "code": "...", "message": "..." }` 형태다(`data` 없음).

| HTTP | code | FE 처리 |
|---|---|---|
| 404 | `MEMBER_NOT_FOUND` | 한 번도 쓰인 적 없는 handle — 없음 페이지 |
| 404 | `ARTWORK_NOT_FOUND` / `PORTFOLIO_NOT_FOUND` | 그 작가에게 해당 코드가 없음 — 없음 페이지 |
| 410 | `MEMBER_WITHDRAWN` | 탈퇴한 사용자 — 탈퇴 안내 페이지(메시지: "탈퇴한 사용자예요."). 이 handle은 영구히 재할당되지 않는다 |
| 410 | `ARTWORK_DELETED` / `ARTWORK_BLOCKED` / `PORTFOLIO_BLOCKED` | 기존과 동일한 삭제·차단 안내 |
| 403 | `ARTWORK_PRIVATE` | 기존과 동일 |
| 400 | `COMMON_INVALID_INPUT` | URL 세그먼트 형식 오류(handle은 `[A-Za-z0-9_-]` 1~50자, code는 `[a-z0-9]` 6자) — 없음 페이지 |

> `MEMBER_WITHDRAWN`(410)은 "예전에 쓰인 handle"임을 드러낸다. 탈퇴 안내 요구에 따른 의도된 노출이다.

### 2.2 화면 → API

| 화면 | API | 비고 |
|---|---|---|
| `/{handle}` 프로필 영역 | `GET /api/members/{handle}` | 옛 handle도 받는다. 응답 `data.handle`이 현재 handle |
| `/{handle}` 작품 그리드(작가 페이지 포트폴리오) | `GET /api/portfolios/shared/{handle}` 과 `GET /api/portfolios/shared/{handle}/artworks?cursor=&size=` | 식별자에 handle을 넣는다. 응답 `data.ownerHandle`이 현재 handle. `.../artworks`는 **인증 선택** — 아래 참고 |
| `/{handle}/artwork/{code}` | `GET /api/members/{handle}/artworks/{code}` | 인증 선택. 접근 판정은 기존 `GET /api/artworks/{id}`와 같다. 조회수는 오르지 않는다 — 기존대로 화면을 연 뒤 응답의 `data.id`로 `POST /api/artworks/{id}/views` 호출 |
| `/{handle}/portfolio/{code}` | `GET /api/members/{handle}/portfolios/{code}` 과 `.../artworks?cursor=&size=` | 포트폴리오 본문은 인증 불필요, `.../artworks`는 **인증 선택** — 아래 참고. `X-Robots-Tag: noindex` 헤더 포함 |
| 고정형 스냅샷 상세 | `GET /api/members/{handle}/portfolios/{code}/snapshots/{snapshotPublicId}` | `snapshotPublicId`는 카드의 `snapshotId` |

- **포트폴리오 작품 목록(`GET /api/members/{handle}/portfolios/{code}/artworks`, `GET /api/portfolios/shared/{handle}/artworks`)은
  인증 선택이다.** 토큰 없이도 열리지만, 로그인 상태라면 `Authorization: Bearer` 토큰을 **보내야** 성인 콘텐츠 표시
  설정(설정-R10)과 본인 예외(포트폴리오 소유자는 자기 성인 작품을 항상 봄)가 반영된다. 토큰을 빼면 비로그인으로
  취급돼 R18·G18 작품이 목록에서 빠진다. 포트폴리오 본문(`.../portfolios/{code}`)과 스냅샷 상세는 뷰어를 보지 않는다.
- 옛 handle로 `GET /api/members/{handle}`를 조회한 요청(리다이렉트 전)은 BE가 조회수 이벤트를 만들지 않는다
  (리다이렉트 전후 이중 집계 방지). FE는 별도 처리가 필요 없다.
- 응답 예시 — 작품 상세(`ArtworkInfo`에서 URL 관련 필드만 발췌):

```json
{ "code": "SUCCESS", "data": { "id": "0194...", "authorHandle": "Pack_1n", "publicCode": "k3x9a2", "title": "..." } }
```

- 응답 예시 — 공유 포트폴리오 헤더(`PortfolioSharedInfo`):

```json
{ "code": "SUCCESS", "data": {
    "id": "0194...", "kind": "SHARED", "reflectionType": "LIVE", "title": "일러스트 모음",
    "ownerName": "김창작", "ownerHandle": "Pack_1n", "publicCode": "m7q2zd", "itemCount": 12,
    "createdAt": "...", "updatedAt": "..." } }
```

## 3. 옛 주소 처리

옛 주소는 **항상** 새 주소로 308 리다이렉트한다(§2.1의 비교 없이). 아래 API 응답이 새 주소 조립 정보를 준다.

| 옛 주소 | 호출 API | 새 주소 조립 |
|---|---|---|
| `/portfolio/{handle\|slug}` (언어 세그먼트가 있던 형태 포함) | `GET /api/portfolios/shared/{identifier}` | 응답 `ownerHandle`·`publicCode`로 조립. `publicCode`가 있으면 `/{ownerHandle}/portfolio/{publicCode}`, **없으면(null, 작가 페이지) `/{ownerHandle}`** |
| `/artwork/{UUID}` | `GET /api/artworks/{id}` | 응답 `authorHandle`·`publicCode`로 `/{authorHandle}/artwork/{publicCode}` |
| `/profile/{handle}` | — | 아래 참고 |

- `identifier`는 옛 22자 slug 또는 handle(옛 handle 포함)이다. BE는 slug를 먼저 해석하고 없으면 handle로 해석한다.
  둘 다 안 되면 `PORTFOLIO_NOT_FOUND`(404), 탈퇴 회원 handle이면 `MEMBER_WITHDRAWN`(410)이다.
- `shareSlug`는 옛 링크 해석용이다. 신규 발급은 중단돼 **새 공유 포트폴리오의 `shareSlug`는 null**이고, 새 링크 생성에
  쓰면 안 된다. 이미 발급된 slug가 담긴 옛 링크는 위 API로 계속 해석된다.
- **`/profile/{handle}` 체인 정리 요청**: `profile`은 이 라우트 때문에 예약어로 막아 두었다. BE는 FE의 리다이렉트 체인을
  볼 수 없으므로, FE에 `/profile/{handle}` → (다른 경로) → ... 식의 연쇄 리다이렉트가 있다면 **`/{handle}`로 한 번에**
  308 하도록 정리해 달라. 다단 리다이렉트는 옛 handle이 섞이면 이중 변환이 생긴다. 실제 라우트 구성을 알려 주면
  BE 쪽에서 더 필요한 것이 있는지 확인하겠다.

## 4. 카드·링크 생성

모든 작품 카드는 **`authorHandle` + `publicCode`**로 `/{authorHandle}/artwork/{publicCode}` 링크를 만든다.

| 응답 | 사용 필드 | 사용처 |
|---|---|---|
| `ArtworkSummaryInfo` | `authorHandle`, `publicCode` | 홈 피드·핫한 작품·작가 찾기·북마크·커뮤니티 포트폴리오 탭·내 작품 목록 |
| `SearchResultItem` | `authorHandle`, `publicCode` | 검색 결과 카드(`postType=PORTFOLIO`). 구인글·구직글·팀원모집글은 두 필드가 null — 기존 링크 방식 유지 |
| `PortfolioArtworkCardInfo` | `publicCode` | 포트폴리오 작품 목록 카드. **카드에 handle이 없으므로** 같은 포트폴리오 응답의 `ownerHandle`(`PortfolioSharedInfo`/`PortfolioInfo`)을 쓴다 — 담긴 작품은 모두 소유자 본인의 작품이다 |
| `PortfolioInfo`·`PortfolioSummaryInfo` | `ownerHandle`, `publicCode` | 내 포트폴리오 목록·상세의 공유 링크 `/{ownerHandle}/portfolio/{publicCode}` |

- 검색 색인은 배포 직후 BE가 전체 재색인을 하므로 그 전에는 `publicCode`가 비어 있을 수 있다(배포 절차에 포함돼 있다).
- **고정형(SNAPSHOT) 포트폴리오 카드**는 `publicCode`와 `artworkId`가 **null**이고 `snapshotId`만 있다. 원본 작품 주소를
  조립할 수 없도록 한 기존 정책(마이페이지_작가-R38·R39)이며 이번에도 그대로다. 카드는 원본 작품이 아니라 **스냅샷 상세**
  (§2.2의 `.../snapshots/{snapshotPublicId}`)로 연결한다. 스냅샷 상세 화면의 FE 라우트 형태는 BE가 정하지 않으니
  `/{handle}/portfolio/{code}` 아래로 맞춰 정해 달라(옛 `.../shared/{identifier}/snapshots/{snapshotId}` 경로 대응).
  스냅샷 응답(`PortfolioSnapshotDetailInfo`)에는 handle이 없으므로 URL의 handle·code를 그대로 쓴다.
- 작품 카드의 `visibility`/`status` 등 기존 필드와 처리는 바뀌지 않는다.

## 5. 설정 > 사용자 아이디 변경 (설정-R17)

### 5.1 변경 API

```
PATCH /api/members/me/handle        (인증 필요)
{ "handle": "Pack_1n" }
```

성공(200):

```json
{ "code": "SUCCESS", "data": { "handle": "Pack_1n", "handleChangeAvailableAt": "2026-11-14T06:02:13.461882Z" } }
```

- `handle`: 서버가 앞뒤 공백(전각 포함)을 제거한다. 최대 50자(초과는 `COMMON_INVALID_INPUT`).
- 응답 `handle`은 입력한 대소문자 그대로의 현재 값이고, `handleChangeAvailableAt`은 다음 변경 가능 시각(UTC)이다.
- **같은 값을 5분 안에 다시 보내면 성공으로 응답한다**(네트워크 재시도 멱등). 이때 `handleChangeAvailableAt`은 처음 성공한
  시각 기준 그대로다. 5분이 지난 뒤에는 일반 변경으로 판정하므로 30일 제한(`HANDLE_CHANGE_TOO_SOON`)이 먼저 걸린다.
- 변경 성공은 `handle_changed_at`을 기록하고, 이후 30일간 재변경이 막힌다. 가입 시 입력한 handle과 운영자 변경은
  30일 산정에 들어가지 않는다 — **가입 직후 첫 변경은 대기 없이 가능**하다.
- 본인이 예전에 쓴 handle로의 복귀, 대소문자만 바꾸는 변경도 가능하다(각각 변경 1회로 계산).

### 5.2 오류코드 → 토스트 매핑

BE 응답 `message`에는 아래 토스트 문구가 들어 있다(명세 문구와 같다). 화면 정책상 FE가 직접 문구를 관리한다면 `code`로
분기한다. 문구의 정본은 설정-R17(노션)이다.

| HTTP | code | 토스트 문구 |
|---|---|---|
| 400 | `HANDLE_UNCHANGED` | 새 사용자 아이디를 입력해주세요. (빈 값 또는 현재 아이디와 동일 — **변경 API는 빈 값에도 이 코드로 답한다**) |
| 400 | `HANDLE_REQUIRED` | 새 사용자 아이디를 입력해주세요. (변경 화면에서는 `HANDLE_UNCHANGED`와 같은 문구로 매핑한다. 이 코드는 가입·사전 확인 API에서 나온다. BE 메시지는 가입용 "사용자 아이디를 입력해주세요.") |
| 400 | `HANDLE_TOO_SHORT` | 사용자 아이디는 4자 이상 입력해주세요. |
| 400 | `HANDLE_TOO_LONG` | 사용자 아이디는 14자 이하로 입력해주세요. |
| 400 | `HANDLE_INVALID_CHARACTERS` | 사용자 아이디는 영문, 숫자, 밑줄(_)만 사용할 수 있어요. |
| 400 | `HANDLE_CONSECUTIVE_UNDERSCORES` | 밑줄(_)은 연속으로 사용할 수 없어요. |
| 400 | `HANDLE_RESERVED` | 사용할 수 없는 사용자 아이디예요. 다른 아이디를 입력해주세요. |
| 409 | `DUPLICATE_HANDLE` | 이미 사용 중인 사용자 아이디예요. (다른 회원이 쓰고 있거나 **쓴 적이 있는** handle — 탈퇴 회원 포함, 구분해 알려 주지 않음) |
| 409 | `HANDLE_CHANGE_TOO_SOON` | 사용자 아이디는 **YYYY.MM.DD**부터 다시 변경할 수 있어요. — 날짜는 §5.3 |
| 409 | `HANDLE_CHANGE_CONFLICT` | 사용자 아이디를 변경하지 못했어요. 잠시 후 다시 시도해주세요. (같은 회원이 **서로 다른 값**으로 동시에 요청. 같은 값을 동시에 두 번 보낸 경우(더블클릭)는 둘 다 성공 응답) |
| 400 | `COMMON_INVALID_INPUT` | 51자 이상 입력 — FE 입력창에서 50자 초과를 막는 것을 권장한다. 서버까지 오면 서버 오류 토스트로 처리 |
| 5xx·네트워크 오류 | — | 사용자 아이디를 변경하지 못했어요. 잠시 후 다시 시도해주세요. |
| 401 | `UNAUTHENTICATED` | 로그인이 만료되었어요. (로그인 만료 처리) |

- 성공 토스트: **사용자 아이디가 변경되었어요.**
- 한 번에 하나의 오류만 내려온다. 우선순위(서버 판정 순서): 멱등 재전송 → 30일 제한 → 빈 값/현재와 동일 → 형식 위반
  (짧음 → 김 → 허용 외 문자 → 연속 밑줄 → 예약어) → 중복. 따라서 **30일 이내인 회원에게는 형식이 틀려도
  `HANDLE_CHANGE_TOO_SOON`이 먼저 온다.**
- 모든 `HANDLE_*` 코드는 변경 API에서도, 가입·사전 확인 API에서도 같은 이름이다.

### 5.3 `handleChangeAvailableAt` 사용법

`HANDLE_CHANGE_TOO_SOON` 오류 응답은 `data`가 비어 있어 날짜를 싣지 못한다. 대신 **`GET /api/members/me`의
`data.handleChangeAvailableAt`**을 읽는다.

- 값은 UTC ISO 8601이다. **회원 시간대**(같은 응답의 `data.timezone`, IANA ID)로 환산해 `YYYY.MM.DD`로 표시한다.
- **필드가 없으면 지금 변경 가능**하다(null이 아니라 응답에서 생략된다). 이미 시각이 지났다면 변경 요청을 그대로 보내면 된다.
- 설정 화면 진입 시 이 값을 미리 읽어 두면 입력 전에 안내할 수 있다. 오류를 받은 뒤 읽어도 된다.
- 판정은 서버가 정확한 시각(Instant) 기준으로 하며, 날짜 표시만 FE가 한다. 표시 날짜 당일이라도 시각이 안 됐으면 409가
  올 수 있으니 오류를 받으면 다시 안내한다.

### 5.4 성공 시 갱신할 상태·UI 규칙

성공(200) 응답을 받으면 아래를 새 handle(`data.handle`)로 갱신한다.

- 로그인 회원 상태의 현재 아이디(`me.handle`) — 계정 탭의 "현재 아이디" 표시
- 입력창 값(저장된 대소문자 그대로 응답 값으로 맞춘다)
- 화면에 노출하는 `@handle`
- **작가 페이지·내 작품·공유 포트폴리오 주소**: `/{handle}`, `/{handle}/artwork/{code}`, `/{handle}/portfolio/{code}` 형태로
  만든 링크와 캐시된 URL 전부. 서버 상태 캐시(내 작품·내 포트폴리오 목록의 `authorHandle`/`ownerHandle`)는 무효화한다.
- 다음 변경 가능 시각 캐시(`handleChangeAvailableAt`)

요청 UI 규칙:

- **요청 중에는 변경 버튼을 비활성화**해 중복 전송을 막는다(5분 멱등이 있어도 여러 번 누르게 두지 않는다).
- **실패하면 입력값을 유지**한다(지우지 않는다). 토스트만 띄운다.

## 6. 사전 확인 API

```
GET /api/members/handles/availability?handle=Pack_1n
```

- **인증 선택.** 가입 화면은 토큰 없이, 설정 화면은 `Authorization: Bearer` 토큰과 함께 호출한다. 토큰이 있으면 **본인이
  소유한(현재·옛) handle은 사용 가능**으로 판정한다. 단 **현재 handle과 대소문자까지 같으면** 변경 API와 같이
  `available: false`, `errorCode: "HANDLE_UNCHANGED"`다(대소문자만 다르면 사용 가능).
- 사용 불가여도 **HTTP는 200**이다. 응답:

```json
{ "code": "SUCCESS", "data": { "available": true, "errorCode": null } }
{ "code": "SUCCESS", "data": { "available": false, "errorCode": "HANDLE_TOO_SHORT" } }
```

- `errorCode`는 가입·변경 API와 같은 이름이다: `HANDLE_REQUIRED`·`HANDLE_TOO_SHORT`·`HANDLE_TOO_LONG`·
  `HANDLE_INVALID_CHARACTERS`·`HANDLE_CONSECUTIVE_UNDERSCORES`·`HANDLE_RESERVED`·`DUPLICATE_HANDLE`, 로그인 시
  `HANDLE_UNCHANGED`. (30일 제한은 사전 확인에서 판정하지 않는다.)
- `handle` 파라미터가 50자를 넘으면 `COMMON_INVALID_INPUT`(400)이다.
- 사전 확인은 참고용이다. 확인 직후에도 다른 사람이 먼저 가져갈 수 있으니 최종 판정은 가입·변경 API의 오류로 한다.
  호출 빈도는 입력 디바운스(예: 300ms 이상)를 권장한다.

## 7. 회원가입

`POST /api/auth/email/register`와 `POST /api/auth/google/register` 요청에 **`handle`**이 추가된다. 기업 계정도 같다.

```json
{
  "email": "user1@example.com", "password": "Passw0rd!", "passwordConfirm": "Passw0rd!",
  "name": "사용자1", "agreeService": true, "agreePrivacy": true, "agreeThirdParty": true, "agreeMarketing": false,
  "timezone": "Asia/Seoul", "countryCode": "KR", "primaryLanguage": "KO",
  "handle": "Pack_1n"
}
```

(Google 가입은 `email`·`password`·`passwordConfirm` 없이 `googleIdToken`을 보내는 기존 형식 — `docs/design/google-login-frontend-integration.md` §3 — 에 `handle`만 추가한다.)

- **`handle`은 필수다(이메일·Google 가입 모두).** 보내지 않거나 빈 문자열·공백뿐이면 `HANDLE_REQUIRED`(400)이고 회원은 만들어지지 않는다.
  서버 자동 생성은 없다.
- 가입 시 입력한 handle은 30일 변경 제한에 산정되지 않는다. 30일 제한·"현재와 동일" 검사도 가입에는 없다.
- 이메일 중복과 handle 중복은 구분된다.

가입 오류 코드:

| HTTP | code | 의미 |
|---|---|---|
| 400 | `HANDLE_REQUIRED` | 미입력·빈 문자열·공백뿐인 값 |
| 400 | `HANDLE_TOO_SHORT` · `HANDLE_TOO_LONG` · `HANDLE_INVALID_CHARACTERS` · `HANDLE_CONSECUTIVE_UNDERSCORES` · `HANDLE_RESERVED` | 형식 위반(§5.2와 같은 이름) |
| 400 | `COMMON_INVALID_INPUT` | handle 51자 이상 등 기존 `@Valid` 실패 |
| 409 | `DUPLICATE_HANDLE` | 다른 회원(탈퇴 회원 포함)이 쓰고 있거나 쓴 적이 있는 handle |
| 409 | `DUPLICATE_EMAIL` | 기존과 동일 |

**가입 토스트 문구는 기획 확인 중이다(PH-02).** 로그인-R19는 "사용자 아이디 형식·예약어·중복 문구는 설정-R17과 동일 안내"
라고만 적고 있어 위 표의 §5.2 문구를 임시로 쓸 수 있지만, 가입 전용 문구와 다중 오류 우선순위는 미정이다.
확정 전까지 BE 응답의 `message`를 그대로 표시하거나 §5.2 문구를 임시로 쓰고, 확정되면 갱신한다.

## 8. 기획 확인 요청 사항 (PH-02 문안)

아래는 FE가 아니라 기획팀에 확인할 사항이다. FE는 결정이 나올 때까지 BE 응답 `message`/§5.2 문구를 임시로 쓴다.

1. **가입 화면(로그인-R19)에 사용자 아이디 입력 항목 반영** — 주 사용 언어 → 이름 → 사용자 아이디 → 약관 순서, 입력
   규칙 안내 문구·placeholder.
2. **가입 전용 오류 문구** — 설정-R17의 "새 사용자 아이디를 입력해주세요."는 변경 화면용이다. 가입에서 빈 값에는
   "사용자 아이디를 입력해주세요."(BE `HANDLE_REQUIRED` 메시지)를 쓸지 확정.
3. **다중 오류 우선순위** — 가입 시 이름·사용자 아이디·약관이 동시에 틀렸을 때 어느 토스트를 먼저 보여줄지.
   BE는 handle 하나에 대해 첫 위반 하나만 돌려준다(짧음 → 김 → 허용 외 문자 → 연속 밑줄 → 예약어 → 중복).
4. **"확인하지 못했어요" 토스트의 용도** — 사전 확인 API 실패(네트워크·5xx)에 쓰는 문구인지, 변경 실패와 구분하는지.
5. **변경 화면 Figma ↔ 명세 불일치 5건**
   - 현재 아이디 placeholder(Figma 표기와 명세 불일치)
   - 주소 변경 안내 누락(변경 즉시 작가 페이지·작품·공유 포트폴리오 주소가 바뀐다는 안내)
   - 모바일 성공 토스트 문구·아이콘
   - "이미 사용 중" 문구 중복
   - 서버 오류 토스트 2종("변경하지 못했어요" 계열의 사용처 구분)
6. **결정 변경 공유**: 6자 코드 대입 가능성 감수(§9), 탈퇴 회원 handle 영구 재할당 금지.

## 9. 알려진 제약과 범위 밖

알려진 제약(의도된 동작):

- **6자 코드는 대입 가능하다.** 작가 handle을 아는 사람이 코드를 대입하면 공유 포트폴리오와 "피드 비공개 + 포트폴리오
  포함" 작품(업로드-R09상 URL 접근 허용)을 찾을 수 있다. 기존 22자 slug·UUID가 주던 추측 불가능성을 URL 길이와
  맞바꾼 결정이며 감수하기로 했다(2026-10-15). 공유 링크 자체가 접근 권한이라는 안내 문구가 필요한지는 기획 판단이다.
- **탈퇴한 회원의 handle은 영구히 재할당되지 않는다.** 탈퇴 후 재가입해도 예전 handle은 쓸 수 없다(`DUPLICATE_HANDLE`).
  이전 탈퇴 회원 중 일부는 handle 원본이 없어 보호되지 않는다(탈퇴 시 비워 왔기 때문).
- **410 `MEMBER_WITHDRAWN`은 "과거에 쓰인 handle"임을 드러낸다.**
- 작품 코드가 삭제 후 재사용되면 옛 링크가 같은 작가의 다른 작품을 열 수 있다(작가당 36^6 경우, 실질 확률 무시 가능).
- 배포 시 **규칙 위반 기존 handle은 일괄 변경**된다(`{이름부}_{숫자 5자리}`). 옛 handle은 새 주소로 이어진다.

범위 밖:

- 공유자 추적 토큰 `?s=`는 이번 범위가 아니다.
- **기업 페이지 주소는 `/company/{companyId}`를 유지한다.** (2026-10-15 확정 — `/{handle}`로 바꾸지 않는다.)
- OG 카드 렌더링, FE 라우팅 구현 자체와 토스트 문구는 FE 몫이다.

## 10. API 문서

- 전체 API 문서(Swagger): https://at-crew-api-docs.pages.dev/ — **main 반영 후 갱신된다.** 그전에는 이 문서가 기준이다.
- 신규·변경 API와 오류 코드(비2xx 응답 description)도 같은 문서에 반영된다.

신규 API 요약:

| API | 인증 |
|---|---|
| `PATCH /api/members/me/handle` | 필요 |
| `GET /api/members/handles/availability?handle=` | 선택 |
| `GET /api/members/{handle}/artworks/{code}` | 선택 |
| `GET /api/members/{handle}/portfolios/{code}`, `.../snapshots/{snapshotPublicId}` | 불필요 |
| `GET /api/members/{handle}/portfolios/{code}/artworks` | 선택 — 로그인 상태면 토큰을 보내야 성인 표시 설정·본인 예외가 반영된다 |

변경된 응답 필드:

| 응답 | 추가 필드 |
|---|---|
| `GET /api/members/me` (`MemberInfo`) | `handleChangeAvailableAt` (없으면 생략) |
| `ArtworkInfo`, `ArtworkSummaryInfo` | `publicCode` |
| `PortfolioInfo`, `PortfolioSummaryInfo`, `PortfolioSharedInfo` | `ownerHandle`, `publicCode` (작가 페이지는 `publicCode` null) |
| `PortfolioArtworkCardInfo` | `publicCode` (고정형 카드는 null) |
| `SearchResultItem` | `publicCode` (`authorHandle`은 기존) |
| 가입 요청 (`EmailRegisterRequest`, `GoogleRegisterRequest`) | `handle` (필수) |
