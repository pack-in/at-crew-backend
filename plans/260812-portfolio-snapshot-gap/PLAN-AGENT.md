# PLAN-AGENT — 포트폴리오 스냅샷 명세 갭 해소

근거 문서: `docs/design/portfolio-snapshot-spec.md`(2026-08-12 확정 명세 16개 규칙) 및 이를 코드와 대조한
QA(같은 날 진행)에서 "설계 판단이 필요"로 분류된 4개 항목. Fable(설계 검토 agent)의 조사·권장안을 그대로
반영했다. `plans/260811-portfolio-module/`의 PA-01~PA-17과는 별개 plan이지만 같은 `com.atcrew.portfolio`/
`com.atcrew.artwork` 코드베이스를 다룬다 — 착수 전 그쪽 plan의 진행 상태(특히 PA-12·PA-14가 건드리는
`accessFor()`/`duplicable()` 인접 코드)를 확인할 것. 각 태스크는 완료 시 `./gradlew build`가 그린이어야
다음 태스크로 넘어간다.

## PA-01. Visibility 3값 → 명세 2요소 모델 정합화

depends on: (없음)

명세(마이페이지_작가-R04·업로드-R09)는 공개 상태를 "피드 공개 여부 × 실시간 반영 포트폴리오 포함 수"
2요소 파생 상태로만 정의하고 "링크공개"라는 제3의 상태를 인정하지 않는다. 그런데 `Artwork.accessFor()`는
`visibility != PRIVATE`면 무조건 ALLOWED라 LINK_ONLY+미편입(완전비공개여야 함) 조합도 통과시킨다.
`PortfolioServiceImpl.duplicable()`(복제 자동선택)에도 같은 구멍이 있다. 검색 인덱서·커뮤니티 피드
쿼리·북마크 서비스는 이미 `== PUBLIC`만 보므로 영향 없음(재확인만).

라이트(Laiteu) `ArtworkStatus`에 LINK_ONLY가 실존하므로(ETL 매핑 대상) enum 값 자체는 이번에 지우지
않는다 — 의미만 "PRIVATE와 동일 취급 + deprecated"로 재정의한다(PH-03 참고).

- [x] `Artwork.accessFor()` — `visibility == PUBLIC`이면 허용, 아니면 `portfolioIncluded`로 판정하도록 수정.
      회귀는 도메인 단위 테스트 `ArtworkAccessTest` 5건으로 뒀다(`ArtworkImageProcessingTest`와 동일 패턴) —
      통합 테스트로 만들면 READY 전환에 media webhook을 태워야 해서 판정 규칙 자체가 잘 안 보인다.
      LINK_ONLY+미편입=PRIVATE(403), LINK_ONLY+편입=ALLOWED, PRIVATE+편입=ALLOWED, PUBLIC=ALLOWED,
      완전비공개도 본인은 ALLOWED
- [x] `PortfolioServiceImpl.duplicable()` — `visibility == PUBLIC || portfolioIncluded`로 수정.
      기존 "LINK_ONLY는 자동 선택 유지" 테스트를 "편입되지 않은 LINK_ONLY는 자동 선택에서 빠진다"로
      뒤집고, 같은 테스트에서 그 작품을 직접 골라 포트폴리오를 만드는 것은 여전히 성공함을 함께 검증했다
      (수동 선택 허용, R41)
- [x] `Visibility.LINK_ONLY`에 `@Deprecated` + Javadoc, 쓰기 경로 400(`ArtworkErrorCode.UNSUPPORTED_VISIBILITY`
      신설). **가드 위치는 도메인이 아니라 `ArtworkServiceImpl`(업로드·`updateVisibility`)로 뒀다** —
      `Artwork.changeVisibility`에서 막으면 라이트 ETL로 유입된 레거시 LINK_ONLY 작품의 휴지통 복원
      (`restore()`가 `visibilityBeforeDelete`를 되돌리는 경로)까지 깨진다. 도메인은 값 보유를 허용하고
      API 입구에서만 거부한다. 회귀 `ArtworkModuleTests` 1건(업로드 400 + 공개상태 변경 400)
