# MVP 시나리오 직접 검증 가이드

> 작성일: 2026-08-12
> 목적: 이번 주 출시 마일스톤(회원가입/로그인 → 포트폴리오 → 커뮤니티 → 검색 → 요금제 → 마이페이지)을
> 처음 가입부터 순서대로 직접 API로 따라가며 확인하기 위한 단계별 가이드.
> 아래 request/response는 로컬 서버에 실제로 호출해서 받은 응답을 그대로 옮긴 것이다(값만 매번 새로
> 생성되는 ID·토큰이라 실제로 해보면 다르게 나온다 — **구조와 상태코드**를 기준으로 비교할 것).

---

## 0. 준비

1. 로컬 서버 기동 확인: `http://localhost:8080/actuator/health` → `{"status":"UP"}`
2. Swagger UI: `http://localhost:8080/swagger-ui/index.html`
3. 로컬 환경 특성(계속 참고할 것):
   - R2/Worker가 dummy라 업로드한 작품은 `READY`로 자동 전이하지 않고 `PROCESSING`에 머문다.
     `PATCH .../visibility`처럼 READY를 요구하는 API를 테스트하려면 DB에서 강제로 상태를 바꿔야 한다(§3.4 참고).
   - 스타터 → 프로 전환은 결제 연동이 없어 API로 안 된다. DB에 `subscriptions` 행을 직접 넣어야 한다(§6.1 참고).
   - 로컬 MariaDB는 기본 포트(3306)가 다른 프로젝트와 충돌해 3307을 쓴다 — DB에 직접 접속하려면:
     ```
     docker exec -it turban-mariadb-local mariadb -uroot -patcrew_local_dev atcrew
     ```

---

## 1. 회원가입 · 로그인

### 1.1 이메일 회원가입

`POST /api/auth/email/register`

**요청**
```json
{
  "email": "scenario-859f4580@example.com",
  "password": "Secure1!",
  "passwordConfirm": "Secure1!",
  "name": "시나리오작가",
  "agreePrivacy": true,
  "agreeService": true,
  "agreeThirdParty": true,
  "agreeMarketing": false,
  "timezone": "Asia/Seoul",
  "countryCode": "KR"
}
```

**응답 `201`**
```json
{
  "code": "SUCCESS",
  "data": {
    "accessToken": "(JWT 문자열 — 로컬 개발용 시크릿으로 서명된 값, 매 호출마다 다름)",
    "refreshToken": "(JWT 문자열 — 로컬 개발용 시크릿으로 서명된 값, 매 호출마다 다름)",
    "member": { "id": "019ff3e8-d3e1-...", "handle": "user_e3c991fc", "name": "시나리오작가", "...": "..." },
    "isNewUser": true
  }
}
```

- `accessToken`을 복사해 Swagger 우측 상단 자물쇠(Authorize)에 등록한다(값만, `Bearer ` 접두어 없이).
- `agreePrivacy`/`agreeService`/`agreeThirdParty`는 셋 다 `true`가 아니면 400(`TERMS_NOT_AGREED`)이 난다.
- 같은 이메일로 다시 가입하면 409(`DUPLICATE_EMAIL`).

### 1.2 재로그인

`POST /api/auth/email/login`

**요청**: `{"email": "scenario-859f4580@example.com", "password": "Secure1!"}`
**응답 `200`**: 가입 때와 같은 형태, `isNewUser: false`. **이 시점에 refresh token이 새로 발급되고
이전 refresh token은 무효화된다** — 회원당 refresh token은 항상 1개만 살아있다(멀티 디바이스 동시 로그인
미지원). §7에서 직접 확인한다.

- 비밀번호를 5회 연속 틀리면 429(`TOO_MANY_ATTEMPTS`, 10분 차단)로 바뀐다.
- 오답/미가입/탈퇴 계정은 전부 401(`AUTHENTICATION_FAILED`)로 통합 응답한다(계정 존재 여부 노출 방지).

---

## 2. 마이페이지 — 프로필

### 2.1 공개 프로필 조회 (비로그인도 가능)

`GET /api/members/{handle}` — 위에서 받은 handle(`user_e3c991fc`)로 조회

