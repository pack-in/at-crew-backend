# PLAN-AGENT — portfolio 모듈 구현

근거 문서: `docs/design/portfolio-module-design.md`. 이 계획은 그 설계의 실행 순서다.
각 태스크는 완료 시 `./gradlew build`(compile + test)가 그린이어야 다음 태스크로 넘어간다.

## PA-01. billing 최소 기반 — PlanService

depends on: (없음)

`com.atcrew.billing` 모듈 신설. `Subscription` 엔티티(assigned UUIDv7 + `Persistable`)와
`subscriptions` 테이블(Flyway V16, `billing-module-design.md` §2.3 스키마 그대로, 단 Stripe 관련 컬럼은
schema에 유지하되 이번 태스크에서 채우지 않음 — 웹훅/Checkout은 범위 밖). `PlanService` 공개 인터페이스
(`getPlan/isPro/assertPro/artworkLimit`, row 없으면 STARTER 기본값). Stripe SDK·Checkout·Webhook은
이번 태스크 범위 밖 — 플랜 승급은 테스트에서 리포지토리로 직접 `Subscription` 행을 만들어 시뮬레이션한다.

- [x] Flyway V16 `subscriptions` 테이블 (`billing_webhook_events`는 웹훅 구현 시점으로 의도적으로 미룸)
- [x] `Subscription` 엔티티(Persistable) + 리포지토리
- [x] `PlanService`/`PlanServiceImpl`/`PlanInfo`/`Plan`/`SubscriptionStatus` enum/`BillingException`+`BillingErrorCode`
- [x] `BillingModuleTests` 5건 + `ModularStructureTests` 통과 — 단방향 의존(common만 참조) 확인됨

## PA-02. artwork 완전 비공개 판정 개정

depends on: (없음, PA-01·PA-03과 병렬 가능 — 서로 다른 파일을 건드림)

`docs/design/portfolio-module-design.md` §1.2, §5.4 및 상위 실행 계획 §4.1 그대로 구현.
`artworks.portfolio_included` 컬럼 추가(Flyway V18 — 이번 태스크는 이 컬럼만, sort 인덱스·4개 제한·
`portfolioIds` 업로드 연동은 범위 밖, 별도 후속 태스크). `Artwork.isVisibleTo(String)` boolean을
`Artwork.accessFor(String)` → `ArtworkAccess` enum(ALLOWED/NOT_FOUND/DELETED/PRIVATE)으로 개정하고
기존 호출부(`ArtworkServiceImpl`, `BookmarkServiceImpl`)를 전부 마이그레이션. `ArtworkService`에
공개 메서드 `void updatePortfolioInclusion(String artworkId, boolean included)` 추가 — portfolio 모듈이
같은 트랜잭션에서 호출한다.

- [x] Flyway V18 `artworks.portfolio_included`
- [x] `Artwork.accessFor()` 개정(신규 `ArtworkAccess` enum) + 기존 `isVisibleTo` 호출부 전부 교체 —
      단, 북마크 검사는 소유자의 DELETED/PROCESSING 작품까지 통과시키는 동작 변경을 막기 위해
      `status == READY` 조건을 의도적으로 남김(사유 코드 주석 처리)
- [x] `ArtworkService.updatePortfolioInclusion()` 공개 API + impl
- [x] 기존 `ArtworkModuleTests`/`BookmarkModuleTests` 등 회귀 통과 확인 — 기대값 변경 없음(기존 테스트가
      전부 소유자/PUBLIC 케이스였음). 제3자의 삭제 작품 접근이 404→410, 완전비공개 접근이 404→403으로
      바뀌는 동작 변경은 있음(설계 의도대로)

## PA-03. member 보조 API

depends on: (없음, PA-01과 병렬 가능)

`MemberService`에 `boolean existsByHandle(String handle)` 공개 메서드 추가(포트폴리오 공유 슬러그가
handle과 충돌하지 않는지 검사하는 용도, `portfolio-module-design.md` §2.6).

- [x] `MemberService.existsByHandle()` + impl + 리포지토리 메서드(이미 `uk_members_handle` 유니크 있음,
      단순 existsBy 추가) — 리포지토리 메서드는 이미 존재했음, 서비스 위임만 추가. 탈퇴 시 handle이
      null로 클리어되므로 별도 active 필터 불필요(확인·테스트 완료)

## PA-04. portfolio 스키마 + 도메인

depends on: PA-01, PA-02, PA-03

`com.atcrew.portfolio` 모듈 신설. `docs/design/portfolio-module-design.md` §2 그대로.

- [x] Flyway V17 `portfolios`/`portfolio_items`/`portfolio_item_snapshots` (`artist_page_key`는 CHAR(1)→VARCHAR(1)로
      정정 — `ddl-auto: validate`의 JDBC 타입코드 검증 통과 위함)
- [x] `Portfolio`/`PortfolioItem`/`PortfolioItemSnapshot` 엔티티(`Persistable` 패턴), `PortfolioKind`/`ReflectionType` enum
- [x] 리포지토리 3종 (`PortfolioItemRepository.deleteByPortfolioId`는 `@Modifying` 벌크 DML — flush 순서상
      파생 삭제가 유니크 제약에 걸림)
- [x] `PortfolioErrorCode`/`PortfolioException`
- [x] `PortfolioModuleTests` 8건 + `ModularStructureTests` 통과

## PA-05. portfolio 코어 서비스 — CRUD

depends on: PA-04

`PortfolioServiceImpl`: 작가 페이지 lazy 생성(`getOrCreateArtistPage`), 목록(`/me`, 커서 페이지네이션
정렬 3종), 생성(LIVE만 — SNAPSHOT은 PA-06), 수정(제목·구성), 삭제, 작품 추가/제거,
`selectable`(작가페이지+LIVE만). 슬러그 생성(§2.6, SecureRandom + handle 충돌 3회 재시도).
플랜 게이팅(`planService.assertPro`)은 SHARED 생성/수정/추가에 적용, ARTIST_PAGE는 전 플랜.
작품 추가/제거 시 `artworkService.updatePortfolioInclusion()` 동기 호출(같은 트랜잭션, 라이브 멤버십만).
`PortfolioController` + 요청/응답 DTO.

- [x] `PortfolioServiceImpl` CRUD 전체(LIVE·ARTIST_PAGE) — SNAPSHOT 생성은 이번엔 400으로 막아둠(PA-06 대상)
- [x] `PortfolioController` (인증 필요 엔드포인트 전체 — `docs/design/portfolio-module-design.md` §4 표 중
      `shared/*`·복제 제외), `PortfolioServiceTests` 11건 + 기존 `PortfolioModuleTests` 통과(총 19건)
- [x] `SecurityConfig` — 전부 authenticated 기본값으로 커버돼 매처 추가 불필요, PA-08용 경고 주석만 선반영

## PA-06. 고정형(SNAPSHOT) 생성

depends on: PA-05

§5.1·§5.2. `POST /api/portfolios`에 `reflectionType=SNAPSHOT` 분기 추가. 생성 시점 작품 표시 필드를
`portfolio_item_snapshots`에 배치 복사(하이브리드 저장 — 컬럼+`payload_json`). 수정 API는 SNAPSHOT에
대해 항상 409. R2 이미지는 원본 키를 그대로 참조(§5.6, media retention은 범위 밖 — 코드 주석으로
알려진 제약 명시).

- [x] 스냅샷 생성 로직 — `payload_json`/`snapshot_owner_profile` 직렬화는 Jackson 3(`tools.jackson` JsonMapper,
      Spring Boot 4 자동구성 빈)로 처리(구 `com.fasterxml.jackson.ObjectMapper` 아님, 주의)