- [x] `docs/design/artwork-module-design.md`(§3 enum·§6.2 뷰 분기·§8.2 상태 전이)·`artwork-module-summary.md`
      (§필드표·상세 조회·`PATCH /visibility`·§11 공개 범위 정책표를 2요소 조합표로 교체)·
      `portfolio-module-design.md` §5.3 제외 판정·REST Docs(`PortfolioApiDocTest` visibility 필드 설명) 갱신.
      enum 물리 제거는 `docs/roadmap.md` 11번 항목으로 등록(라이트 마이그레이션 완료 후)
- [x] `./gradlew test --tests "com.atcrew.portfolio.*" --tests "com.atcrew.artwork.*"` 109건 전부 통과
      (artwork 36 + portfolio 73). 검색 인덱서 2곳·커뮤니티 피드·북마크는 재확인 결과 이미 `== PUBLIC`만
      보고 있어 손대지 않았다

## PA-02. 운영 차단(모더레이션) 최소 구현

depends on: (없음)

명세(R39·R41·R42·R46·휴지통-R04)가 반복 요구하는 "삭제와 구분되는 운영 차단" tier가 `ArtworkStatus`에
없다. `portfolios.blocked_at`은 회원 탈퇴용이라 별개. 관리자 Role·API는 로드맵상 이번 마일스톤 범위
밖(roadmap 8번)이므로, portfolio 모듈이 회원 탈퇴에 썼던 것과 동일한 패턴(컬럼+판정 로직만, API 미노출,
운영 조치는 DB 직접 UPDATE)으로 최소 구현한다.

- [x] Flyway **V20**(`V20__moderation_block.sql`) — 착수 직전 확인 결과 최신은 V19라 번호 그대로 썼다.
      `artworks.blocked_at`, `portfolio_item_snapshots.blocked_at` 둘 다 `DATETIME(6) NULL`
- [x] `ArtworkAccess.BLOCKED` 추가, `accessFor()` 최우선 분기. **단 작성자 본인은 통과시킨다**(PH-08 기본값
      "본인 열람 허용 + 배지") — 배지용으로 `ArtworkInfo.blocked`·`ArtworkSummaryInfo.blocked`를 함께 노출했다.
      `ArtworkErrorCode.ARTWORK_BLOCKED`(410)
- [x] portfolio 소비 지점 반영 — `resolveOwnedArtworks()`는 `PortfolioErrorCode.ARTWORK_BLOCKED`(400)로
      거부(본인 작품이라 존재 여부가 새지 않으므로 `ARTWORK_NOT_FOUND`로 뭉개지 않았다), `duplicable()`·
      `loadCards`·`loadCoverThumbnails`·`liveArtworkPage`는 공통 판정 `viewable(ArtworkInfo)`로 통일,
      스냅샷 쿼리는 `...AndBlockedAtIsNull...` 파생 쿼리로 교체. **복제 후보 추출(`duplicationCandidateIds`)만
      차단 필터를 걸지 않았다** — 차단 스냅샷을 후보에서 통째로 빼면 R41의 "제외된 {개수}개" 안내에서
      누락되기 때문이다(원본 상태로 `duplicable()`이 걸러 excludedCount에 잡힌다).
      "작품 선택 대상 목록(R38)"은 portfolio가 아니라 artwork의 `GET /members/me/artworks`가 담당하는데,
      R04가 "작품 관리 화면에는 공개 위치와 무관하게 본인 모든 작품을 노출"하라고 요구해 목록에서 지우지
      않고 `blocked` 플래그만 내려준다(프론트가 선택 대상에서 제외, 서버는 생성 시 재검증으로 강제)
- [x] 고정형 작품 개수 — 캐시 컬럼 `item_count`는 차단 시 갱신될 경로가 없어(이벤트 미발행) 고정형은
      조회 시점에 `countByPortfolioIdAndBlockedAtIsNull`로 센다(`itemCountOf`). 이에 맞춰
      `PortfolioMapper.toInfo`/`toSummaryInfo` 시그니처에 itemCount를 명시적으로 넘기게 바꿨다
