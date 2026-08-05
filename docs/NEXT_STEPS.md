# 다음 세션 시작 가이드 (2026-08-04 점검 시점)

> 이 문서는 세션 인수인계용 체크리스트다. 장기 로드맵 전체는 `docs/roadmap.md`가 정본이고,
> 이 문서는 "지금 당장 뭐부터 볼지"만 정리한다. 작업 완료 후 이 파일은 삭제해도 된다.

## 2026-08-04 점검 결과

media 모듈 추출(이슈 [#42](https://github.com/pack-in/at-crew-backend/issues/42), PR
[#43](https://github.com/pack-in/at-crew-backend/pull/43)) main 병합 완료. **가장 중요한 후속 조치**:
recruit 이미지 업로드가 실제로 동작하려면 Cloudflare Worker 스크립트가 트리거/콜백 페이로드를
`artworkId` 단일 필드에서 `ownerType`+`ownerId`로 받도록 바뀌어야 한다. 롤아웃 순서는
`docs/design/media-module-design.md` §9.2에 정리돼 있다(서버 선배포 → Worker 배포 → 구 경로 제거 —
순서를 반대로 하면 기존 artwork 업로드가 깨진다는 걸 로컬 검증으로 확인함).

**2026-08-05**: Worker 관리 위치를 이 레포 `cloudflare-worker/`(wrangler로 버전관리)로 확정하고
스캐폴드(`wrangler.toml`, `src/index.js`, `README.md`)를 작성했다. `src/index.js`는
`ownerType`/`ownerId`/`imageKeys`/`variantProfile` 신규 계약, Cloudflare Images 바인딩(`env.IMAGES`)으로
원본 avif 변환·3:4 294px 썸네일 크롭·성인물 blur(20)까지 구현한 상태 — **미검증**(실제 Cloudflare
계정에 배포해본 적 없음). 남은 건 전부 이 레포 밖 사용자 작업: Cloudflare 계정 생성, `wrangler login`,
R2 버킷 생성(`at-crew-media` — artwork·recruit 공용, 버킷 이름은 2026-08-05 세션에서 `atcrew-artwork` →
`atcrew-media` → `at-crew-media`로 정정), 시크릿 등록(`wrangler secret put`), `wrangler dev --remote`로 로컬
검증, `wrangler deploy`, 서버 쪽 `WORKER_TRIGGER_URL`/`R2_*`/`WORKER_CALLBACK_SECRET`/
`ARTWORK_INTERNAL_SECRET` 환경변수 실제 값 채우기. 상세 절차는 `cloudflare-worker/README.md` 참고.

## 2026-08-03 점검 결과

`main` 최신 커밋이 2026-08-01 마지막 세션 종료 시점(`324ae60`)과 동일 — 그 사이 커밋 없음.
GitHub Actions 권한 이슈(#38)는 `release.yml` 삭제로, 설계-구현 불일치(#39)는 문서 정정으로 해소. 나머지
TODO/스텁 3건은 소스에 그대로 존재(아래 표 참고).

부수 정리: 지난 세션에서 worker 에이전트가 남긴 로컬 잔여 브랜치(`worktree-agent-*`, `feat/recruit-rest-docs`)와
stale remote-tracking refs를 정리했다(원격 브랜치는 이미 PR 병합 시 삭제되어 있었음).

## 완료된 것 (이력)

- **2026-08-04**: media 모듈 추출 — artwork에 내장된 Presigned URL/Worker/webhook/재시도/고아정리
  파이프라인을 범용 `media` 모듈로 뽑아 artwork·recruit이 공용 소비하도록 재설계(이슈
  [#42](https://github.com/pack-in/at-crew-backend/issues/42), PR
  [#43](https://github.com/pack-in/at-crew-backend/pull/43)). recruit 게시글(구인글/팀원모집글/구직글)
  이미지 업로드도 이번에 같이 구현됨(자식 테이블 3개, 발행 상태와 독립된 imageProcessingStatus).
  설계 QA·병합 과정에서 결함 다수 발견해 반영 완료(READY 전환 조건, Worker 롤아웃 순서, 영구삭제 후
  media_assets 잔존, 빈 이름 충돌 등 — 상세는 `docs/design/media-module-design.md` §11). main 병합,
  전체 `./gradlew build` 그린, CI 통과. **미완료**: Cloudflare Worker 스크립트 배포(위 점검 결과 참고),
  동시 webhook 경쟁 조건(artwork 원본부터 있던 기존 위험 수준이라 의도적으로 범위 밖).
- **2026-08-03**: 이슈 [#39](https://github.com/pack-in/at-crew-backend/issues/39) 해결 — `docs/design/global-timezone-strategy.md` §3.3.1을 실제 구현에 맞게 정정. 최초 설계는 `timezone` nullable+UTC 폴백이었으나, 구현 시 `Member.validateTimezone()`이 가입 경로(자체·소셜 공통)에서 null을 거부하도록 확정해 폴백 로직 자체가 불필요해졌다 — 버그가 아니라 더 단순한 확정 설계였음을 문서에 반영.
- **2026-08-03**: `release.yml`(release-please) 삭제로 이슈 [#38](https://github.com/pack-in/at-crew-backend/issues/38)
  해소. 실패 로그 확인 결과, 워크플로우 YAML에 `permissions: pull-requests: write`를 명시해도
  "GitHub Actions is not permitted to create or approve pull requests" 에러가 발생 — 이건 조직/저장소
  설정의 하드 게이트(`can_approve_pull_request_reviews`)이고 YAML 권한 블록으로 우회 불가함을 확인.
  `laiteu-be`(같은 pack-in 조직)의 `auto-pr.yml`도 API로 확인하니 동일하게 `false` — 다만 마지막 성공
  실행이 2026-07-07(조직이 이후 정책을 강화한 것으로 추정)이라 laiteu-be도 지금 트리거되면 똑같이
  막힐 가능성이 높음. 조직 admin 권한이 없어 근본 해결은 보류하고, 대신 이슈/PR 생성은 Claude가 `gh`
  CLI(개인 인증 토큰 사용, GITHUB_TOKEN과 무관)로 수행하고 merge/승인은 사람이 직접 하는 워크플로우로
  전환 — release-please 자동화 자체가 불필요해져 워크플로우 파일 삭제. 조직 admin이 나중에
  `https://github.com/organizations/pack-in/settings/actions`에서 "Allow GitHub Actions to create and
  approve pull requests"를 풀면 재도입 검토(git 이력에 원본 파일 남아있음).
- **2026-08-01**: search/company의 recruit 포트 스텁 → 실구현 교체(PR [#41](https://github.com/pack-in/at-crew-backend/pull/41), 이슈 [#36](https://github.com/pack-in/at-crew-backend/issues/36)), 최근 본 작가 자동 기록 이벤트 연동(PR [#40](https://github.com/pack-in/at-crew-backend/pull/40), 이슈 [#37](https://github.com/pack-in/at-crew-backend/issues/37)), 관련 설계 문서 3건 정합성 정정 — 전부 main 병합, 테스트 그린
- **2026-07-31**: 로드맵 대규모 갱신(Polar 결제·PASS 인증·recruit 스코프 확대·i18n/관리자/OG카드 신규 항목), 글로벌 시간대 UTC 전환(PR #30), MariaDB P4(PR #32), recruit 모듈 구현(PR #34), recruit REST Docs(PR #35)

recruit 모듈(구인글/팀원모집글/구직글/지원/끌어올리기/관심작가/포트연동/최근본작가) 스코프는 이제 전부 완료 상태다.

## 지금 바로 처리할 것 (우선순위순)

### 1. recruit 검색 후속 과제 (PR #41에서 의도적으로 남긴 것)
지금 동작에 문제는 없지만, 데이터가 늘거나 기획이 확정되면 손봐야 하는 항목들이다.

1. **태그 정본 목록 정규화** — recruit의 `roles`/`genres`는 작성자가 입력하는 자유 문자열이고, 검색
   필터 chip은 `ArtworkRole` enum이다. 지금은 enum 상수 이름으로만 문자열 비교하므로 실질적으로 거의
   매칭되지 않는다. Notion 태그 정본 목록이 확정되면 양쪽을 같은 어휘로 정규화해야 한다
   (`SearchQuery.java` TODO, `search-module-design.md` §9-2와 동일 과제)
2. **recruit 검색의 ES 색인 이관** — 완료함(2026-08-05). RDB `LIKE` + EXISTS 서브쿼리 기반이던
   `RecruitSearchService`/`RecruitSearchQueryRepository`(recruit 모듈)를 삭제하고, artwork와 동일하게
   `search` 모듈이 `recruit_posts` ES 인덱스를 소유·질의하도록 이관함 — `RecruitSearchIndexer`/
   `RecruitReindexService`/`search.internal.persistence.RecruitSearchQueryRepository` 참고
3. **병합 검색의 알려진 제약** — 포트폴리오와 recruit을 함께 요청하면 (a) 정렬은 항상 최신순 고정
   (관련도 점수를 소스 간 비교할 수 없음), (b) 작품 분야·창작 유형·연령대·소재 대상 필터가 걸리면
   recruit은 결과에서 제외. (a)(b)는 의도된 동작. (c) 커서 밀리초 충돌 경계 누락 문제는 소스별
   서브커서(`MergedSearchCursor`) 방식으로 재설계해 해소함 — `SearchServiceImpl.searchMerged` 참고
4. **관심 작가 검색 후보 로딩** — `q`가 있으면 해당 기업이 좋아요한 작가 ID를 전부 읽어 member 모듈에
   넘기고 `IN` 절로 페이지네이션한다. 좋아요 규모가 커지면(수천 건) 개선 필요
5. **`CompanyInfo.hasOpenJobPosting` 조회 비용** — 기업 프로필 단건 조회마다 recruit `exists` 쿼리가
   1회 추가된다. 목록 API가 생기면 N+1이 되므로 그때 일괄 조회/캐시 검토

### 2. 코드에 남아 있는 스텁·TODO 지도 (착수 전 참고용)
다른 모듈 작업 시 함께 풀어야 하는 것들. 각 항목 옆이 선행 조건이다. (2026-08-03 grep으로 전부 재확인)

| 위치 | 내용 | 선행 |
|---|---|---|
| `RecruitServiceImpl` boostJobPosting/boostTeamPosting | 끌어올리기 구매 개수 확인·차감 | 로드맵 5(Polar 결제) |
| `JobPostingController` 관리자 섹션, `BannerController` | 인증만 요구, Role 검증 없음 | 로드맵 8(관리자 Role 체계) |
| `Company.verified` | 항상 false, API 미노출 | 로드맵 1(기업 인증) |
| `ArtworkField.PRINT_COMIC` | 피그마는 `WEBNOVEL` — enum 교체 + 데이터 마이그레이션 필요 | 피그마 정본 확인 |
| `ActiveRegion`(company) | 피그마에서 옵션 값 특정 실패 | 피그마 확인 |
| search 분석기 | Phase 1은 `standard`, nori 도입 미정 | 검색 품질 이슈 발생 시 |

## 로드맵에 남은 큰 항목 (설계부터 필요, `docs/roadmap.md` 참고)

이 항목들은 코드 작업 전에 설계 결정이 더 필요해 아직 착수하지 않았다.

| 순서 | 항목 | 착수 전 필요한 것 |
|---|---|---|
| 1 | 본인/기업 인증(verification) | PASS 연동 방식 상세 설계(SDK/API 계약), `Member` 인증 상태 필드 스키마 |
| 5 | 결제/구독(Polar) | Polar API 연동 상세 설계(Checkout·웹훅 이벤트 스펙), 이메일 발송 인프라(현재 없음) |
| 6 | 설정 나머지 | 비교적 작음, 아무 때나 착수 가능 |
| 7 | 다국어(i18n) | `MessageSource`/로케일 전략 설계, `Member.primaryLanguage`/`postLanguages` 스키마 |
| 8 | 관리자/모더레이션 콘솔 | 관리자 Role 체계 설계(member 모듈에 아직 없음) |
| 9 | 소셜 공유 메타(OG 카드) | 서버 렌더링 방식 vs 메타 엔드포인트 방식 결정 |
| 0 (마이그레이션) | MariaDB P5·P6 | P5: Modulith 이벤트 레지스트리 전환+Mongo 제거. P6: prod 접속정보(호스팅 형태 확정 필요) |

## 참고 문서

- `docs/roadmap.md` — 로드맵 정본, 각 항목 Figma/기획서 근거 포함
- `docs/AT-CREW_서비스기획서_전체_20260728.xlsx` — 서비스 기획서 정본(요구사항/정책/화면/플로우/기능명세/QA)
- `docs/design/recruit-module-design.md` — recruit 모듈 상세 설계(2026-08-01 갱신 완료)
- `docs/design/mariadb-migration-design.md` — MariaDB 전환 상세(§6 P5·P6 미착수, §13에 artwork 전환 함정 기록)
- `docs/design/global-timezone-strategy.md` — 시간대 설계(확정 상태, 이슈 #39 문서 정합성 정정 완료)
