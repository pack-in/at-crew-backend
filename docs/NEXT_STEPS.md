# 다음 세션 시작 가이드 (2026-08-01 세션 종료 시점)

> 이 문서는 세션 인수인계용 체크리스트다. 장기 로드맵 전체는 `docs/roadmap.md`가 정본이고,
> 이 문서는 "지금 당장 뭐부터 볼지"만 정리한다. 작업 완료 후 이 파일은 삭제해도 된다.

## 2026-08-01 완료된 것

1. **search/company의 recruit 포트 스텁 → 실구현 교체** (PR [#41](https://github.com/pack-in/at-crew-backend/pull/41), 이슈 [#36](https://github.com/pack-in/at-crew-backend/issues/36))
2. **최근 본 작가 자동 기록 이벤트 연동** (PR [#40](https://github.com/pack-in/at-crew-backend/pull/40), 이슈 [#37](https://github.com/pack-in/at-crew-backend/issues/37))

상세는 아래 "지금 바로 처리할 것" 2·3번 항목 참고. 두 PR 모두 main 병합 완료, 전체 테스트 그린.

## 2026-07-31 완료된 것

1. 정식 서비스 기획서(`docs/AT-CREW_서비스기획서_전체_20260728.xlsx`) 전체 대조로 `docs/roadmap.md` 대규모 갱신
   - 결제 PG **Polar** 확정, 본인인증 **PASS**·기업인증 **수동 이메일 심사** 확정
   - recruit 스코프에 **끌어올리기·관심 작가** 복원(기획서 P0 요구사항으로 확인)
   - 신규 로드맵 항목 3개 추가: **7.다국어(i18n)**, **8.관리자/모더레이션 콘솔**, **9.소셜 공유 메타(OG 카드)**
2. 글로벌 시간대 **UTC 저장 확정 + 구현 완료** → main 병합 (PR [#30](https://github.com/pack-in/at-crew-backend/pull/30), 이슈 [#29](https://github.com/pack-in/at-crew-backend/issues/29))
3. MariaDB 마이그레이션 **P4 완료(artwork 모듈 전환)** → main 병합 (PR [#32](https://github.com/pack-in/at-crew-backend/pull/32), 이슈 [#31](https://github.com/pack-in/at-crew-backend/issues/31))
4. **recruit 모듈 구현 완료**(JobPosting/TeamPosting/JobSeekingPost CRUD, 지원/지원자관리, 끌어올리기, 관심 작가, `RecruitService` 공개 API) → main 병합 (PR [#34](https://github.com/pack-in/at-crew-backend/pull/34), 이슈 [#33](https://github.com/pack-in/at-crew-backend/issues/33))
5. **recruit REST Docs 테스트 작성 완료**(컨트롤러 5종 × `*ControllerValidationTest`+`*ApiDocTest`, 69개 테스트) → main 병합 (PR [#35](https://github.com/pack-in/at-crew-backend/pull/35))

네 PR 모두 워커 에이전트가 구현 → 테스트 그린 확인 → 이슈/PR 생성 → 병합까지 전부 자동으로 처리했다.
recruit는 main과 15개 커밋 격차가 있어 병합 충돌(공용 JPA 인프라 중복 구현 등)이 있었고 별도로 해결했다.

## 지금 바로 처리할 것 (우선순위순)

### 1. ~~recruit REST Docs 테스트 작성~~ — 완료 (2026-07-31, PR [#35](https://github.com/pack-in/at-crew-backend/pull/35))
컨트롤러 5종(JobPosting/TeamPosting/JobSeekingPost/Application/LikedArtist) 각각에 `*ControllerValidationTest`
+ `*ApiDocTest` 작성, 총 69개 테스트 그린. 4개 워커 에이전트 병렬 작업 → 전체 스위트 통합 실행 시
`JobPostingApiDocTest`의 전역 공개 목록 조회가 다른 doc 테스트와 MariaDB Testcontainer를 공유하며
정확한 개수(`length()==1`)를 assert하다 실패하는 테스트 격리 버그를 발견해 수정(ID 포함 여부 검증으로 변경).
이슈 [#33](https://github.com/pack-in/at-crew-backend/issues/33) 체크리스트 항목 해소.

### 2. ~~search/company 모듈의 recruit 포트 스텁을 실구현으로 교체~~ — 완료 (2026-08-01, PR [#41](https://github.com/pack-in/at-crew-backend/pull/41), 이슈 [#36](https://github.com/pack-in/at-crew-backend/issues/36))
`CompanyRecruitPort`/`RecruitSearchPort` Noop 스텁 폐기, `RecruitService` 직접 주입으로 교체.
관심 작가 검색어 필터는 설계(§2.7)와 달리 search가 아닌 `recruit → member` 경로로 구현(search에 작가
검색 인덱스가 없고, `search → recruit` 의존이 이미 있어 반대 방향을 추가하면 순환 의존이 되기 때문).
`docs/design/recruit-module-design.md` §2.7에 정정 반영됨.

### 3. ~~최근 본 작가 자동 기록 연동~~ — 완료 (2026-08-01, PR [#40](https://github.com/pack-in/at-crew-backend/pull/40), 이슈 [#37](https://github.com/pack-in/at-crew-backend/issues/37))
member 마이페이지 조회 시 `ArtistProfileViewedEvent` 발행 → recruit `@ApplicationModuleListener`가 구독해
자동 기록.

### 4. ~~설계 문서 정합성 정정~~ — 완료 (2026-08-01)
PR #41로 포트 스텁이 폐기됐는데 설계 문서가 "스텁으로 남긴다"고 서술하던 부분을 갱신 노트로 정정:
`docs/design/search-module-design.md` 머리말, `docs/design/company-profile-module-design.md` 머리말,
`docs/roadmap.md` 검색 모듈 절. 각 문서는 설계 당시 기록은 보존하고 "2026-08-01 갱신" 노트로 현재 상태를
덧붙이는 방식으로 처리(recruit-module-design.md §2.7과 동일 컨벤션).

### 5. recruit 검색 후속 과제 (PR #41에서 의도적으로 남긴 것)
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

### 6. 코드에 남아 있는 스텁·TODO 지도 (착수 전 참고용)
다른 모듈 작업 시 함께 풀어야 하는 것들. 각 항목 옆이 선행 조건이다.

| 위치 | 내용 | 선행 |
|---|---|---|
| `RecruitServiceImpl` boostJobPosting/boostTeamPosting | 끌어올리기 구매 개수 확인·차감 | 로드맵 5(Polar 결제) |
| `JobPostingController` 관리자 섹션, `BannerController` | 인증만 요구, Role 검증 없음 | 로드맵 8(관리자 Role 체계) |
| `Company.verified` | 항상 false, API 미노출 | 로드맵 1(기업 인증) |
| recruit 이미지 필드 | URL 문자열 저장만, 업로드 API 없음 | artwork Presigned URL 파이프라인 재사용 |
| `ArtworkField.PRINT_COMIC` | 피그마는 `WEBNOVEL` — enum 교체 + 데이터 마이그레이션 필요 | 피그마 정본 확인 |
| `ActiveRegion`(company) | 피그마에서 옵션 값 특정 실패 | 피그마 확인 |
| search 분석기 | Phase 1은 `standard`, nori 도입 미정 | 검색 품질 이슈 발생 시 |

### 7. GitHub Actions 권한 이슈 (인프라, 낮은 우선순위)
`release-please` 워크플로우가 오늘 병합한 PR 3건 전부에서 "GitHub Actions is not permitted to create or
approve pull requests"로 실패했다. 저장소 Settings → Actions → General → Workflow permissions에서
"Allow GitHub Actions to create and approve pull requests" 활성화 필요해 보임(기능 자체에는 영향 없음).

### 8. (검토만) 설계-구현 불일치 1건
`docs/design/global-timezone-strategy.md` §3.3.1은 `Member.timezone`을 nullable+UTC 폴백으로 규정했지만
실제 `Member.validateTimezone()`은 가입 시 필수값으로 강제한다(2026-07-15 커밋). 버그라기보다 "가입 시
항상 값이 있어 폴백이 불필요해진" 더 단순한 설계일 가능성 — auth/member를 다음에 다룰 때 문서를 실제
구현에 맞게 정정할지 결정 필요.

## 로드맵에 남은 큰 항목 (설계부터 필요, `docs/roadmap.md` 참고)

이 항목들은 코드 작업 전에 설계 결정이 더 필요해 이번 세션에서 착수하지 않았다.

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
- `docs/design/recruit-module-design.md` — recruit 모듈 상세 설계(오늘 갱신됨)
- `docs/design/mariadb-migration-design.md` — MariaDB 전환 상세(§13에 artwork 전환 함정 기록)
- `docs/design/global-timezone-strategy.md` — 시간대 설계(확정 상태)
