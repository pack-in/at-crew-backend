# artwork 모듈 설계

> 작성일: 2026-06-15
> 상태: 설계안 (구현 전)
> 범위: 작품 업로드·조회·수정·삭제, 북마크 폴더 관리, 휴지통 복구
> 피그마 근거: UI개편_커뮤니티(4856:14126), UI개편_작품세부페이지(4927:1354), UI개편_작품 업로드(4979:871 § 5193:30333), UI개편_북마크/휴지통(5154:41399)

---

## 0. 설계 요약 (TL;DR)

| 항목 | 결정 |
|------|------|
| 모듈 경계 | 단일 `artwork` 모듈 — bookmark·trash는 내부 서브패키지, 별도 최상위 모듈 분리 안 함 |
| DB | MongoDB (기존 스택 통일) |
| 이미지 업로드 | Presigned URL(원본) → R2 직접 업로드 → Cloudflare Worker 비동기 avif 변환 |
| 성인물 블러 | Worker가 서버 사이드에서 blur 썸네일을 별도 생성 (클라이언트 우회 불가) |
| 이미지 뷰어 | `imageLayoutType` 필드로 가로 스와이프 / 세로 스크롤 작가가 업로드 시 선택 |
| member 의존 | `MemberService.findById(memberId)` 인터페이스만 사용, 직접 컬렉션 접근 금지 |
| 탈퇴 연동 | `MemberDeactivatedEvent` 구독 → 작품 전체 `visibility=PRIVATE` 처리 |
| 북마크 삭제/비공개 | DB 데이터 유지 (추후 공개 전환 대비), 조회 시 필터링 |
| 복구 정책 | 삭제 전 `visibility` 스냅샷 보존 → 복구 시 원래 공개 상태 복원 |

---

## 1. 모듈 분리 결정 근거

### 1.1 왜 bookmark·trash를 별도 모듈로 분리하지 않는가

피그마 북마크/휴지통 어노테이션에서 확인한 규칙:

```
"복구된 작품은 삭제되기 전 공개 상태와 폴더/정렬 정보를 유지한다"
```

이 규칙은 `Artwork.status` 변경과 `BookmarkEntry` 복원이 **같은 트랜잭션**에서 일어나야 함을 의미한다. bookmark와 trash를 별도 모듈로 분리하면 분산 트랜잭션 문제가 발생한다.

또한 bookmark는 `Artwork`를 직접 참조(artworkId)하므로 artwork 모듈에 의존이 필수다. 별도 모듈로 나눠도 `bookmark → artwork` 단방향 의존이 생길 뿐, 모듈 수만 늘어난다.

### 1.2 내부 구조 분리

외부에서 보이는 공개 인터페이스는 분리하되, 내부 패키지로 관심사를 구분한다.

```
artwork/                          ← public API
├── ArtworkService.java
├── BookmarkService.java
├── ArtworkInfo.java              ← 조회 결과 record
├── ArtworkSummaryInfo.java       ← 카드용 요약 record
├── BookmarkFolderInfo.java
├── BookmarkEntryInfo.java
└── internal/
    ├── domain/
    │   ├── artwork/
    │   │   ├── Artwork.java
    │   │   ├── ArtworkImage.java      ← embedded
    │   │   ├── Material.java          ← embedded (소재)
    │   │   ├── ArtworkStatus.java     ← PROCESSING / READY / DELETED
    │   │   ├── Visibility.java        ← PUBLIC / LINK_ONLY / PRIVATE
    │   │   ├── ImageLayoutType.java   ← VERTICAL_SCROLL / HORIZONTAL_SWIPE
    │   │   ├── AgeRating.java         ← ALL / ADULT
    │   │   ├── ArtworkField.java      ← 작품 분야 enum
    │   │   ├── CreativeType.java      ← 창작 유형 enum
    │   │   └── ArtworkRole.java       ← 담당 업무 enum
    │   └── bookmark/
    │       ├── BookmarkFolder.java
    │       └── BookmarkEntry.java
    ├── application/
    │   ├── ArtworkServiceImpl.java
    │   ├── BookmarkServiceImpl.java
    │   └── ArtworkEventListener.java  ← MemberDeactivatedEvent
    ├── infra/
    │   └── storage/
    │       └── ArtworkStoragePort.java    ← presigned URL 발급 인터페이스
    ├── persistence/
    │   ├── ArtworkRepository.java
    │   ├── BookmarkFolderRepository.java
    │   └── BookmarkEntryRepository.java
    └── web/
        ├── ArtworkController.java
        ├── BookmarkController.java
        └── dto/
```

