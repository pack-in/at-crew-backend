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