- [x] `PATCH`/작품 추가·제거 API의 SNAPSHOT 409 가드 — PA-05의 `assertEditable`이 이미 커버, 회귀 테스트만 추가
- [x] 원본 수정·삭제·비공개 전환 후에도 스냅샷 응답이 불변임을 검증하는 테스트 — `PortfolioServiceTests` 15건
      (총 `com.atcrew.portfolio.*` 23건) 전부 통과

## PA-07. 복제

depends on: PA-05

§5.3. `GET /api/portfolios/{id}/duplication-source` — 원본 유형별 후보 추출, 제외 판정(DELETED/PRIVATE
제외, LINK_ONLY 포함), `{defaultTitle, selectedArtworkIds, excludedCount}` 응답. 실제 생성은 프론트가
받은 값으로 `POST /api/portfolios`를 재호출(신규 API 없음).

- [x] `duplication-source` 엔드포인트 + 로직 — 설계의 `getArtworkStatesFor` 배치 조회 API는 신설하지 않고
      기존 `artworkService.getArtworkForIndexing()` 건당 조회를 썼다(`resolveOwnedArtworks`·`loadItemArtworks`와
      동일 패턴, artwork 공개 API를 늘리지 않기 위함)
- [x] 제외 판정 테스트(DELETED/PRIVATE/LINK_ONLY) + 고정형·작가 페이지 기본 제목·빈 포트폴리오 5건

## PA-08. 공유 링크 공개 열람

depends on: PA-05

§4(공유 API 2건). `GET /api/portfolios/shared/{identifier}`, `GET /api/portfolios/shared/{identifier}/artworks`
— slug 우선, 없으면 handle. 탈퇴(`blocked_at`)·존재하지 않음 처리. `X-Robots-Tag: noindex` 헤더.
`SecurityConfig` permitAll 추가.

- [x] 공유 열람 API 2건 (`PortfolioSharedInfo` 신설, 작품 목록은 ordinal 단일값 커서 —
      정렬 기준이 ordinal 하나뿐이라 복합 커서·base64 인코딩 없이 정수 문자열을 그대로 쓴다)
- [x] `MemberDeactivatedEvent` 구독(`PortfolioMemberEventListener`) → `blocked_at` 설정 + 조회 시점 이중 확인
      — 설계의 `memberService.isActive` 신규 공개 메서드는 만들지 않고 기존 `findById`가 탈퇴 회원에 대해
      던지는 예외로 판정했다(member 공개 API를 늘리지 않기 위함). 이중 확인 경로의 차단 쓰기는
      `PortfolioBlocker`(REQUIRES_NEW)로 분리 — 곧바로 410을 던져 조회 트랜잭션이 롤백되므로 같은
      트랜잭션에 두면 차단이 남지 않는다
- [x] `SecurityConfig` permitAll(GET 2건) + `X-Robots-Tag: noindex` 헤더(해당 2개 메서드만 `ResponseEntity`로 감쌈)
- [x] `PortfolioServiceTests` 7건 추가(슬러그·handle 해석, 고정형 작성자 이름 고정, 404, 탈퇴 410,
      이벤트 유실 시 조회 시점 차단, 커서 페이지네이션) — 총 `com.atcrew.portfolio.*` 35건 통과

## PA-09. REST Docs·검증 테스트 정리

depends on: PA-06, PA-07, PA-08

기존 3계층 테스트 패턴(`docs/testing/rest-docs-guide.md`) 그대로. `PortfolioApiDocTest`,
`PortfolioControllerValidationTest`, `PortfolioModuleTests`(작가페이지 유일성, 슬러그 충돌 재시도,
순환 의존 없음).

- [x] `PortfolioApiDocTest` 8건 — 공유(LIVE) 생성→조회→수정→작품 추가·제거→삭제, 고정형 생성 +
      수정 409, 내 목록(정렬·필터), 선택 가능 목록, 복제 원본, 공유 열람(슬러그·handle·404),
      공유 작품 목록(커서), 스타터 403. 스니펫 16종 생성
- [x] `PortfolioControllerValidationTest` 8건 — 제목 공백/누락, reflectionType 누락, artworkIds
      원소 공백, PATCH 경로변수 UUID 위반, 작품 추가 빈 배열, 공유 식별자 패턴 위반 2건
- [x] `PortfolioModuleTests` (PA-01에서 작성, 8건 통과)
- [x] `ModularStructureTests` 통과 확인(신규 모듈 순환 없음)
- [x] `./gradlew test --tests "com.atcrew.portfolio.*"` 51건 전부 통과
      (ServiceTests 27 + ModuleTests 8 + ApiDocTest 8 + ValidationTest 8)

## PA-11. 포트폴리오 카드 커버 썸네일 (설계 문서 §0/§4 누락분 보강)

depends on: PA-09

Figma/기획서 마이페이지_작가-R39("포트폴리오 카드 구성"): 커버는 열람 가능 작품 중 업로드일이 가장
오래된 4개의 썸네일을 2x2로 배치(3:4 비율, 크롭 없음), 4개 미만이면 빈 칸은 회색, 작품 0개면 커버
전체 회색. `docs/design/portfolio-module-design.md` 작성 시 이 요구사항을 §4 API 표에 반영하지 못해
`PortfolioSummaryInfo`(내 포트폴리오 목록, `GET /me`)와 `PortfolioSelectableInfo`(선택 목록)에
썸네일 필드가 빠져 있다 — 목록 화면 카드 UI를 만들 수 없는 상태였다.

- [x] `PortfolioSummaryInfo`에 `List<PortfolioCoverThumbnailInfo> coverThumbnails` 추가.
      신규 record `PortfolioCoverThumbnailInfo(thumbKey, thumbAdultKey)`를 `com.atcrew.portfolio` 루트에 둔다 —
      목록은 포트폴리오 다건 × 4장이 함께 내려가므로 `PortfolioArtworkCardInfo` 재사용 대신 경량 타입으로 분리.
      `PortfolioSelectableInfo`는 체크박스 선택 목록이라 커버 노출 요구사항이 없어 제외
- [x] LIVE/ARTIST_PAGE는 `portfolio_items`에서 ordinal(업로드순) 앞의 4개, SNAPSHOT은
      `portfolio_item_snapshots`에서 동일하게 4개를 조회해 채운다(`PortfolioServiceImpl.loadCoverThumbnails`).
      4개 미만이면 있는 만큼만, 0개면 빈 배열 (프론트가 회색 처리를 담당 — 서버는 배열 길이로만 판단하게 한다).
      SNAPSHOT은 원본을 다시 조회하지 않고 스냅샷 컬럼(`thumb_key`, `thumb_adult_key`)만 쓴다(§5.1)
- [x] N+1 우려는 기존 코드 전반의 확립된 패턴(`getArtworkForIndexing` 건당 조회)을 그대로 따른다 — 이
      태스크에서 새 배치 조회 API를 만들지 않는다(페이지당 최대 20개 포트폴리오 × 4장 = 80건 수준으로
      기존 다른 곳과 동일 특성)
- [x] 회귀 테스트 5건 추가(`PortfolioServiceTests`): 5개 담긴 LIVE에서 가장 오래된 4개(개수·순서),
      2개만 담긴 경우 2개, 빈 포트폴리오는 빈 배열, LIVE에서 휴지통 이동 작품 제외,
      SNAPSHOT은 원본 삭제 후에도 커버 불변
- [x] REST Docs 스니펫(`PortfolioApiDocTest`)의 `list-my-portfolios`에 필드 설명 추가
- [x] `./gradlew test --tests "com.atcrew.portfolio.*"` 56건 전부 통과
      (ServiceTests 32 + ModuleTests 8 + ApiDocTest 8 + ValidationTest 8)

## PA-10. 전체 빌드 검증