---

## 2. 도메인 모델

### 2.1 Artwork

```java
@Document(collection = "artworks")
public class Artwork {

    @Id
    private String id;

    private String authorId;          // member 모듈 MemberId (직접 참조 금지)
    private String title;
    private String description;

    private List<ArtworkImage> images;
    private int representativeImageIndex;     // 대표 이미지 (0-based, 카드 썸네일)
    private ImageLayoutType imageLayoutType;  // 세로 스크롤 / 가로 스와이프

    // 분야 · 장르 · 유형 (피그마 업로드 5193:30333 기준)
    private ArtworkField artworkField;        // 작품 분야 (단일 선택)
    private CreativeType creativeType;        // 창작 유형 (단일 선택)
    private List<ArtworkRole> roles;          // 담당 업무 (다중 선택)
    private List<Genre> genres;               // 장르 (다중 선택, 정본 29종)
    private List<String> tags;                // 작품 태그 — 카드에 최대 7개 표시

    // 제작 정보
    private List<String> tools;              // 사용 툴
    private YearMonth workPeriodStart;       // 작업 시작 연월
    private YearMonth workPeriodEnd;         // 작업 종료 연월

    // 접근 제어
    private AgeRating ageRating;             // 전체 / 성인(R-18)
    private Visibility visibility;           // PUBLIC / LINK_ONLY / PRIVATE
    private Visibility visibilityBeforeDelete; // 복구 시 원상 복원용 스냅샷

    // 소재 정보 (피그마 소재 등록 섹션)
    private List<Material> materials;

    // 상태
    private ArtworkStatus status;            // PROCESSING / READY / DELETED
    private Instant deletedAt;

    @CreatedDate
    private Instant createdAt;
    @LastModifiedDate
    private Instant updatedAt;
}
```

### 2.2 ArtworkImage (Artwork 내 embedded)

```java
public class ArtworkImage {
    private String originalKey;        // R2 원본 key ("raw/uuid.jpg")

    // Worker 처리 완료 후 채워짐
    private String thumbKey;           // 썸네일 key — 3:4, 294px ("thumb/uuid.avif")
    private String thumbAdultKey;      // 성인물 blur 썸네일 key (ageRating=ADULT 시만)
    private String originalAvifKey;    // 원본 avif key ("processed/uuid.avif")

    private ImageProcessingStatus processingStatus; // PENDING / DONE / FAILED
}
```

**이미지 상태 전이:**
```
업로드 완료 → processingStatus = PENDING
Worker 변환 완료 → processingStatus = DONE, thumbKey/originalAvifKey 채워짐
Worker 실패 → processingStatus = FAILED (재시도 큐 진입)
```

`Artwork.status = READY` 조건: 모든 `ArtworkImage.processingStatus == DONE`

### 2.3 Material (Artwork 내 embedded)

```java
public class Material {
    private String name;              // 소재명
    private List<String> targets;     // 소재 대상 — 인물 / 배경 / 소품
    private List<String> attachmentKeys; // R2 참고 이미지 key
}
```

### 2.4 BookmarkFolder

```java
@Document(collection = "bookmarkFolders")
public class BookmarkFolder {

    @Id
    private String id;

    private String memberId;
    private String name;              // 최대 20자, 앞뒤 공백 제거, 동일 회원 내 중복 불가
    private int sortOrder;            // 폴더 정렬 순서

    @CreatedDate
    private Instant createdAt;
}
```