- [x] 검색 인덱서 2곳(`ArtworkSearchIndexer`·`ArtworkReindexService`)에 `!artwork.blocked()` 추가.
      **플랜에 없던 소비 지점 2곳도 함께 막았다** — 커뮤니티 피드 쿼리(`buildCommunitySpecification`)와
      북마크 목록(`BookmarkServiceImpl`)이 각각 `PUBLIC` 작품을 그대로 노출하고 있어 차단이 반쪽이 된다
      (R39 "외부 노출 즉시 중단"). 각각 조건 1줄. `PortfolioMembershipReconciler`의 열람 가능 개수 판정에도
      차단을 반영해 6h 배치가 itemCount를 회수하도록 했다
- [x] 운영 절차 문서 `docs/operations/moderation-block.md` 신설(차단/해제 SQL, 재색인 호출, 자동 갱신되지
      않는 파생 상태 3종, 보관 정책) + `CLAUDE.md` 문서 목록에 등록
- [x] 회귀 테스트 5건 — `ArtworkModuleTests` 1건(제3자 410·비로그인 410·본인 열람+`blocked=true`),
      `PortfolioServiceTests` 4건(담기 400 2경로, 복제 자동선택 제외, 최신 반영형 목록·커버 제외,
      고정형 카드·커버·개수·공유 목록 전부 제외). 차단은 실제 운영과 동일하게 `JdbcTemplate` 직접
      UPDATE로 재현했다. `./gradlew test --tests "com.atcrew.portfolio.*" --tests "com.atcrew.artwork.*"`
      114건 통과(artwork 37 + portfolio 77)

## PA-03. 스냅샷 공개 식별자(snapshot_public_id) 도입

depends on: (없음)

`PortfolioItemSnapshot`의 현재 PK(Long auto-increment)는 "외부 노출 안 함" 전제로 설계됐는데, PA-04가
이를 외부 URL 식별자로 써야 한다. 이 저장소의 외부 식별자 관례(UUIDv7 String)를 따른다.

- [x] Flyway **V21**(`V21__snapshot_public_id.sql`) — 착수 직전 확인 결과 PA-02가 쓴 V20이 최신이라 번호
      그대로. NULL 컬럼 추가 → `UUID()` 백필 → NOT NULL → `uk_pis_public_id` 유니크 4단계. 컬럼 charset은
      다른 ID 컬럼과 동일하게 `latin1/latin1_bin`. 유니크는 (portfolio_id, id) 복합이 아니라 식별자 단독으로
      걸었다 — 발급된 URL이 항상 스냅샷 하나만 가리켜야 하기 때문이다(R37)
- [x] `PortfolioItemSnapshot.of(...)`에서 `UuidV7Generator.generate()`로 채움. PK(BIGINT)는 그대로 두고
      외부 식별자만 추가했다 — 기존 FK 성격 참조(`uk_pis_order` 등)를 건드리지 않기 위함
- [x] `PortfolioArtworkCardInfo`에 `snapshotId` 추가(artworkId 바로 뒤). 고정형 카드는 `artworkId=null`,
      최신 반영형·작가 페이지 카드는 `snapshotId=null`로 상호 배타다(PH-01 결정: `sourceArtworkId` 노출 제거)
- [x] 회귀 테스트 — `PortfolioServiceTests` 기존 고정형 테스트 4건을 snapshotId 기준으로 갱신하고
      "생성 응답의 snapshotId가 비어있지 않고 저장된 `snapshot_public_id`와 같다", "고정형 카드의
      artworkId는 null이다"를 추가 검증. REST Docs 3개 스니펫(create-shared-live·create-snapshot·
      shared-artworks)에 `snapshotId` 필드 설명 추가. `./gradlew test --tests "com.atcrew.portfolio.*"`
      77건 통과

## PA-04. 고정형 스냅샷 상세 API

depends on: PA-03, PA-02 (차단된 스냅샷을 상세 조회에서 걸러내려면 blocked_at 필터가 먼저 있어야 함)

