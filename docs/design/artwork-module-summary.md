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
  domain/         도메인 엔티티 (Artwork, BookmarkFolder 등 — 이미지는 media 모듈)
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
| (이미지) | — | `media_assets`에만 있다(#193). 작품 행에는 없고 `MediaService.getAssets`로 읽는다 |
| `representativeImageIndex` | int | 대표 이미지 인덱스 |
| `thumbnailKey` | String | 사용자 지정 썸네일의 업로드 키(식별용). 변환 결과는 `media_assets`의 `ARTWORK_THUMBNAIL` 자산에 있다 |
| `imageLayoutType` | enum | VERTICAL_SCROLL / HORIZONTAL_SWIPE |
| `artworkField` | enum | ILLUSTRATION / WEBTOON / PRINT_COMIC / ANIMATION / ETC |
| `creativeType` | enum | ORIGINAL / SECONDARY / FAN_ART / OC / COMMISSION |
| `roles` | `List<ArtworkRole>` | 담당업무 (24종, 아래 표 참고) |
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
| `status` | enum | PROCESSING / READY / FAILED / DELETED |
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

#### ArtworkRole (담당업무 — 24종)

| 값 | 의미 |
|----|------|
| `TOTAL_ARTWORK` | 전체 작화 |
| `ARTWORK` | 작화 |
| `ADAPTATION_STORYBOARD` | 각색·콘티 |
| `STORYBOARD` | 콘티 |
| `DIRECTION` | 연출 |
| `LINEART` | 선화 |
| `SKETCH` | 스케치 |
| `COLORING` | 채색 |
| `BASE_COLOR` | 밑색 |
| `TONE_WORK` | 톤 작업 |
| `POST_PROCESSING` | 후보정 |
| `PANEL_DECORATION` | 원고꾸미기 |
| `LETTERING` | 식자 |
| `FULL_COLOR` | 풀컬러 |
| `THREE_D_MODELING` | 3D 모델링 |
| `MATERIAL_MAKING` | 소재 제작 |
| `MATERIAL_PLACEMENT` | 소재 배치 |
| `BACKGROUND` | 배경 |
| `WEBNOVEL_COVER` | 웹소설 표지 |
| `CHARACTER_DESIGN` | 캐릭터 디자인 |
| `CHARACTER_SHEET` | 캐릭터 시트 |
| `TYPOGRAPHY` | 타이포 |
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
           DELETED   ──(restoreArtworks)──▶ 이미지 현황으로 재계산 (PROCESSING/READY/FAILED)
              ↓ (permanentlyDeleteArtworks, 또는 보관 1년 경과 시 TrashPurgeScheduler)
           [DB에서 삭제]

           PROCESSING ──(모든 이미지 콜백 수신, 전부 FAILED)──▶ FAILED
           READY·FAILED ──(updateArtwork로 이미지 목록 변경)──▶ PROCESSING
```

- **PROCESSING**: 이미지 Worker 처리 중. 작가 본인은 조회 가능, 다른 사람은 접근 불가.
- **READY**: 정상 공개 가능 상태. Visibility에 따라 노출 범위 결정.
- **FAILED**: 이미지가 한 장도 성공하지 못한 상태. 작가 본인은 조회 가능(재업로드 안내용), 다른 사람은
  접근 불가. 재시도 대상이 아니므로 작가가 이미지를 교체하면(`updateArtwork`) 다시 PROCESSING으로 간다.
- **DELETED**: 휴지통. 다른 조회 API에 노출되지 않음. 복구 또는 영구 삭제 가능.

#### 이미지 처리 상태 (각 media 자산)

```
PENDING → (Worker DONE 콜백) → DONE
        → (Worker FAILED 콜백) → FAILED
```

모든 이미지가 PENDING이 아니게 되고(DONE or FAILED), 하나라도 DONE이면 Artwork status를 READY로 전환. 전부 FAILED면 `FAILED`로 전환한다 — 재시도 스케줄러는 PENDING만 다루므로 PROCESSING에 두면 영구 고착된다.

### 이미지 (media 모듈 `media_assets`)

| 필드 | 설명 |
|------|------|
| `originalKey` | R2에 업로드된 원본 파일 키 (`raw/UUID.ext`). **처리 완료 후 실제 객체는 삭제된다** — 변환 결과가 원본을 대체하므로 식별·정리용 값일 뿐, 이미지 로드에 쓰지 않는다 |
| `thumbKey` | 카드 썸네일(588×784 AVIF). 지정 썸네일 자산에만 있다. 본문 이미지는 null(역할 분리 이전 업로드분에는 남아 있음) |
| `thumbAdultKey` | 성인물 블러 썸네일. 지정 썸네일 자산에만 있다 |
| `originalAvifKey` | 본문 표시용 AVIF 변환본. 본문 이미지에만 있다 |
| `processingStatus` | PENDING / DONE / FAILED |

본문 이미지는 `ARTWORK`(프로필 `ORIGINAL`), 지정 썸네일은 `ARTWORK_THUMBNAIL`(프로필 `THUMBNAIL_WITH_ADULT_BLUR`)
owner로 따로 둔다(media-module-design.md §3). 응답에서는 `images[]`와 `thumbnailImage`로 나뉜다.

**카드 썸네일 판정**(`ArtworkCardThumbnail`, 작품 목록·검색·포트폴리오 공통):
1. 지정 썸네일 자산의 `thumbKey`/`thumbAdultKey`. 변환 전·실패면 그 raw(`originalKey`)
2. 자산이 없는 옛 지정 썸네일은 `thumbnailKey`(raw) 그대로, 블러 없음
3. 지정 썸네일이 없으면 대표 이미지의 옛 `thumbKey`, 그것도 없으면 대표 이미지의 표시 key

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
     │                        │ applyImageStatuses()      │                   │
     │                        │ 전체 완료 → READY         │                   │
```

### 썸네일 업로드

피그마 업로드 플로우 7페이지 기준: 작품 이미지 목록에서 썸네일로 사용할 이미지를 선택하거나, 새로운 이미지를 별도로 업로드하고 3:4 비율로 자를 수 있다.

- 사용자 지정 썸네일도 일반 작품 이미지와 동일한 Presigned URL 방식으로 R2에 업로드. 기존 이미지를 골라도 FE가 잘라 새 파일로 올린다
- `thumbnailKey`는 등록 시 필수이며 `Artwork`에 식별용으로 저장되고, 같은 key를 `ARTWORK_THUMBNAIL` 자산으로 등록해 Worker가 `thumb/`·`thumb-adult/`를 만든다
- 본문 이미지와 같은 key는 거부한다(`THUMBNAIL_KEY_IN_IMAGES`) — 한 raw를 두 번 변환하면 먼저 끝난 쪽이 raw를 지워 다른 쪽이 실패한다
- 변환이 끝나면 raw는 지워지므로 표시에는 `thumbnailImage.thumbKey`(변환 전이면 `thumbnailImage.originalKey`)를 쓴다

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
| `POST /api/artworks/{artworkId}/views` | 선택적 (비인증은 `X-Anonymous-Id` 헤더로 식별) |
| `GET /api/community/artworks` | 불필요 (완전 공개) |
| `GET /api/community/artworks/hot` | 불필요 (완전 공개) |
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
- `thumbnailKey`: 필수. Presigned URL로 미리 업로드한 사용자 지정 썸네일(3:4)의 R2 키. `imageKeys`와 겹치면 400 `THUMBNAIL_KEY_IN_IMAGES`
- `workDuration`: 선택. 작업 기간 (months/days/hours/minutes 중 null 허용)
- `cutCount`: 선택. 웹툰·출판만화 분야에서 사용하는 작품 컷 수
- `videoLinks`: 선택. YouTube 등 영상 링크 (최대 5개)

#### `GET /api/artworks/{artworkId}` — 작품 상세 조회

- 인증 선택적. 비인증 또는 타인의 경우 `READY`이면서 `PUBLIC`이거나 라이브 포트폴리오에 편입된 작품만 노출(§11).
- 작가 본인은 `PROCESSING` / `DELETED` 포함 항상 조회 가능.
- 조회수를 올리지 않는다(2026-09-22, 홈-R14). FE SSR·편집 화면·포트폴리오가 같은 GET을 부르므로 열람자를
  식별할 수 없다 — 집계는 아래 `POST /views`가 담당한다.

#### `POST /api/artworks/{artworkId}/views` — 작품 열람 기록

- 브라우저가 상세 화면을 연 뒤 호출한다. 응답은 **항상 204**(기록 여부를 드러내지 않는다).
- 로그인 회원은 회원 ID, 비로그인은 `X-Anonymous-Id`(FE 1st-party 쿠키로 발급한 익명 UUID)로 식별한다.
  둘 다 있으면 회원 기준이고 헤더는 보지 않는다. 둘 다 없으면 기록하지 않는다.
- 비로그인 요청의 헤더가 표준 36자 UUID가 아니면 400 `INVALID_ANONYMOUS_ID`.
- 동일 열람자의 24시간 이내 반복 열람, 본인 작품, 열람 불가(`accessFor != ALLOWED`)·없는 작품은 기록하지 않는다.
- 유효 열람(최초 `FIRST`, 24시간 경과 재방문 `REVISIT`)이면 `artwork_view_events`에 남기고 `view_count` +1.
  회원 기록과 로그인 전 익명 기록은 합치지 않는다. 판정·동시성·격리 수준은 `artwork-module-design.md` §10.6.

#### `GET /api/artworks/{artworkId}/status` — 처리 상태 폴링

작가 본인만. `PROCESSING / READY / FAILED / DELETED` 반환. 클라이언트는 이 엔드포인트를 폴링해 업로드 완료를 감지.

#### `PATCH /api/artworks/{artworkId}` — 작품 수정

모든 필드가 Optional (null이면 기존 값 유지).

- `imageKeys`가 **현재 목록과 다르면** 기존 이미지는 `OrphanedImageKey`로 등록 후 새 이미지로 교체, 상태는 다시 `PROCESSING`으로 전환.
- `imageKeys`가 현재 목록과 순서까지 같으면 교체하지 않는다(recruit의 `ImageSyncResult.UNCHANGED`와 같은 규칙). 프론트가 수정 요청마다 폼 전체를 보내므로, 교체하면 이미지를 건드리지 않은 수정도 변환 결과를 버리고 파일을 고아 큐로 넘겨 이미지가 깨진다(#193). 일부만 바꾸는 경우는 여전히 전체 교체이며 #193에서 재설계한다. `representativeImageIndex`는 이 경우에도 반영된다.
- `thumbnailKey`는 이미지 교체와 무관하게 독립적으로 수정 가능. 기존 값과 같으면 아무 일도 하지 않고, 바뀌면 새 썸네일을 변환하고 이전 썸네일(원본·변형본)을 고아 큐로 보낸다.
- `DELETED` 상태 작품은 수정 불가 (404).
- `imageLayoutType`은 이미지 교체 여부와 무관하게 항상 반영.

#### `PATCH /api/artworks/{artworkId}/publication` — 노출 위치 재선언

휴지통(`DELETED`) 작품은 불가하고, `PROCESSING`·`FAILED` 상태에서도 가능하다. 요청 본문은 `publishToFeed`(필수)와 `portfolioIds`(선택)이며, 공개 상태는
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

`sort=VIEW_COUNT`의 조회수는 `POST /views`로 집계된 누적 조회수(24시간 dedup)다. 2026-09-22 V44에서 0으로 초기화됐다.

#### `GET /api/community/artworks/hot` — 이번 주 가장 핫한 작품

인증 불필요. `ApiResponse<List<ArtworkSummaryInfo>>`, 최대 6개(홈-R03·R14).

- 후보: `GET /api/community/artworks`와 같은 노출 조건(공개·READY·미차단·언어 세그먼트·성인 콘텐츠 설정).
- 정렬: 최근 168시간 기간 조회수 DESC → 현재 북마크 수 DESC → 등록일 DESC → ID ASC.
- 기간 조회수는 매시 정각 `HotScoreScheduler`가 `artwork_hot_scores`에 다시 계산한다(요청 시 계산하지 않는다).
- 기간 조회수가 있는 후보로 6개가 차지 않으면 기간 조회수 0 후보로 같은 규칙(북마크 수 이하)을 따라 채운다.

---

### 휴지통 (`/api/trash`)

#### `GET /api/trash/artworks` — 휴지통 목록

`DELETED` 작품만. `createdAt DESC`. 커서 페이지네이션.

#### `POST /api/trash/artworks/restore` — 복구

```json
{ "artworkIds": ["id1", "id2"] }
```

- `status` → 이미지 현황으로 재계산(READY·PROCESSING·FAILED), `visibility` → `visibilityBeforeDelete` 복원, `deletedAt` 초기화
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
- `status: DONE`/`FAILED` → media가 자산 행을 갱신하고, 작품은 현황을 받아 상태만 다시 계산한다
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

`changeVisibility()`는 `DELETED` 상태를 거부하지만, 탈퇴 이벤트 처리는 `PROCESSING` / `DELETED` 작품에도 적용해야 하므로 `forcePrivate()`를 별도로 구현해 상태 체크를 건너뜀.

같은 이벤트를 `ArtworkViewMemberEventListener`도 탈퇴 트랜잭션 안에서 동기로 받아 열람 기록을 비식별화한다 —
해당 회원의 `MEMBER` 열람 이벤트 `viewer_key`를 NULL로 바꾸고 `MEMBER` dedup 행을 지운다. 이벤트 행 수와
`view_count`는 그대로다.

### ArtworkPermanentlyDeletedEvent 발행 (비동기)

영구 삭제 후 R2 파일 정리를 **트랜잭션 커밋 뒤에** 비동기로 처리한다. 롤백되면 파일을 지우지 않는다.

```
permanentlyDeleteArtworks() / TrashPurgeScheduler
  → ArtworkPurger.purge()  (호출자 트랜잭션 필수)
      → artworkRepository.deleteAll()
      → publishEvent(ArtworkPermanentlyDeletedEvent)  — key: 이미지 4종 + 지정 썸네일(자료 첨부는 제외, #190)

onPermanentlyDeleted() [@Async, @TransactionalEventListener(AFTER_COMMIT)]
  → 스냅샷 보존 key 제외 후 mediaService.deleteFiles()
  → 실패 시 mediaService.markOrphaned()
  → mediaService.deleteAssetsForOwner(handledKeys)  (위에서 처리하지 않은 행의 key만 고아 큐로)
```

---

## 7. 스케줄러

### ImageRetryScheduler (media 모듈, 5분마다)

10분 넘게 `PENDING`인 `media_assets`를 owner·화질별로 묶어 Worker를 다시 트리거한다. 작품 상태는 보지도
바꾸지도 않는다 — 결과는 콜백 → `MediaAssetProcessedEvent` → `ArtworkMediaEventListener`로 반영된다.

`FAILED` 이미지는 대상이 아니다(전량 실패한 작품은 FAILED로 끝나고 작가가 이미지를 교체한다). 재시도 횟수
상한은 두지 않는다 — 콜백이 서버에 닿지 않을 때 유일한 자동 복구 수단이고, PENDING 잔량 알람이 장애를
드러내므로 의도된 동작이다.

### TrashPurgeScheduler (1시간마다)

휴지통으로 옮긴 지 보관 기간(기본 `P1Y`, `artwork.trash.retention`)이 지난 작품을 최대 100건씩 영구 삭제한다(#178).
보관 기간은 `Period`라 윤년을 끼어도 달력 기준 1년이고, 30일 미만으로 설정하면 앱이 기동하지 않는다.
사용자 영구 삭제와 같은 `ArtworkPurger`를 거치므로 스냅샷 보존·R2 정리가 똑같이 적용된다.
작품마다 별도 트랜잭션이라 한 건이 실패해도 나머지는 지우고, 실패한 작품은 하루 동안 조회에서 빼 뒤의 작품이
밀리지 않게 한다(건너뛴 수만큼 더 조회해 배치를 채운다. 기록은 메모리에만 두며 재기동 시 비워진다).

### HotScoreScheduler (매시 정각 UTC)

한 트랜잭션에서 `artwork_hot_scores`를 비우고 최근 168시간(`[now-168h, now)`) `artwork_view_events`를 작품별로 세어
다시 채운다. 멱등이라 다중 인스턴스에서 동시에 돌아도 결과가 같다. `READ COMMITTED`로 돌려 원본 이벤트에 락을 걸지 않는다.

### ViewRetentionScheduler (매일 03:30 UTC)

1년(UTC 달력 기준)이 지난 **익명** 열람 이벤트를 `artwork_view_daily_stats`(작품·UTC 날짜·열람자 유형·열람 종류)에
UPSERT로 더한 뒤 원본을 지우고, 같은 기준으로 익명 dedup 행을 지운다. 한 트랜잭션이라 재실행해도 이중 합산이 없다.
회원 기록은 대상이 아니다.

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

### 작품 열람 집계 테이블 (V44, MariaDB)

| 테이블 | 키·인덱스 | 용도 |
|---|---|---|
| `artwork_view_dedup` | PK `(artwork_id, viewer_type, viewer_key)`, `idx_avd_viewer (viewer_type, viewer_key)`, `idx_avd_retention (viewer_type, last_counted_at)` | 24시간 dedup 판정, 탈퇴·보관 기간 삭제 |
| `artwork_view_events` | PK `id`, `idx_avev_viewed_at (viewed_at, artwork_id)`, `idx_avev_viewer (viewer_type, viewer_key)` | 유효 열람 원천, 168시간 집계·1년 보관 범위, 탈퇴 비식별화 |
| `artwork_hot_scores` | PK `artwork_id` | 매시간 재계산되는 기간 조회수 |
| `artwork_view_daily_stats` | PK `(artwork_id, stat_date, viewer_type, view_kind)` | 1년 경과 익명 이벤트의 일별 합계 |

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
| `INVALID_ANONYMOUS_ID` | 400 | 비로그인 열람 기록의 `X-Anonymous-Id`가 UUID 형식이 아님 |
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
- 휴지통 이동 시 강제 PRIVATE으로 변경. 복구 시 공개 범위는 이전 값으로 복원한다.
- 복구 시 **작품 상태는 삭제 전 값이 아니라 이미지 현황으로 다시 계산한다**(이슈 #146). 휴지통에 있는
  동안에도 Worker 콜백이 이미지 행을 갱신하므로 삭제 시점 스냅샷은 낡은 값이 될 수 있다. 예전에는
  무조건 READY라 이미지가 아직 없거나 전량 실패한 작품이 공개 상태로 살아났다.
- 탈퇴 이벤트 수신 시 모든 작품 강제 PRIVATE (`forcePrivate()`, 상태 무관).
- `changeVisibility()`는 `DELETED`만 거부한다(PROCESSING·FAILED에서도 변경 가능). `forcePrivate()`는 상태 무관.

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