### 2.5 BookmarkEntry

```java
@Document(collection = "bookmarkEntries")
public class BookmarkEntry {

    @Id
    private String id;

    private String memberId;
    private String artworkId;
    private String folderId;          // null이면 기본 폴더
    private Instant savedAt;          // 정렬 기준 — 최신 저장순

    // 북마크 당시 artwork의 visibility 스냅샷
    // 용도: artwork가 삭제/비공개 처리됐다가 다시 공개될 때 재노출 판단
    // 실제 노출 여부는 조회 시 artwork.visibility 기준이므로 이 필드는 필요 없음
    // → 유지: 추후 "내가 저장했을 때 공개 작품이었는가" 이력 추적 대비
    private Visibility artworkVisibilityAtSave;
}
```

---

## 3. Enum 정의

### 3.1 피그마 직접 도출 (5193:30333)

```
ArtworkField   : ILLUSTRATION(일러스트) / WEBTOON(웹툰) /
                 PRINT_COMIC(출판만화) / ANIMATION(애니메이션) / ETC(기타)

CreativeType   : ORIGINAL(원작) / SECONDARY(2차창작) /
                 COMMERCIAL(상업) / PERSONAL(개인) / ETC(기타)

ArtworkRole    : SKETCH(선화) / ARTWORK(작화) / STORYBOARD(콘티) /
                 DIRECTION(연출) / COLORING(채색) / INKING(먹선) / ETC(기타)
                 — 다중 선택

ImageLayoutType: VERTICAL_SCROLL(세로 스크롤) / HORIZONTAL_SWIPE(가로 스와이프)

AgeRating      : ALL(전체) / ADULT(성인 R-18)

Visibility     : PUBLIC(전체 공개) / LINK_ONLY(링크 공개) / PRIVATE(비공개)

ArtworkStatus  : PROCESSING / READY / DELETED
```

---

## 4. 피그마 비즈니스 규칙

### 4.1 커뮤니티 작품 카드 (4856:14126)

| 규칙 | 내용 |
|------|------|
| 썸네일 비율 | 3:4 고정 (Worker가 크롭) |
| 태그 표시 | 최대 7개, 1줄, 카드 너비 초과 시 말줄임 |
| 성인물 태그 위치 | 태그 목록 맨 앞에 노출 |
| 성인물 이미지 | 항상 blur 처리 — 본인 인증 미완료 시 접근 불가 모달, 19세 이하 시 차단 모달 |
| 호버 (데스크톱) | 작품 제목 + 작가명 오버레이 (패딩 12px, 말줄임 적용) |

### 4.2 북마크 (5154:41399)

| 규칙 | 내용 |
|------|------|
| 카드 정렬 | `savedAt` 기준 최신 저장순 (최근 저장 → 맨 위 왼쪽) |
| 폴더명 | 필수, 최대 20자, 동일 회원 내 중복 불가, 앞뒤 공백 제거, 공백만 입력 불가 |
| 폴더 높이 | 폴더 목록 max-height 160px, 초과 시 세로 스크롤 |
| 삭제/비공개 작품 | 북마크 목록 미노출. **단 DB 데이터 삭제 금지** — 추후 공개 전환 시 자동 재노출 대비 |
| 폴더 이동 | 복수 선택 후 이동 가능 (전체 선택/해제 토글 포함) |

### 4.3 휴지통 (5154:41399)

| 규칙 | 내용 |
|------|------|
| 삭제 | 작품 선택 후 '삭제하기' → 확인 모달 → 영구 삭제 |
| 복구 | 선택 즉시 복구, 모달 없이 하단 알림창만 표시 |
| 복구 후 상태 | 삭제 전 `visibility` 원상 복원, 마이페이지 목록 재노출 |
| 미선택 버튼 클릭 | 알림창으로 안내 (모달 아님) |
| 전체 선택 | '전체 선택하기' ↔ '전체 해제하기' 버튼명 토글 |

---