명세(R39·R42)가 요구하는 "portfolioId+snapshotId로 식별되는 독립 열람 전용 자원"이 현재 전혀 없다.
엔드포인트: `GET /api/portfolios/shared/{identifier}/snapshots/{snapshotId}` — 기존 `/shared/*` 비인증
네임스페이스 재사용(SecurityConfig 매처 1줄 추가로 끝남, `/portfolios/{portfolioId}` 템플릿 경로를 새로
열지 않아 §매처 순서 함정 회피).

- [x] `PortfolioItemSnapshotRepository.findByPortfolioIdAndSnapshotPublicId()` 추가 — 식별자 단독 조회를
      만들지 않았다(타 포트폴리오 스냅샷이 열리면 안 되므로 짝을 맞춰서만 찾는다)
- [x] `PortfolioServiceImpl.getSharedSnapshotDetail(identifier, snapshotId)` — 설계 순서 그대로. 고정형이
      아님·타 포트폴리오 스냅샷·운영 차단을 전부 `PORTFOLIO_NOT_FOUND`로 뭉갠다(어느 쪽인지 알려주면 다른
      포트폴리오의 스냅샷 존재 여부가 샌다)
- [x] `com.atcrew.portfolio.PortfolioSnapshotDetailInfo` record 신설 — 지정된 15개 필드 그대로.
      카운트 필드는 넣지 않았다(PH-02)
- [x] **`ArtworkSnapshotPayload.representativeImageIndex`를 `int` → `Integer`로 바꿨다**(설계에 없던 판단).
      하위 호환 테스트가 바로 잡아낸 결함으로, 필드가 없는 구버전 JSON을 역직렬화하면 Jackson 3가
      `FAIL_ON_NULL_FOR_PRIMITIVES`로 실패해 옛 스냅샷 상세가 통째로 열리지 않는다. 전역 매퍼 설정을
      바꾸는 대신 payload 레코드를 전부 nullable로 두고 매퍼에서 null→0으로 정규화했다(리스트도 빈 목록으로)
- [x] `PortfolioController`에 `GET /shared/{identifier}/snapshots/{snapshotId}` 추가(`X-Robots-Tag: noindex`,
      snapshotId는 `@Pattern` UUID 검증), `SecurityConfig` permitAll 1줄. 기존 `/shared/*` 네임스페이스
      재사용이라 매처 순서 함정 없음
- [x] REST Docs 스니펫 `portfolio/get-shared-snapshot-detail` 추가(경로변수·응답 헤더·본문 필드) +
      `portfolio/validation/get-shared-snapshot-detail-invalid-id`
- [x] 회귀 테스트 7건(`PortfolioServiceTests`) — 정상(원본 수정·작성자 개명 후에도 생성 시점 값 유지),
      LIVE 유형 요청 404, 타 포트폴리오 스냅샷 404, 운영 차단 스냅샷 404, 삭제된 포트폴리오 404,
      탈퇴 회원 410, 구버전 payload 하위 호환. `docs/design/portfolio-module-design.md` §4 API 표·
      SecurityConfig 목록에도 신규 엔드포인트 반영
- [x] `./gradlew test --tests "com.atcrew.portfolio.*" --tests "com.atcrew.artwork.*"` 122건 통과
      (artwork 37 + portfolio 85)

## PA-05. 업로드 API 노출위치 조합 계약 전환

depends on: PA-01 (accessFor의 2요소 모델이 전제)

명세(업로드-R09)는 "추상적 공개상태값을 직접 선택하게 하지 않는다. 노출 위치를 선택하면 시스템이 계산한다"고
확정했는데 `UploadArtworkRequest.visibility`는 여전히 `@NotNull` 3값 필수다. `portfolio-module-design.md`
§5.5·`docs/roadmap.md` 10번이 이미 예고했던 잔여 작업이다. `portfolio → artwork` 의존만 있고 역방향은
순환이 되므로, artwork가 업로드 트랜잭션 안에서 이벤트를 발행하고 portfolio가 동기 리스너로 소비해
원자성을 지킨다.