depends on: PA-09, PA-11

- [x] `./gradlew build` 전체 그린 — 오케스트레이터가 직접 2회 단독 실행(PA-09 이후, PA-11 이후). 376개 중
      5개 실패는 매번 `SearchApiDocTest`·`EventPublicationRegistryTest`뿐이고 둘 다 격리 실행하면
      통과하는 기존 flaky 결함(공유 ES Testcontainer가 컨텍스트 종료 시 같이 죽는 구조적 문제,
      `docs/NEXT_STEPS.md`에 이미 기록돼 있던 사전 이슈 — 이번 작업과 무관)
- [x] `docs/design/portfolio-module-design.md`에 "구현 완료" 상태 갱신 + §7(카드 커버 썸네일, 누락분 보강)·
      §8(설계 대비 실제 구현 차이 8개 항목) 추가
- [x] `docs/roadmap.md` 10번 항목 상태 갱신

## PA-12. 포트폴리오-작품 정합성 재계산 구현 (PA-10이 "완료"로 잘못 기록한 누락분)

depends on: PA-10

2026-08-12 QA에서 발견: §1.2/§5.4가 명시한 `ArtworkChangedEvent`/`ArtworkPermanentlyDeletedEvent` 구독
리스너와 §5.5의 `PortfolioMembershipReconcileScheduler`(6h 주기 보정)가 실제 코드엔 없다(grep 0건) —
PA-10의 "설계 그대로 구현됐다"는 §8.2 기록은 오기. 포트폴리오 API를 거치지 않고(즉 add/remove/update 없이)
구성 작품이 휴지통 이동하거나 비공개 전환되면 `itemCount`·`portfolio_items` 행이 무기한 stale로 남는다
(재현 확인됨, PA-13의 원인이기도 함).

- [x] `ArtworkChangedEvent` 구독 리스너(`PortfolioArtworkEventListener`) — 2026-08-12 확정 명세
      (마이페이지_작가-R38·R39)에 맞춰 **제외 트리거는 휴지통 이동뿐**으로 확정했다. 비공개 전환(공개 OFF)은
      구성 행도 `itemCount`도 건드리지 않는다 — 라이브 소속 자체가 유효한 공개 위치이고, 행을 지우면
      편입 여부가 false로 떨어져 스스로 완전 비공개가 되는 자가당착이 된다. 구현은 상태 기준 절대값
      재계산(`PortfolioMembershipReconciler.reconcileArtwork`)이라 휴지통 이동·복원만 값이 바뀌고
      visibility 변경은 결과가 같아 아무것도 쓰지 않는다(전용 트래시 이벤트는 artwork에 없음을 grep으로 확인)
- [x] `ArtworkPermanentlyDeletedEvent` 구독 리스너 — `portfolio_items` 행 제거 + 개수 재계산
      (커버는 목록 조회 시점에 원본을 읽어 판정하므로 저장할 상태가 없음). 남은 행의 ordinal은 재번호를
      매기지 않는다(조회가 ordinal 비교만 쓰므로 빈 번호가 무해, uk_pi_order 충돌 위험만 늘어남)
- [x] `PortfolioMembershipReconcileScheduler`(`@Scheduled(fixedDelay = 21_600_000)`) — `AuthCleanupScheduler`
      패턴. 포트폴리오 단위 트랜잭션으로 (1) 원본이 사라진 고아 구성 행 제거 (2) `itemCount` 재계산
      (3) `artworks.portfolio_included` 불일치 보정(값이 이미 맞으면 쓰지 않음 — 6h마다 updated_at을 흔들지
      않기 위함). 역방향(행 없는데 included=true)은 portfolio에서 조회할 수단이 없어 대상 밖(코드 주석에 명시)
- [x] 회귀 테스트 — `PortfolioServiceTests` 2건(휴지통 이동 → 개수만 감소·행 유지·복원 시 복구, 영구 삭제 →
      행까지 제거)과 신규 `PortfolioMembershipReconcileTests` 3건(비공개 전환은 구성·개수 불변, 배치가
      고아 행·편입 여부·개수 불일치 보정, 배치가 휴지통 작품 행은 유지). 배치·재계산 컴포넌트가
      package-private이라 후자는 같은 패키지에 뒀다

## PA-13. 공유 LIVE 목록 커서 페이지네이션 hasNext 버그 수정

depends on: PA-05 (PA-12과 근본 원인 공유하지만 별도 결함이라 독립 수정 필요)

2026-08-12 QA에서 발견·재현: `PortfolioServiceImpl.getSharedPortfolioArtworks`의 LIVE 분기(210~222행)가
DELETED 필터링 **이전** 원본 행 개수로 `hasNext`/`nextCursor`를 계산해, 페이지 안에 트래시 이동 작품이
섞이면 `items=[]`인데 `hasNext=true`인 응답이 나올 수 있다. PA-12가 배포되면 stale 행 자체가 줄어들어
발생 빈도는 낮아지지만, 이벤트 처리 지연 구간에는 여전히 재현 가능하므로 근본 수정(필터링 이후 개수로
hasNext 판정, 또는 필터 통과분이 부족하면 다음 페이지를 이어서 채우는 방식)이 필요하다.

- [x] `hasNext`/`nextCursor` 계산을 DELETED 필터링 이후 기준으로 수정 — LIVE 분기를 `liveArtworkPage()`로
      분리해 필터 통과 카드가 size+1개가 될 때까지 원본 행을 청크 단위로 이어 읽는다(커서도 필터를 통과한
      마지막 행의 ordinal). SNAPSHOT 분기는 조회 시점 필터가 없어 기존 방식 유지
- [x] 회귀 테스트 2건 — 페이지 중간 2건이 휴지통일 때 size가 그대로 채워지고 다음 커서로 나머지에 도달,
      뒷부분이 전부 휴지통이면 빈 페이지 없이 첫 페이지에서 `hasNext=false`로 끝남

## PA-14. 복제 자동선택 필터가 완전비공개 기준이 아닌 문제 수정

depends on: PA-05

2026-08-12 QA·PM 확인([[project_portfolio_visibility_pm_decision]])에서 발견: `PortfolioServiceImpl.duplicable()`
(575~580행)이 `artwork.visibility() != PRIVATE`만 검사하고 `portfolioIncluded`는 보지 않는다. 정책상
자동선택에서 제외돼야 하는 건 "완전비공개"(`PRIVATE && !portfolioIncluded`)뿐인데, 이미 다른 라이브
포트폴리오에 담겨 열람 가능한(포트폴리오 한정 공개) PRIVATE 작품까지 무조건 제외한다. 원인은
`ArtworkInfo` record에 애초에 `portfolioIncluded` 필드가 없어 이 구분이 구조적으로 불가능했던 것.

- [x] `com.atcrew.artwork.ArtworkInfo` record에 `portfolioIncluded` 필드 추가(visibility 바로 뒤),
      `ArtworkMapper.toInfo()`가 `Artwork.isPortfolioIncluded()`로 채운다 — `getArtworkForIndexing()`을 포함한
      모든 `ArtworkInfo` 생성 경로가 이 매퍼 하나뿐이라 나머지 사용처(search 색인·community 피드)는 그대로
      동작한다. 생성자 직접 호출은 `ArtworkSearchMapperTest` 1곳뿐이라 거기만 인자 추가
- [x] `PortfolioServiceImpl.duplicable()`을 `visibility == PRIVATE && !portfolioIncluded`(완전비공개)만
      제외하도록 수정
- [x] 회귀 테스트 3건 — 기존 "삭제·비공개 제외" 테스트를 새 정책(삭제만 제외)으로 갱신,
      고정형에만 담긴 PRIVATE(=완전비공개)는 제외, 작가 페이지에도 담긴 PRIVATE은 자동선택 유지
