# portfolio 모듈 설계

> 작성일: 2026-08-10 / 구현 완료: 2026-08-11
> 상태: **구현 완료** — `com.atcrew.portfolio` 모듈, Flyway V17(+V16 billing, V18 artwork). 실행 계획·작업 분할은
> `plans/260811-portfolio-module/`(PLAN.md·PLAN-AGENT.md·PLAN-HUMAN.md) 참고. §8에 실제 구현이 이 설계와
> 달라진 지점을 전부 정리했다 — 코드를 보기 전에 먼저 읽을 것.
> 범위: 작가 페이지 포트폴리오(기본) + 공유 포트폴리오(고정형/최신 반영형) 생성·수정·삭제·복제, 공유 링크 열람, 작품→포트폴리오 편입에 따른 접근 제어
> 범위 밖: 관리자 강제 차단 UI(컬럼만 예약), 포트폴리오 OG 카드 메타 렌더링(백엔드는 썸네일 후보만 제공), 작품 상단 고정(마이페이지_작가-R16, MVP 제외), Stripe 실연동(billing은 게이팅 조회 인터페이스만 — `billing-module-design.md` 참고)
> 정본 근거: `docs/AT-CREW_서비스기획서_전체_20260728.xlsx` REQ-010·REQ-011·REQ-018·REQ-020, 정책 마이페이지_작가-R37~R47·홈-R05·홈-R07·POL-001, Figma `UI개편_마이페이지_작가_수정페이지`(4971:25431)

---

## 0. 설계 요약 (TL;DR)

| 항목 | 결정 |
|---|---|
| 배경 | 지금까지 코드에서 "포트폴리오"는 Artwork(작품)를 가리키는 UI 용어였다(`PostType.PORTFOLIO`, `CommunityController` 주석). 기획서 REQ-010/011의 포트폴리오는 **작품과 별개인 독립 도메인**이며 이 문서가 그 최초 설계다 |
| 모듈 경계 | 신규 `com.atcrew.portfolio` 모듈. artwork 모듈에 얹지 않는다 — artwork은 이미 작품·북마크·휴지통 3개 애그리게잇으로 비대하고, portfolio는 결제(`billing`) 게이팅에 의존하는 별개 관심사 |
| 순환 의존 | `portfolio → artwork`는 필요(작품 검증·조회)하지만 `artwork → portfolio`(완전 비공개 판정)는 순환이 된다 → `artworks.portfolio_included` 비정규화 컬럼을 두고 portfolio가 같은 트랜잭션에서 동기 갱신하는 방식으로 끊는다(§4.1) |
| 유형 | 작가 페이지(`ARTIST_PAGE`, 회원당 1개, 전 플랜) / 공유(`SHARED`, 프로 전용) — 공유는 다시 최신 반영형(`LIVE`)과 고정형(`SNAPSHOT`)으로 나뉜다. 유형 전환 불가, 복제만 가능 |
| 스냅샷 저장 | 하이브리드 — 렌더 핵심 필드(제목·썸네일·연령등급 등)는 컬럼, 상세 본문은 `payload_json` 1컬럼(§2.3) |
| 공유 링크 | `/portfolio/{id}` — 작가 페이지는 `member.handle`, 공유는 `SecureRandom` 기반 22자 slug. 조회는 slug 우선 → 없으면 handle |
| 미해결 리스크 | 고정형 스냅샷이 원본 R2 이미지 키를 그대로 참조 — 원본 영구삭제 시 이미지가 깨짐(§5). media retention 확장 PR(별도)로 해소 예정, 이번 설계는 알려진 제약으로 명시 |

---

## 1. 모듈 분리 결정 근거

### 1.1 왜 artwork 확장이 아닌 신규 모듈인가

`ArtworkServiceImpl`은 이미 업로드·수정·삭제·휴지통·북마크까지 담당한다. 포트폴리오는 여기에 더해 (1) 프로 플랜 게이팅, (2) 공유 링크 발급·비로그인 열람, (3) 고정형 스냅샷 라이프사이클이라는 완전히 다른 관심사를 갖는다. artwork에 얹으면 결제 개념(`billing`)이 artwork 패키지 전체에 스며든다.

### 1.2 순환 의존 문제와 해소 — 이 설계의 핵심 결정

기획서 업로드-R09·마이페이지_작가-R04·홈-R05는 "피드 비공개 + 어느 포트폴리오에도 미포함(완전 비공개)인 작품만 제3자 URL 접근을 차단"한다고 명시한다. 즉 **artwork가 자신이 어느 포트폴리오에 포함됐는지 알아야** 접근 판정이 가능하다.

```
portfolio → artwork   (작품 존재·소유자·상태 검증, 스냅샷 원본 조회)    필요
artwork   → portfolio (완전 비공개 판정에 "포함 여부" 필요)            필요해 보이지만 순환
```