**응답 `200`**
```json
{
  "code": "SUCCESS",
  "data": {
    "id": "019ff3e8-d3e1-...", "handle": "user_e3c991fc", "name": "시나리오작가",
    "creatorRole": null, "employmentStatus": "PREPARING",
    "activityFields": [], "experienceLevel": null, "activeRegions": [], "teamExperiences": [],
    "totalSlotCount": 5, "availableSlotCount": 5,
    "contact": null, "sns": null, "tools": null, "careers": [],
    "createdAt": "...", "updatedAt": "..."
  }
}
```
선택 프로필 항목(창작자 유형·경력 등)을 아무것도 안 채운 상태라 대부분 `null`/`[]`다 — 실제로 채우면
해당 필드가 값으로 채워져 내려온다.

### 2.2 이름 수정

`PATCH /api/members/me/name` (인증 필요) — `{"name": "수정된작가명"}` → **`204`**(본문 없음)

---

## 3. 포트폴리오 — 작품 업로드

### 3.1 이미지 업로드 URL 발급

`POST /api/artwork/images/presign` (인증 필요)

**요청**: `{"count": 1, "contentTypes": ["image/jpeg"]}`
**응답 `200`**
```json
{"code": "SUCCESS", "data": [{"key": "raw/019ff3e8-d4f7-....jpg", "uploadUrl": "https://...X-Amz-Expires=600..."}]}
```
로컬은 R2가 dummy라 이 `uploadUrl`로 실제 PUT 업로드를 할 필요 없이, `key` 값만 다음 단계에 그대로
쓰면 된다.

### 3.2 작품 업로드

`POST /api/artworks` (인증 필요)

**요청**
```json
{
  "imageKeys": ["raw/019ff3e8-d4f7-....jpg"],
  "representativeImageIndex": 0,
  "imageLayoutType": "VERTICAL_SCROLL",
  "title": "겨울 숲의 아이",
  "description": "겨울 숲을 배경으로 그린 일러스트",
  "artworkField": "ILLUSTRATION",
  "creativeType": "ORIGINAL",
  "roles": ["TOTAL_ARTWORK", "COLORING"],
  "genres": ["판타지"],
  "tags": ["겨울", "판타지"],
  "ageRating": "ALL",
  "visibility": "PUBLIC"
}
```
**응답 `201`** — `status: "PROCESSING"`으로 생성된다. 작품 `id`(`019ff3e8-d51b-...`)를 기록해둔다.

### 3.3 작품 상세 조회 — 접근 권한 확인 (중요)

`GET /api/artworks/{artworkId}`

| 호출자 | 결과 |
|---|---|
| 비로그인 (또는 남) | **`404` `ARTWORK_NOT_FOUND`** — `PROCESSING` 상태인 작품은 본인 외에는 존재 자체가 노출되지 않는다 |
| 본인(Authorization 헤더 포함) | `200`, 정상 조회 |

즉 방금 올린 작품을 Swagger에서 인증 없이 조회하면 **404가 정상**이다. 헷갈리지 않으려면 항상
Authorize를 걸어둔 상태로 테스트하는 게 편하다.

### 3.4 처리 상태 폴링

`GET /api/artworks/{artworkId}/status` (본인만) → `{"code":"SUCCESS","data":"PROCESSING"}`

실제 서비스에서는 Cloudflare Worker가 이미지 변환 후 콜백을 보내 `READY`로 바뀐다. 로컬에서
`READY`로 강제 전이시키려면(휴지통 이후 단계, 공개범위 변경 API 등을 테스트하려면 필요):
```sql
UPDATE artworks SET status='READY' WHERE id='{artworkId}';
```

### 3.5 내 작품 목록

`GET /api/members/me/artworks` (인증 필요) → `CursorPage<ArtworkSummaryInfo>`, 방금 올린 작품 1건 확인

---

## 4. 포트폴리오 — 작가 페이지 · 공유

### 4.1 작가 페이지 lazy 생성

`GET /api/portfolios/me` (인증 필요) — **최초 호출 시점에 작가 페이지가 자동 생성**된다.

**응답 `200`**
```json
{
  "code": "SUCCESS",
  "data": {
    "items": [{
      "id": "019ff3e8-d591-...", "kind": "ARTIST_PAGE", "reflectionType": "LIVE",
      "title": null, "shareSlug": null, "itemCount": 0, "coverThumbnails": [],
      "createdAt": "...", "updatedAt": "..."
    }],
    "nextCursor": null, "hasNext": false
  }
}
```
`title`/`shareSlug`가 `null`인 게 정상이다(작가 페이지는 화면에서 사용자 이름을 대신 쓰고, 공유 링크는
handle로 접근한다). 이 응답의 `id`를 "작가 페이지 ID"로 기록해둔다.