- [x] `docs/design/portfolio-module-design.md` §4 표의 `INVALID_PORTFOLIO_TITLE`을 POST 행에서 PATCH 행으로
      정정 + 도달 조건 각주 추가(코드 확인 결과 `validateTitle`은 양쪽에서 불리지만 POST는 `@NotBlank`가
      먼저 막아 도달 불가능 — 코드는 손대지 않음)

## PA-15. 고정형 스냅샷 R2 이미지 orphan 정리 데이터 유실 방지 (긴급 핫픽스)

depends on: (없음, 다른 태스크와 독립적인 파일을 건드림 — artwork/media 모듈, portfolio 모듈 아님)

2026-08-12 명세서 대조 QA에서 발견: `PortfolioItemSnapshot`은 원본 이미지 R2 key를 복사하지 않고 그대로
참조하는데(`PortfolioItemSnapshot.java:25` 자체 주석에 이미 리스크로 명시돼 있었음), (1) 원본 영구삭제 시
`ArtworkEventListener.onPermanentlyDeleted`가 그 key들을 즉시 R2에서 물리 삭제하고, (2) 원본을 단순
수정(이미지 교체)만 해도 `OrphanImageCleanupScheduler`(1시간 주기)가 버려진 key를 정리한다 — 둘 다
`portfolio_item_snapshots`가 같은 key를 참조 중인지 전혀 확인하지 않는다. 활성 고정형 포트폴리오가 있다면
지금도 조용히 썸네일이 깨지고 있을 수 있는 실사용 데이터 유실 리스크다. 오늘 확정된 마이페이지_작가-R39·
휴지통-R04("활성 고정형 포트폴리오가 참조하는 자산 버전은 보존")가 이를 명시적으로 요구한다.

- [x] `PortfolioItemSnapshotRepository.findActiveByThumbnailKeys()` 추가 — 후보 key가 카드 썸네일
      (`thumb_key`/`thumb_adult_key`)로 걸린 스냅샷을 찾고, 포트폴리오 행이 남아있는 것만 대상으로 한다
      (`exists (select 1 from Portfolio ...)`). `payload_json` 안의 상세 이미지 key는 SQL로 조회할 수 없어
      걸린 스냅샷의 payload를 `SnapshotRetainedMediaKeyProvider`가 Jackson으로 펼쳐 보존 집합에 넣는다
      — 삭제 후보는 항상 한 작품의 key 전체로 들어오므로 썸네일 하나만 걸려도 그 작품의 스냅샷을 찾는다
- [x] 모듈 경계상 media/artwork가 portfolio 리포지토리를 직접 볼 수 없어 media 공개 SPI
      `RetainedMediaKeyProvider`(`Set<String> retainedKeys(Collection<String>)`)를 신설하고 portfolio가
      구현했다(portfolio→media 단방향, `ModularStructureTests` 통과). 구현체가 없는 부트스트랩
      (artwork 모듈 단위 테스트)에서는 `List<RetainedMediaKeyProvider>` 주입이 빈 리스트가 돼 무보존으로 동작
- [x] `ArtworkEventListener.onPermanentlyDeleted` — 보존 key를 뺀 나머지만 `deleteFiles`. 보존 판정 자체가
      실패하면 전체 key를 고아 큐로 넘긴다(스케줄러가 같은 판정을 다시 하므로 즉시 삭제되지 않음)
- [x] `OrphanImageCleanupScheduler` — 배치마다 보존 판정 후 비보존 key만 삭제. 보존 key가 남으면 행을
      큐에 유지(`OrphanedMediaKey.keepOnly`)하되 `marked_at`을 재판정 시점으로 갱신하고 배치를 `marked_at`
      오름차순으로 읽어, 보존 행이 큐 앞을 막아 뒤의 행이 굶는 것을 막았다(설계에 없던 판단 — 보존 행이
      영구히 남을 수 있어 기존 "첫 페이지 100건" 방식과 결합하면 정리 자체가 멈출 수 있었다)
- [x] 회귀 테스트 10건 — `ArtworkEventListenerTest` +3(참조 중 key 제외, 전량 보존 시 빈 삭제 요청,
      보존 판정 실패 시 전체 고아 적재), `OrphanImageCleanupSchedulerTest` 신규 3건(이미지 교체 경로:
      비참조 key만 삭제·행 제거, 참조 key는 유예하고 큐 유지, 전량 참조 시 삭제 미요청),
      `SnapshotRetainedMediaKeyProviderTests` 신규 4건(썸네일+payload 상세 key 보존, 무관한 key 제외,
      포트폴리오 없는 스냅샷 행 제외, 빈 후보)
- [x] `docs/design/portfolio-module-design.md` §5.6을 "보존 판정으로 해소"로 갱신(채택/미채택 대안, 남은
      제약 3가지 명시) + §6 D3·§8.8 상태 갱신
- [x] `./gradlew test --tests "com.atcrew.artwork.*"` 30건, `--tests "com.atcrew.media.*"` 9건,
      `--tests "com.atcrew.portfolio.*"` 73건, `ModularStructureTests` 2건 전부 통과

## PA-16. 포트폴리오 작품 개수 100개 상한 제거

depends on: (없음)

`CreatePortfolioRequest`/`UpdatePortfolioRequest`/`AddPortfolioArtworksRequest`의 `artworkIds`에 걸린
`@Size(max = 100)`이 "포트폴리오·작품 선택 개수 제한 없음"이라는 오늘 확정 명세(마이페이지_작가-R37·R38·
R46)와 충돌한다. `PortfolioErrorCode.PORTFOLIO_ITEM_LIMIT_EXCEEDED`는 정의만 되고 어디서도 throw되지
않는 죽은 코드다.

- [x] 세 DTO의 리스트 `@Size(max = 100)` 제거 — 원소 단위 제약(`@NotBlank @Size(max = 36)`)과
      `AddPortfolioArtworksRequest.@NotEmpty`(0개 추가는 무의미한 호출)는 그대로 뒀다
- [x] 사용되지 않는 `PortfolioErrorCode.PORTFOLIO_ITEM_LIMIT_EXCEEDED` 제거 — src 전체 grep 결과
      정의부 1곳뿐이었고, 설계 문서 §4 에러코드 목록에서도 함께 지웠다
- [x] 회귀 테스트 2건 — 컨트롤러 검증(`PortfolioControllerValidationTest`: 150개 요청이 400이 아닌 201),
      서비스(`PortfolioServiceTests`: 실제 작품 101개로 생성 후 1개 추가 → itemCount 102)

## PA-17. 포트폴리오 "업데이트순" 정렬이 시스템 변경에도 갱신되는 문제 수정

depends on: PA-12 (같은 `Portfolio.updatedAt`/`PortfolioMembershipReconciler` 영역을 건드리므로 PA-12
완료 후 진행)

오늘 확정된 마이페이지_작가-R37은 "업데이트순은 최신 반영형 공유 포트폴리오가 [수정하기]로 마지막으로
수정된 시점 기준"이라고 명시하는데, 실제로는 `@LastModifiedDate`가 엔티티 dirty 시마다(작품 추가/제거
API, `PortfolioMembershipReconciler`의 자동 재계산까지) 갱신돼 사용자가 [수정하기]를 안 눌러도 정렬
순서가 바뀐다.

