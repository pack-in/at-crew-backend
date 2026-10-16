# 사용자 아이디(handle) 설계 — 변경·원장·공개 URL·작품/포트폴리오 6자 코드

> 작성일: 2026-10-15
> 상태: **구현 완료(브랜치 `danhandev/member-handle`, 미배포)** — 실행 계획은 `plans/261015-member-handle/`, 설계와 달라진 지점은 §7. 운영 절차는 `docs/operations/handle-change.md`, FE 연동은 `docs/design/member-handle-frontend-integration.md`
> 범위: handle 규칙 정비, 설정 화면 handle 변경, 가입 시 직접 입력, 이전 handle 리다이렉트, 작품·공유 포트폴리오 6자 공개 코드와 `/{handle}/...` URL 체계, 기존 데이터 이전, 검색 색인 정합
> 범위 밖: 공유자 추적 토큰(`?s=`), 운영자 handle 변경 관리자 API(SQL 런북으로 대체), OG 카드 렌더링, FE 라우팅·토스트 문구
> 정본 근거: 노션 설정-R17(2026-10-14, 사용자 아이디 변경), Figma `LAITEU` 8583:28622(변경 화면)·5266:39358(계정 탭)·5918:8949(가입 공통 항목). xlsx 마이페이지_작가-R07·MY-P01·WORK-P02·REQ-011의 URL·"변경 불가" 서술은 이 문서 결정으로 대체된다(xlsx 갱신은 Plan task)

---

## 0. 설계 요약 (TL;DR)

| 항목 | 결정 |
|---|---|
| URL 체계 | 작가 페이지 `/{handle}`, 작품 `/{handle}/artwork/{6자}`, 공유 포트폴리오 `/{handle}/portfolio/{6자}`. 공유 URL에서 언어 세그먼트 제거. 옛 `/portfolio/{handle\|slug}`·`/artwork/{UUID}`는 새 주소로 리다이렉트 |
| handle 규칙 | `[A-Za-z0-9_]` 4~14자, 연속 밑줄 금지, 대소문자 무관 중복 금지, 예약어 금지. 표시는 저장한 대소문자 유지 |
| 저장 구조 | `member_handles` 원장(삭제 없음, `handle` UNIQUE) + `members.handle`(현재값 사본). 한 번 쓰인 handle은 **영구히** 그 회원 소유 — 다른 계정 재할당 금지, 본인 복귀 허용 |
| 변경 주기 | 사용자 변경 성공 시점부터 정확히 30일. 가입 handle·운영자 변경은 미산정 |
| 탈퇴 | `members.handle`은 비우고 원장 행은 유지 → 영구 재할당 금지, 접근 시 410 안내 |
| URL 해석 | 콘텐츠 API가 원장으로 직접 해석하고 응답에 현재 handle을 담는다. FE는 URL과 다르면 308(대소문자만 다르면 리다이렉트 안 함) |
| 6자 코드 | `[a-z0-9]` 6자, 작가 안에서만 유일, 행이 완전 삭제되면 재사용 허용. 작가 페이지 포트폴리오는 코드 없음 |
| 기존 데이터 | 규칙 위반 handle은 마이그레이션에서 `{이름부 ≤8자}_{숫자 5자리}`로 일괄 변경(운영자 변경 취급), 옛 handle은 원장에 남겨 리다이렉트 |

### 0.1 알려진 제약 (의도된 동작)

- **6자 코드는 대입 가능하다.** 작가 handle을 아는 사람이 코드를 대입하면 공유 포트폴리오와 "피드 비공개+포트폴리오 포함" 작품(업로드-R09상 URL 접근 허용)을 찾을 수 있다. 기존 22자 slug·UUIDv7이 주던 추측 불가능성을 URL 길이와 맞바꾼 결정이며 감수하기로 했다(2026-10-15).
- **탈퇴 후 재가입자는 예전 handle을 쓸 수 없다.** 영구 재할당 금지를 그대로 적용한 결과. 가입 시 `DUPLICATE_HANDLE`.
- **410은 "과거에 쓰인 handle"임을 드러낸다.** 탈퇴 안내 요구(결정 5)가 요구하는 노출.
- **이번 작업 이전에 탈퇴한 회원의 handle은 일부만 보호된다.** 탈퇴 시 handle을 null로 비워 왔기 때문에 원본이 없다. 고정형 포트폴리오 `snapshot_owner_profile` JSON에 남은 것만 복구한다(§4.2).
- **작품 코드 재사용 시 옛 링크가 같은 작가의 다른 작품을 열 수 있다.** 작가당 경우의 수 36^6(약 21억)이라 실질 확률은 무시 가능.
- **6자 코드 경로의 오류 구분이 비공개·휴지통 작품의 존재를 드러낸다.** `GET /api/members/{handle}/artworks/{code}`는 완전 비공개 작품에 403(`ARTWORK_PRIVATE`), 휴지통·운영 차단 작품에 410(`ARTWORK_DELETED`·`ARTWORK_BLOCKED`), 없는 코드에 404(`ARTWORK_NOT_FOUND`)로 답한다. 코드를 대입하는 사람은 "이 작가에게 이 코드의 작품이 있다(비공개거나 지웠다)"를 알 수 있다. 기존 `GET /api/artworks/{id}`와 같은 접근 판정·응답을 유지하려는 결정이며 감수한다(2026-10-15).

