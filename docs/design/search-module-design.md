# search 모듈 설계

> 작성일: 2026-07-30
> 상태: 설계안 (Phase 1 구현 착수)
> 범위: **포트폴리오(artwork) 검색만** — 텍스트 검색 + 다중선택 chip 필터(작품 분야·창작 유형·연령대·담당 업무·장르·소재 대상), Elasticsearch 색인/동기화
> 범위 밖(추후 이관): 구인글/구직글/팀원모집글 검색 — 설계 당시 `recruit` 모듈이 아직 없어 `RecruitSearchPort` 스텁으로 대체. 기업 계정/프로필 검색 — 피그마 검색 화면에 해당 유형 없음
> 피그마 근거: UI개편_검색(5154:41768) — 태그 검색 필터 패널(node `5752:27908`) 실측 스크린샷 기준
> **2026-08-01 갱신**: `RecruitSearchPort`/`NoopRecruitSearchPort` 스텁은 폐기됐다. `SearchServiceImpl`이
> recruit 모듈의 `RecruitService`를 직접 호출해 포트폴리오와 recruit 3종을 통합 커서로 병합 검색한다
> (PR #41, `docs/design/recruit-module-design.md` §2.7). 이 문서의 "스텁" 서술은 설계 당시 시점 기록으로
> 남겨두되, 현재 상태는 위 갱신 노트를 기준으로 본다.

---

## 0. 설계 요약 (TL;DR)

| 항목 | 결정 |
|------|------|
| 검색 엔진 | Elasticsearch, `spring-boot-starter-data-elasticsearch` (Spring Data Elasticsearch) |
| 원본 데이터 | MongoDB `artworks` 컬렉션 그대로 — search 모듈은 조회 전용 색인만 소유, 원본 저장소는 바꾸지 않음 |
| 색인 동기화 | 도메인 이벤트(`ArtworkChangedEvent`) 기반 upsert/remove + 관리자용 전체 재색인 배치 |
| 모듈 경계 | community 모듈과 동일한 패턴 — search는 `ArtworkService`(공개 인터페이스)만 의존, artwork 내부 저장소 직접 접근 금지 |
| recruit 3종 검색 | **설계만 완료, 구현 보류** — `RecruitSearchPort` 인터페이스만 정의, `NoopRecruitSearchPort`가 항상 빈 결과 반환 (community의 `RecruitFeedPort` 선례 그대로 적용) |
| 필터 다중선택 의미 | 축 내부 OR(terms), 축 간 AND(bool filter) — 피그마 "모든 chip은 복수선택 가능" 규칙 |
| 최초 진입 규칙 | 검색어·필터 모두 비어 있으면 결과를 반환하지 않음(빈 결과) — 전체 목록 노출 안 함 |
| 병렬 작업 충돌 | recruit 모듈, 기업 프로필 모듈과 별도 워크트리(`worktree-search-module`)에서 독립 진행. artwork 모듈은 이벤트 발행 지점 추가만(기존 로직 미변경) |

---

## 1. 배경 및 스코프 결정

### 1.1 왜 Elasticsearch인가

Figma `UI개편_검색`(5154:41768)을 실측한 결과, 검색 화면은 텍스트 검색창 하나와 **7개 축의 다중선택 chip 필터**(게시글 유형·작품 분야·창작 유형·연령대·담당 업무·장르·소재 대상)로 구성된다. 담당 업무 22종, 장르 29종 등 축마다 선택지가 많고 모두 다중선택이 가능해 필터 조합의 폭이 넓다. 여기에 텍스트 관련도 검색(제목·설명·작가명)까지 결합해야 하므로, MongoDB 텍스트 인덱스만으로는 다축 필터 조합과 관련도 정렬을 동시에 만족시키기 어렵다. `artwork-module-design.md`에서도 검색은 "MongoDB text index 또는 Elasticsearch — 별도 설계"로 유보해두었던 부분이다.

### 1.2 검색 대상 4종 중 3종이 아직 없다

피그마 "게시글 유형" 필터는 **포트폴리오 / 구인글 / 구직글 / 팀원모집글** 4개 값을 갖는다(작가 프로필은 포함되지 않음 — 작가 찾아보기는 community 모듈의 별도 기능이며 이번 검색 스코프가 아니다). 이 중 구인글/구직글/팀원모집글은 recruit 모듈 소유 데이터인데, recruit 모듈은 현재 다른 워크트리에서 병렬 개발 중이라 at-crew-backend 메인 트리에는 아직 엔티티가 없다.

community 모듈이 동일한 상황(§1.3, `community-module-design.md`)에서 택한 해법을 그대로 따른다: `RecruitSearchPort` 인터페이스만 정의해 search 모듈이 의존하고, recruit 모듈이 없는 동안은 `NoopRecruitSearchPort`가 항상 빈 결과를 반환한다. recruit 모듈이 완성되면 이 포트의 실제 구현체로 교체한다.

### 1.3 Figma 필터 스펙 ↔ 기존 enum 대조

| Figma 라벨 | 검색 파라미터 | 기존 타입 | 비고 |
|---|---|---|---|
| 게시글 유형 | `postTypes` | 신규 `PostType` | PORTFOLIO / JOB_POSTING / JOB_SEEKING / TEAM_RECRUIT |
| 작품 분야 | `artworkFields` | `com.atcrew.artwork.ArtworkField` | ⚠️ 아래 §1.4 참고 |
| 창작 유형 | `creativeTypes` | `com.atcrew.artwork.CreativeType` | 1차창작/2차창작/팬아트/OC/커미션 5개 일치 |
| 연령대 | `ageRatings` | `com.atcrew.artwork.AgeRating` | 전체연령가/R18/G18 3개 일치 |
| 담당 업무 | `roles` | `com.atcrew.artwork.ArtworkRole` | 22개 일치(직접입력→`ETC`) |
| 장르 | `genres` | `Artwork.genres`(`List<String>`, 자유입력) | Figma는 고정 29종 + 직접입력 — 아래 §1.4 참고 |
| 소재 대상 | `materialTargets` | `Material.targets`(`List<String>`) | Figma 7종: 무기/배경/장신구/컷꾸미기/효과/식자/인물 |

### 1.4 확인 필요 사항 (구현 착수 전 확정 대상)

1. **`ArtworkField` 불일치** — Figma는 `일러스트 / 웹툰 / 애니메이션 / 웹소설 / 기타`인데 현재 enum은 `ILLUSTRATION, WEBTOON, PRINT_COMIC, ANIMATION, ETC`다. Figma의 **웹소설** 자리에 코드는 **PRINT_COMIC(출판만화)**가 있다. artwork 모듈 자체의 기존 enum 문제이므로 search 모듈에서 임의로 값을 바꾸지 않는다 — artwork 모듈 담당 트랙과 별도 확인 필요.
2. **태그 목록 갱신** — 필터 패널에 "담당 업무/장르/소재 대상 태그 리스트 모두 변경되었습니다. 기존 노션 페이지에 변경된 내용으로 교체 해놨습니다" 주석이 있다. 정본 목록은 Notion(`https://www.notion.so/sehandev/30033d366244807bb241cb3298d6a960`)에 있으며, 확정 전까지는 `genres`/`materialTargets`를 자유 문자열 keyword 필드로 색인해 목록이 바뀌어도 재색인만으로 흡수되게 한다.

### 1.5 Figma에서 확정된 동작 규칙

- **최초 진입 시 결과 미노출**: "검색어 또는 필터가 적용되기 전에는 검색 결과 목록을 노출하지 않는다" → `q`와 모든 필터가 비어 있으면 API는 빈 결과를 반환한다(전체 목록 반환 아님).
- **결과 건수 표시**: 결과 없음 안내가 "검색 결과 수 영역"을 기준으로 배치된다 → 응답에 총 건수(`totalCount`)가 필요하다. 공용 `CursorPage`에는 이 필드가 없어 검색 모듈이 자체 응답 타입(`SearchPage`)을 갖는다(§4.2).
- **모든 chip은 다중선택 가능**, 최초 상태는 선택 없음.
- 선택된 태그는 하단에 개별 chip + "전체 해제" 버튼으로 노출(클라이언트 관심사, 백엔드 영향 없음).

---

## 2. 모듈 구조

`community` 모듈과 동일한 계층 규칙: 공개 API는 루트 패키지, 구현은 `internal/*`.

```
src/main/java/com/atcrew/search/
├── SearchService.java              # 공개 인터페이스
├── SearchQuery.java                # 검색 조건 command (record)
├── PostType.java                   # PORTFOLIO / JOB_POSTING / JOB_SEEKING / TEAM_RECRUIT
├── SearchSort.java                 # RELEVANCE / LATEST
├── SearchPage.java                 # items + nextCursor + hasNext + totalCount
├── SearchResultItem.java           # 카드 표현용 통합 결과 항목
├── RecruitSearchPort.java          # recruit 모듈용 스텁 포트 (공개)
└── internal/
    ├── domain/ArtworkSearchDocument.java       # @Document(indexName = "artworks")
    ├── application/
    │   ├── SearchServiceImpl.java
    │   ├── ArtworkSearchIndexer.java           # 이벤트 리스너 → upsert/remove
    │   ├── ArtworkSearchMapper.java             # ArtworkInfo → ArtworkSearchDocument
    │   ├── SearchIndexInitializer.java          # 인덱스/alias 부트스트랩
    │   ├── ArtworkReindexService.java           # 전체 재색인
    │   └── NoopRecruitSearchPort.java            # @Component, 항상 빈 결과
    ├── persistence/
    │   └── ArtworkSearchQueryRepository.java    # ElasticsearchOperations + NativeQuery
    ├── web/
    │   ├── SearchController.java
    │   └── SearchAdminController.java           # 재색인 트리거 (내부 인증)
    └── exception/
        ├── SearchErrorCode.java                 # ArtworkErrorCode 패턴 준수
        └── SearchException.java
```

**`SearchPage`를 검색 모듈이 자체 소유하는 이유**: 공용 `common/response/CursorPage.java`에 `totalCount`를 추가하면 병렬 진행 중인 recruit/기업 프로필 브랜치와 공용 파일 충돌 표면이 늘어난다. 총 건수가 필요한 곳이 검색뿐이므로 검색 모듈 내부에 별도 타입으로 둔다.

---

## 3. Elasticsearch 인덱스 설계

**인덱스 명명**: 실제 인덱스 `artworks_v1` + alias `artworks`. 재색인 시 `artworks_v2`를 새로 만들고 alias를 원자적으로 전환해 무중단 재색인을 지원한다.

`ArtworkSearchDocument` 필드:

| 필드 | ES 타입 | 용도 |
|---|---|---|
| `id` | keyword | 문서 ID = artworkId |
| `title` | text(+`.keyword`) | 관련도 검색(가중치 높음) |
| `description` | text | 관련도 검색 |
| `tags` | keyword | 태그 검색 + 필터 |
| `authorName`, `authorHandle` | text + keyword | 작가명 검색 |
| `authorId` | keyword | 필터 |
| `artworkField`, `creativeType`, `ageRating` | keyword | 단일값 필터 |
| `roles`, `genres`, `materialTargets` | keyword(배열) | 다중값 필터 |
| `thumbKey`, `thumbAdultKey` | keyword(`index: false`) | 카드 렌더링용, 검색 대상 아님 |
| `createdAt`, `updatedAt` | date | 정렬/커서 |

- **한국어 분석기**: 관련도 검색 품질을 높이려면 `nori` 플러그인이 필요하지만, 현재 필터·태그 위주 검색이므로 Phase 1은 `standard` 분석기로 시작하고 nori 도입은 별도 후속 과제로 둔다.
- **색인 대상 조건**: `status == READY && visibility == PUBLIC`인 작품만 색인한다. 그 외 상태로 전이하면 인덱스에서 제거한다(비공개 전환·휴지통 이동·영구 삭제 모두 동일).

**정렬/커서**: ES `search_after`를 사용한다. 정렬 키는 `RELEVANCE`일 때 `[_score desc, createdAt desc, id asc]`, `LATEST`일 때 `[createdAt desc, id asc]` — `id`를 tie-breaker로 넣어 동일 점수/시각에서도 커서가 안정적으로 동작하게 한다. 커서는 정렬 값 튜플을 base64로 인코딩한 불투명 문자열로 노출하며, 디코딩 실패 시 `SearchErrorCode.INVALID_CURSOR`(400)를 반환한다(`ArtworkErrorCode.INVALID_CURSOR` 선례를 그대로 따름).

---

## 4. 검색 API 계약

### 4.1 조회

```
GET /api/search
  q               (optional) 검색어
  postTypes       (optional, 다중) PORTFOLIO,JOB_POSTING,JOB_SEEKING,TEAM_RECRUIT
  artworkFields   (optional, 다중)
  creativeTypes   (optional, 다중)
  ageRatings      (optional, 다중)
  roles           (optional, 다중)
  genres          (optional, 다중)
  materialTargets (optional, 다중)
  sort            (optional) RELEVANCE | LATEST  — 기본: q 있으면 RELEVANCE, 없으면 LATEST
  cursor          (optional)
  size            (optional) 기본 20, 최대 50 — CommunityController.resolveSize 규칙과 동일
```

- 인증 불필요(`CommunityController` 피드 API와 동일 정책). `SecurityConfig`에 공개 경로로 등록한다.
- `q`와 모든 필터가 비어 있으면 `SearchPage.empty()`를 반환한다(§1.5).
- 필터 다중선택 의미: 축 내부는 OR(terms 쿼리), 축 간에는 AND(bool filter).
- `postTypes`에 recruit 소유 유형만 지정되면 `RecruitSearchPort`로 위임 → Phase 1에서는 빈 결과. Swagger 설명에 "recruit 모듈 미구현으로 현재 빈 목록"을 명시한다(`CommunityController` 구인글 탭 서술 선례).

### 4.2 관리자/내부

```
POST /internal/search/reindex
```
`artwork.internal.secret` 방식의 내부 인증 헤더 패턴을 재사용한다. 전체 재색인을 트리거한다(§5.3).

### 4.3 성인물 게이팅

현재 `ArtworkService.getCommunityArtworks`는 `ageRating`을 필터로만 쓰고 게이팅하지 않는다(본인/기업 인증 시스템이 로드맵 1순위로 아직 미구현). 검색도 **동일 동작을 유지**하며, 인증 시스템 완료 후 게이팅이 필요한 모든 모듈(artwork/community/search)에 일괄 적용할 기술 부채로 `docs/roadmap.md` 1번 항목에 이미 기록되어 있다.

---

## 5. 색인 동기화

### 5.1 artwork 모듈 변경 (최소 침습)

MariaDB 전환(P2~P4)이 `ArtworkServiceImpl`의 영속성 계층을 재작성할 예정이므로, artwork 모듈 변경은 **추가만** 하고 기존 로직은 건드리지 않는다.

**신규 이벤트** (`com.atcrew.artwork` 공개 루트 — `ArtworkPermanentlyDeletedEvent`와 동일 위치):
```java
public record ArtworkChangedEvent(String artworkId) {}
```
단일 이벤트 타입으로 통일하고, 색인/제거 판단은 수신 측이 한다(페이로드를 얇게 유지해 artwork 모듈과의 결합을 최소화).

**`ArtworkService` 인터페이스 추가 메서드**:
- `Optional<ArtworkInfo> getArtworkForIndexing(String artworkId)` — 뷰어 권한 검사 없이 조회. 수신 측이 `status`/`visibility`를 보고 색인 여부를 판단한다.
- `CursorPage<ArtworkInfo> getArtworksForReindex(String cursor, int size)` — 전체 재색인용 순회.

`ArtworkInfo`는 이미 `artworkField`/`creativeType`/`roles`/`genres`/`tags`/`ageRating`/`visibility`/`status`/`materials`(→`targets`)/`authorName`/`authorHandle`을 모두 담고 있어 검색 전용 DTO를 새로 만들 필요가 없다.

**이벤트 발행 지점** (기존 `eventPublisher` 필드 재사용): `uploadArtwork` / `updateArtwork` / `updateVisibility` / `deleteArtwork` / `restoreArtworks` / `permanentlyDeleteArtworks` / `handleImageProcessedCallback`(PROCESSING→READY 전이 지점) — 각 지점에 `eventPublisher.publishEvent(new ArtworkChangedEvent(artworkId))` 한 줄씩 추가한다.

### 5.2 search 모듈 수신

```java
@ApplicationModuleListener   // @Async + @Transactional(REQUIRES_NEW) + 원본 트랜잭션 커밋 후 실행
void onArtworkChanged(ArtworkChangedEvent event) {
    artworkService.getArtworkForIndexing(event.artworkId())
        .filter(a -> a.status() == READY && a.visibility() == PUBLIC)
        .ifPresentOrElse(indexer::upsert, () -> indexer.remove(event.artworkId()));
}
```

- 재조회 기반이라 **멱등**하다 — 중복 이벤트·순서 뒤바뀜에 안전하다.
- 기존 `ArtworkEventListener`는 `@EventListener` + `@Async`를 쓰지만, 검색 색인은 원본 트랜잭션 커밋 이후에 조회해야 stale 데이터를 읽지 않으므로 `@ApplicationModuleListener`를 쓴다.
- ES 장애가 원본 트랜잭션을 롤백시켜서는 안 된다. 색인 실패는 로그만 남기고 전체 재색인으로 복구하는 정책으로 하며, Modulith 이벤트 퍼블리케이션 레지스트리(재시도 보장) 도입은 MariaDB 전환의 레지스트리 설정과 얽히므로 이번 범위에서는 제외한다.

### 5.3 전체 재색인

`ArtworkReindexService` — `getArtworksForReindex` 커서 순회 → 새 인덱스(`artworks_v2`)에 bulk 색인 → alias 원자적 전환 → 구 인덱스 삭제. `POST /internal/search/reindex`로 트리거하며, 최초 배포 시 백필(backfill) 용도로도 사용한다.

---

## 6. 인프라 변경

- **`build.gradle`**: `implementation 'org.springframework.boot:spring-boot-starter-data-elasticsearch'`, `testImplementation 'org.testcontainers:elasticsearch'`
- **`docker-compose.yml`**: `elasticsearch` 서비스 추가(single-node, 보안 비활성 — 로컬 개발 전용, mongodb/mariadb와 병행)
- **`application.yml`**: `spring.elasticsearch.uris: ${ELASTICSEARCH_URIS:http://localhost:9200}`
- **`SharedContainersConfig`**: `ElasticsearchContainer` + `@ServiceConnection` 추가(mongo/mariadb와 동일 패턴)
- **`SecurityConfig`**: `/api/search/**` 공개 경로 추가

> `build.gradle`/`docker-compose.yml`/`application.yml`/`SharedContainersConfig`는 병렬 브랜치(recruit, 기업 프로필, MariaDB 전환)도 건드릴 수 있는 공용 파일이다. 모두 **추가만** 하므로 머지 충돌이 나더라도 기계적으로 해소 가능하다.

---

## 7. 테스트 전략

`docs/testing/rest-docs-guide.md`의 계층 구조를 따른다.

| 테스트 | 위치 | 내용 |
|---|---|---|
| 매퍼 단위 | `search/internal/application/ArtworkSearchMapperTest.java` | `ArtworkInfo` → 문서 변환, `materials[].targets` 평탄화 |
| 커서 인코딩 단위 | `search/internal/application/SearchCursorTest.java` | 인코딩/디코딩 왕복, 잘못된 커서 → 예외 |
| 컨트롤러 검증 | `search/internal/web/SearchControllerValidationTest.java` | size 상한, 잘못된 enum 값, 빈 조건 → 빈 결과 |
| 모듈 통합 | `search/SearchModuleTests.java` | `@ApplicationModuleTest` + ES 컨테이너 — 색인→검색→필터 조합, 이벤트 수신 후 upsert/remove |
| REST Docs | `search/docs/SearchApiDocTest.java` | 검색 성공/결과 없음 문서화 |
| 모듈 경계 회귀 | 기존 `ModularStructureTests.java` | 신규 모듈이 경계 규칙을 위반하지 않는지 자동 검증 |

---

## 8. 병렬 작업 충돌 관리

| 대상 | 소유 | 이 모듈의 처리 |
|---|---|---|
| recruit 3종(구인글/구직글/팀원모집글) 검색 | recruit 워크트리 | `RecruitSearchPort` 스텁만 정의, 실제 구현 없음 |
| 기업 계정 검색 | 기업 프로필 워크트리 | 범위 외(피그마 검색 화면에 기업/작가 유형 없음) |
| `ArtworkServiceImpl` | MariaDB 전환(main) | 이벤트 발행 1줄씩 추가만 — 영속성 로직 미변경 |
| 공용 설정 파일 | 공유 | 추가만(§6) |

recruit 모듈 완성 후 `NoopRecruitSearchPort`를 실제 구현으로 교체하는 것이 후속 작업이다 — community의 `NoopRecruitFeedPort` → 실구현 전환과 동일한 이관 경로.

---

## 9. 후속 작업 (Figma 기준으로 확정 — 코드에 TODO로 남김)

Figma가 기획 기준이라는 원칙에 따라 아래 항목은 "확인 필요"가 아니라 "Figma를 따라 후속 반영해야 할 작업"으로 확정한다. 이번 Phase 1 범위에서는 구현하지 않고 코드에 TODO 주석만 남긴다.

1. `ArtworkField`의 `PRINT_COMIC` → Figma의 `웹소설(WEBNOVEL)`로 교체(또는 추가) — artwork 모듈 enum 변경 + 데이터 마이그레이션이 필요해 이번 브랜치 범위 밖. TODO: `ArtworkField.java`
2. Notion 태그 정본 목록(담당 업무/장르/소재 대상) — 확정되면 enum화 검토. 현재는 자유 문자열 keyword로 색인해 목록이 바뀌어도 재색인만으로 흡수됨. TODO: `SearchQuery.java`
3. nori 분석기 도입 여부 — Phase 1은 `standard`로 시작, 관련도 품질 개선이 필요해지면 후속 적용 (§3)