- [x] 별도 필드 `Portfolio.lastEditedAt` 도입(Flyway **V19**: 컬럼 추가 + `updated_at`으로 backfill 후
      NOT NULL + `idx_pf_owner_edited` 생성, 쓰이지 않게 된 `idx_pf_owner_updated`는 제거).
      `@LastModifiedDate`를 떼는 대신 필드를 추가한 이유는 (1) 모든 엔티티가 created_at/updated_at 감사
      컬럼을 두는 리포지토리 컨벤션을 깨지 않기 위해서, (2) 누군가 나중에 `@LastModifiedDate`를 되살려도
      정렬이 조용히 회귀하지 않게 하기 위해서다. 갱신 지점은 `updatePortfolio` 한 곳
      (`Portfolio.markEdited()`)이고, 생성 시점에 초기값을 넣는다
- [x] "업데이트순" 정렬·커서(`sortOf`/`cursorField`/`cursorValueOf`)가 `lastEditedAt`을 쓰도록 수정.
      응답에도 `PortfolioSummaryInfo.lastEditedAt`을 추가했다 — 정렬 기준과 카드에 표시되는 시각이
      어긋나면 목록이 뒤죽박죽으로 보이기 때문(REST Docs `list-my-portfolios` 필드 설명도 갱신)
- [x] 회귀 테스트 2건 — `PortfolioServiceTests`(작품 추가·제거로는 업데이트순 순서가 그대로, [수정하기]
      호출 후에만 뒤바뀜), `PortfolioMembershipReconcileTests`(원본 휴지통 이동 후 재계산해도
      `lastEditedAt` 불변, `itemCount`만 감소)

## PA-18. replaceItems() 재작성 경로 3종 결함 수정 (휴지통 소속 유실·낙관적 락 우회·무관한 업로드 롤백)

depends on: PA-12 (같은 "휴지통 소속 유지" 정책 위에서 작업)

2026-08-12 그릴링 + `/code-review`(fable, high, `docs/design/portfolio-module-design.md` §8.9 대상
파일 지정)에서 같은 `replaceItems()` 계열 코드에서 3개 결함을 확인했다.

**결함 A — 휴지통 소속 유실(당초 발견)**: PA-12가 정한 "휴지통 이동된 작품도 portfolio_items 행은
유지한다"는 정책을 `appendArtworks`(작품 추가 API)와 `updatePortfolio`(수정하기, 전체 재선택) 두
경로가 지키지 못한다. 상세 근거는 §8.9. `removeArtwork`→`detachArtwork`는 이미 안전하다.

**결함 B — 낙관적 락 우회 레이스(code-review 발견)**: `appendArtworks`/`applyPortfolioSelection`의
추가 경로는 `updatePortfolio`와 달리 `Portfolio.markEdited()`를 무조건 호출하지 않는다. 병합 후
개수가 자신이 읽은 시점의 메모리값과 우연히 같으면 Hibernate가 dirty로 안 잡아 `Portfolio` UPDATE
자체가 안 나가고, 그러면 `@Version` 낙관적 락 검사도 통째로 스킵된다. 그 상태에서
`replaceItems()`의 벌크 DELETE는 DB의 "현재" 커밋 상태를 그대로 지우므로, 동시에 커밋된 다른
트랜잭션이 추가한 작품이 조용히 사라질 수 있다.

**결함 C — MANDATORY 전파로 무관한 업로드 롤백(code-review 발견, 이번에 함께 처리하기로 확정)**:
`PortfolioArtworkEventListener`가 업로드 트랜잭션 안에서 `MANDATORY`로 `applyPortfolioSelection`을
호출하는데(검증 실패 시 업로드 전체를 원자적으로 되돌리기 위한 의도적 설계, R46), 그 포트폴리오를
동시에 수정 중인 무관한 트랜잭션과 낙관적 락이 충돌하면 업로드 자체가(작품 데이터는 멀쩡한데도)
전부 실패한다.

- [x] **결함 A·B를 `replaceItems()` 안에서 함께 중앙집중 처리** — 호출자가 넘긴 목록과 무관하게 기존
      휴지통·운영차단 작품 행은 항상 유지시키고(A), `replaceItems()` 호출 시 `Portfolio` 엔티티가 항상
      dirty로 잡혀 버전 체크가 스킵되지 않도록 강제한다(B) — 예: `replaceItems()` 진입 시 무조건
      `portfolio.markEdited()`(또는 동등한 강제 dirty 트리거)를 호출. 호출부(`appendArtworks`/
      `updatePortfolio`) 개별 수정은 채택하지 않는다
      → (A) `PortfolioServiceImpl.withRetainedItems()`가 요청 목록에 없는 기존 휴지통·차단 항목을 항상
      다시 합치고 업로드순으로 재정렬한다. `item_count`도 열람 가능 항목만 세도록 함께 정정했다.
      (B) `markEdited()`는 "업데이트순" 정렬 기준(R37)이라 재사용하지 않고, 새 카운터 컬럼
      `portfolios.items_revision`(V22)을 `Portfolio.markItemsReplaced()`가 무조건 올려 엔티티를 항상
      dirty로 만든 뒤 `flushWithVersionCheck()`가 `saveAndFlush`로 표준 낙관적 락 UPDATE를 태운다.
      잠금(pessimistic) API는 쓰지 않는다 — 초안의 `EntityManager.lock(PESSIMISTIC_FORCE_INCREMENT)`는
      `SELECT ... FOR UPDATE`로 실제 잠금 대기를 만들어(테스트 스위트가 멈췄다) 철회했다. 충돌은
      `PORTFOLIO_CONCURRENTLY_MODIFIED`(409). 호출 위치는 쓰기 순서(items → portfolios) 유지를 위해
      구성 행을 모두 쓴 뒤다
- [x] **결함 C — 낙관적 락 충돌만 재시도**: `applyPortfolioSelection`(MANDATORY, 같은 트랜잭션 유지 —
      검증 실패는 여전히 업로드 전체를 롤백해야 하므로 `REQUIRES_NEW` 분리는 채택하지 않는다) 안에서
      `ObjectOptimisticLockingFailureException`만 잡아 포트폴리오를 다시 읽어(1차 캐시 우회 필요 —
      `EntityManager.refresh()` 또는 재조회 방식은 착수 시 확인) 병합·`replaceItems()`를 유한 횟수(예: 3회)
      재시도한다. 검증 실패(소유권·고정형·플랜)로 인한 예외는 재시도 대상이 아니며 그대로 전파해 업로드를
      롤백해야 한다 — 재시도 로직이 이 둘을 정확히 구분하는지가 핵심
      → **미채택(구현 불가 확인, 대안 제안으로 종료)**. 실측(MariaDB 11.4 Testcontainer) 결과 (1) 낙관적 락
      예외가 나는 순간 Hibernate가 세션을 `rollbackOnly`로 표시해 재시도해도 커밋에서
      `UnexpectedRollbackException`이 나고, (2) 격리 수준이 `REPEATABLE READ`라 같은 트랜잭션의 잠금 없는
      재조회는 옛 스냅샷을 그대로 본다(`FOR UPDATE` 조회만 최신값 반환). 같은 물리 트랜잭션 안 재시도는
      성립하지 않는다. 현재 동작(업로드 전체 409 롤백, 데이터 유실 없음)과 예방 방식 대안은 §8.9에 기록