---

## 1. handle 원장·규칙·변경 흐름

### 1.1 스키마

```sql
CREATE TABLE member_handles (
    handle      VARCHAR(50) NOT NULL,        -- 테이블 기본 utf8mb4_unicode_ci → 대소문자 무관 UNIQUE
    member_id   VARCHAR(36) CHARACTER SET latin1 COLLATE latin1_bin NOT NULL,
    created_at  DATETIME(6) NOT NULL,
    PRIMARY KEY (handle),
    KEY idx_mh_member (member_id)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

ALTER TABLE members ADD COLUMN handle_changed_at DATETIME(6) NULL;
```

- 원장 행은 **삭제하지 않는다.** "이 handle은 이 회원 소유"라는 영구 기록이다.
- `VARCHAR(50)`인 이유: 일괄 변경 전 옛 handle(최대 30자, 하이픈 포함)도 리다이렉트 대상으로 넣어야 한다. 새 규칙 4~14자는 코드에서 검증한다.
- 불변식: **활성 회원의 `members.handle`은 반드시 원장에 그 회원 소유로 존재한다.** 모든 쓰기 경로(가입·변경·운영 SQL)가 이를 지킨다.
- `members.handle`의 기존 UNIQUE(`uk_members_handle`)는 유지한다(이중 방어).
- `handle_changed_at`은 **사용자 직접 변경 성공 시에만** 기록한다. 가입 시 handle과 운영자 변경은 기록하지 않는다 → 가입 후 첫 변경은 대기 없이 허용(R17 "변경 주기 제한").

### 1.2 handle 검증기 (`HandleValidator`)

입력은 `strip()`(전각 공백 포함)한 뒤 아래 순서로 검사하고 **첫 실패 하나만** 돌려준다. 가입·변경·사전 확인 API가 공유한다.

| 순서 | 조건 | 오류 코드 |
|---|---|---|
| 1 | 빈 값 | `HANDLE_REQUIRED` |
| 2 | 4자 미만 | `HANDLE_TOO_SHORT` |
| 3 | 15자 이상 | `HANDLE_TOO_LONG` |
| 4 | `[A-Za-z0-9_]` 외 문자(내부 공백 포함) | `HANDLE_INVALID_CHARACTERS` |
| 5 | `__` 포함 | `HANDLE_CONSECUTIVE_UNDERSCORES` |
| 6 | 예약어(대소문자 무관) | `HANDLE_RESERVED` |

단일 밑줄의 위치는 제한하지 않는다(첫·끝 글자 허용).

**예약어**는 서버 코드 상수로 관리한다. 형식상 handle이 될 수 있는 것(4자 이상, 영문·숫자·`_`)만 의미가 있다.

```
artwork bookmark community login portfolio pricing privacy profile search setting
terms trash upload billing _next admin help about signup settings company
```

`api`·`me`(3자 이하)와 `tag-search`·`refund-policy`·`third-party-privacy`(하이픈)는 형식 검사에서 이미 걸린다. FE에 최상위 라우트가 추가되면 이 목록도 갱신한다.

### 1.3 변경 API — `PATCH /api/members/me/handle`

요청 `{ "handle": "Pack_1n" }`, 응답 `{ handle, handleChangeAvailableAt }`.

1. 인증 → 실패 시 401 (FE 토스트 "로그인이 만료되었어요")
2. **멱등 재전송**: 요청값이 현재 handle과 정확히 같고 `handle_changed_at`이 5분 이내면 성공 응답을 그대로 반환(R17 "네트워크 재시도는 멱등 처리"). 5분으로 좁힌 이유 — 30일로 잡으면 제한 기간 중 같은 값 재입력이 "30일 제한" 대신 성공으로 응답된다.
3. 30일 제한: `handle_changed_at + 30일 > now` → `HANDLE_CHANGE_TOO_SOON`
4. 빈 값 또는 현재 값과 대소문자까지 동일 → `HANDLE_UNCHANGED`(FE 토스트 "새 사용자 아이디를 입력해주세요")
5. `HandleValidator` 2~6단계
6. 원장 조회
   - 다른 회원 소유(탈퇴 회원 포함) → `DUPLICATE_HANDLE`
   - 본인 소유 → 복귀(또는 대소문자만 변경). 원장 insert 없이 `members.handle`만 갱신
   - 없음 → 원장 insert. 동시 insert 경쟁은 PK 위반 → `DUPLICATE_HANDLE`