**해소책**: `artworks`에 `portfolio_included TINYINT(1)` 비정규화 컬럼을 두고, `ArtworkService`에 공개 메서드를 하나 추가한다.

```java
// com.atcrew.artwork.ArtworkService (공개 인터페이스, portfolio 모듈이 호출)
void updatePortfolioInclusion(String artworkId, boolean included);
```

`Artwork.isVisibleTo()`(→ `accessFor()`로 개정, §4.1 artwork 문서 참조)는 이 컬럼만 보고 판정하며 `portfolio` 패키지를 전혀 import하지 않는다. 갱신 주체는 항상 `portfolio` 모듈이고, **같은 트랜잭션 안에서 동기 호출**하므로 이벤트 유실이나 순서 역전 문제가 없다.

라이브 멤버십(작가 페이지 + 최신 반영형)만 이 카운트에 반영한다. **고정형은 반영하지 않는다** — 고정형은 스냅샷이라 원본과 완전히 분리돼 있고(마이페이지_작가-R39 "원본의 수정·삭제·비공개 전환에 영향받지 않음"), 고정형에 넣었다고 원본의 접근 권한이 바뀌면 이 정책과 모순된다.

역방향(작품 삭제·비공개 전환 시 멤버십 정리)은 신규 이벤트를 만들지 않고 **기존 이벤트를 재사용**한다:

| 이벤트(기존) | 발행처 | portfolio의 처리 |
|---|---|---|
| `ArtworkChangedEvent` | artwork | 비공개 전환된 작품을 라이브 포트폴리오 커버·개수 계산에서 제외 |
| `ArtworkPermanentlyDeletedEvent` | artwork | `portfolio_items` 행 제거, 커버 재계산 |

정합성 안전망으로 `PortfolioMembershipReconcileScheduler`(`@Scheduled(fixedDelay = 6h)`)를 둬서 `portfolio_items`와 `artworks.portfolio_included`의 불일치를 주기적으로 보정한다(`AuthCleanupScheduler`와 동일 패턴).

### 1.3 최종 의존 그래프

```
portfolio → artwork, member, billing, media, common
artwork   → member, media, billing, common      (billing은 스타터 4개 제한 조회용, 신규)
billing   → member, notification, common
```

`billing`은 어느 도메인 모듈도 참조하지 않는다 — 다른 모듈이 `PlanService`를 단방향으로 조회한다.

### 1.4 명칭 주의 — 기존 "포트폴리오" 용어와의 충돌

`search.PostType.PORTFOLIO`, `CommunityController`의 "포트폴리오 탭" 주석은 전부 **Artwork를 가리키는 기존 UI 용어**이며 이 모듈과 무관하다. 이번 설계에서는 손대지 않는다 — 건드리면 diff가 search/community 전역으로 번진다. 대신 이 문서와 신규 모듈 Javadoc에 "search/community의 PORTFOLIO = 레거시 용어 = Artwork"라고 명시한다. `PostType.PORTFOLIO → ARTWORK` 리네이밍은 후속 과제로 로드맵에 등록한다(§6 D7).

---

## 2. 도메인 모델

### 2.1 Portfolio (테이블: `portfolios`)

```java
@Entity
@Table(name = "portfolios")
public class Portfolio implements Persistable<String> {

    @Id
    private String id;                       // UUIDv7

    private String ownerMemberId;

    @Enumerated(EnumType.STRING)
    private PortfolioKind kind;              // ARTIST_PAGE | SHARED

    @Enumerated(EnumType.STRING)
    private ReflectionType reflectionType;   // LIVE | SNAPSHOT (ARTIST_PAGE는 항상 LIVE)

    private String title;                    // ARTIST_PAGE는 null — 화면은 사용자 이름 헤더 사용

    private String shareSlug;                // SHARED만. SecureRandom 22자, 회원당 아닌 포트폴리오당 1개

    private String artistPageKey;            // ARTIST_PAGE면 "Y", SHARED면 null — 1인 1개 보장용 유니크 키

    private int itemCount;                   // 캐시(카드 "N개" 표기용, 열람 가능 작품 수 기준)

    private Instant snapshotAt;              // SNAPSHOT만
    private String snapshotOwnerName;        // 고정형 프로필 스냅샷(마이페이지_작가-R44)
    private String snapshotOwnerProfileJson;

    private Instant blockedAt;               // 탈퇴·운영 조치(POL-001) — null이면 정상

    @Version
    private Long version;

    private Instant createdAt;
    private Instant updatedAt;

    @Transient
    private boolean isNew;                   // 신규 엔티티 공통 패턴 (RefreshToken.java 참조)
}
```