### 4.2 작가 페이지에 작품 추가

`POST /api/portfolios/{artistPageId}/artworks` — `{"artworkIds": ["019ff3e8-d51b-..."]}` → `204`

`GET /api/portfolios/{artistPageId}`로 재조회하면 `itemCount: 1`, `artworks` 배열에 방금 추가한 작품이
보인다.

### 4.3 공유 포트폴리오 — 스타터는 403부터 확인

`POST /api/portfolios` — `{"title": "테스트", "reflectionType": "LIVE", "artworkIds": []}`

**스타터(기본) 상태 응답 `403`**
```json
{"code": "PRO_PLAN_REQUIRED", "message": "프로 플랜에서만 사용할 수 있는 기능입니다"}
```

### 4.4 프로 전환 (로컬 전용 — DB 직접 조작)

Stripe 연동이 아직 없어 API로 전환할 방법이 없다. DB에 직접 구독 행을 넣는다:
```sql
INSERT INTO subscriptions (id, member_id, plan, status, cancel_at_period_end, version, created_at, updated_at)
VALUES (UUID(), '{내 memberId}', 'PRO_MONTHLY', 'ACTIVE', 0, 0, NOW(6), NOW(6));
```
`GET /api/billing/me`로 `plan: "PRO_MONTHLY", status: "ACTIVE"`가 뜨는지 확인.

### 4.5 공유 포트폴리오 생성 (프로 전환 후)

같은 요청을 다시 보내면 `201`:
```json
{
  "code": "SUCCESS",
  "data": {
    "id": "019ff3e8-d885-...", "kind": "SHARED", "reflectionType": "LIVE",
    "title": "겨울 컨셉 모음", "shareSlug": "wGs7IVd0GCPlCWR3gofaIg", "itemCount": 1,
    "artworks": [{"artworkId": "019ff3e8-d51b-...", "title": "겨울 숲의 아이", "...": "..."}],
    "createdAt": "...", "updatedAt": "..."
  }
}
```

### 4.6 공유 링크 비로그인 열람

Authorize를 끈 상태(또는 새 시크릿 창)에서:
- `GET /api/portfolios/shared/{shareSlug}` → `200`, `ownerName`만 노출(개인정보 최소화)
- `GET /api/portfolios/shared/{shareSlug}/artworks` → `200`, 담긴 작품 카드 목록

---

## 5. 북마크 · 휴지통

### 5.1 북마크는 다른 계정으로 테스트해야 자연스럽다

내 작품을 내가 북마크하는 것도 되긴 하지만, 실제 시나리오처럼 **계정 2개**로 해보길 권한다.

1. `POST /api/bookmarks/folders` — `{"name": "즐겨찾기"}` → `201`
2. (새 계정으로 로그인 전환) `POST /api/bookmarks` — `{"artworkId": "019ff3e8-d51b-..."}`
   - 원본 작품이 아직 `PROCESSING`이면 **`404` `ARTWORK_NOT_FOUND`** — 북마크는 `READY`만 대상이다.
     §3.4의 SQL로 `READY`로 바꾼 뒤 재시도하면 `201`.
3. `GET /api/bookmarks` → 방금 저장한 작품이 목록에 보이는지 확인

### 5.2 휴지통

1. `DELETE /api/artworks/{artworkId}` (본인) → `204`, 휴지통 이동
2. `GET /api/trash/artworks` → 목록에서 확인, `status: "DELETED"`, `visibility: "PRIVATE"`로 강제 전환된 것도 확인
3. `POST /api/trash/artworks/restore` — `{"artworkIds": ["..."]}` → `204`, 복구 시 삭제 전 공개범위(`PUBLIC`)로 되돌아옴

---

## 6. 커뮤니티 · 검색 (비로그인 가능)