7. `members.handle`, `handle_changed_at = now` 저장, `MemberHandleChangedEvent(memberId)` 발행

- 변경 API에서 `HANDLE_REQUIRED`와 `HANDLE_UNCHANGED`는 같은 토스트("새 사용자 아이디를 입력해주세요")로 매핑한다. 가입에서는 `HANDLE_REQUIRED`만 나온다.
- 대소문자만 바꾼 변경도 원장상 같은 행이라 "본인 소유" 경로를 타며 변경 1회로 계산한다.
- 같은 회원의 동시 두 요청은 `Member @Version`이 한쪽을 실패시킨다(원장 insert도 같은 트랜잭션이라 롤백). `OptimisticLockingFailureException`을 잡아 `HANDLE_CHANGE_CONFLICT`로 변환한다(500 금지, `PortfolioServiceImpl` 1120행 패턴). FE는 서버 오류 토스트로 처리.
- 실패는 `handle_changed_at`을 건드리지 않는다(R17 "변경 실패는 제한 기간을 시작·연장하지 않는다").

**다음 변경 가능 시각 전달**: `ApiResponse`는 오류 시 `data`가 null 고정이라 오류 응답에 시각을 실을 수 없다. 공통 봉투를 바꾸지 않고 `GET /api/members/me` 응답에 `handleChangeAvailableAt`(지금 변경 가능하면 null)을 추가한다. FE는 `HANDLE_CHANGE_TOO_SOON`을 받으면 이 값으로 "YYYY.MM.DD부터" 토스트를 만든다. 판정은 정확한 시각(Instant) 기준이며, 날짜 표시는 회원 시간대로 FE가 한다.

### 1.4 사전 확인 API — `GET /api/members/handles/availability?handle=`

- 인증 선택(`getOptionalMemberId()` 패턴). 가입 화면은 토큰 없이, 설정 화면은 토큰과 함께 호출한다.
- `HandleValidator` + 원장 조회. 토큰이 있으면 본인 소유 handle을 사용 가능으로 판정한다.
- 응답 `{ available, errorCode }`. 오류 코드는 변경·가입 API와 같다.

### 1.5 탈퇴

`Member.deactivate()`는 지금처럼 `members.handle`을 null로 비운다. 원장 행은 남으므로 그 handle은 영구히 재할당되지 않는다.

### 1.6 운영자 강제 변경

관리자 API 없이 SQL로 수행한다(`docs/operations/handle-change.md` 런북). 원장 insert와 `members.handle` 갱신을 한 트랜잭션에서 하고 `handle_changed_at`은 건드리지 않는다. 변경 후 해당 작가 재색인 호출을 포함한다.

---

## 2. 공개 URL 해석·리다이렉트·탈퇴 안내

### 2.1 원칙

handle을 받는 모든 API는 공통 해석기 `MemberService.resolveHandle(handle)`을 거친다.

| 원장 상태 | 결과 |
|---|---|
| 활성 회원 소유, 현재 handle | `{memberId, currentHandle, moved=false}` |
| 활성 회원 소유, 옛 handle | `{memberId, currentHandle, moved=true}` |
| 탈퇴 회원 소유 | 410 `MEMBER_WITHDRAWN` |
| 원장에 없음 | 404 `MEMBER_NOT_FOUND` |

조회는 원장 PK로 하므로 대소문자 무관이다. 응답에는 항상 **현재 handle**을 담고, FE는 URL의 handle과 대소문자 무시 비교로 다르면 308 리다이렉트한다. 대소문자만 다르면 리다이렉트하지 않는다.

### 2.2 API

| 화면 | API | 비고 |
|---|---|---|
| `/{handle}` | `GET /api/members/{handle}` | 기존. 옛 handle 수용 |
| `/{handle}` 작품 그리드 | `GET /api/portfolios/shared/{identifier}`, `.../artworks` | 기존. identifier가 handle이면 해석기 경유 |
| `/{handle}/artwork/{code}` | `GET /api/members/{handle}/artworks/{code}` | 신규. 접근 판정은 기존 `ArtworkAccess` 그대로 |
| `/{handle}/portfolio/{code}` | `GET /api/members/{handle}/portfolios/{code}`, `.../artworks` | 신규 |
| 고정형 스냅샷 상세 | `GET /api/members/{handle}/portfolios/{code}/snapshots/{snapshotPublicId}` | 신규 |
| 옛 `/artwork/{UUID}` | `GET /api/artworks/{id}` | 응답에 `authorHandle`·`publicCode` 포함 → FE가 새 주소로 308 |
| 옛 `/portfolio/{handle\|slug}` | `GET /api/portfolios/shared/{identifier}` | 응답에 `ownerHandle`·`publicCode`(작가 페이지면 null) 포함 → FE 308 |