- [x] 회귀 테스트 — (A) 휴지통 작품이 담긴 포트폴리오에 `addArtworks`/`updatePortfolio`/
      `applyPortfolioSelection` 세 경로 전부 기존 휴지통 작품 행이 살아남는지, `removeArtwork`는 여전히
      정상 동작하는지. (B) 동시성 재현 — 두 트랜잭션이 겹쳐서 `appendArtworks`를 호출해도 먼저 커밋된
      쪽의 작품이 유실되지 않는지(낙관적 락이 실제로 걸리는지). (C) 업로드 트랜잭션 도중 포트폴리오에
      낙관적 락 충돌을 인위로 발생시켜도 재시도 후 업로드가 성공하는지, 검증 실패(스타터가 SHARED 지정
      등)는 여전히 업로드 전체를 롤백하는지
      → `PortfolioServiceTests`에 8건 추가(A 4건 + 제거 경로 1건, B 2건, C 1건). B·C는 실제 동시
      트랜잭션·스레드를 쓰지 않는다 — 한 트랜잭션 안에서 엔티티를 먼저 읽어 옛 버전을 쥐게 한 뒤
      `UPDATE portfolios SET version = version + 1`을 직접 실행해 "다른 요청이 먼저 바꾼" 상태만
      흉내낸다(실제 두 트랜잭션을 겹치려던 초안은 잠금 대기로 스위트가 멈췄다). C는 "재시도 후 성공"이
      아니라 "업로드 전체 409 롤백 + 기존 구성 보존"을 검증한다. 검증 실패 롤백은 기존
      `스타터가_업로드에서_공유_포트폴리오를_지정하면_작품까지_롤백된다`가 이미 담당한다. 수정 코드를 잠시
      되돌려 실패를 확인했다 — A는 소속 유지 코드를 되돌렸을 때 4건(제거 경로는 원래 안전해 통과),
      B·C는 버전 강제 증가·flush를 되돌렸을 때 3건이 실패한다
- [x] `docs/design/portfolio-module-design.md` §8.9를 이번 수정 결과로 갱신(결함 A·B·C 전부 해소로 표시)
      → A·B는 해소로, C는 "미해소(남은 제약) + 실측 근거 + 예방 방식 대안"으로 갱신했다

## PA-19. replaceItems()/withRetainedItems() lock 순서 통일 + 명시적 제거 회귀 수정

depends on: PA-18 (같은 코드를 이어서 고침)

2026-08-13 `/code-review`(max)에서 PA-18 자체가 만든 결함 2개를 발견, 직접 코드로 재확인했다.

**결함 D — `withRetainedItems()`가 명시적 제거도 막는다**: 요청 목록에 없고 열람 불가능(휴지통·차단)한
기존 항목을 무조건 다시 합치는 로직이 `removeArtwork`/`detachArtwork`·`applyPortfolioSelection`의
해제 경로에도 똑같이 적용된다. 즉 휴지통·차단된 작품은 사용자가 명시적으로 빼려고 해도(`DELETE
.../artworks/{artworkId}`, 204 반환) 실제로는 절대 안 빠진다 — §8.9가 "removeArtwork는 안전하다"고
적어둔 것과 반대로, 결함 A를 고치면서 새로 깨뜨렸다.

**결함 E — lock 순서 위반 3곳**: `flushWithVersionCheck()`의 주석은 "portfolio_items를 먼저 쓰고
portfolios를 나중에 쓴다"를 전제하는데, 실제로는 이 순서를 지키지 않는 호출부가 있다.
1. `updatePortfolio()`(438행)가 `replaceItems()` 호출 **전에** `portfolio.markEdited()`를 불러
   Portfolio를 미리 dirty로 만든다. `replaceItems()` 안의 `deleteByPortfolioId`는
   `@Modifying(flushAutomatically=true)`라 호출 직전 대기 중인 변경사항을 자동 flush한다 — 즉
   portfolios가 먼저 flush되고 portfolio_items가 나중이라 순서가 뒤집힌다. `appendArtworks`(정상
   순서: items→portfolios)와 동시에 실행되면 MariaDB 데드락(1213) 가능성이 있다(직접 코드 확인 완료).
2. `PortfolioMembershipReconciler.reconcilePortfolio()`(95행)도 고아 `PortfolioItem` 삭제(plain
   delete)와 `Portfolio.itemCount` 변경 감지가 둘 다 커밋 시점 암묵적 flush로 미뤄지는데, Hibernate
   기본 flush 순서(Update가 Delete보다 먼저)상 여기서도 portfolios가 먼저 쓰인다 — `/code-review` 지적,
   착수 시 재확인 필요.
3. `ArtworkServiceImpl.updatePublication()`도 `artwork.changeVisibility()`로 Artwork를 먼저 dirty로
   만든 뒤 동기 이벤트로 `replaceItems()`의 `deleteByPortfolioId`를 트리거해, artworks가 portfolio_items보다
   먼저 잠긴다 — `DELETE /api/portfolios/{id}`(items→artworks 순서로 `syncPortfolioInclusion` 호출)와
   반대 방향이라 세 번째 독립적 데드락 경로. `/code-review` 지적, 착수 시 재확인 필요.

- [x] **결함 D 수정** — `replaceItems()`/`withRetainedItems()`에 `removedArtworkIds` 파라미터를 추가해
      "요청 목록에 안 적힌 것"(암묵적)과 "빼달라고 한 것"(명시적)을 구분했다. `detachArtwork()`만 이
      집합을 채우므로 `removeArtwork`·`applyPortfolioSelection` 해제 두 경로가 함께 고쳐진다.
      기존 1-인자 오버로드는 추가·전체재선택 경로가 그대로 쓴다(소속 유지 유지)
- [x] **결함 E 수정** — 세 호출부 모두 `portfolio_items` → `portfolios`(또는 `artworks`) 순으로 쓰도록
      고쳤다. (1) `updatePortfolio()`는 `updateTitle`/`markEdited`를 `replaceItems()` **뒤로** 옮겼다
      (제목 검증은 트랜잭션이 함께 롤백되므로 결과 동일). (2) `PortfolioMembershipReconciler
      .reconcilePortfolio()`는 고아 행 삭제 후 `portfolioItemRepository.flush()`로 DELETE를 먼저 확정한
      뒤 `itemCount`·편입 여부를 쓰도록 순서를 뒤집었다(원본 조회 결과를 모아 재사용). (3)
      `ArtworkServiceImpl.updatePublication()`은 `changeVisibility`+`save`를 편입 반영 이벤트 **뒤로**
      옮겨 artworks가 portfolio_items보다 먼저 flush되지 않게 했다
- [x] 회귀 테스트 7건 — 결함 D 3건(`PortfolioServiceTests`: 휴지통 작품의 명시적 제거, 운영 차단 작품의
      명시적 제거, 노출 위치 재선언 해제 경로의 차단 작품 제거), 결함 E 4건(`PortfolioServiceTests`:
      제목+구성 동시 반영, 작가 페이지 제목 실패 시 구성 교체까지 롤백, 재선언 실패 시 공개 상태 불변 /
      `PortfolioMembershipReconcileTests`: 고아 행 제거와 휴지통 개수 재계산 동시 반영).
      실제 동시 트랜잭션·스레드·잠금 API는 쓰지 않았다 — 세 경로의 기능 회귀만 확인한다
- [x] `docs/design/portfolio-module-design.md` §8.9 갱신(결함 D·E 해소로 표시)
- [x] `./gradlew test --tests "com.atcrew.portfolio.*"` 107건 통과

## PA-20. R2 보존 로직 보강 — 커스텀 썸네일키·고아 키 추적

depends on: (없음, artwork/media/portfolio 파일이지만 PA-19와 별개 메서드)

`/code-review`(max) 지적 2건, 착수 시 코드로 먼저 재확인해라(사실관계 미검증).

- [x] **사실 확인됨, 수정 완료** — `PortfolioMapper.toCardInfo()`는 `artwork.thumbnailKey()`가 있으면
      그 키를 스냅샷 `thumb_key`로 쓰는데, `ArtworkServiceImpl.permanentlyDeleteArtworks`의 후보 키
      목록은 `artwork.getImages()`만으로 만들어 지정 썸네일 키가 빠져 있었다 → `findActiveByThumbnailKeys()`가
      스냅샷을 못 찾아 보존 판정이 통째로 무력화되고 상세 이미지까지 삭제된다. 후보 목록에
      `getThumbnailKey()`를 포함시켜 해소(지정 썸네일 키가 어디서도 안 지워지던 누수도 함께 해소).
      단, **이미지 교체(고아 큐) 경로는 여전히 매칭 불가** — 고아 후보에 지정 썸네일 키가 들어갈 일이
      없어 스냅샷을 찾을 단서가 없다. 해소하려면 스냅샷 참조 키 색인용 자식 테이블(스키마 변경)이
      필요해 범위 밖으로 두고 §5.6 "남은 제약"에 기록했다