### 2.2 PortfolioItem (테이블: `portfolio_items`) — LIVE·ARTIST_PAGE 전용

```
id (AUTO_INCREMENT), portfolio_id, artwork_id, ordinal
UNIQUE(portfolio_id, artwork_id), UNIQUE(portfolio_id, ordinal)
INDEX(artwork_id)   -- 작품 → 포함 포트폴리오 역조회(삭제 시 정리, 멤버십 재계산)
```

순서는 업로드순(오래된순) 고정이 MVP 규칙(마이페이지_작가-R16)이라 `artwork.createdAt` 정렬만으로도 충분해 보이지만, `ordinal`을 별도로 둔 이유는 (1) 마이페이지_작가-R38이 "고정형은 생성 시점 순서 고정"을 명시해 SNAPSHOT과 규칙을 통일해야 하고 (2) 로드맵에 이미 "추후 포트폴리오 내 작품 순서 조정 기능으로 대체 예정"이 명시돼 있어 확장 지점을 남겨야 하기 때문이다.

### 2.3 PortfolioItemSnapshot (테이블: `portfolio_item_snapshots`) — SNAPSHOT 전용

**저장 방식 검토** — 3안을 비교했다.

| 안 | 내용 | 판정 |
|---|---|---|
| (A) 완전 정규화 | Artwork 필드 전체 + images/materials 1:N까지 복제 | 테이블 4개, 스키마 진화 시 이중 관리 — 과함 |
| (B) 단일 JSON | `portfolios.snapshot_json` 한 컬럼 | 커버 썸네일 4장만 필요해도 매번 전체 JSON 파싱, 목록 API에서 N건 로드 — 부적합 |
| **(C) 하이브리드(채택)** | 렌더 핵심 필드는 컬럼, 상세 본문은 JSON 1컬럼 | 목록/커버는 컬럼만 읽어 빠르고, 상세는 JSON 1회 파싱. `artworks.video_links`가 이미 이 패턴(JSON 컬럼)을 씀 |

```
portfolio_item_snapshots
  id, portfolio_id, ordinal, source_artwork_id,
  title, thumb_key, thumb_adult_key, age_rating, artwork_field, source_created_at,   -- 컬럼(카드·커버용)
  payload_json                                                                       -- JSON(상세: images/materials/tags/tools/roles/genres/videoLinks/description)
  UNIQUE(portfolio_id, ordinal)
```

### 2.4 Enum

```java
public enum PortfolioKind { ARTIST_PAGE, SHARED }
public enum ReflectionType { LIVE, SNAPSHOT }
```

### 2.5 Flyway — V17

DDL 컨벤션은 `V1__baseline_schema.sql`을 따른다(ID `VARCHAR(36) CHARACTER SET latin1 COLLATE latin1_bin`, `ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4`, 인덱스명 `idx_<접두사>_<용도>`, FK 미사용).