## 5. 이미지 업로드 플로우

> **2026-08-03 이관 예정**: 이 섹션이 서술하는 Presigned URL 발급·Worker 트리거·webhook·재시도·고아파일
> 정리는 recruit도 필요로 하게 되어 범용 `media` 모듈로 추출하기로 결정했다(`docs/design/media-module-design.md`).
> artwork 도메인 로직(성인 blur 적용 여부, `Artwork.status` 전이, 대표 이미지 인덱스)은 그대로 남고,
> 인프라(§5.1~5.3, §6.7, §10.1~10.3)만 media로 옮겨간다. 아래 서술은 이관 전 원본 설계로 보존한다.

### 5.1 아키텍처 결정

**Presigned URL + Cloudflare Worker 비동기 avif 변환**을 사용한다.

선택 근거:
- 서버를 이미지 바이트가 통과하지 않아 메모리/CPU 부하 없음
- 성인물 blur를 서버(Worker) 사이드에서 처리 → 클라이언트 우회 불가
- 카드/상세 등 **사이즈가 고정** → On-the-fly 대비 비용 예측 쉬움
- Cloudflare R2 ↔ Worker는 같은 네트워크 내 통신 → 변환 속도 빠름

### 5.2 업로드 시퀀스

```
1. 클라이언트
   POST /api/artwork/images/presign
   { "count": 5, "contentTypes": ["image/jpeg", ...] }

2. 서버 (ArtworkController)
   R2 Presigned PUT URL 5개 생성 (만료 10분)
   응답: [{ "key": "raw/uuid1.jpg", "uploadUrl": "https://..." }, ...]

3. 클라이언트
   R2에 직접 PUT (원본 이미지, presigned URL 사용)
   서버 경유 없음

4. 클라이언트
   POST /api/artworks
   {
     "imageKeys": ["raw/uuid1.jpg", ...],
     "representativeImageIndex": 0,
     "imageLayoutType": "VERTICAL_SCROLL",
     "title": "...",
     "artworkField": "WEBTOON",
     "creativeType": "ORIGINAL",
     "roles": ["ARTWORK", "COLORING"],
     "genres": ["ROMANCE_FANTASY"],
     "tags": ["웹툰", "판타지", "연재중"],
     "ageRating": "ALL",
     "visibility": "PUBLIC",
     "tools": ["클립스튜디오"],
     "workPeriodStart": "2025-03",
     "workPeriodEnd": "2026-06",
     "materials": [],
     "description": "..."
   }

5. 서버
   Artwork 저장 (status=PROCESSING, 모든 image.processingStatus=PENDING)
   artworkId 반환

6. R2 Event Notification (또는 서버에서 Worker 직접 호출)
   → Cloudflare Worker 트리거 (imageKey 목록 전달)

7. Cloudflare Worker (이미지별 처리)
   원본 다운로드 (R2 private 버킷)
   → avif 변환 → processed/uuid.avif 저장
   → 3:4 크롭 + 294px 리사이즈 → thumb/uuid.avif 저장
   → ageRating=ADULT인 경우 blur(20) → thumb_adult/uuid.avif 저장
   → POST /internal/artwork/images/processed (서버 내부 webhook)

8. 서버 (ArtworkController 내부 엔드포인트)
   해당 ArtworkImage.processingStatus = DONE, key 업데이트
   모든 이미지 DONE → Artwork.status = READY

9. 클라이언트 (폴링 or SSE)
   GET /api/artworks/{artworkId}/status
   → READY 응답 시 상세 페이지로 이동
```

### 5.3 Presigned URL 보안 제약

R2 Presigned PUT URL 생성 시 아래 조건을 포함한다.

| 조건 | 값 | 이유 |
|------|-----|------|
| 만료 시간 | 10분 | 장시간 노출 방지 |
| Content-Type | 요청한 MIME만 허용 | 임의 파일 업로드 차단 |
| Content-Length-Range | 0 ~ 50MB | 대용량 업로드 제한 |
| 허용 MIME | image/jpeg, image/png, image/webp | PDF·실행파일 등 차단 |