**PH-05 결정: 즉시 전환**(과도기 병행 없음) — `visibility` 필드는 제거하고 `publishToFeed`로 완전히
대체한다. **PH-06 결정: 구 `PATCH /visibility` 제거** — 새 `PATCH /publication`이 유일한 재선택 경로다.

- [x] `UploadArtworkRequest`·`UploadArtworkCommand`에서 `visibility` 제거, `publishToFeed`(`@NotNull Boolean`)·
      `portfolioIds`(`List<@NotBlank @Size(max=36) String>`, optional) 추가
- [x] `com.atcrew.artwork.ArtworkPortfolioSelectionRequested` 이벤트 record 신설, 업로드 저장 직후 발행.
      **미디어 처리 트리거보다 앞에 발행하도록 순서를 잡았다**(설계에 없던 판단) — 외부 Worker 호출은
      롤백되지 않으므로 편입 검증을 그보다 먼저 끝내야 한다. `ModularStructureTests` 통과(순환 없음)
- [x] portfolio 동기 리스너(`PortfolioArtworkEventListener.onPortfolioSelectionRequested`, `@EventListener`)
      + `PortfolioServiceImpl.applyPortfolioSelection(propagation = MANDATORY)`. 기존 `addArtworks`/
      `removeArtwork`의 본문을 `appendArtworks`/`detachArtwork`로 추출해 재사용했고, 검증은 기존
      `findOwned` + `assertEditable`(고정형 409, SHARED는 프로 전용)을 그대로 쓴다. **편입 해제에는
      플랜 게이팅을 걸지 않았다**(설계에 없던 판단) — 다운그레이드 사용자가 자기 작품을 빼내지 못하면
      갇히고, 이는 "삭제는 허용"(요금제-R01)과 같은 계열의 판단이다
- [x] `publishToFeed` 기반 서버 계산(`ArtworkServiceImpl.visibilityOf`) — `true → PUBLIC`, `false → PRIVATE`
- [x] 구 `PATCH /visibility`·`UpdateVisibilityRequest`·`ArtworkService.updateVisibility` 제거.
      **PA-01이 추가한 `ArtworkErrorCode.UNSUPPORTED_VISIBILITY`도 함께 제거했다** — LINK_ONLY를 입력할
      수 있는 API 경로가 하나도 남지 않아 도달 불가능한 죽은 코드가 됐다(enum의 `@Deprecated`와
      `accessFor`의 PRIVATE 동일 취급은 그대로 유지). PA-01의 400 회귀 테스트도 같은 이유로 삭제
- [x] `PATCH /api/artworks/{artworkId}/publication` 신설(`UpdatePublicationRequest`, 같은 이벤트 경로 재사용)
- [x] 회귀 테스트 10건 — `PortfolioServiceTests` 7건(조합표 3행의 저장 상태·`portfolioIncluded`,
      스타터 SHARED 지정 시 403 + 작품 미생성·포트폴리오 개수 0 유지, 타인 포트폴리오 403,
      고정형 409, 재선언이 목록에 없는 포트폴리오에서 제외), 신규 `ArtworkPublicationControllerTest` 3건
      (정상 위임, `publishToFeed` 누락 400, 구 `PATCH /visibility` 404)
- [x] 문서 갱신 — `api-manual-test-guide.md`(엔드포인트 목록·업로드 본문·필드 제약·예외표·3-8 절 전체·
      체크리스트), `artwork-module-design.md`(§6.2 API 표·업로드 본문), `artwork-module-summary.md`
      (업로드 본문·`PATCH /publication` 절), `portfolio-module-design.md`(§5.4 이벤트 경로·§5.5 게이팅 표),
      `docs/roadmap.md` 10번. `rest-docs-guide.md`에는 구 계약 서술이 없어 손대지 않았다
- [x] `./gradlew test --tests "com.atcrew.portfolio.*" --tests "com.atcrew.artwork.*"` 131건 통과
      (artwork 39 + portfolio 92)