- `GET /api/community/artworks` — `PUBLIC` + `READY`인 작품만 노출(위에서 복구한 작품이 여기 보임)
- `GET /api/community/authors` — 구인 가능 상태(`AVAILABLE`/`NEGOTIABLE`)인 회원만 노출(가입 직후 기본값은
  `PREPARING`이라 지금 계정은 안 보이는 게 정상 — `PATCH /api/members/me/info`로 `employmentStatus`를
  바꾸면 나타난다)
- `GET /api/search?q=겨울` — 제목·태그로 색인된 작품 검색. **주의: 실제 서비스는 Elasticsearch 인덱싱이
  비동기라 방금 올린 작품이 몇 초 뒤에야 검색에 잡힐 수 있다.**

---

## 7. 설정 관련 — 토큰 재발급 (로그아웃 API는 아직 없음)

`POST /api/auth/refresh` — `{"refreshToken": "..."}`

**직접 확인해볼 것 — refresh token은 회원당 1개만 유효하다:**
1. §1.1에서 받은 최초 `refreshToken`을 어딘가에 적어둔다.
2. §1.2에서 재로그인하면 **새 refreshToken이 발급되고 옛것은 즉시 무효화**된다.
3. 시간이 좀 지난 뒤(같은 초 안에 연달아 하면 우연히 같은 토큰이 나올 수 있어 재현이 안 될 수 있다 —
   최소 몇 초 간격을 두고 테스트할 것) 1번에서 적어둔 **옛 토큰으로 재발급을 시도하면 401**:
   ```json
   {"code": "INVALID_REFRESH_TOKEN", "message": "Refresh Token이 유효하지 않거나 만료되었습니다"}
   ```
4. 방금 로그인으로 받은 **최신** refreshToken으로 시도하면 `200`으로 새 토큰 쌍이 나온다.

> 참고: 로그아웃 전용 API, 비밀번호 변경 API, 마케팅 동의 토글, 성인 콘텐츠 표시 토글은 이번 마일스톤
> 백엔드 범위 밖이다(`docs/design/settings-i18n-design.md` 설계만 돼 있고 미구현). 지금은 "다른 토큰으로
> 갈아타면 이전 토큰이 죽는다"는 것으로 로그아웃과 비슷한 효과만 낼 수 있다.

---

## 8. 요금제

- `GET /api/billing/plans` (비로그인) — 스타터/프로월간/프로연간 3장 카드 정적 목록
- `GET /api/billing/me` (인증) — 내 플랜 상태. §4.4에서 DB로 프로 전환했다면 `plan: "PRO_MONTHLY"`
- **결제(Checkout)·해지·플랜 변경 API는 없다** — Stripe 연동 자체가 이번 범위 밖이라 프론트가 실제로
  결제 버튼을 눌러 완결할 수 있는 흐름이 아직 없다는 뜻이다.

---

## 9. 탈퇴로 마무리

`DELETE /api/members/me` (인증) → `204`. 탈퇴 후:
- 같은 토큰으로 다시 호출하면 403(`MEMBER_DEACTIVATED`)
- `GET /api/members/{handle}`로 조회하면 404
- 탈퇴 계정이 만든 공유 포트폴리오 링크는 410(`PORTFOLIO_BLOCKED`)으로 막힌다(§4.6에서 만든 링크로 확인 가능)

---

## 부록 — 자주 마주치는 상태코드 요약

| 상황 | 코드 | 이유 |
|---|---|---|
| PROCESSING 작품을 남이(또는 비로그인으로) 조회 | 404 `ARTWORK_NOT_FOUND` | 아직 공개되지 않은 것으로 취급 |
| PROCESSING 작품을 북마크 | 404 `ARTWORK_NOT_FOUND` | 북마크는 READY만 대상 |
| 휴지통 작품 조회(제3자) | 410 `ARTWORK_DELETED` | 존재했었지만 지금은 없음을 구분해서 알려줌 |
| 스타터가 공유 포트폴리오 생성/수정 | 403 `PRO_PLAN_REQUIRED` | 삭제는 스타터도 허용(§4.3과 다름) |
| 고정형(SNAPSHOT) 포트폴리오 수정 | 409 `SNAPSHOT_PORTFOLIO_IMMUTABLE` | 설계상 불변 |
| 만료/재사용된 refresh token | 401 `INVALID_REFRESH_TOKEN` | §7 참고 |
| 탈퇴 회원의 토큰으로 API 호출 | 403 `MEMBER_DEACTIVATED` | |