---

## 6. API 설계

### 6.1 이미지 업로드

| 메서드 | 경로 | 설명 |
|--------|------|------|
| POST | `/api/artwork/images/presign` | Presigned URL 발급 |

```
POST /api/artwork/images/presign
Authorization: Bearer {accessToken}

요청
{
  "count": 5,
  "contentTypes": ["image/jpeg", "image/png", ...]
}

응답
{
  "data": [
    { "key": "raw/uuid1.jpg", "uploadUrl": "https://r2.cloudflare.com/..." },
    ...
  ]
}
```

### 6.2 작품 CRUD

| 메서드 | 경로 | 인증 | 설명 |
|--------|------|------|------|
| POST | `/api/artworks` | 필수 | 작품 업로드 |
| GET | `/api/artworks/{artworkId}` | 선택 | 작품 상세 조회 |
| PATCH | `/api/artworks/{artworkId}` | 필수 (본인) | 작품 수정 |
| PATCH | `/api/artworks/{artworkId}/visibility` | 필수 (본인) | 공개 상태 변경 |
| DELETE | `/api/artworks/{artworkId}` | 필수 (본인) | 작품 삭제 (휴지통 이동) |
| GET | `/api/artworks/{artworkId}/status` | 필수 (본인) | 처리 상태 폴링 |

**조회 시 뷰 분기:**
- 본인: 모든 visibility 조회 가능, Card Action Menu 포함
- 타인: `status=READY && visibility=PUBLIC`만 조회 가능
- 링크 공개(`LINK_ONLY`): 직접 URL 접근 시 조회 가능, 목록에는 미노출

### 6.3 커뮤니티 피드

| 메서드 | 경로 | 인증 | 설명 |
|--------|------|------|------|
| GET | `/api/community/artworks` | 선택 | 작품 목록 (페이지네이션) |

```
GET /api/community/artworks
  ?artworkField=WEBTOON
  &ageRating=ALL            // 미인증 사용자는 ALL만 조회 가능
  &cursor={lastId}          // 커서 기반 페이지네이션
  &size=20
```

조회 조건: `status=READY && visibility=PUBLIC`

### 6.4 마이페이지 작품

| 메서드 | 경로 | 인증 | 설명 |
|--------|------|------|------|
| GET | `/api/members/me/artworks` | 필수 | 내 작품 목록 (전체 visibility) |

### 6.5 휴지통

| 메서드 | 경로 | 인증 | 설명 |
|--------|------|------|------|
| GET | `/api/trash/artworks` | 필수 | 휴지통 목록 |
| POST | `/api/trash/artworks/restore` | 필수 | 작품 복구 |
| DELETE | `/api/trash/artworks` | 필수 | 영구 삭제 |

```
POST /api/trash/artworks/restore
{ "artworkIds": ["id1", "id2"] }

→ Artwork.status = READY
→ Artwork.visibility = visibilityBeforeDelete (스냅샷 복원)
→ deletedAt = null

DELETE /api/trash/artworks
{ "artworkIds": ["id1", "id2"] }

→ 영구 삭제 (R2 원본 파일 삭제 포함)
```

### 6.6 북마크

| 메서드 | 경로 | 인증 | 설명 |
|--------|------|------|------|
| GET | `/api/bookmarks/folders` | 필수 | 폴더 목록 |
| POST | `/api/bookmarks/folders` | 필수 | 폴더 생성 |
| DELETE | `/api/bookmarks/folders/{folderId}` | 필수 | 폴더 삭제 |
| GET | `/api/bookmarks` | 필수 | 북마크 목록 (폴더별) |
| POST | `/api/bookmarks` | 필수 | 북마크 저장 |
| DELETE | `/api/bookmarks/{artworkId}` | 필수 | 북마크 해제 |
| PATCH | `/api/bookmarks/move` | 필수 | 폴더 이동 |