- 경로 변수 handle 정규식은 옛 handle까지 받도록 `^[A-Za-z0-9_-]{1,50}$`로 넓힌다. 새 규칙은 생성·변경 시에만 적용한다.
- 옛 handle로 해석된 요청(`moved=true`)은 `ArtistProfileViewedEvent`를 발행하지 않는다 — 리다이렉트 전후 두 번 집계되는 것을 막는다.
- `SecurityConfig`: 신규 공개 경로를 permitAll에 추가하되 `/api/members/me/**`를 그보다 먼저 `authenticated()`로 선언한다(기존 `/api/members/me` 리터럴 우선 패턴과 동일). `/me/artworks/{id}` 같은 미래 경로가 공개 패턴에 걸려 인증 없이 열리는 것을 막는다.

---

## 3. 작품·포트폴리오 6자 공개 코드

### 3.1 스키마

```sql
ALTER TABLE artworks   ADD COLUMN public_code CHAR(6) CHARACTER SET latin1 COLLATE latin1_bin NULL;
ALTER TABLE portfolios ADD COLUMN public_code CHAR(6) CHARACTER SET latin1 COLLATE latin1_bin NULL;
-- 백필 후
ALTER TABLE artworks   MODIFY public_code ... NOT NULL, ADD UNIQUE KEY uk_artworks_author_code (author_id, public_code);
ALTER TABLE portfolios ADD UNIQUE KEY uk_pf_owner_code (owner_member_id, public_code);  -- ARTIST_PAGE는 NULL
```

- `[a-z0-9]` 6자. 작가 안에서만 유일하다. 작품과 포트폴리오는 경로(`/artwork/`·`/portfolio/`)로 갈리므로 서로 같은 코드를 써도 된다.
- 코드는 엔티티 컬럼에만 둔다(원장 없음). 휴지통 작품은 행이 남아 코드도 막혀 있고, 영구 삭제(`ArtworkPurger`)·포트폴리오 삭제로 행이 사라지면 재사용될 수 있다(결정 2026-10-15).
- 작가 페이지 포트폴리오(`ARTIST_PAGE`)는 코드가 없다(주소가 `/{handle}`).
- 복제(R41)는 새 코드를 받는다.

### 3.2 발급

`PublicCodeGenerator`(`com.atcrew.common.id`, SecureRandom)가 `(소유자, code)` 사전 조회로 최대 3회 추첨한 뒤 저장한다. insert 후 UNIQUE 위반을 잡아 재시도하지 않는 이유 — 리포지토리 예외가 나면 트랜잭션이 rollback-only가 되어 같은 트랜잭션에서 재시도할 수 없다. 사전 조회 통과 후 경쟁으로 UNIQUE 위반이 나면 `PUBLIC_CODE_GENERATION_FAILED`(500)로 끝낸다. 작가당 경우의 수 36^6이라 실질 발생은 없다.

### 3.3 기존 slug

`portfolios.share_slug`는 옛 링크 해석용으로 남긴다. 신규 발급은 **가입 handle과 같은 전환기가 끝날 때(PA-14) 중단한다.**

