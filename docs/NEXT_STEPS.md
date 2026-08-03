# 다음 세션 시작 가이드 (2026-08-03 점검 시점)

> 이 문서는 세션 인수인계용 체크리스트다. 장기 로드맵 전체는 `docs/roadmap.md`가 정본이고,
> 이 문서는 "지금 당장 뭐부터 볼지"만 정리한다. 작업 완료 후 이 파일은 삭제해도 된다.

## 2026-08-03 점검 결과: 코드 변경 없음, 아래 항목 전부 그대로 유효

`main` 최신 커밋이 2026-08-01 마지막 세션 종료 시점(`324ae60`)과 동일 — 그 사이 커밋 없음.
아래 4개 항목(1~4)을 코드 grep + `gh api`로 재확인한 결과 전부 그대로 열려 있다:
- GitHub Actions 워크플로우 권한: `gh api repos/pack-in/at-crew-backend/actions/permissions/workflow` →
  `can_approve_pull_request_reviews: false`, `release-please` 워크플로우가 이후 모든 push에서도 계속 실패 중(이슈 #38)
- 나머지 TODO/스텁 3건은 소스에 그대로 존재(아래 표 참고)

부수 정리: 지난 세션에서 worker 에이전트가 남긴 로컬 잔여 브랜치(`worktree-agent-*`, `feat/recruit-rest-docs`)와
stale remote-tracking refs를 정리했다(원격 브랜치는 이미 PR 병합 시 삭제되어 있었음).

## 완료된 것 (이력)

- **2026-08-01**: search/company의 recruit 포트 스텁 → 실구현 교체(PR [#41](https://github.com/pack-in/at-crew-backend/pull/41), 이슈 [#36](https://github.com/pack-in/at-crew-backend/issues/36)), 최근 본 작가 자동 기록 이벤트 연동(PR [#40](https://github.com/pack-in/at-crew-backend/pull/40), 이슈 [#37](https://github.com/pack-in/at-crew-backend/issues/37)), 관련 설계 문서 3건 정합성 정정 — 전부 main 병합, 테스트 그린
- **2026-07-31**: 로드맵 대규모 갱신(Polar 결제·PASS 인증·recruit 스코프 확대·i18n/관리자/OG카드 신규 항목), 글로벌 시간대 UTC 전환(PR #30), MariaDB P4(PR #32), recruit 모듈 구현(PR #34), recruit REST Docs(PR #35)

recruit 모듈(구인글/팀원모집글/구직글/지원/끌어올리기/관심작가/포트연동/최근본작가) 스코프는 이제 전부 완료 상태다.

## 지금 바로 처리할 것 (우선순위순)

### 1. GitHub Actions 권한 이슈 (인프라, 저장소 관리자만 처리 가능) — 이슈 [#38](https://github.com/pack-in/at-crew-backend/issues/38)
`release-please` 워크플로우가 main push마다 "GitHub Actions is not permitted to create or approve pull
requests"로 실패 중(최근 확인: 2026-08-01 이후 push 4건 전부 동일 증상). 저장소 Settings → Actions →
General → Workflow permissions에서 "Allow GitHub Actions to create and approve pull requests" 활성화
필요. 코드 작업이 아니라 사람이 직접 처리해야 함(오케스트레이터가 `gh api`로 확인은 했지만 저장소 보안
설정 변경은 자동 실행하지 않음).

### 2. (검토만) 설계-구현 불일치 1건 — 이슈 [#39](https://github.com/pack-in/at-crew-backend/issues/39)
`docs/design/global-timezone-strategy.md` §3.3.1은 `Member.timezone`을 nullable+UTC 폴백으로 규정했지만
실제 `Member.validateTimezone()`은 가입 시 필수값으로 강제한다(2026-07-15 커밋). 버그라기보다 "가입 시
항상 값이 있어 폴백이 불필요해진" 더 단순한 설계일 가능성 — auth/member를 다음에 다룰 때 문서를 실제
구현에 맞게 정정할지 결정 필요.

### 3. recruit 검색 후속 과제 (PR #41에서 의도적으로 남긴 것)
지금 동작에 문제는 없지만, 데이터가 늘거나 기획이 확정되면 손봐야 하는 항목들이다.

1. **태그 정본 목록 정규화** — recruit의 `roles`/`genres`는 작성자가 입력하는 자유 문자열이고, 검색
   필터 chip은 `ArtworkRole` enum이다. 지금은 enum 상수 이름으로만 문자열 비교하므로 실질적으로 거의
   매칭되지 않는다. Notion 태그 정본 목록이 확정되면 양쪽을 같은 어휘로 정규화해야 한다
   (`SearchQuery.java` TODO, `search-module-design.md` §9-2와 동일 과제)
2. **recruit 검색의 ES 색인 이관 검토** — 현재 제목 `LIKE '%q%'` + 태그 EXISTS 조인이라 인덱스를 타지
   않는다. 공개 글이 늘면 artwork처럼 색인 파이프라인으로 옮기는 편이 낫다
   (`RecruitSearchQueryRepository` 클래스 주석에 근거 기록)
3. **병합 검색의 알려진 제약** — 포트폴리오와 recruit을 함께 요청하면 (a) 정렬은 항상 최신순 고정
   (관련도 점수를 소스 간 비교할 수 없음), (b) 작품 분야·창작 유형·연령대·소재 대상 필터가 걸리면
   recruit은 결과에서 제외, (c) 커서가 `(createdAtMillis, id)` 값 기반이라 서로 다른 소스의 항목이
   **밀리초까지 동일**하면 경계에서 한 건이 밀릴 수 있다. 기획상 문제가 되면 소스별 서브커서 방식으로
   재설계 필요
4. **관심 작가 검색 후보 로딩** — `q`가 있으면 해당 기업이 좋아요한 작가 ID를 전부 읽어 member 모듈에
   넘기고 `IN` 절로 페이지네이션한다. 좋아요 규모가 커지면(수천 건) 개선 필요
5. **`CompanyInfo.hasOpenJobPosting` 조회 비용** — 기업 프로필 단건 조회마다 recruit `exists` 쿼리가
   1회 추가된다. 목록 API가 생기면 N+1이 되므로 그때 일괄 조회/캐시 검토

### 4. 코드에 남아 있는 스텁·TODO 지도 (착수 전 참고용)
다른 모듈 작업 시 함께 풀어야 하는 것들. 각 항목 옆이 선행 조건이다. (2026-08-03 grep으로 전부 재확인)

| 위치 | 내용 | 선행 |
|---|---|---|
| `RecruitServiceImpl` boostJobPosting/boostTeamPosting | 끌어올리기 구매 개수 확인·차감 | 로드맵 5(Polar 결제) |
| `JobPostingController` 관리자 섹션, `BannerController` | 인증만 요구, Role 검증 없음 | 로드맵 8(관리자 Role 체계) |
| `Company.verified` | 항상 false, API 미노출 | 로드맵 1(기업 인증) |
| recruit 이미지 필드 | URL 문자열 저장만, 업로드 API 없음 | artwork Presigned URL 파이프라인 재사용 |
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
- `docs/design/global-timezone-strategy.md` — 시간대 설계(확정 상태, 이슈 #39 문서 정합성 검토 잔여)