```
GET /api/bookmarks?folderId={folderId}&cursor={cursor}&size=20

북마크 조회 시 필터링:
  artwork.status = READY
  && artwork.visibility = PUBLIC
  (삭제/비공개 작품은 미노출 — DB 데이터는 BookmarkEntry 유지)

PATCH /api/bookmarks/move
{ "artworkIds": ["id1", "id2"], "targetFolderId": "folderId" }
```

### 6.7 내부 Webhook (Cloudflare Worker → 서버)

> **2026-08-03 이관 예정**: `/internal/artwork/images/processed`는 `/internal/media/images/processed`로
> 이관되고 `artworkId` 필드는 `ownerType`+`ownerId`로 일반화된다 — `docs/design/media-module-design.md` §6.

| 메서드 | 경로 | 설명 |
|--------|------|------|
| POST | `/internal/artwork/images/processed` | Worker 처리 완료 콜백 |

```
POST /internal/artwork/images/processed
X-Internal-Secret: {shared-secret}    ← 외부 노출 차단용

{
  "artworkId": "...",
  "imageKey": "raw/uuid.jpg",
  "thumbKey": "thumb/uuid.avif",
  "thumbAdultKey": "thumb_adult/uuid.avif",  // ageRating=ADULT 시만
  "originalAvifKey": "processed/uuid.avif",
  "status": "DONE"                           // or "FAILED"
}
```

---

## 7. 모듈 경계

### 7.1 artwork → member

```java
// artwork 모듈은 MemberService 인터페이스만 사용
// 직접 MemberRepository 또는 members 컬렉션 접근 금지

MemberInfo author = memberService.findById(artwork.getAuthorId());
```

작가 프로필(이름, handle, 슬롯, 활동 분야 등)은 member 모듈에서 조회한다.
작품 상세 응답에 작가 정보가 필요한 경우 `MemberProfileInfo`를 조합한다.

### 7.2 member → artwork (이벤트)

```java
// MemberDeactivatedEvent (member 모듈 발행)
// ArtworkEventListener (artwork 모듈 구독)

@EventListener
public void onMemberDeactivated(MemberDeactivatedEvent event) {
    // 탈퇴 회원의 모든 작품 visibility = PRIVATE 처리
    artworkRepository.updateVisibilityByAuthorId(
        event.memberId(), Visibility.PRIVATE
    );
}
```

---

## 8. 상태 전이

### 8.1 Artwork.status

```
[업로드 완료]
     ↓
PROCESSING ──(모든 이미지 Worker 처리 완료)──▶ READY
                                                 ↓
                                           (삭제 요청)
                                                 ↓
                                             DELETED
                                                 ↓
                                           (복구 요청)
                                                 ↓
                                        READY (visibility 복원)
```

### 8.2 Artwork.visibility (READY 상태에서만 변경 가능)

```
PUBLIC ──▶ LINK_ONLY ──▶ PRIVATE
  ↑__________________________↑   (자유롭게 전환)

삭제 시: visibilityBeforeDelete = 현재 visibility 스냅샷
복구 시: visibility = visibilityBeforeDelete
```

---

## 9. 인덱스 설계

```javascript
// artworks 컬렉션
{ authorId: 1, status: 1 }           // 마이페이지 작품 목록
{ status: 1, visibility: 1, createdAt: -1 }  // 커뮤니티 피드 (커서 페이지네이션)
{ status: 1, artworkField: 1, visibility: 1 } // 분야별 필터링

// bookmarkEntries 컬렉션
{ memberId: 1, folderId: 1, savedAt: -1 }    // 폴더별 북마크 목록
{ memberId: 1, artworkId: 1 }  unique: true  // 중복 북마크 방지

// bookmarkFolders 컬렉션
{ memberId: 1, name: 1 }       unique: true  // 폴더명 중복 방지
{ memberId: 1, sortOrder: 1 }                // 폴더 정렬
```

---

## 10. 구현 결정 사항