- [x] **사실 확인됨, 수정 완료** — 보존된 키는 `deleteFiles` 대상에서 빠질 뿐 아무 데도 기록되지 않아,
      원본 행이 사라진 뒤 그 스냅샷을 담은 고정형 포트폴리오까지 삭제되면 추적 수단이 없어진다.
      `ArtworkEventListener.onPermanentlyDeleted`가 보존 키를 `mediaService.markOrphaned`로 고아 큐에
      적재하게 했다 — 시간당 배치가 같은 보존 판정을 다시 해 참조가 있으면 유예(`keepOnly`)하고 참조가
      끊기면 정리한다. `deletePortfolio()`에 media 정리 호출을 넣는 안은 채택하지 않았다(포트폴리오가
      어떤 경로로 사라지든 회수되고, portfolio→media 호출 의존을 늘리지 않는 쪽이 낫다)
- [x] 회귀 테스트 3건 — `ArtworkEventListenerTest` 신규 1건(보존 키 고아 큐 적재) + 기존 1건 기대값
      갱신(전량 보존 시에도 고아 적재), `ArtworkModuleTests` 신규 1건(영구 삭제 이벤트가 지정 썸네일
      키까지 담는지, Modulith `PublishedEvents`로 확인). `--tests "com.atcrew.artwork.*"` 41건,
      `--tests "com.atcrew.media.*"` 9건 통과
- [x] `docs/design/portfolio-module-design.md` §5.6/§8.8 갱신

## PA-21. 작가 페이지 제3자 접근 시 lazy 생성 확장

depends on: (없음)

`resolveArtistPageByHandle()`(공유 링크로 제3자가 접근하는 경로)는 작가 페이지를 lazy 생성하지 않는다
— 오직 본인 전용 엔드포인트(`getMyPortfolios`/`getSelectablePortfolios`)만 생성한다. 자기 포트폴리오
탭을 한 번도 안 연 회원은 작가 페이지 행 자체가 없어, 커뮤니티 "작가 찾아보기" 등에서 발견한 제3자가
`GET /api/portfolios/shared/{handle}`을 호출하면 404가 난다. V17에 백필이 없다고 명시돼 있다.

- [x] **사실 확인됨, 수정 완료** — `resolveArtistPageByHandle()`이 `getOrCreateArtistPage()`를 그대로
      재사용하도록 바꿨다(유니크 제약 충돌 시 재조회 폴백까지 동일 경로). 트랜잭션은 **같은 트랜잭션
      안에서 생성**하는 쪽으로 정했다 — `/shared/*` 3개 메서드의 `readOnly = true`를 뗐다
      (`getMyPortfolios`가 lazy 생성 때문에 읽기 전용이 아닌 것과 같은 판단). `REQUIRES_NEW` 별도
      트랜잭션 안은 채택하지 않았다: MariaDB 기본 격리 수준이 REPEATABLE READ라 바깥 트랜잭션이 이미
      스냅샷을 잡은 뒤 커밋된 행은 후속 SELECT에 보이지 않아(§8.9에 실측 기록) 같은 요청 안에서 방금
      만든 작가 페이지를 다시 못 읽는 함정이 있다. 비인증 쓰기 범위는 "실재하는 회원의 빈 작가 페이지
      1행"으로 한정되고 탈퇴 회원은 handle이 클리어돼 애초에 해석되지 않는다
- [x] 회귀 테스트 2건 — 작가 페이지 행이 없는 회원의 handle 접근이 200(빈 목록)이고 그때 만들어진 행을
      본인이 재사용하는지, 탈퇴 회원 handle로는 404이며 행이 생기지 않는지.
      `--tests "com.atcrew.portfolio.*"` 109건 통과

## PA-22. updatePublication의 PROCESSING 상태 제약 완화

depends on: (없음)

`ArtworkServiceImpl.updatePublication()`이 `changeVisibility()`(내부적으로 `assertReady()`)를 거쳐
PROCESSING 상태면 항상 400 `ARTWORK_NOT_READY`를 던진다. 그런데 업로드 시점에는 PROCESSING 상태에서도
`portfolioIds` 선택이 바로 적용된다(§업로드-R09) — 업로드 직후 선택을 정정하려는 사용자가 이미지 처리가
끝날 때까지 포트폴리오 편입을 못 고친다.

- [x] **사실 확인됨, 수정 완료 — 둘을 분리하지 않고 재선언 전체를 PROCESSING에서도 허용했다.**
      `updatePublication`은 두 값을 한 요청으로 받으므로 "편입만 반영하고 publishToFeed는 조용히 무시"는
      반쪽 저장이라 채택할 수 없고, 업로드 시점에 이미 PROCESSING 상태에서 같은 조합을 받아 visibility를
      계산해 저장한다(업로드-R09). PROCESSING 작품은 공개 상태와 무관하게 피드(status 필터)·상세
      (`accessFor`)·공유 목록(PA-24)에 노출되지 않으므로 저장된 값은 처리 완료 시 적용될 의도일 뿐이다.
      `Artwork.changeVisibility()`의 `assertReady()`를 휴지통(DELETED) 거부로 바꿨고, 유일한 호출부가
      이곳이라 쓰이지 않게 된 `assertReady()`는 제거했다. **부수 변경**: 휴지통 작품 재선언 응답이
      400 `ARTWORK_NOT_READY` → 410 `ARTWORK_DELETED`가 된다(기존 메시지가 "이미지 처리 중"이라 휴지통
      상황에 맞지 않았고, 삭제 자원에 410을 쓰는 artwork 모듈 관례에 맞췄다)
- [x] 회귀 테스트 2건 — 처리 중 작품의 노출 위치 재선언이 반영되는지, 휴지통 작품은 410으로 막히고
      편입 반영까지 함께 롤백되는지

## PA-23. 커서 페이지네이션 밀리초 충돌 방지

depends on: (없음)

`Instant.toEpochMilli()` 기반 커서가 같은 밀리초에 생성/수정된 행이 있으면 그 행을 건너뛰거나
(LATEST/UPDATED) 페이지가 멈출 수 있다(OLDEST). DATETIME(6)이 마이크로초까지 저장하는데 커서는
밀리초로 자른다.

- [x] **사실 확인됨, 수정 완료 — 둘 다 적용했다.** 커서를 `"<epochMicros>_<id>"`로 인코딩해 (1) DB
      저장 해상도(마이크로초)를 그대로 보존하고 (2) 시각이 완전히 같을 때는 id로 가른다. 정렬도
      `(정렬기준, id)` 복합으로 바꿔 커서가 가리키는 지점이 유일해지게 했다. 마이크로초 인코딩만으로는
      정확히 같은 마이크로초 동률이 남고, id 보조 키만으로는 밀리초 절삭이 남아 한쪽만으로는 부족했다.
      커서 형식이 바뀌므로 예전 형식(순수 숫자) 커서는 400 `INVALID_CURSOR`가 된다 — 아직 배포 전인
      신규 모듈이라 하위 호환 처리는 넣지 않았고, 컨트롤러·REST Docs의 커서 설명을 "직전 응답의
      nextCursor를 그대로 전달"로 고쳐 내부 형식을 계약에서 뺐다