- **왜 바로 중단하지 않는가**: 운영 FE는 새 주소 체계를 배포하기 전까지 `shareSlug ?? portfolio.id`로 `/portfolio/{식별자}`
  링크를 만든다. BE가 먼저 나가면서 slug를 발급하지 않으면 새 공유 포트폴리오의 링크가 UUID가 되고,
  `GET /api/portfolios/shared/{identifier}`는 UUID를 해석하지 않으므로 생성 직후 화면·카드·복사한 링크가 모두 404가 된다
  (PR #252 리뷰에서 발견).
- `ShareSlugGenerator`는 슬러그 중복만 검사한다. 예전의 handle 충돌 검사는 뺐다 — 슬러그는 22자 base64url이고 새 handle은
  4~14자라 겹칠 수 없으며, 해석 순서도 슬러그가 먼저다.
- 전환기가 끝나면 `ShareSlugGenerator`를 지우고 `Portfolio.createShared`의 slug 인자를 없앤다. 이미 발급된 slug는 옛 링크
  해석용으로 계속 남는다.

### 3.4 응답 필드

작품 링크를 만드는 모든 응답에 `publicCode`를 추가한다 — `ArtworkInfo`, `ArtworkSummaryInfo`(홈 피드·핫한 작품·작가 찾기·북마크·포트폴리오 작품 목록), 검색 `SearchResultItem`. 검색은 ES 문서에 `publicCode` 필드를 추가한다. `authorHandle`은 이미 있다. 포트폴리오 응답에는 `ownerHandle`·`publicCode`를 추가한다.

내부 API(수정·삭제·열람 기록 `POST /views` 등)는 계속 UUID `artworkId`로 동작한다. 코드는 공개 URL 해석에만 쓴다.

---

## 4. 가입 변경·기존 데이터 이전·검색 색인

### 4.1 가입

- 이메일·Google 가입 API(`/api/auth/**/register`)가 `handle`을 받는다. 기업 계정도 같다.
- `HandleValidator`로 검증한다. 30일 제한·"현재 값과 같음" 검사는 없다.
- 회원 insert와 원장 insert를 같은 트랜잭션에서 한다. `DuplicateKeyException`은 지금처럼 이메일 중복을 먼저 재조회하고, 아니면 `DUPLICATE_HANDLE`.
- `generateUniqueHandle`의 기존 형식(`{이름}_{hex8}`)과 3회 재시도 루프, `HANDLE_GENERATION_FAILED`는 제거한다.
- **FE 전환기**: FE 배포 전에 BE가 나가면 handle 없는 가입 요청이 모두 400이 된다. 그래서 전환기 동안 `handle`을 선택 입력으로 두고, 없으면 서버가 새 형식(`{이름부 ≤8자}_{숫자 5자리}`)으로 생성한다. FE 배포 후 필수로 전환한다(후속 이슈로 추적).
- `DevMemberController` 경로도 원장 insert를 거친다.

### 4.2 기존 데이터 이전 (SQL 마이그레이션)

1. `member_handles` 생성, 활성 회원의 현재 handle 전부를 원장에 백필(규칙 위반 handle 포함 — 리다이렉트 대상).
2. 탈퇴 회원 handle 복구: 고정형 포트폴리오 `snapshot_owner_profile` JSON의 handle 중 소유자가 탈퇴 회원인 것을 `INSERT IGNORE`.
3. 규칙 위반 handle(형식·연속 밑줄·예약어) 일괄 변경
   - 새 값: 기존 handle의 마지막 `_` 앞부분에서 영문·숫자만 남겨 최대 8자 + `_` + 숫자 5자리. 남은 글자가 4자 미만이면 `user`. 예: `user_48213`.
   - 5자리인 이유: 한글 이름 회원은 모두 `user`로 시작해 4자리면 1만 개 공간에서 겹친다.
   - 새 handle도 원장에 insert. `handle_changed_at`은 null(운영자 변경 취급 → 사용자는 즉시 변경 가능).
   - 기존 handle은 모두 자동 생성값이라 사용자가 고른 이름을 잃지 않는다.
4. 작품·포트폴리오 `public_code` 백필: 36자 알파벳에서 6자를 뽑아 채운 뒤, `ROW_NUMBER()`로 작가 내 충돌 행만 골라 다시 뽑기를 2회 수행하고 UNIQUE를 건다. 그래도 남으면 UNIQUE 생성이 실패해 즉시 드러난다. (Flyway Java 마이그레이션은 레포에 선례가 없고 CI `guard-applied-migrations`와의 상호작용이 미검증이라 쓰지 않는다.)
5. 마이그레이션 직후 "모든 활성 handle이 `HandleValidator`를 통과한다"를 테스트로 검증한다 — SQL과 Java의 예약어 목록이 어긋나면 여기서 잡힌다.

### 4.3 검색 색인

- handle 변경 시 `MemberHandleChangedEvent(memberId)` → search 모듈이 그 작가 작품만 재색인(`ArtworkChangedEvent` 리스너와 같은 `@ApplicationModuleListener` 패턴).
- 마이그레이션의 일괄 변경·`publicCode` 백필은 이벤트를 내지 않으므로 배포 후 `POST /internal/search/reindex` 전체 재색인이 **필수**다(배포 체크리스트).
- 고정형 포트폴리오 `snapshot_owner_profile.handle`은 읽는 코드가 없어 갱신하지 않는다.

---

## 5. 테스트 전략

| 층 | 대상 | 핵심 케이스 |
|---|---|---|
| 단위 | `HandleValidator` | 3·4·14·15자 경계, 연속 밑줄, 첫·끝 밑줄 허용, 앞뒤 공백·전각 공백 제거, 내부 공백, 예약어 대소문자 변형, 다중 위반 시 우선순위 1개만 |
| 통합 | 변경 흐름 | 30일 경계(정확한 시각 ±1초 — `handle_changed_at` 픽스처로 조작, `Clock` 리팩터링은 하지 않음), 복귀, 대소문자만 변경(1회 산정), 타 회원 소유, 탈퇴 회원 소유, 5분 멱등, 가입 handle 미산정 |
| 통합 | 동시성 | 서로 다른 두 회원이 같은 handle 동시 요청 → 1명만 성공. 같은 회원 동시 2회 → 1성공 + `HANDLE_CHANGE_CONFLICT`(500 아님) |
| 통합 | 해석기 | 현재·옛·탈퇴·없음 → 200·200(현재 handle)·410·404, 옛 handle 조회 시 조회수 이벤트 미발행, portfolio API 3종도 해석기 경유 |
| 통합 | 6자 코드 | 작가 내 유일(타 작가와 동일 코드 허용), 충돌 재발급(난수 고정), 휴지통 작품 코드 유지, 복제본 새 코드, 작가 페이지 null |
| 통합 | 가입 | handle 입력 경로, 전환기 미입력 자동 생성, 이메일 중복과 handle 중복 구분 |
| 통합 | 마이그레이션 | 규칙 위반 handle·탈퇴 회원·스냅샷 JSON 픽스처 → 적용 후 활성 handle 전부 검증기 통과, 옛 handle 원장 잔존, 코드 작가별 유일 |
| 통합 | search | `MemberHandleChangedEvent` → 해당 작가 문서만 재색인 |
| 보안 | `SecurityConfig` | `/api/members/me/**`가 공개 패턴보다 먼저 인증으로 막힘 |
| 문서 | REST Docs·Swagger | 신규·변경 API 전부, 비2xx 설명에 오류 코드(`docs/conventions/swagger.md`) |
| 배포 후 | 스모크 | 운영에서 handle 변경 → 옛 주소 해석 → 검색 반영 |

---

## 6. 결정 기록 (2026-10-14~15)

| # | 결정 | 근거·비고 |
|---|---|---|
| 1 | URL은 `/{handle}`, `/{handle}/artwork/{6자}`, `/{handle}/portfolio/{6자}`. 언어 세그먼트 제거, 옛 주소 리다이렉트 | 기획 스레드 합의(김다연·김인서). `/p/` 약어 대신 단어 사용 |
| 2 | 공유자 추적 토큰 `?s=`는 이번 범위 제외 | |
| 3 | 설정-R17이 정본, xlsx R07 "변경 불가"는 갱신 | |
| 4 | 규칙 위반 기존 handle은 운영 일괄 변경(이력 미산정) | |
| 5 | 탈퇴 handle도 영구 재할당 금지, 접근 시 안내(410) | 한때 "유예 후 해제"를 검토했으나 포트폴리오 서비스 특성상 사칭 위험이 커 영구 금지로 복귀 |
| 6 | 본인은 옛 handle로 복귀 가능, 리다이렉트 영구 | |
| 7 | 코드는 작가 안에서만 유일, 소급 발급, 완전 삭제 후 재사용 허용 | 처음엔 재사용 금지였으나 같은 작가 범위·확률상 무해해 완화 |
| 8 | 재전송 멱등: 같은 값 + 5분 이내 → 성공 | |
| 9 | 30일 제한은 정확한 시각 | |
| 10 | 가입 화면 문구·우선순위·사전 확인 단계는 기획 요청(FE 몫). BE는 사전 확인 API를 만든다 | |
| 11 | 기업 계정도 동일 적용 | 기업도 `members` 행이며 `Company.memberId`로 연결 |
| 12 | 예약어는 서버 상수 | §1.2 |
| 13 | 운영자 변경은 SQL 런북 | §1.6 |
| 14 | 기업 페이지 주소는 `/company/{companyId}` 유지, `company`는 예약어 추가 | 2026-10-15. 기업 handle은 가입·변경만 하고 공개 주소에는 쓰지 않는다 |
| — | 저장 구조는 원장 테이블(A안) | 재할당 금지·동시성을 DB 제약 하나로 보장. 이력 테이블+앱 검사(B안)는 두 테이블 간 유일성을 DB가 보장 못 함 |
| — | 해석은 콘텐츠 API 직접 해석(B안) | 페이지당 왕복 1회, 옛 UUID·slug 리다이렉트도 같은 원리로 해결 |
| — | 6자 코드 대입 위험 감수 | §0.1 |

---

## 7. 구현 차이 (2026-10-15, 코드 확인 기준)

설계 본문과 다르게 구현됐거나 설계가 정하지 않았던 지점이다. 본문은 결정 당시 기록으로 두고 여기에 차이만 적는다.

| # | 지점 | 구현 | 이유 |
|---|---|---|---|
| a | 변경 시 flush 순서 | `changeHandle`이 `members`를 **원장(`member_handles`)보다 먼저** `saveAndFlush`한다. `OptimisticLockingFailureException`은 `HANDLE_CHANGE_CONFLICT`, 이어지는 제약 위반은 `DUPLICATE_HANDLE` | 같은 회원의 동시 요청은 `@Version` 검사에서, 서로 다른 회원의 동일 handle 요청은 `uk_members_handle`에서 먼저 갈려야 두 오류를 구분할 수 있다. 원장을 먼저 넣으면 같은 회원의 동시 요청이 원장 PK 위반(`DUPLICATE_HANDLE`)으로 보여 "다른 사람이 쓰는 아이디"와 "동시 요청"이 섞인다. 커밋 시점까지 flush를 미루면 서비스 밖에서 낙관적 락 예외가 터져 500이 된다 |
| b | 자동 생성 handle의 충돌 처리 | 전환기 자동 생성(`generateAvailableHandle`)은 **원장을 사전 조회**해 비어 있는 후보를 고른 뒤 저장한다(최대 3회 추첨). 저장 중 제약 위반은 재시도하지 않고 `HANDLE_GENERATION_FAILED`(500) | insert 후 UNIQUE 위반을 잡아 재시도하면 Spring 트랜잭션이 rollback-only가 돼 같은 트랜잭션에서 다시 저장할 수 없다(§3.2의 6자 코드와 같은 이유) |
| c | `handleChangeAvailableAt` 직렬화 | `GET /api/members/me`(`MemberInfo`)는 `NON_EMPTY`라 지금 변경 가능하면 **필드가 응답에서 생략**된다(`null`로 내려오지 않음). 반면 사전 확인 API의 `errorCode`는 사용 가능일 때 `null`로 내려온다 | FE는 "필드 없음 = 지금 변경 가능"으로 처리한다 |
| d | 51자 이상 입력 | 변경·가입 요청 DTO의 `@Size(max = 50)`가 먼저 막아 `COMMON_INVALID_INPUT`(400)이다. 15~50자는 서비스의 `HANDLE_TOO_LONG` | `member_handles.handle`이 `VARCHAR(50)`이라 DB 길이를 넘는 값이 서비스까지 오지 않게 한다. 사전 확인 API도 `@Size(max = 50)` |
| e | `PortfolioArtworkCardInfo.publicCode` | 설계 §3.4에 없던 필드. 포트폴리오 작품 카드에도 원본 작품 `publicCode`를 싣는다. **최신 반영형·작가 페이지 카드만** 값이 있고 **고정형(SNAPSHOT) 카드는 null**(`snapshotId`로 스냅샷 상세를 연다) | 카드에서 작품 주소 `/{ownerHandle}/artwork/{publicCode}`를 조립해야 한다. 고정형은 원본 작품 URL을 제3자에게 조립해 주지 않는 기존 정책(마이페이지_작가-R38·R39)을 유지 |
| f | 공유 식별자 해석의 오류 | `GET /api/portfolios/shared/{identifier}` 3종에서 슬러그도 handle도 해석되지 않으면(원장에 없는 handle 포함) `PORTFOLIO_NOT_FOUND`(404)이고, 탈퇴 회원 소유 handle이면 `MEMBER_WITHDRAWN`(410)을 그대로 전파한다. 반면 새 주소 API(`/api/members/{handle}/...`)는 원장에 없는 handle을 `MEMBER_NOT_FOUND`(404)로 답한다 | 옛 API는 slug와 handle이 한 식별자를 공유해 "없음"을 구분할 수 없고(기존 응답 유지), 탈퇴 안내(결정 5)는 두 경로 모두 필요하다 |
| g | 검색 리스너 범위 | `MemberHandleChangedEvent` 수신 시 그 작가의 **작품 전부를 단건 `reindexOne`**으로 처리한다(작품 단위 예외 격리, 색인 대상이 아닌 작품은 색인에서 제거). 문서 전체 재색인과 별개 | §4.3의 "그 작가 작품만 재색인"의 구체형. 한 작품의 실패가 나머지를 막지 않는다 |
| h | `DevMemberController` 검증 | `RegisterRequest.handle`은 길이 상한(`@Size(max = 50)`)만 두고, 컨트롤러가 `HandleValidator.validate`를 호출해 `HANDLE_*` 코드로 답한다. `MemberService.register(email, handle, name)`(개발용)은 형식을 검증하지 않는다 | 가입·변경 API와 같은 오류 코드를 쓰기 위해 Bean Validation `@Pattern` 대신 공유 검증기를 쓴다 |
| i | `HANDLE_GENERATION_FAILED` 잔존 | §4.1은 이 코드의 제거를 적었지만, 전환기 자동 생성 경로가 남아 있어 **코드도 유지**한다. 제거는 PA-14(가입 handle 필수화)와 함께 | 새 형식(`{이름부 ≤8자}_{숫자 5자리}`) 추첨이 3회 모두 원장과 충돌하는 경우의 500 |
| j | `MemberService.findByHandle` 제거 | 호출처가 테스트뿐이어서 공개 인터페이스에서 제거했다. 테스트는 `resolveHandle` + `findById`로 대체. `MemberRepository.findByHandle`은 남아 있으나 현재 main 호출처가 없다 | 모듈 공개 API를 작게 유지. 원장 해석은 `resolveHandle`로 일원화 |
| k | 마이그레이션 번호 | 원장 `V52__member_handle_ledger.sql`, 공개 코드 `V53__artwork_portfolio_public_code.sql`(작업용 테이블 `v52_handle_renames`). 마이그레이션 테스트는 1단계 target을 `"51?"`(없어도 실패하지 않음)·`"52"`로 두고 2단계에서 자기 버전 하나만 적용한다 | V51은 병렬 브랜치 `feat/og-image`(`V51__og_image_keys.sql`, 미병합)가 쓴다. `outOfOrder`가 꺼져 있어 운영에는 V51이 V52·V53보다 먼저 적용돼야 한다(런북 `handle-change.md` §5) |
| l | 변경 시각 정밀도 | `changeHandle`의 기준 시각을 `Instant.now().truncatedTo(MICROS)`로 잘라 쓴다(멱등 판정·30일 판정·`handleChangeAvailableAt` 계산 모두 이 값) | `handle_changed_at`이 `DATETIME(6)`이라 나노초가 남으면 첫 응답의 `handleChangeAvailableAt`과 DB에서 다시 읽은 값이 달라진다(재전송 응답이 첫 응답과 불일치). `HotArtworkTests`와 같은 처리 |
| m | 같은 값 동시 요청(더블클릭) | 트랜잭션 본체를 `MemberHandleChanger.change`로 옮기고 `MemberServiceImpl.changeHandle`은 트랜잭션 없는 래퍼로 둔다. `HANDLE_CHANGE_CONFLICT`가 나면 롤백 뒤 새 읽기 트랜잭션(`findConcurrentReplay`)에서 멱등 재전송과 같은 기준(현재 handle = 요청값 strip, 대소문자 일치, 마지막 변경 5분 이내)으로 다시 판정해 맞으면 성공 응답. 다른 값끼리의 동시 요청은 그대로 `HANDLE_CHANGE_CONFLICT` | 진 쪽 트랜잭션은 `@Version` 충돌로 rollback-only라 같은 트랜잭션에서 성공을 반환하면 커밋 시 `UnexpectedRollbackException`(500)이 된다. §1.3 2단계 멱등을 동시 도착에도 적용 |
| n | 가입 빈 문자열 | 전환기 동안 가입 `handle`이 null뿐 아니라 빈 문자열·공백뿐이어도 자동 생성 경로로 간다(`HANDLE_REQUIRED` 아님) | FE가 빈 입력란을 `""`로 보낼 수 있다. PA-14(필수화)에서 제거 |
| o | 중복 키 예외 범위 | 가입·변경·작품/공유 포트폴리오 저장의 `saveAndFlush`에서 `DataIntegrityViolationException`을 잡되, `DuplicateKeyViolations.isDuplicateKey`(원인 체인의 `SQLException` 에러코드 1062)일 때만 도메인 오류로 바꾸고 나머지 무결성 위반은 그대로 전파한다. §4.1의 `DuplicateKeyException`은 쓰지 않는다 | 실측상 JPA 경로의 MariaDB 중복 키는 `DuplicateKeyException`이 아니라 `DataIntegrityViolationException`(원인 `SQLIntegrityConstraintViolationException`, errorCode 1062)으로 번역된다. 전체를 잡으면 NOT NULL·FK 위반까지 `DUPLICATE_HANDLE` 등으로 오인한다 |
| p | 사전 확인의 현재 handle | 로그인 본인의 현재 handle과 대소문자까지 같으면 `available=false`, `errorCode=HANDLE_UNCHANGED`. 대소문자만 다른 현재 handle·옛 handle은 사용 가능 | §1.4 "본인 소유는 사용 가능"을 그대로 두면 사전 확인은 가능, 변경 API는 `HANDLE_UNCHANGED`로 판정이 어긋난다 |