> **2026-08-03 이관 예정**: §10.1(Worker 트리거)·§10.2(재시도)·§10.3(고아파일 정리)는 `media` 모듈로
> 이관된다. 재시도는 `Artwork.status` 간접 조회 대신 `MediaAsset` 테이블 직접 스캔으로 단순화됨 —
> `docs/design/media-module-design.md` §7.

### 10.1 Worker 트리거 방식 — 서버 → Worker 직접 호출 (@Async)

R2 Event Notification 대신 서버가 Worker URL을 직접 호출한다.

```java
// ArtworkServiceImpl.java
public ArtworkInfo uploadArtwork(String memberId, UploadArtworkCommand command) {
    Artwork artwork = ...; // DB 저장 (status=PROCESSING)
    artworkRepository.save(artwork);
    imageProcessingWorker.triggerAsync(artwork.getId(), command.imageKeys()); // @Async
    return toInfo(artwork);
}
```

R2 Event Notification은 Cloudflare Queue 설정이 선행되어야 해 복잡도가 높다.
서버 재시작 시 @Async 유실은 아래 10.2 스케줄러가 커버한다.

### 10.2 Worker 실패 재시도 — 서버 스케줄러

10분 이상 `PENDING` 상태인 이미지를 주기적으로 스캔해 Worker를 재호출한다.

```java
// ImageRetryScheduler.java
@Scheduled(fixedDelay = 300_000) // 5분마다
void retryStuckImages() {
    Instant threshold = Instant.now().minus(10, ChronoUnit.MINUTES);
    // processingStatus=PENDING && updatedAt < threshold 인 Artwork 조회
    // → Worker 재호출
}
```

단일 서버 인스턴스이므로 분산 락 불필요.

### 10.3 작품 수정 시 기존 R2 파일 정리 — Orphan 배치 정리

이미지 삭제·교체 시 제거된 key를 `orphanedImageKeys` 컬렉션에 적재하고,
스케줄러가 주기적으로 R2에서 실제 삭제 후 컬렉션에서 제거한다.

```
수정 API:
  DB 업데이트 (트랜잭션 내)
  → 제거된 imageKey 목록을 orphanedImageKeys 컬렉션에 insert

배치 스케줄러 (1시간마다):
  orphanedImageKeys 조회
  → R2 파일 삭제 (originalKey, thumbKey, thumbAdultKey, originalAvifKey)
  → 삭제 성공 시 orphanedImageKeys에서 제거
```

DB 커밋 이후에 R2 정리가 이뤄지므로 정합성이 안전하다.
R2 삭제 실패 시 다음 배치에서 재시도된다.

### 10.4 영구 삭제 R2 청소 — 이벤트 기반 비동기

DB 삭제 트랜잭션 커밋 후 `ArtworkPermanentlyDeletedEvent`를 발행한다.
리스너가 @Async로 R2 파일을 삭제한다.

```java
// ArtworkServiceImpl.java
public void permanentlyDelete(String memberId, List<String> artworkIds) {
    List<Artwork> artworks = ...; // 조회 + 권한 검증
    artworkRepository.deleteAll(artworks);
    artworks.forEach(a ->
        eventPublisher.publishEvent(new ArtworkPermanentlyDeletedEvent(a))
    );
}

// ArtworkEventListener.java
@Async
@EventListener
public void onPermanentlyDeleted(ArtworkPermanentlyDeletedEvent event) {
    // R2에서 모든 variant 파일 삭제
    // 실패 시 orphanedImageKeys에 적재 → 10.3 배치 스케줄러가 재처리
}
```

`MemberDeactivatedEvent` 처리와 동일한 패턴으로 일관성을 유지한다.

### 10.5 추후 별도 설계 (1차 구현 범위 외)

| 항목 | 선행 조건 |
|------|----------|
| 성인물 인증 연동 | member 모듈에 `adultVerificationStatus` 필드 추가 + PASS/KCB 외부 API 연동 |
| 검색 | 피그마 UI개편_검색(5154:41768) 별도 설계 — MongoDB text index 또는 Elasticsearch |