- [x] 회귀 테스트 2건 — 밀리초는 같고 마이크로초만 다른 3건, 마이크로초까지 완전히 같은 3건을
      정렬 3종 × size=1 커서 순회로 전부 빠짐없이 조회하는지(페이지가 멈추면 실패)

## PA-24. liveArtworkPage 무제한 스캔 방어 + PROCESSING 노출 레이스 수정

depends on: PA-19 (같은 `viewable()`/`liveArtworkPage()` 영역)

**스캔 방어**: `liveArtworkPage()`(공유 링크 비인증 목록)가 필터 통과 카드가 size+1개 될 때까지
원본 행을 청크 단위로 무제한 이어 읽는다. PA-16(100개 상한 제거)과 PA-18(휴지통 행 영구 보존)이
겹치면서, 대량 휴지통 작품이 쌓인 포트폴리오의 공유 링크에 비인증 요청이 반복되면 매 요청마다 큰
스캔 비용이 든다.

- [x] **수정 완료** — 한 요청이 이어 읽는 청크 수를 10으로 제한했다(`MAX_SCAN_CHUNKS`, 청크 크기가
      size+1이라 상한도 요청 크기에 비례한다 — 기본 size=20이면 210행). 상한에 걸리면 **거기까지 모은
      카드와 마지막으로 읽은 행의 ordinal 커서**를 돌려준다 — `hasNext=false`로 끊으면 뒤의 작품이
      응답에서 통째로 사라지고, 에러로 알리면 정상 데이터인데 링크가 깨진 것처럼 보인다. 이 경우에만
      size보다 적은(0건일 수도 있는) 페이지가 나올 수 있다는 제약을 메서드 주석에 명시했다

**PROCESSING 노출 레이스**: `viewable()`/`duplicable()`이 `status != DELETED && !blocked`만 보고
`Artwork.accessFor()`가 비소유자에게 요구하는 `status == READY` 조건이 없다. 업로드 시
`portfolioIds` 선택이 동기 이벤트로 즉시 적용되는데, 이미지가 아직 PROCESSING이면 공유 목록에는
잠깐 노출되는데 그 작품의 `GET /api/artworks/{id}`는 정작 404가 나는 불일치가 생긴다.

- [x] **사실 확인됨, 수정 완료 — `viewable()` 자체가 아니라 상위 사용처(공유 목록)에만 넣었다.**
      `viewable()`은 소속 유지 판정(§8.9 결함 A)·`item_count`·본인 화면 카드까지 함께 쓰므로 여기에
      READY를 넣으면 (1) 처리 중 작품이 "휴지통·차단과 같은 소속 유지 대상"이 돼 [빼기]가 막히고
      (2) 본인 포트폴리오 상세에서 방금 올린 작품이 사라진다. 그래서 비인증 공유 목록
      (`liveArtworkPage`)만 `sharedViewable()`(READY && !blocked)을 쓰게 했다 — 상세(`accessFor`)가
      제3자에게 READY를 요구하는 것과 같은 기준이다. `item_count`는 종전대로 처리 중 작품도 세므로
      처리 완료 전까지 헤더 개수와 목록 건수가 잠깐 어긋날 수 있다(처리 시간 동안만, 자동 수렴)
- [x] 회귀 테스트 2건 — 처리 중 작품이 공유 목록에서는 빠지고 본인 화면에는 남았다가 처리 완료 후
      공유 목록에 나타나는지, 스캔 상한에서 끊고 커서로 이어받아 뒤의 작품을 결국 돌려주는지.
      기존 공유 목록 테스트 6건(서비스 5 + REST Docs 1)은 PROCESSING 작품을 쓰고 있어 READY까지
      올리도록 함께 고쳤다

## PA-25. 탈퇴 회원의 portfolio_included 정리 + 북마크 write/read 필터 정합화

depends on: (없음, artwork 모듈)

**결함 1**: `ArtworkEventListener.onMemberDeactivated()`가 `Artwork::forcePrivate`만 호출하고
`portfolio_included`는 안 지운다. `accessFor()`는 `portfolioIncluded=true`면 여전히 ALLOWED를
반환해, 탈퇴 회원의 라이브 포트폴리오 편입 작품이 제3자에게 계속 노출될 수 있다(현재는
`memberService.findById`가 던지는 예외로 우연히 막히지만, 이건 존재 여부·탈퇴 상태를 노출하는
우회 경로이기도 하다).

- [x] **사실 확인됨, 수정 완료 — 탈퇴 시 해제 쪽을 택했다.** `Artwork.forcePrivate()`가
      `portfolioIncluded`도 false로 내린다. `accessFor()`가 작성자 활성 여부를 보게 하는 대안은
      도메인 엔티티가 member를 알아야 해서(모듈 경계 위반) 채택하지 않았다. 여기에 더해 편입 여부
      재계산이 **차단된 포트폴리오를 세지 않도록** 고쳤다(`PortfolioItemRepository.countActiveByArtworkId`,
      기존 `countByArtworkId` 대체) — 탈퇴 회원의 포트폴리오는 차단되지만 구성 행은 남으므로, 세면
      6시간 보정 배치가 편입 여부를 도로 켜서 이 수정이 무효화된다. 운영 차단된 포트폴리오에도 같은
      규칙이 적용된다(차단된 포트폴리오는 유효한 공개 위치가 아니다)
- [x] 회귀 테스트 2건 — 탈퇴 시 편입 여부까지 해제되는지(`PortfolioServiceTests`), 보정 배치가 차단된
      포트폴리오를 근거로 편입 여부를 되살리지 않는지(`PortfolioMembershipReconcileTests`)

**결함 2**: `BookmarkServiceImpl.saveBookmark()`(오늘 PA-01에서 `accessFor()==ALLOWED` 기준으로 넓어짐)는
포트폴리오 한정 공개(비PUBLIC) 작품 북마크 저장을 허용하는데, `getBookmarks()`(목록 조회)는 여전히
`visibility==PUBLIC`만 필터링해 보여준다 — 저장은 되는데 목록에서 영원히 안 보이고 해제도 못 하는
북마크가 생긴다.

- [x] **사실 확인됨, 수정 완료** — `getBookmarks()` 필터를 `status == READY && !blocked &&
      accessFor(memberId) == ALLOWED`로 맞췄다(저장 기준과 동일). 운영 차단 제외만 따로 남긴다 —
      `accessFor`는 작성자 본인에게 차단 작품도 허용하는데 목록 노출은 R39가 금지하기 때문이다
- [x] 회귀 테스트 2건 — 포트폴리오 한정 공개 작품이 목록에 보이는지, 편입이 풀려 완전 비공개가 되면
      목록에서 빠지는지(`BookmarkModuleTests`)

## PA-26. REST Docs·설계문서 정합화 (2026-08-13 완료)

depends on: (없음)

`/code-review`(max)가 지적한 stale 문서 2건은 오케스트레이터가 직접 수정 완료했다 — 별도 작업 불필요.

- [x] `PortfolioApiDocTest.java` 4곳의 "최대 100개"/"1~100개" 필드 설명을 실제 동작(개수 제한 없음,
      `AddPortfolioArtworksRequest`만 `@NotEmpty`로 1개 이상)에 맞게 수정
- [x] `docs/design/artwork-module-summary.md`의 `UNSUPPORTED_VISIBILITY` 관련 서술을 PA-05 이후
      실제 계약(visibility 입력 경로 자체가 없음)에 맞게 갱신
- [x] `docs/operations/moderation-block.md`의 차단 해제 SQL이 차단 SQL과 같은 `WHERE ... AND
      blocked_at IS NULL` 조건을 그대로 써서 0행 no-op이 되던 결함 수정 — 별도 해제 SQL 명시