```sql
CREATE TABLE portfolios (
    id                 VARCHAR(36) CHARACTER SET latin1 COLLATE latin1_bin NOT NULL,
    owner_member_id    VARCHAR(36) CHARACTER SET latin1 COLLATE latin1_bin NOT NULL,
    kind               VARCHAR(20)  NOT NULL,
    reflection_type    VARCHAR(20)  NOT NULL,
    title              VARCHAR(100) NULL,
    share_slug         VARCHAR(32) CHARACTER SET latin1 COLLATE latin1_bin NULL,
    artist_page_key    CHAR(1)      NULL,
    item_count         INT          NOT NULL DEFAULT 0,
    snapshot_at             DATETIME(6)  NULL,
    snapshot_owner_name     VARCHAR(50)  NULL,
    snapshot_owner_profile  JSON         NULL,
    blocked_at         DATETIME(6)  NULL,
    version            BIGINT       NOT NULL DEFAULT 0,
    created_at         DATETIME(6)  NOT NULL,
    updated_at         DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_pf_slug (share_slug),
    UNIQUE KEY uk_pf_owner_artist_page (owner_member_id, artist_page_key),
    KEY idx_pf_owner_created (owner_member_id, created_at DESC, id),
    KEY idx_pf_owner_updated (owner_member_id, updated_at DESC, id)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE portfolio_items (
    id           BIGINT AUTO_INCREMENT NOT NULL,
    portfolio_id VARCHAR(36) CHARACTER SET latin1 COLLATE latin1_bin NOT NULL,
    artwork_id   VARCHAR(36) CHARACTER SET latin1 COLLATE latin1_bin NOT NULL,
    ordinal      INT NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_pi_order (portfolio_id, ordinal),
    UNIQUE KEY uk_pi_pf_artwork (portfolio_id, artwork_id),
    KEY idx_pi_artwork (artwork_id)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE portfolio_item_snapshots (
    id                BIGINT AUTO_INCREMENT NOT NULL,
    portfolio_id      VARCHAR(36) CHARACTER SET latin1 COLLATE latin1_bin NOT NULL,
    ordinal           INT NOT NULL,
    source_artwork_id VARCHAR(36) CHARACTER SET latin1 COLLATE latin1_bin NOT NULL,
    title             VARCHAR(255) NULL,
    thumb_key         VARCHAR(500) NULL,
    thumb_adult_key   VARCHAR(500) NULL,
    age_rating        VARCHAR(30)  NULL,
    artwork_field     VARCHAR(30)  NULL,
    source_created_at DATETIME(6)  NULL,
    payload_json      JSON NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_pis_order (portfolio_id, ordinal),
    KEY idx_pis_portfolio (portfolio_id)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

**작가 페이지 포트폴리오는 가입 시 자동 생성하지 않는다.** 최초 조회·작품 추가 시 `getOrCreateArtistPage(memberId)`로 lazy 생성한다 — 기존 회원 backfill 마이그레이션이 불필요해진다.

### 2.6 공유 슬러그 생성

```java
byte[] buf = new byte[16];
SecureRandom.getInstanceStrong().nextBytes(buf);
String slug = Base64.getUrlEncoder().withoutPadding().encodeToString(buf);  // 22자
```

UUIDv7을 그대로 노출하지 않는다 — 시간 정보가 새고 열거 가능성이 생긴다. 링크를 아는 사람은 항상 접근 가능(마이페이지_작가-R39)한 설계이므로 추측 불가능성이 유일한 보호막이다.

**handle과의 네임스페이스 충돌**: `/portfolio/{식별자}`가 handle(정규식 `^[a-zA-Z0-9_-]{3,30}$`)과 slug(base64url 22자)를 한 경로에서 공유하므로 22자 handle이 실제로 존재할 수 있다. 생성 시 `MemberService.existsByHandle(slug)`(member 공개 API 신설)로 검사하고 충돌 시 3회 재시도(`MemberServiceImpl.register()`의 핸들 충돌 재시도 패턴과 동일). 조회는 **slug 우선 → 없으면 handle**로 해석하고 이 순서를 코드 주석으로 명시한다.

---

## 3. 공개 API (`com.atcrew.portfolio`, 모듈 밖에서 참조 가능)

```java
public interface PortfolioService {
    // artwork 모듈이 삭제/영구삭제 이벤트 처리 후 정합성 재계산에 사용하지는 않음(이벤트 기반) —
    // 다른 모듈에서 직접 호출할 일은 현재 없고, 확장 지점으로만 인터페이스를 공개한다.
}
```

portfolio는 현재 다른 모듈이 동기 호출할 필요가 없는 리프(leaf) 성격 모듈이다(공유 링크는 자체 컨트롤러가 처리). `ArtworkService.updatePortfolioInclusion(...)`을 **호출하는 쪽**이며 공개 인터페이스를 노출하는 쪽이 아니다.

---

## 4. REST API

| 메서드 | 경로 | 인증 | 요청/파라미터 | 응답 | 에러 |
|---|---|---|---|---|---|
| GET | `/api/portfolios/me` | 필요 | `kind?`, `reflectionType?`, `sort=OLDEST\|LATEST\|UPDATED`(기본 LATEST), `cursor`, `size` | `CursorPage<PortfolioSummaryInfo>` | 401 |
| GET | `/api/portfolios/selectable` | 필요 | — | `List<PortfolioSelectableInfo>`(작가 페이지 + LIVE만, 고정형 제외) | 401 |
| POST | `/api/portfolios` | 필요(프로) | `{title, reflectionType, artworkIds[]}` | 201 `PortfolioInfo` | 403 `PRO_PLAN_REQUIRED`, 400 `INVALID_PORTFOLIO_TITLE`, 404 `ARTWORK_NOT_FOUND` |
| GET | `/api/portfolios/{portfolioId}` | 필요(소유자) | — | `PortfolioInfo` | 403 `PORTFOLIO_ACCESS_DENIED`, 404 |
| PATCH | `/api/portfolios/{portfolioId}` | 필요(프로) | `{title?, artworkIds?}` | 200 | 403 `PRO_PLAN_REQUIRED`, 409 `SNAPSHOT_PORTFOLIO_IMMUTABLE`, 400 `ARTIST_PAGE_TITLE_IMMUTABLE` |
| DELETE | `/api/portfolios/{portfolioId}` | 필요 | — | 204 | 409 `ARTIST_PAGE_NOT_DELETABLE` |
| GET | `/api/portfolios/{portfolioId}/duplication-source` | 필요 | — | `{defaultTitle, selectedArtworkIds[], excludedCount}` | 403, 404 |
| POST | `/api/portfolios/{portfolioId}/artworks` | 필요(프로) | `{artworkIds[]}` | 204 | 403 `PRO_PLAN_REQUIRED`, 409 `SNAPSHOT_PORTFOLIO_IMMUTABLE` |
| DELETE | `/api/portfolios/{portfolioId}/artworks/{artworkId}` | 필요 | — | 204 | 동일 |
| GET | `/api/portfolios/shared/{identifier}` | **불필요** | — | `SharedPortfolioInfo` | 404 `PORTFOLIO_NOT_FOUND`, 410 `PORTFOLIO_BLOCKED` |
| GET | `/api/portfolios/shared/{identifier}/artworks` | **불필요** | `cursor`, `size` | `CursorPage<PortfolioArtworkCardInfo>` | 동일 |

복제는 별도 생성 API 없이 `duplication-source`로 기본값(기본 제목·자동 선택 작품·제외 개수)만 받고, 실제 생성은 `POST /api/portfolios`를 재호출한다. 마이페이지_작가-R41("유형은 상속되지 않고 매번 새로 선택")과 정확히 맞고 생성 검증 로직을 한 곳에 유지할 수 있다.

**`SecurityConfig` 반영 사항**:
- `permitAll` 추가: `GET /api/portfolios/shared/{identifier}`, `GET /api/portfolios/shared/{identifier}/artworks`
- **경로 매처 순서 주의**: `/api/portfolios/me`, `/api/portfolios/selectable` 같은 리터럴 경로를 `/api/portfolios/{portfolioId}` 템플릿보다 먼저 선언해야 한다. `SecurityConfig`에 recruit `/trash`·`/me` 관련 동일 함정 주석이 이미 있다.
- 검색엔진 색인 제외(마이페이지_작가-R42)는 서버가 `GET /api/portfolios/shared/*` 응답에 `X-Robots-Tag: noindex` 헤더를 붙여 보조한다. 실제 페이지 `<meta name="robots">`는 프론트 SSR 담당.

에러 코드(`PortfolioErrorCode`, `portfolio/internal/exception/`):
```
PORTFOLIO_NOT_FOUND(404), PORTFOLIO_ACCESS_DENIED(403), PORTFOLIO_BLOCKED(410),
SNAPSHOT_PORTFOLIO_IMMUTABLE(409), ARTIST_PAGE_NOT_DELETABLE(409),
ARTIST_PAGE_TITLE_IMMUTABLE(400), INVALID_PORTFOLIO_TITLE(400),
PORTFOLIO_ITEM_LIMIT_EXCEEDED(400), SLUG_GENERATION_FAILED(500), INVALID_CURSOR(400)
```

---

## 5. 핵심 로직

### 5.1 생성

```
1. reflectionType == SNAPSHOT 이면 planService.assertPro(memberId)
   (LIVE/ARTIST_PAGE는 전 플랜 가능 — 단, POST /api/portfolios로 SHARED-LIVE를 만드는 것도 REQ-010상 프로 전용이므로
    kind == SHARED 전체에 assertPro 적용. ARTIST_PAGE는 별도 생성 API가 없다 — lazy 생성만 존재)
2. artworkService.getArtworksForSnapshot(memberId, artworkIds)  -- 신규 배치 조회, 소유자 아님/DELETED는 예외
3. reflectionType == LIVE  → portfolio_items 저장 + artworkService.updatePortfolioInclusion(각 id, true)
   reflectionType == SNAPSHOT → memberService 프로필 조회 후 portfolio_item_snapshots 배치 INSERT
     (ordinal = artwork.createdAt 오름차순), updatePortfolioInclusion 호출 안 함(§1.2)
4. 응답에 shareSlug 포함 — 프론트는 이 값으로 즉시 공유 URL 구성
```

### 5.2 고정형(SNAPSHOT) 불변성

수정 API(`PATCH`)는 SNAPSHOT에 대해 항상 409 `SNAPSHOT_PORTFOLIO_IMMUTABLE`을 반환한다. 무효화(콘텐츠 접근 차단)는 원칙적으로 없고 예외 2가지만 존재한다:

- **탈퇴** — `MemberDeactivatedEvent` 구독 → `portfolios.blocked_at = now`. 이벤트 유실 대비, 조회 시점에도 `memberService.isActive(ownerId)`(신규 공개 메서드)로 이중 확인한다.
- **운영자 강제삭제/저작권 신고** — 관리자 Role 체계가 없어(로드맵 8번 미착수) 이번 범위에서는 `blocked_at` 컬럼과 판정 로직만 준비하고 API는 노출하지 않는다.

### 5.3 복제

```
GET /api/portfolios/{id}/duplication-source:
1. 원본 로드 + 소유자 검증
2. 후보 artworkId 추출
   - LIVE/ARTIST_PAGE → portfolio_items.artwork_id (ordinal 순)
   - SNAPSHOT        → portfolio_item_snapshots.source_artwork_id (ordinal 순)
3. artworkService.getArtworkStatesFor(memberId, candidateIds) 배치 조회
   (신규 공개 record: ArtworkStateInfo(id, status, visibility))
4. 제외 판정 = 존재하지 않음 OR status == DELETED OR visibility == PRIVATE   (마이페이지_작가-R41)
   ※ LINK_ONLY는 "비공개"가 아니므로 포함 유지
5. 응답 = { defaultTitle: "{원본 제목 또는 사용자 이름} 복사본", selectedArtworkIds: 남은 ID들, excludedCount }
```

자동 선택 0개여도 진행 가능(R41 명시). 원본이 ARTIST_PAGE여도 복제 결과는 항상 SHARED다(작가 페이지는 회원당 1개로 강제됨). 원본 유형은 복제본에 상속되지 않고 매번 새로 선택한다.

### 5.4 완전 비공개 판정과의 연동

이 모듈이 `artworkService.updatePortfolioInclusion()`을 호출하는 시점:
- 포트폴리오 생성/작품 추가/작품 제거/삭제 후 → 영향받은 artworkId 집합에 대해 `portfolio_items`에서 라이브 멤버십 카운트를 재계산 → `count > 0` 여부를 배치 반영.
- **절대값 재계산**이라 멱등하다. 이벤트가 아니라 같은 트랜잭션 내 동기 호출이므로 순서 역전이 없다.

상세 판정 로직(`Artwork.accessFor()`)은 `docs/design/artwork-module-summary.md`(포트폴리오 연동 반영본, 이번 스코프에서 갱신 예정)를 참조.

### 5.5 다운그레이드 시 동작 (요금제-R01)

| 대상 | 스타터 동작 |
|---|---|
| 기존 공유 포트폴리오 + 링크 | 유지 — `GET /shared/{id}`는 플랜을 보지 않음 |
| 생성/수정/작품 추가(SHARED) | 403 `PRO_PLAN_REQUIRED` |
| 삭제(SHARED) | **허용** — 요금제-R01 "더보기 메뉴는 [삭제하기]만 노출"에 근거. 이 부분이 사용자 스코프 지시("생성·수정·복제·삭제는 프로 전용")와 문면상 어긋나 §6 R2로 별도 확인 필요, 이 문서는 정본(기획서) 우선으로 삭제 허용을 채택 |
| 작가 페이지 포트폴리오 수정/작품 추가 | 허용(전 플랜 제공) |
| `POST/PATCH /api/artworks`의 `portfolioIds` | 작가 페이지 ID만 허용, SHARED ID 포함 시 403 |

### 5.6 R2 이미지 생명주기 — 알려진 제약

고정형 스냅샷(`portfolio_item_snapshots`)은 R2 키를 복사하지 않고 **원본 키를 그대로 참조**한다. 원본 작품을 영구 삭제하면 `ArtworkPermanentlyDeletedEvent` 리스너가 R2 파일을 지우므로, 정책상 불변이어야 할 스냅샷 이미지가 함께 깨진다.

검토한 해결책:
1. R2 server-side copy로 스냅샷 전용 경로에 복제 — 정책은 완벽히 지켜지나 작품 20장 × 4 variant까지 복사 비용이 크고 생성 API가 느려짐.
2. **`media` 모듈에 참조 카운트(retention) 개념 추가**(권장, 별도 PR) — `media`는 이미 artwork·portfolio 양쪽이 의존하는 하위 공용 모듈이라 순환이 없다. `MediaService.retain(holderType, holderId, keys)` / `release(...)`를 추가하고, `deleteFiles()`가 retention이 걸린 키는 건너뛰게 한다.
3. 이번 마일스톤은 **3번(알려진 제약으로 수용)**으로 출시하고, 2번을 후속 이슈로 등록한다. 릴리스 노트·인계 문서에 "고정형 포트폴리오가 참조하는 원본을 휴지통에서 영구 삭제하면 스냅샷 썸네일이 깨질 수 있음"을 명시한다.

---

## 6. 미확정 항목

| # | 항목 | 상세 |
|---|---|---|
| R2 | 다운그레이드 시 삭제 허용 여부 | **2026-08-12 사용자 확정: 허용(현재 구현 유지)** — 요금제-R01 문면("더보기 메뉴는 [삭제하기]만") 근거 채택. 코드 변경 없음 |
| R3 | 복제 시 PRIVATE 제외 vs 생성 시 PRIVATE 포함 | **2026-08-12 재조사 결과 재오픈** — 정책 시트 마이페이지_작가-R38("삭제되지 않은 모든 작품이 선택 대상, 피드 공개 여부 무관")을 근거로 "현재 구현 그대로(생성 시 PRIVATE 포함 허용)"로 사용자에게 확인받았으나, 이후 Figma 원본(node 6987:97606 "포트폴리오 생성하기" 하위 작품 선택 화면)을 직접 열어보니 캡션 레이어명이 **"포트폴리오에 포함할 작품을 골라주세요. 비공개 작품은 포함할 수 없어요"**로 정책 시트와 정반대다. 이 프로젝트는 Figma-정본 원칙([[feedback_figma_source_of_truth]])을 쓰므로 현재 구현(`resolveOwnedArtworks`가 PRIVATE 허용)이 틀렸을 가능성이 높다 — **재확인 및 수정 필요**(`docs/design/figma.md` "검증 중 확인된 이슈" 참고) |
| D3 | 고정형 스냅샷 R2 보존 | §5.6 — media retention 확장 PR을 이번 마일스톤에 포함할지 다음으로 미룰지 |
| D7 | `PostType.PORTFOLIO` 리네이밍 | search/community의 기존 "포트폴리오" 용어(=Artwork)와 신규 도메인명 충돌. 이번엔 유지, 후속 리네이밍 권장(§1.4) |

---

## 7. 카드 커버 썸네일 (구현 중 발견된 설계 누락분, PA-11로 보강)

**§4/§0 작성 당시 이 요구사항을 놓쳤다.** 마이페이지_작가-R39는 포트폴리오 카드 커버로 "열람 가능 작품 중
업로드일 오래된 4개 썸네일을 2x2 배치(3:4 비율, 크롭 없음), 4개 미만이면 빈 칸, 0개면 커버 전체 빈 상태"를
요구하는데, §4의 `PortfolioSummaryInfo`에는 이 필드가 없었다 — 목록 화면(`GET /api/portfolios/me`) 카드
UI를 만들 방법이 없는 상태로 설계가 나갔다.

구현(§8에서 상세):
- `PortfolioSummaryInfo.coverThumbnails: List<PortfolioCoverThumbnailInfo>`(최대 4개, `{thumbKey, thumbAdultKey}`)
- LIVE/ARTIST_PAGE는 `portfolio_items`에서 ordinal 오름차순 최대 4개 → 원본 작품 건당 조회(§8의 "배치 조회
  API 미도입" 방침과 동일). SNAPSHOT은 `portfolio_item_snapshots`의 스냅샷 컬럼을 그대로 사용(원본 재조회 없음).
- `PortfolioSelectableInfo`(선택 목록, 체크박스 UI)에는 커버가 필요 없어 추가하지 않았다.

---

## 8. 실제 구현이 이 설계와 달라진 지점 (2026-08-11, 오케스트레이션 구현 완료 후 정리)

이 절은 `plans/260811-portfolio-module/PLAN-AGENT.md`의 PA-01~PA-11 완료 보고를 종합한 것이다. 코드가
정본이고 아래는 "왜 그렇게 됐는지"를 추적하기 위한 기록이다.

### 8.1 스키마

- `portfolios.artist_page_key`는 설계 §2.5의 `CHAR(1)`이 아니라 **`VARCHAR(1)`**로 구현됐다 —
  `ddl-auto: validate`가 JDBC 타입 코드(CHAR≠VARCHAR)까지 검증해 CHAR로는 컨텍스트가 안 떴다.
- `PortfolioItemSnapshot.ageRating`/`artworkField`는 VARCHAR(30) 원문 대신 **artwork 공개 enum 타입**으로
  매핑했다(이미 존재하는 `portfolio → artwork` 의존 안에서 타입 안전성을 택함).
- `billing_webhook_events` 테이블(§2.3 참고, billing 설계 문서 소관)은 이번에 만들지 않았다 — Stripe
  웹훅 자체가 이번 마일스톤 범위 밖이라 소비 엔티티 없는 스키마를 미리 두지 않았다.

### 8.2 순환 의존 회피 — 예상대로 동작

§1.2의 `artworks.portfolio_included` 비정규화 컬럼 + `ArtworkService.updatePortfolioInclusion()` 동기
호출 방식은 설계 그대로 구현됐고 순환 없이 동작한다(`ModularStructureTests` 통과). 다만 artwork 쪽 접근
판정은 `isVisibleTo(String): boolean`이 아니라 **`accessFor(String): ArtworkAccess`**(ALLOWED/NOT_FOUND/
DELETED/PRIVATE) enum으로 다시 설계됐다 — 제3자의 삭제 작품 접근이 404→**410**, 완전 비공개 접근이
404→**403**으로 세분화되는 동작 변경이 생겼다(설계 의도대로이지만 프론트 계약 변경이므로 인계 시 명시 필요).

### 8.3 artwork 배치 조회 API — 설계에 있었지만 도입하지 않음

§5.1이 언급한 `getArtworkService.getArtworksForSnapshot(...)`, §5.3이 언급한 `getArtworkStatesFor(...)`
+ `ArtworkStateInfo` 배치 조회 API는 **만들지 않았다**. 대신 기존에 이미 있던 `ArtworkService
.getArtworkForIndexing(String)`(단건) 건당 호출을 스냅샷 생성·복제 후보 판정·커버 썸네일 조회까지
전부 일관되게 재사용했다 — `resolveOwnedArtworks`/`loadItemArtworks`가 이미 이 패턴이었고, artwork의
공개 API 표면을 불필요하게 넓히지 않는 쪽을 택했다. 포트폴리오당 작품 수가 작아(카드 커버는 최대
4개) N+1 부담은 이 코드베이스 다른 곳과 동일 수준으로 판단.

### 8.4 billing — Stripe 없이 게이팅만

§3(REST API)·§4.1~4.3(Checkout/Portal/웹훅)은 이번에 구현하지 않았다. `com.atcrew.billing`은
`PlanService`(getPlan/isPro/assertPro/artworkLimit)와 `subscriptions` 테이블만 갖고, 플랜 승급은 테스트·
운영 모두 **`SubscriptionRepository`에 행을 직접 만드는 방식**으로만 가능하다(Stripe 실연동은
`billing-module-design.md`가 다루는 후속 범위). `PlanInfo`에 `starter()` 정적 팩토리를 공개로 두려다,
구현체(`PlanServiceImpl`)만 접근하면 되므로 package-private 매퍼로 내렸다.

### 8.5 로그아웃·인증 관련 신규 메서드를 만들지 않음

§5.2가 언급한 `memberService.isActive(ownerId)`(신규 공개 메서드)는 **만들지 않았다**. 대신 기존
`MemberService.findById()`가 탈퇴 회원에 대해 이미 `MemberException(MEMBER_DEACTIVATED)`를 던지는 것을
그대로 이용해 "예외가 나면 비활성"으로 판정한다 — member 모듈의 공개 API 표면을 늘리지 않기 위함.
탈퇴 이벤트 유실 시 조회 시점에 즉시 차단(`blocked_at` 기록)하는 쓰기는 읽기 전용 트랜잭션 안에서 하면
직후 예외로 롤백돼 반영되지 않는다는 걸 구현 중 발견해, `PortfolioBlocker`라는 `REQUIRES_NEW` 전용
트랜잭션 컴포넌트로 분리했다(`LoginAttemptLimiter.recordFailure`와 같은 계열의 함정).

### 8.6 커서 — 설계보다 단순화

포트폴리오 자체 목록(`/me`)의 정렬 3종은 상위 실행 계획이 제안한 복합 커서(base64(sortValue:id))가
아니라, **기존 artwork 목록과 동일한 epochMilli 단일값 커서** 관례를 그대로 따랐다(정교한 tie-breaker를
새로 설계하지 않음 — 기존 코드베이스가 이미 그 수준으로 충분하다고 판단해온 곳이라 일관성을 우선함).
공유 포트폴리오의 작품 목록(`/shared/{id}/artworks`)은 정렬 기준이 `ordinal` 정수 하나뿐이라 그 값을
그대로 커서로 쓴다(base64 인코딩도 하지 않음 — 불투명하게 만들 이유가 없는 값).

### 8.7 Jackson — Spring Boot 4는 Jackson 3

고정형 스냅샷(`payload_json`, `snapshot_owner_profile`)을 JSON으로 직렬화할 때 `com.fasterxml.jackson
.databind.ObjectMapper`가 아니라 **`tools.jackson.databind.json.JsonMapper`**(Jackson 3, Spring Boot 4가
실제로 자동 구성하는 빈)를 써야 한다. 앞으로 이 리포지토리에서 신규 JSON 직렬화 코드를 작성할 때
공통적으로 부딪힐 함정이라 여기 기록해둔다. `@JdbcTypeCode(SqlTypes.JSON)`가 붙은 `String` 컬럼은 저장 시
Hibernate가 JSON을 정규화하므로, 원문 문자열과 재조회한 문자열이 바이트 단위로 같지 않을 수 있다 —
비교 검증은 반드시 파싱해서 하고 문자열 동등 비교로 하지 말 것.

### 8.8 §5.6 R2 이미지 생명주기 — 최종 확인

media retention(§5.6의 해결책 2번)은 이번 마일스톤에 포함하지 않았다. **3번(알려진 제약 수용)**으로
출시됐다 — 고정형 포트폴리오가 참조하는 원본을 휴지통에서 영구 삭제하면 스냅샷 썸네일이 깨질 수 있다.
