# Artwork 모듈 총 정리

> 이 문서는 코드를 직접 읽지 않고도 artwork 모듈의 전체 기능과 설계를 파악할 수 있도록 작성되었습니다.
> 최종 반영 커밋: feat/artwork-module (피그마 전면 반영)

---

## 목차

1. [모듈 개요 및 아키텍처](#1-모듈-개요-및-아키텍처)
2. [도메인 모델](#2-도메인-모델)
3. [이미지 업로드 플로우](#3-이미지-업로드-플로우)
4. [API 엔드포인트 전체](#4-api-엔드포인트-전체)
5. [커서 페이지네이션](#5-커서-페이지네이션)
6. [이벤트 드리븐 연동](#6-이벤트-드리븐-연동)
7. [스케줄러](#7-스케줄러)
8. [DB 인덱스 설계](#8-db-인덱스-설계)
9. [설정 항목](#9-설정-항목)
10. [에러 코드](#10-에러-코드)
11. [공개 범위(Visibility) 정책](#11-공개-범위visibility-정책)
12. [미구현 항목](#12-미구현-항목)

---

## 1. 모듈 개요 및 아키텍처

### 위치

`com.atcrew.artwork` — 모듈형 모놀리식 아키텍처에서 하나의 독립 모듈.

### 패키지 구조

```
com.atcrew.artwork/                        ← 외부에 공개되는 인터페이스 경계
  ArtworkService.java                      (인터페이스)
  BookmarkService.java                     (인터페이스)
  UploadArtworkCommand.java / UpdateArtworkCommand.java / ...
  ArtworkInfo.java / ArtworkSummaryInfo.java / BookmarkEntryInfo.java / ...
  ArtworkPermanentlyDeletedEvent.java
  WorkDuration.java                        (작업 기간 record)
  각종 enum: ArtworkStatus, Visibility, AgeRating, ArtworkField,
             CreativeType, ArtworkRole, ImageLayoutType, ...

com.atcrew.artwork.internal/               ← 모듈 외부에서 직접 접근 불가
  application/    서비스 구현체, 스케줄러, 이벤트 리스너, Mapper
  domain/         도메인 엔티티 (Artwork, ArtworkImage, BookmarkFolder 등)
  exception/      ArtworkErrorCode, ArtworkException
  infra/storage/  R2StoragePort 인터페이스 + R2StorageAdapter 구현체
  persistence/    MongoDB Repository 인터페이스들
  web/            Controller 4개 + DTO
```

다른 모듈이 artwork 기능을 사용할 때는 `ArtworkService` 또는 `BookmarkService` 인터페이스만 주입받아 사용한다. `internal` 하위의 도메인·구현체에 직접 접근하는 것은 금지된다.

---

## 2. 도메인 모델

### Artwork (컬렉션: `artworks`)

작품 하나를 나타내는 핵심 엔티티.

| 필드 | 타입 | 설명 |
|------|------|------|
| `id` | String | UUID |
| `authorId` | String | 작성자 member ID |
| `title` | String | 작품 제목 (최대 100자) |
| `description` | String | 설명 (최대 500자) |
| `images` | `List<ArtworkImage>` | 이미지 목록 (1~30장) |
| `representativeImageIndex` | int | 대표 이미지 인덱스 |
| `thumbnailKey` | String | 사용자 지정 썸네일 R2 키 (별도 업로드). null이면 대표 이미지의 Worker 생성 썸네일 사용 |
| `imageLayoutType` | enum | VERTICAL_SCROLL / HORIZONTAL_SWIPE |
| `artworkField` | enum | ILLUSTRATION / WEBTOON / PRINT_COMIC / ANIMATION / ETC |
| `creativeType` | enum | ORIGINAL / SECONDARY / FAN_ART / OC / COMMISSION |
| `roles` | `List<ArtworkRole>` | 담당업무 (22종, 아래 표 참고) |
| `genres` | `List<Genre>` | 장르 태그 (정본 29종, 자유 입력 불가) |
| `tags` | `List<String>` | 일반 태그 (최대 7개) |
| `tools` | `List<String>` | 사용 도구 |
| `workDuration` | `WorkDuration` | 작업 기간 (개월/일/시간/분) |
| `cutCount` | Integer | 작품 컷 수 (웹툰·출판만화 분야 전용) |
| `videoLinks` | `List<String>` | 영상 링크 URL (최대 5개, YouTube 등) |
| `ageRating` | enum | ALL / R18 / G18 |
| `languages` | enum 배열 | 게시물 작성·노출 언어 KO / JA / ZH / EN. 필수이며 **주 사용 언어를 반드시 포함**한다. 스타터는 주 언어 1개로 고정, 프로는 주 언어를 포함한 최대 4개(업로드-R30, REQ-020) |
| `visibility` | enum | PUBLIC(피드 공개 ON) / PRIVATE(피드 공개 OFF), LINK_ONLY는 deprecated |
| `visibilityBeforeDelete` | enum | 휴지통 이동 전 공개 상태 스냅샷 |
| `materials` | `List<Material>` | 소재 정보 (이름·대상·R2 첨부키·외부 링크) |
| `status` | enum | PROCESSING / READY / DELETED |
| `deletedAt` | Instant | 휴지통 이동 시각 |
| `createdAt / updatedAt` | Instant | `@CreatedDate` / `@LastModifiedDate` 자동 관리 |

#### AgeRating

| 값 | 의미 |
|----|------|
| `ALL` | 전체연령가 |
| `R18` | 성인물 — 성적 콘텐츠 |
| `G18` | 성인물 — 고어/폭력 |

#### CreativeType

| 값 | 의미 |
|----|------|
| `ORIGINAL` | 1차 창작 |
| `SECONDARY` | 2차 창작 |
| `FAN_ART` | 팬아트 |
| `OC` | OC (오리지널 캐릭터) |
| `COMMISSION` | 커미션 |

#### ArtworkRole (담당업무 — 22종)

| 값 | 의미 |
|----|------|
| `TOTAL_ARTWORK` | 전체 작화 |
| `ADAPTATION_STORYBOARD` | 각색·콘티 |
| `STORYBOARD` | 콘티 |
| `DIRECTION` | 연출 |
| `LINEART` | 선화 |
| `SKETCH` | 스케치 |
| `COLORING` | 채색 |
| `BASE_COLOR` | 밑색 |
| `TONE_WORK` | 톤 작업 |
| `POST_PROCESSING` | 후보정 |
| `FULL_COLOR` | 풀컬러 |
| `PANEL_DECORATION` | 컷꾸미기 |
| `THREE_D_MODELING` | 3D 모델링 |
| `MATERIAL_MAKING` | 소재 제작 |
| `MATERIAL_PLACEMENT` | 소재 배치 |
| `BACKGROUND` | 배경 |
| `WEBNOVEL_COVER` | 웹소설 표지 |
| `CHARACTER_DESIGN` | 캐릭터 디자인 |
| `CHARACTER_SHEET` | 캐릭터 시트 |
| `TYPOGRAPHY` | 식자 |
| `BROADCAST_THUMBNAIL` | 방송 썸네일 |
| `ETC` | 기타 (직접 입력) |

#### WorkDuration record

```java
record WorkDuration(Integer months, Integer days, Integer hours, Integer minutes)
```

피그마 페이지 4 기준: "00개월 00일 00시간 00분" 형식의 작업 기간 스피너 입력. 기간의 시작/종료가 아닌 순수 소요 시간.

#### 상태 전이 (status)

```
[업로드] → PROCESSING
              ↓ (모든 이미지 콜백 수신, 하나라도 DONE)
           READY
              ↓ (deleteArtwork)
           DELETED   ←→ (restoreArtworks) → READY
              ↓ (permanentlyDeleteArtworks)
           [DB에서 삭제]
```

- **PROCESSING**: 이미지 Worker 처리 중. 작가 본인은 조회 가능, 다른 사람은 접근 불가.
- **READY**: 정상 공개 가능 상태. Visibility에 따라 노출 범위 결정.
- **DELETED**: 휴지통. 다른 조회 API에 노출되지 않음. 복구 또는 영구 삭제 가능.

#### 이미지 처리 상태 (각 ArtworkImage)

```
PENDING → (Worker DONE 콜백) → DONE
        → (Worker FAILED 콜백) → FAILED
```

모든 이미지가 PENDING이 아니게 되고(DONE or FAILED), 하나라도 DONE이면 Artwork status를 READY로 전환. 전부 FAILED면 PROCESSING 유지 (수동 개입 필요).

### ArtworkImage (Artwork에 내장)

| 필드 | 설명 |
|------|------|
| `originalKey` | R2에 업로드된 원본 파일 키 (`raw/UUID.ext`) |
| `thumbKey` | Worker 생성 썸네일 키 |
| `thumbAdultKey` | Worker 생성 성인물 블러 썸네일 키 |
| `originalAvifKey` | Worker 생성 AVIF 변환본 키 |
| `processingStatus` | PENDING / DONE / FAILED |

**썸네일 우선순위**: `Artwork.thumbnailKey`(사용자 업로드) → 대표 이미지의 `ArtworkImage.thumbKey`(Worker 생성) 순으로 사용. `ArtworkSummaryInfo`의 `thumbKey` 필드에 최종 값이 담김.

### Material (Artwork에 내장)

| 필드 | 설명 |
|------|------|
| `name` | 소재 이름 |
| `targets` | 소재 대상 (무기/배경/장신구/컷꾸미기/효과/식자/인물/직접입력) |
| `attachmentKeys` | R2 업로드 이미지 키 목록 |
| `links` | 외부 소재 URL 목록 (acon3d 등 외부 링크) |

### BookmarkFolder (컬렉션: `bookmarkFolders`)

| 필드 | 설명 |
|------|------|
| `id` | UUID |
| `memberId` | 소유자 |
| `name` | 폴더명 (최대 20자, 동일 회원 내 중복 불가) |
| `sortOrder` | 정렬 순서 (생성 순서 자동 부여) |
| `createdAt` | `@CreatedDate` 자동 관리 |

### BookmarkEntry (컬렉션: `bookmarkEntries`)

| 필드 | 설명 |
|------|------|
| `id` | UUID |
| `memberId` | 소유자 |
| `artworkId` | 북마크한 작품 |
| `folderId` | 폴더 ID (`null` = 기본 폴더) |
| `savedAt` | 저장 시각 |
| `artworkVisibilityAtSave` | 저장 시점의 작품 공개 상태 스냅샷 |

### OrphanedImageKey (컬렉션: `orphanedImageKeys`)

| 필드 | 설명 |
|------|------|
| `id` | UUID |
| `keys` | 삭제 실패한 R2 파일 키 목록 |
| `markedAt` | 등록 시각 |

이미지 교체·영구 삭제 시 R2 파일 삭제에 실패하면 해당 키들을 여기에 보관. 1시간마다 OrphanImageCleanupScheduler가 배치 재시도.

---

## 3. 이미지 업로드 플로우

클라이언트가 이미지를 직접 R2에 업로드하는 구조. 서버는 파일을 거치지 않는다.

```
[클라이언트]                [서버]                  [Cloudflare R2]      [Worker]
     │                        │                           │                   │
     │ ① POST /artwork/       │                           │                   │
     │   images/presign       │                           │                   │
     │──────────────────────→ │ S3Presigner.presign()     │                   │
     │                        │──────────────────────────→│                   │
     │ [{key, uploadUrl}×N]   │←──────────────────────────│                   │
     │←─────────────────────  │                           │                   │
     │                        │                           │                   │
     │ ② PUT {uploadUrl}      │                           │                   │
     │   (R2 직접 업로드)     │                           │                   │
     │──────────────────────────────────────────────────→ │                   │
     │                        │                           │                   │
     │   (썸네일도 별도       │                           │                   │
     │    Presign → R2 업로드)│                           │                   │
     │                        │                           │                   │
     │ ③ POST /artworks       │                           │                   │
     │   {imageKeys,          │                           │                   │
     │    thumbnailKey, ...}  │                           │                   │
     │──────────────────────→ │ DB 저장 (PROCESSING)      │                   │
     │                        │ @Async triggerWorker()    │                   │
     │                        │───────────────────────────────────────────→   │
     │ {status:PROCESSING}    │                           │                   │
     │←─────────────────────  │                           │                   │
     │                        │                           │                   │
     │ ④ GET /artworks/{id}/  │                           │ 썸네일·avif 생성  │
     │   status (폴링)        │                           │←──────────────────│
     │──────────────────────→ │                           │                   │
     │ PROCESSING / READY     │                           │                   │
     │←─────────────────────  │                           │                   │
     │                        │ ⑤ POST /internal/artwork/ │                   │
     │                        │   images/processed        │                   │
     │                        │ {artworkId, imageKey,     │                   │
     │                        │  thumbKey, status:DONE}   │                   │
     │                        │←──────────────────────────────────────────────│
     │                        │ markImageProcessed()      │                   │
     │                        │ 전체 완료 → READY         │                   │
```

### 썸네일 업로드

피그마 업로드 플로우 7페이지 기준: 작품 이미지 목록에서 썸네일로 사용할 이미지를 선택하거나, 새로운 이미지를 별도로 업로드하고 3:4 비율로 자를 수 있다.

- 사용자 지정 썸네일도 일반 작품 이미지와 동일한 Presigned URL 방식으로 R2에 업로드
- `thumbnailKey`는 `UploadArtworkCommand`에 포함되어 `Artwork`에 별도 필드로 저장
- `thumbnailKey`가 null이면 대표 이미지의 Worker 생성 `thumbKey`가 대신 사용됨

### Presigned URL 제약

- `count`: 1~30, `contentTypes`: `image/jpeg` / `image/png` / `image/webp`만 허용
- `count`와 `contentTypes` 배열 크기는 반드시 일치해야 함 (불일치 시 400)
- 유효기간: 10분 (`presign-expiration-minutes` 설정)
- 생성된 키 형식: `raw/{UUID}.{ext}`

### Worker 연동

- 서버 → Worker: `POST {workerTriggerUrl}` + `X-Callback-Secret` 헤더 + `{artworkId, imageKeys}`
- Worker → 서버: `POST /internal/artwork/images/processed` + `X-Internal-Secret` 헤더
- 내부 webhook은 `permitAll` + `MessageDigest.isEqual()`로 상수 시간 비교 인증
- Worker 트리거는 `@Async`로 비차단 실행. 트리거 실패 시 로그만 기록 (5분 후 재시도 스케줄러 처리)

---

## 4. API 엔드포인트 전체

### 인증 정책

| 엔드포인트 | 인증 |
|---|---|
| `GET /api/artworks/{artworkId}` | 선택적 (비인증 가능, 공개 작품만 노출) |
| `GET /api/community/artworks` | 불필요 (완전 공개) |
| `POST /internal/artwork/images/processed` | X-Internal-Secret 헤더 |
| 나머지 모든 엔드포인트 | JWT 필수 |

---

### 작품 업로드·관리 (`/api`)

#### `POST /api/artwork/images/presign` — Presigned URL 발급

```json
요청: { "count": 3, "contentTypes": ["image/jpeg", "image/png", "image/webp"] }
응답 200: [{ "key": "raw/UUID.jpg", "uploadUrl": "https://..." }, ...]
```

#### `POST /api/artworks` — 작품 업로드

R2 업로드 완료 후 작품 메타데이터를 저장. 바로 `PROCESSING` 상태로 시작.

```json
요청: {
  "imageKeys": ["raw/uuid1.jpg", "raw/uuid2.jpg"],
  "representativeImageIndex": 0,
  "thumbnailKey": "raw/uuid-thumb.jpg",
  "imageLayoutType": "VERTICAL_SCROLL",
  "title": "작품 제목",
  "description": "설명 (최대 500자)",
  "artworkField": "ILLUSTRATION",
  "creativeType": "ORIGINAL",
  "roles": ["LINEART", "COLORING"],
  "genres": ["FANTASY"],
  "tags": ["드래곤", "판타지"],
  "ageRating": "ALL",
  "publishToFeed": true,
  "portfolioIds": ["4c8c0d5e-1b2a-7c3d-8e4f-5a6b7c8d9e0f"],
  "tools": ["Procreate"],
  "workDuration": { "months": 1, "days": 0, "hours": 3, "minutes": 30 },
  "cutCount": null,
  "videoLinks": ["https://youtube.com/..."],
  "materials": [
    {
      "name": "소재명",
      "targets": ["배경"],
      "attachmentKeys": ["raw/uuid3.jpg"],
      "links": ["https://acon3d.com/..."]
    }
  ]
}
응답 201: ArtworkInfo (전체 필드)
```

**필드 설명**:
- `thumbnailKey`: 선택. Presigned URL로 미리 업로드한 사용자 지정 썸네일의 R2 키
- `workDuration`: 선택. 작업 기간 (months/days/hours/minutes 중 null 허용)
- `cutCount`: 선택. 웹툰·출판만화 분야에서 사용하는 작품 컷 수
- `videoLinks`: 선택. YouTube 등 영상 링크 (최대 5개)

#### `GET /api/artworks/{artworkId}` — 작품 상세 조회

- 인증 선택적. 비인증 또는 타인의 경우 `READY`이면서 `PUBLIC`이거나 라이브 포트폴리오에 편입된 작품만 노출(§11).
- 작가 본인은 `PROCESSING` / `DELETED` 포함 항상 조회 가능.

#### `GET /api/artworks/{artworkId}/status` — 처리 상태 폴링

작가 본인만. `PROCESSING / READY / DELETED` 반환. 클라이언트는 이 엔드포인트를 폴링해 업로드 완료를 감지.

#### `PATCH /api/artworks/{artworkId}` — 작품 수정

모든 필드가 Optional (null이면 기존 값 유지).

- `imageKeys`가 포함되면 기존 이미지는 `OrphanedImageKey`로 등록 후 새 이미지로 교체, 상태는 다시 `PROCESSING`으로 전환.
- `thumbnailKey`는 이미지 교체와 무관하게 독립적으로 수정 가능.
- `DELETED` 상태 작품은 수정 불가 (404).
- `imageLayoutType`은 이미지 교체 여부와 무관하게 항상 반영.

#### `PATCH /api/artworks/{artworkId}/publication` — 노출 위치 재선언

`READY` 상태일 때만 가능. 요청 본문은 `publishToFeed`(필수)와 `portfolioIds`(선택)이며, 공개 상태는
이 조합으로 서버가 계산한다(업로드-R09) — 공개 상태값을 직접 받는 필드는 없다. `portfolioIds`는
증분이 아니라 전체 재선언이라 목록에서 빠진 포트폴리오에서는 제외된다.

#### `DELETE /api/artworks/{artworkId}` — 작품 삭제 (휴지통 이동)

- `status` → `DELETED`, `visibility` → `PRIVATE` (강제)
- `visibilityBeforeDelete`에 기존 공개 상태 스냅샷 저장
- 이미 `DELETED`인 경우 멱등 처리 (재호출해도 에러 없음)

#### `GET /api/members/me/artworks` — 내 작품 목록

커서 페이지네이션. `DELETED`인 작품 제외. `createdAt DESC` 정렬.

---

### 커뮤니티 피드 (`/api/community`)

#### `GET /api/community/artworks` — 공개 피드

인증 불필요. `READY + PUBLIC` 작품만 노출. 최신순.

```
?artworkField=ILLUSTRATION   선택, 작품 분야 필터
?ageRating=ALL               선택, 연령 등급 필터. 미지정 시 ALL+R18+G18 전체 노출
?cursor=1718500000000        선택, 이전 페이지 마지막 createdAt millis
?size=20                     선택, 기본 20, 최대 50
```

**ageRating 정책**: `null`이면 ALL / R18 / G18 전부 반환. 설계상 성인물도 피드에 노출하되 블러 처리는 클라이언트 담당. 특정 값으로 필터링하면 해당 등급만 반환.

---

### 휴지통 (`/api/trash`)

#### `GET /api/trash/artworks` — 휴지통 목록

`DELETED` 작품만. `createdAt DESC`. 커서 페이지네이션.

#### `POST /api/trash/artworks/restore` — 복구

```json
{ "artworkIds": ["id1", "id2"] }
```

- `status` → `READY`, `visibility` → `visibilityBeforeDelete` 복원, `deletedAt` 초기화
- 요청한 ID 중 존재하지 않는 것이 하나라도 있으면 404 (전체 롤백)
- 소유권 위반 시 403 (전체 롤백)

#### `DELETE /api/trash/artworks` — 영구 삭제

```json
{ "artworkIds": ["id1", "id2"] }
```

- DB에서 완전 삭제 후 `ArtworkPermanentlyDeletedEvent` 발행
- 이벤트를 비동기(`@Async`)로 수신하여 R2 파일 삭제 시도
- R2 삭제 실패 시 → `OrphanedImageKey`에 기록 → 1시간 배치 재시도

---

### 북마크 (`/api/bookmarks`)

#### 폴더 관리

| 메서드 | 경로 | 설명 |
|---|---|---|
| `GET` | `/api/bookmarks/folders` | 폴더 목록 (sortOrder ASC) |
| `POST` | `/api/bookmarks/folders` | 폴더 생성 (이름 최대 20자, 중복 불가) |
| `DELETE` | `/api/bookmarks/folders/{folderId}` | 폴더 삭제 (내부 항목은 기본 폴더로 이동) |

- 폴더명은 앞뒤 공백 자동 제거 후 저장
- `sortOrder`는 생성 순서(현재 폴더 수)로 자동 부여

#### 북마크 항목 관리

| 메서드 | 경로 | 설명 |
|---|---|---|
| `GET` | `/api/bookmarks` | 북마크 목록 |
| `POST` | `/api/bookmarks` | 북마크 저장 |
| `DELETE` | `/api/bookmarks/{artworkId}` | 북마크 해제 |
| `PATCH` | `/api/bookmarks/move` | 폴더 이동 |

```json
GET /api/bookmarks?folderId=xxx&cursor=xxx&size=20
// folderId 미지정 시 기본 폴더(null) 조회
// 응답: READY + PUBLIC 작품만 노출 (삭제/비공개 작품은 조회에서 제외)

POST /api/bookmarks
{ "artworkId": "xxx", "folderId": null }
// folderId null = 기본 폴더. READY + PUBLIC(또는 본인 작품)인 경우만 저장 가능
// 같은 작품 중복 저장 시 409 BOOKMARK_ALREADY_EXISTS

PATCH /api/bookmarks/move
{ "artworkIds": ["id1", "id2"], "targetFolderId": "folderX" }
// targetFolderId null = 기본 폴더로 이동
```

---

### 인터널 웹훅 (`/internal/artwork`)

#### `POST /internal/artwork/images/processed` — Worker 콜백

Cloudflare Worker가 이미지 처리 완료 후 호출. Swagger에서 숨김(`@Hidden`).

```json
헤더: X-Internal-Secret: {secret}
요청: {
  "artworkId": "xxx",
  "imageKey": "raw/uuid.jpg",
  "thumbKey": "thumb/uuid.jpg",
  "thumbAdultKey": "thumb-adult/uuid.jpg",
  "originalAvifKey": "avif/uuid.avif",
  "status": "DONE"
}
```

- `X-Internal-Secret`는 `MessageDigest.isEqual()`로 상수 시간 비교
- `status: DONE` → `ArtworkImage.markDone()`, `FAILED` → `markFailed()`
- 처리 중인 이미지가 없고 하나라도 DONE이면 Artwork status → `READY`

---

## 5. 커서 페이지네이션

모든 목록 API는 커서 기반 페이지네이션 사용 (OFFSET 방식 금지).

**커서 형식**: `createdAt` (또는 `savedAt`)의 epoch millis를 String으로 인코딩.
예: `"1718500000000"`

**동작 원리**:

```
1회 요청: size+1개 조회
결과가 size+1개이면 → hasNext=true, nextCursor=마지막 항목의 createdAt millis
결과가 size 이하이면 → hasNext=false, nextCursor=null

다음 페이지: createdAt < cursor (strictly less than)
```

**응답 구조 (`CursorPage<T>`)**:

```json
{
  "items": [...],
  "nextCursor": "1718499000000",
  "hasNext": true
}
```

**알려진 한계**: 동일 밀리초에 생성된 작품이 페이지 경계에 걸리면 누락될 수 있음. 복합 커서 `(createdAt, _id)` 방식으로 개선 예정.

---

## 6. 이벤트 드리븐 연동

### MemberDeactivatedEvent 수신 (동기)

회원 탈퇴 이벤트를 받으면 해당 회원의 모든 작품을 강제 비공개 처리.

```
onMemberDeactivated() [동기, @EventListener]
  → artworkRepository.findAllByAuthorId(memberId)
  → 각 Artwork.forcePrivate()  // status 체크 없이 강제 PRIVATE
  → artworkRepository.saveAll()
```

`changeVisibility()`는 `READY` 상태만 허용하지만, 탈퇴 이벤트 처리는 `PROCESSING` / `DELETED` 작품에도 적용해야 하므로 `forcePrivate()`를 별도로 구현해 상태 체크를 건너뜀.

### ArtworkPermanentlyDeletedEvent 발행 (비동기)

영구 삭제 후 R2 파일 정리를 비동기로 처리.

```
permanentlyDeleteArtworks()
  → artworkRepository.deleteAll()
  → eventPublisher.publishEvent(ArtworkPermanentlyDeletedEvent)

onPermanentlyDeleted() [@Async, @EventListener]
  → storagePort.deleteFiles(allImageKeys)
  → 실패 시 orphanedRepo.save(OrphanedImageKey.ofKeys(keys))
```

---

## 7. 스케줄러

### ImageRetryScheduler (5분마다)

10분 이상 `PROCESSING` 상태인 작품을 찾아 아직 `PENDING`인 이미지만 Worker에 재전송.

```
findStuckProcessingArtworks(now - 10분)
  → 각 작품의 isPending() 이미지 필터
  → pendingKeys 있으면 triggerAsync() 재시도
```

`FAILED` 이미지는 `isPending()`이 false이므로 재시도 대상에서 제외. 전체 FAILED 케이스는 수동 개입 필요.

### OrphanImageCleanupScheduler (1시간마다)

R2 삭제에 실패해 `orphanedImageKeys`에 쌓인 파일 키들을 배치로 정리.

```
orphanedRepo.findAll(PageRequest.of(0, 100))  // 한 번에 최대 100건
  → 각 orphan: storagePort.deleteFiles(keys) 성공 시 orphanedRepo.delete()
  → 실패 시 로그만 기록, 다음 배치에서 재시도
```

---

## 8. DB 인덱스 설계

인덱스는 `ArtworkIndexInitializer`의 `@PostConstruct`에서 앱 기동 시 `ensureIndex`로 자동 생성.

### artworks 컬렉션

| 인덱스 이름 | 키 | 용도 |
|---|---|---|
| `idx_artwork_author_status` | `{authorId:1, status:1, createdAt:-1}` | 내 작품 목록, 휴지통 목록 |
| `idx_artwork_community_feed` | `{status:1, visibility:1, ageRating:1, createdAt:-1}` | 커뮤니티 피드 기본 경로 (artworkField 없을 때) |
| `idx_artwork_field_filter` | `{status:1, visibility:1, artworkField:1, ageRating:1, createdAt:-1}` | 커뮤니티 피드 artworkField 필터 경로 |

### bookmarkEntries 컬렉션

| 인덱스 이름 | 키 | 용도 |
|---|---|---|
| `idx_bookmark_entry_folder` | `{memberId:1, folderId:1, savedAt:-1}` | 북마크 목록 커서 조회 |
| `idx_bookmark_entry_unique` | `{memberId:1, artworkId:1}` (unique) | 중복 북마크 방지 |

### bookmarkFolders 컬렉션

| 인덱스 이름 | 키 | 용도 |
|---|---|---|
| `idx_bookmark_folder_unique` | `{memberId:1, name:1}` (unique) | 폴더명 중복 방지 |
| `idx_bookmark_folder_sort` | `{memberId:1, sortOrder:1}` | 폴더 목록 정렬 조회 |

---

## 9. 설정 항목

```yaml
cloudflare:
  r2:
    endpoint: ${R2_ENDPOINT}                     # Cloudflare R2 S3 호환 엔드포인트
    access-key: ${R2_ACCESS_KEY}
    secret-key: ${R2_SECRET_KEY}
    bucket: ${R2_BUCKET:atcrew-artwork}
    presign-expiration-minutes: 10               # Presigned URL 유효시간 (분)
    worker-trigger-url: ${WORKER_TRIGGER_URL}    # Worker에 처리 요청할 URL
    callback-secret: ${WORKER_CALLBACK_SECRET}   # Worker→서버 요청 시 비밀값

artwork:
  internal:
    secret: ${ARTWORK_INTERNAL_SECRET}           # /internal webhook 인증 비밀값
```

R2는 Cloudflare R2 (S3 호환). AWS SDK S3 v2를 사용하되 `Region.of("auto")`, `forcePathStyle(true)` 설정으로 R2에 맞게 동작.

---

## 10. 에러 코드

| 코드 | HTTP | 설명 |
|---|---|---|
| `ARTWORK_NOT_FOUND` | 404 | 작품 없음 또는 접근 불가 |
| `ARTWORK_ACCESS_DENIED` | 403 | 본인 작품이 아님 |
| `ARTWORK_NOT_READY` | 400 | 처리 중인 작품에서 불가한 작업 |
| `ARTWORK_NOT_DELETED` | 400 | 휴지통에 없는 작품에 복구/영구삭제 시도 |
| `INVALID_IMAGE_COUNT` | 400 | 이미지 수 범위(1~30) 초과 또는 count·contentTypes 수 불일치 |
| `INVALID_CONTENT_TYPE` | 400 | jpeg/png/webp 외 형식 |
| `INVALID_REPRESENTATIVE_INDEX` | 400 | 대표 이미지 인덱스 범위 초과 |
| `INVALID_CURSOR` | 400 | 비정수 커서 값 |
| `BOOKMARK_FOLDER_NOT_FOUND` | 404 | 폴더 없음 |
| `BOOKMARK_FOLDER_DUPLICATE_NAME` | 409 | 폴더명 중복 |
| `BOOKMARK_FOLDER_NAME_BLANK` | 400 | 빈 폴더명 |
| `BOOKMARK_ALREADY_EXISTS` | 409 | 이미 북마크한 작품 |
| `BOOKMARK_NOT_FOUND` | 404 | 북마크 없음 |
| `INTERNAL_SECRET_INVALID` | 401 | 내부 webhook 인증 실패 |
| `PRESIGN_FAILED` | 500 | Presigned URL 생성 실패 |

---

## 11. 공개 범위(Visibility) 정책

제3자 열람 가능 여부는 `visibility` 단독이 아니라 "피드 공개 여부 × 라이브 포트폴리오(작가 페이지·
최신 반영형) 편입 여부" 2요소로 계산한다(마이페이지_작가-R04). 고정형(SNAPSHOT) 포함은 이 계산에
들어가지 않는다.

| 피드 공개 | 라이브 포트폴리오 편입 | 비인증 사용자 | 타인(인증) | 작가 본인 |
|---|---|---|---|---|
| PUBLIC | 무관 | ✅ 조회 가능 | ✅ 조회 가능 | ✅ |
| OFF(PRIVATE) | 1개 이상 | ✅ 조회 가능 | ✅ 조회 가능 | ✅ |
| OFF(PRIVATE) | 0개 (= 완전 비공개) | ❌ 403 | ❌ 403 | ✅ |

- "링크 공개"라는 제3의 상태는 없다. 레거시 `LINK_ONLY`(라이트 ETL 매핑용)는 판정상 `PRIVATE`와
  동일 취급한다. 업로드·공개 상태 변경 API(`publishToFeed`/`portfolioIds` 조합, §업로드-R09)는 애초에
  `visibility`를 입력받지 않으므로 `LINK_ONLY`를 신규 생성할 경로 자체가 없다(2026-08-13 PA-05로
  `UNSUPPORTED_VISIBILITY` 400 가드와 함께 구 `PATCH /visibility` 제거).
- 커뮤니티 피드·검색 색인은 `PUBLIC`만 대상으로 한다(포트폴리오 한정 공개는 노출하지 않음).
- 휴지통 이동 시 강제 PRIVATE으로 변경. 복구 시 이전 상태로 복원.
- 탈퇴 이벤트 수신 시 모든 작품 강제 PRIVATE (`forcePrivate()`, 상태 무관).
- `changeVisibility()`는 `READY` 상태만 허용. `forcePrivate()`는 상태 무관.

---

## 12. 미구현 항목

| 항목 | 비고 |
|---|---|
| 성인물 인증 연동 | R18·G18 작품은 피드에 노출되지만 블러 처리 및 성인 인증 상태 응답 로직 미구현 |
| 썸네일 자르기 서버 처리 | 3:4 크롭 좌표는 클라이언트에서 처리하고 완성된 이미지를 업로드. 서버는 키만 수신 |
| 검색 기능 | 제목·태그·작가명 검색 미구현 |
| 북마크 폴더 순서 변경 | sortOrder 재정렬 API 미구현 |
| 커서 동률(tie-breaking) | 동일 ms 생성 작품이 페이지 경계에 걸릴 때 누락 가능. 복합 커서 `(createdAt, _id)` 방식으로 개선 예정 |
| MongoDB 레플리카셋 트랜잭션 | `@Transactional` 다중 도큐먼트 작업은 레플리카셋 환경에서만 원자성 보장 |
