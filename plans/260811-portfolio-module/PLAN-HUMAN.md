# PLAN-HUMAN — portfolio 모듈 구현

## PH-01. 설계 미확정 항목 결정

depends on: (없음)

`docs/design/portfolio-module-design.md` §6에 남겨둔 두 항목은 기획서 문면과 사용자 스코프 지시가
어긋나 에이전트가 임의로 결정하지 않고 정본(기획서) 우선으로 진행했다. 다르게 가고 싶으면 알려달라 —
코드 변경 1줄 수준이라 바로 반영 가능하다.

- [x] R2 — 스타터(다운그레이드) 계정도 공유 포트폴리오 **삭제**는 허용할지 → **허용으로 확정**(현재 구현
      그대로, 요금제-R01 근거). 코드 변경 없음.
- [x] R3 — 복제는 비공개(PRIVATE) 작품을 자동 선택에서 제외하는데, 생성/수정 화면은 PRIVATE도 선택
      가능해 비대칭으로 보였다 → **현재 구현 그대로 확정**. 실제로는 "선택 가능 여부"의 비대칭이 아니라
      "복제 시 기본으로 미리 체크해주는지"의 차이일 뿐이다 — PRIVATE 작품은 자동 재선택만 안 될 뿐
      복제 후 작품 선택 화면(R38, 생성/수정과 동일 화면)에서 수동으로 다시 체크할 수 있다. 코드 변경 없음.

## PH-02. 로컬 기동 확인

depends on: PA-10

에이전트가 `./gradlew build`까지는 확인하지만, 실제 로컬 서버 기동과 curl 스모크 테스트는 사람이
`docker compose up -d mariadb elasticsearch && ./gradlew bootRun`로 직접 확인한다.

- [x] 로컬 기동 확인 — `Started AtCrewBackendApplication in 4.654 seconds`, 포트 8080 정상. 로컬 `mariadb`
      기본 포트(3306)가 다른 프로젝트 MySQL 컨테이너(`minicore-mysql`)와 충돌해 별도 컨테이너
      `turban-mariadb-local`을 3307로 기동, `MARIADB_PORT=3307`로 실행(docker-compose.yml 자체는 변경
      없음). Elasticsearch는 이전 세션에서 뜬 `at-crew-backend-elasticsearch-1`(이미지 일치 확인) 재사용
- [x] Flyway V16~V18이 순서대로 적용되는지 확인 — 로그에서 `Migrating schema atcrew to version "16 -
      billing subscriptions"` → `"17 - portfolio schema"` → `"18 - artwork portfolio inclusion"` →
      `Successfully applied 16 migrations ... now at version v18` 확인

## PH-03. API 하나씩 직접 검증

depends on: PA-10

이전 대화에서 요청한 대로 — "기능 하나씩, API 하나씩 직접 검증". 에이전트가 자동 테스트(REST Docs
스니펫·모듈 테스트)로 계약을 검증해두지만, 실제 화면 흐름과 Figma 대조는 사람이 진행한다.

에이전트가 실행 중인 로컬 서버(`localhost:8080`, MariaDB `turban-mariadb-local:3307`, 기존 ES
`at-crew-backend-elasticsearch-1` 재사용)에 대고 curl 대신 Python `requests`로 아래를 전부 직접
호출해 검증했다(스크립트: `/private/tmp/.../scratchpad/verify*.py`, 세션 종료 시 사라짐 — 재현하려면
아래 결과를 참고해 다시 작성).

- [x] 작가 페이지 포트폴리오 lazy 생성 → 조회 → 작품 추가/제거 — 최초 `GET /me` 호출 시 1개 생성,
      재호출해도 중복 생성 안 됨, 작품 추가 후 `itemCount=1`→제거 후 `0` 확인
- [x] 공유(LIVE) 포트폴리오 생성 → 공유 링크 비로그인 열람 → 원본 작품 수정 시 실시간 반영 확인 —
      `PATCH /api/artworks/{id}`로 제목 변경 후 `GET /api/portfolios/shared/{slug}/artworks`에
      즉시 반영됨. `X-Robots-Tag: noindex` 헤더도 확인
- [x] 고정형(SNAPSHOT) 포트폴리오 생성 → 원본 작품 수정·삭제 후에도 스냅샷 불변 확인 — 원본 제목
      변경·휴지통 이동 둘 다 스냅샷 응답에 영향 없음, `PATCH`로 스냅샷 자체를 수정하려 하면
      `SNAPSHOT_PORTFOLIO_IMMUTABLE`(409) 확인
- [x] 복제 → 삭제되거나 비공개인 작품이 자동 선택에서 빠지고 개수가 맞는지 확인 — 3개 담긴 뒤 1개
      비공개·1개 삭제로 전환, `duplication-source` 응답에서 정상 1개만 `selectedArtworkIds`에 남고
      `excludedCount=2` 정확히 일치
- [x] 스타터 계정으로 SHARED 생성 시도 → 403(`PRO_PLAN_REQUIRED`) 확인 → `subscriptions` 테이블에
      직접 `PRO_MONTHLY`/`ACTIVE` row 삽입 → 재시도 시 201로 성공 확인
- [x] **Figma 화면 대조 완료(2026-08-12)** — 사용자가 실제 포트폴리오 화면 노드(`4955:2076` 하위, 21개 섹션)
      링크를 제공해 `docs/design/figma.md`에 정리하고 Workflow로 12개 섹션(MVP 범위) 문구·플로우를 백엔드와
      전수 대조했다. 결과는 `plans/260811-portfolio-module/PLAN-AGENT.md` PA-12~PA-14(백엔드 버그 3건)로
      반영, 나머지는 아래 PH-04(정책 확인 필요 3건)와 디자인팀 문구 정비 TODO(코드 아님, 이 plan 범위 밖)로 분리.

## PH-04. QA에서 발견된 정책 미확정 항목 확인 (2026-08-12)

depends on: (없음)

Figma 화면엔 있는데 백엔드·설계문서 어디에도 없는 3건. 버그가 아니라 MVP 스코프 판단이 필요한 항목 —
PM에게 확인 부탁드립니다.

- [ ] **동일 구성 포트폴리오 중복 방지** — "동일한 작품으로 구성된 포트폴리오가 이미 있어요" 토스트가
      포트폴리오 복제하기(`7160:83461`)·성인물 작품 포함 모달(`7039:49304`) 등 3개 화면에 독립적으로
      등장하는데, 백엔드엔 이 검증이 없고 설계문서에도 언급이 없다. MVP에 필요한 기능인지 확인 필요 —
      필요하다면 새 PA 태스크로 분리.
- [ ] **포트폴리오 생성 최소 작품 개수** — "2개 이상의 작품을 선택해주세요" 토스트(`7160:83461` 복제 화면)가
      있는데, `CreatePortfolioRequest` 코드 주석과 설계문서는 "0개로 생성 가능"이라고 명시한다(포트폴리오에
      작품 0개인 경우 화면도 별도로 존재). 실제 정책이 무엇인지 확인 필요.
- [ ] **"추가할 포트폴리오가 없어요" 빈상태 카피 검토** — 이 문구가 가리키는 상황(LIVE 포트폴리오가 하나도
      없음)은 작가 페이지가 항상 lazy 생성돼 백엔드 구조상 발생 불가능하다(`GET /api/portfolios/selectable`이
      최소 1건은 항상 보장). 카피 오류로 보이나, 디자인 의도를 다시 확인해달라.
