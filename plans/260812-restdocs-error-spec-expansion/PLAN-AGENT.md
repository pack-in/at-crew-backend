# PLAN-AGENT — restdocs-api-spec 에러 문서 확장

## 배경

`plans/260812-restdocs-error-spec-poc/`에서 portfolio 모듈 `ARTWORK_NOT_FOUND` 케이스 하나로
`restdocs-api-spec` 파이프라인을 검증했다(성공). Spring Boot 4.0.6 조합에서 라이브러리 호환성
문제 없음을 확인했고, `docs/testing/rest-docs-guide.md` §6에 패턴이 문서화돼 있다. 이 계획은 그
검증된 파이프라인을 나머지 8개 모듈(artwork/billing/media/auth/community/member/recruit/search)로
확장하는 실행 계획이다.

## 결정 (그릴링 결과)

- **서빙 전환**: 애초엔 범위 밖으로 뒀으나, 8개 모듈 확장 완료 후 PH-01 검토에서 "company도 마저
  커버 후 전환"으로 승인됨. 완전 대체(springdoc 동적 생성을 끄고 정적 스펙만 서빙) 방식 — 병행
  서빙(일부만 정적)은 커스텀 merge 컨트롤러가 필요해 난이도가 높아 채택하지 않는다. PA-10(company)
  완료 후 PA-11로 진행한다.
- **company 모듈 제외**: 로드맵상 이번 출시 마일스톤 재점검 범위 밖이고, `mvp-scenario-walkthrough.md`
  에도 전혀 등장하지 않으며, `Company.verified`가 항상 false로 고정된 스텁 상태 — 지금 투자할
  이유가 약함. 이번 8개 모듈에 포함하지 않는다.
  **(2026-08-12 갱신)** PH-01 검토에서 "company도 마저 커버 후 서빙 전환"으로 결정 — company는
  `CompanyErrorCode` 6개뿐으로 규모가 작아 PA-10으로 추가한다.
- **커버리지 단위**: 4xx/5xx `@ApiResponse` 선언 123개 전수가 아니라, **모듈 내 고유 에러코드마다
  1개씩**만 REST Docs로 커버한다. 같은 코드가 여러 엔드포인트에 반복 선언돼 있어도 한 번만 검증하면
  됨 — mismatch는 코드-설명 짝이 다른 데서 생기지, 같은 코드의 반복에서 생기지 않는다.
- **MariaDB Testcontainer flaky 이슈는 이번 범위 밖**. 구조적 문제(컨테이너 재사용 전략이
  `SharedContainersConfig` 방식과 개별 `static` 필드 방식 두 갈래로 혼재, 컨텍스트 캐시 키가
  갈려 커넥션 풀이 여러 개 동시에 뜸)로 확인됐다. 사용자가 추후 다시 요청하기로 함 —
  agent 메모리 `project_restdocs_flaky_followup.md`에 남겨둠. 이번 작업에서는 각 모듈 태스크가
  전체 test suite를 돌리지 않고 자기 모듈의 신규 테스트 클래스만 개별 실행해서 flaky를 회피한다.
- **테스트 파일 구성**: 컨트롤러당 `<Controller명>ErrorApiSpecTest.java` 신규 파일. PoC에서 쓴
  `PortfolioErrorApiSpecTest.java` 패턴(`MockMvcRestDocumentationWrapper.document(...)` +
  `ResourceDocumentation.resource(...)`) 그대로 따른다.

## 검증

- 각 모듈 태스크는 자기 모듈의 신규 테스트 클래스만 개별 실행(`./gradlew test --tests "..."`)해서
  통과를 확인한다 — 전체 suite 실행 금지(flaky 회피).
- 모든 모듈 태스크 완료 후, 통합 태스크(PA-09)에서 `./gradlew openapi3 -x test`로 전체 스펙을
  생성하고, 새로 커버된 모든 케이스의 `message`가 실제 ErrorCode enum 값과 정확히 일치하는지
  확인한다.

## 금지 범위

- MariaDB Testcontainer flaky 구조 정리 — `plans/260812-testcontainer-reuse-unification/`로 분리.
- 전체 test suite 실행(각 모듈 태스크에서 자기 모듈 테스트만 개별 실행).
- 커밋.

(company 모듈, Swagger UI 서빙 전환은 2026-08-12 PH-01 검토에서 승인되어 PA-10/PA-11로 이동 —
더 이상 금지 범위 아님.)

## PA-01. artwork 모듈 에러 케이스 커버리지

depends on: (없음, 다른 모듈 태스크와 병렬 가능)

3개 컨트롤러, 4xx/5xx `@ApiResponse` 선언 50개(현재 가장 많음) — `ArtworkErrorCode`의 고유 코드마다
REST Docs 테스트 1개씩 작성.

- [x] `ArtworkErrorCode`(+`BookmarkErrorCode`) 고유 코드 16개 목록 파악
- [x] 컨트롤러별 `<Controller명>ErrorApiSpecTest.java` 3개 작성(Artwork/Bookmark/Trash), 14개 코드
      문서화 — 2개(`BOOKMARK_FOLDER_NAME_BLANK`, `PRESIGN_FAILED`)는 공개 API로 트리거 불가한 방어
      코드/dead code라 스킵
- [x] 3개 클래스 개별 실행 — 14개 테스트 전부 통과

## PA-02. billing 모듈 에러 케이스 커버리지

depends on: (없음, 다른 모듈 태스크와 병렬 가능)

1개 컨트롤러, `@ApiResponse` 선언 1개뿐 — 범위가 작음. `BillingErrorCode`의 고유 코드 기준으로
커버.

- [x] `BillingErrorCode` 고유 코드 1개(`PRO_PLAN_REQUIRED`) 확인 — billing 자신의 컨트롤러(2개
      엔드포인트뿐, 사전조사 "3개"는 부정확)로는 트리거 불가. `PlanServiceImpl.assertPro()`를
      경유해 portfolio 모듈에서만 소비됨(portfolio 담당 영역)
- [x] 신규 파일 없음 — 커버 대상이 자기 모듈에 없어 정당하게 0건

## PA-03. media 모듈 에러 케이스 커버리지

depends on: (없음, 다른 모듈 태스크와 병렬 가능)

2개 컨트롤러, 선언된 `@ApiResponse`가 0개 — Swagger 문서 자체에 에러 응답이 아예 없는 상태.
`MediaErrorCode`를 확인해서 실제로 던져지는 코드가 있다면 이번에 처음으로 문서화한다(단순
mismatch 수정이 아니라 누락 보완).

- [x] `MediaErrorCode` 자체가 존재하지 않음 확인 — 두 컨트롤러 모두 `@Hidden`이고 유일한 에러는
      공통 `HTTP_401`(X-Internal-Secret 불일치)
- [x] `MediaInternalControllerErrorApiSpecTest.java` 작성
- [x] 개별 실행 재확인(최초 보고는 Testcontainer 자원경합으로 미확인 상태였음) — 통과

## PA-04. auth 모듈 에러 케이스 커버리지

depends on: (없음, 다른 모듈 태스크와 병렬 가능)

1개 컨트롤러, `@ApiResponse` 선언 8개, 기존 `AuthControllerValidationTest` 있음(입력검증용,
REST Docs 에러 문서화는 아님). `AuthErrorCode` 고유 코드 기준으로 커버.

- [x] `AuthErrorCode` 고유 코드 8개 목록 파악
- [x] `AuthControllerErrorApiSpecTest.java` 작성, 5개 코드 문서화 — 나머지 3개(`MEMBER_NOT_REGISTERED`,
      `INVALID_FIREBASE_TOKEN`, `UNSUPPORTED_AUTH_PROVIDER`)는 테스트 환경에 Firebase 미설정이라
      NoOp verifier가 선차단해 도달 불가
- [x] 개별 실행 — 5개 테스트 전부 통과

## PA-05. community 모듈 에러 케이스 커버리지

depends on: (없음, 다른 모듈 태스크와 병렬 가능)

2개 컨트롤러, `@ApiResponse` 선언 6개. `CommunityErrorCode` 고유 코드 기준으로 커버.

- [x] `CommunityErrorCode` 고유 코드 3개 목록 파악
- [x] `BannerControllerErrorApiSpecTest.java`/`CommunityControllerErrorApiSpecTest.java` 작성, 2개
      코드 문서화 — `INVALID_CURSOR`는 community 모듈 내 실제 throw 지점이 없는 죽은 코드(위임받는
      artwork/member/recruit가 각자의 `INVALID_CURSOR`를 던짐)라 스킵
- [x] 개별 실행 — 2개 테스트 전부 통과

## PA-06. member 모듈 에러 케이스 커버리지

depends on: (없음, 다른 모듈 태스크와 병렬 가능)

2개 컨트롤러(현재 `MemberController`만 `*ApiDocTest` 있음), `@ApiResponse` 선언 9개.
`MemberErrorCode` 고유 코드 기준으로 커버.

- [x] `MemberErrorCode` 고유 코드 15개 목록 파악
- [x] `MemberControllerErrorApiSpecTest.java`/`DevMemberControllerErrorApiSpecTest.java` 작성, 9개
      코드 문서화 — 나머지 6개는 auth 모듈 전용 가입 경로, TOCTOU 레이스 전용, dead code, member
      컨트롤러에 미노출된 인터페이스 메서드라 스킵(사유 기록됨)
- [x] 개별 실행 — 9개 테스트 전부 통과

## PA-07. recruit 모듈 에러 케이스 커버리지

depends on: (없음, 다른 모듈 태스크와 병렬 가능)

6개 컨트롤러, 54개 엔드포인트 — 가장 규모가 크다. `@ApiResponse` 4xx/5xx가 개별 선언 없이
`OpenApiConfig`의 전역 자동 주입에만 의존하고 있고, 이미 에러케이스 REST Docs 테스트 9건이
존재(단, `resource()` wrapper 사용 여부 미확인 — 확인 후 필요하면 전환). `RecruitErrorCode` 고유
코드 기준으로 나머지를 커버.

- [x] 기존 9건은 `resource()` wrapper 미사용(구식 형식, `openapi3`가 못 읽음) 확인 — 손대지 않고
      별도 신규 파일로 대체
- [x] `RecruitErrorCode` 고유 코드 13개 전부 목록 파악
- [x] 4개 신규 파일(JobPosting/TeamPosting/JobSeekingPost/ApplicationErrorApiSpecTest)로 13개 코드
      전부 문서화
- [x] 개별 실행 — 최초 실행은 다른 워커들과의 Testcontainer 자원경합으로 미완료 보고됐으나, 재검증
      과정에서 실제 assertion 실패 1건 발견: `JobSeekingPostErrorApiSpecTest`의 `INVALID_CURSOR`
      테스트가 잘못된 엔드포인트(`GET /api/recruit/job-seeking-posts`, 커서 형식 검증 자체가 없음)를
      대상으로 작성돼 있었음. `GET /api/recruit/job-postings`(`CompositeCursor.decode` 사용)로 옮겨
      수정 — 13개 테스트 전부 통과, `openapi3.yaml`에 13개 코드 전부 반영 확인

## PA-08. search 모듈 에러 케이스 커버리지

depends on: (없음, 다른 모듈 태스크와 병렬 가능)

2개 컨트롤러, `@ApiResponse` 선언 0개(전역 자동 주입 의존 추정). `SearchErrorCode` 고유 코드
기준으로 커버.

- [x] `SearchErrorCode` 고유 코드 4개 목록 파악
- [x] `SearchControllerErrorApiSpecTest.java`/`SearchAdminControllerErrorApiSpecTest.java` 작성, 3개
      코드 문서화 — `REINDEX_FAILED`는 실제 ES 인프라 장애 시나리오라 공개 API로 재현 불가해 스킵
- [x] 개별 실행 — 3개 테스트 전부 통과

## PA-09. 통합 검증 및 문서 갱신

depends on: PA-01, PA-02, PA-03, PA-04, PA-05, PA-06, PA-07, PA-08

`./gradlew openapi3 -x test`로 전체 스펙을 생성해, 8개 모듈에서 새로 커버한 모든 케이스의
`message`가 실제 ErrorCode enum 값과 정확히 일치하는지 확인한다. `docs/testing/rest-docs-guide.md`
§6에 확장 완료 상태(모듈별 커버리지, 남은 갭이 있다면 명시)를 반영한다.

- [x] `./gradlew openapi3 -x test` 성공, `build/api-spec/openapi3.yaml`에 8개 모듈 케이스 전부 반영
      확인(최초 실행 시 recruit 13개 누락 발견 → 재검증·수정 후 전부 반영 확인)
- [x] 전체 44개 고유 code에 대해 소스(`*ErrorCode.java` 전체)와 자동 교차검증 스크립트로 재확인 —
      소스에 없는 code 0건, 실제 message와 다른 값 0건. (`ARTWORK_NOT_FOUND`/`INVALID_CURSOR`가
      YAML 안에서 두 번 이상 다른 message로 등장하는 건 버그가 아니라 서로 다른 모듈의 동명 코드가
      각자 다른 문구를 갖기 때문 — 정상)
- [x] `docs/testing/rest-docs-guide.md` §7 갱신(모듈별 커버리지 표, 발견·수정한 결함, 남은 갭)

## PA-10. company 모듈 에러 케이스 커버리지

depends on: (없음)

`CompanyErrorCode` 6개(`COMPANY_NOT_FOUND`, `COMPANY_ALREADY_EXISTS`, `COMPANY_ACCESS_DENIED`,
`CAREER_NOT_FOUND`, `CAREER_LIMIT_EXCEEDED`, `INVALID_CAREER_PERIOD`) — 다른 모듈과 동일한
패턴(`<Controller명>ErrorApiSpecTest.java`, `MockMvcRestDocumentationWrapper.document()` +
`ResourceDocumentation.resource()`)으로 커버한다. `CAREER_NOT_FOUND`/`CAREER_LIMIT_EXCEEDED`/
`INVALID_CAREER_PERIOD`는 member 모듈에도 동명 코드가 있었으나(PA-06에서 이미 커버) 서로 다른
enum이므로 company 쪽도 별도로 커버해야 한다.

- [x] `CompanyErrorCode` 고유 코드 6개 목록 파악
- [x] `CompanyControllerErrorApiSpecTest.java` 작성, 4개 코드 문서화 — `COMPANY_ACCESS_DENIED`(구조상
      항상 통과하는 assertOwner라 공개 API로 트리거 불가), `CAREER_NOT_FOUND`(company 모듈에 경력
      삭제/수정 API 자체가 없는 dead code)는 스킵
- [x] 개별 실행 재확인 — 4개 테스트 전부 통과

## PA-11. Swagger UI 실제 서빙 전환 → 회귀 발견 → 롤백 (2026-08-13)

depends on: PA-10

`build/api-spec/openapi3.yaml`(portfolio PoC + 8개 모듈 + company)을 Swagger UI가 실제로
서빙하도록 전환한다. 완전 대체 방식 — `springdoc.api-docs.enabled=false` +
`springdoc.swagger-ui.url`을 정적 리소스 경로로 지정(`AbstractSwaggerUiConfigProperties.url` 필드
활용, 이전 조사에서 확인된 방식). 병행 서빙(일부만 정적)은 채택하지 않는다.

**전환 후 커밋까지 했으나, 사용자 요청으로 후속 검토 중 심각한 회귀를 발견해 롤백했다.** 정적
스펙은 에러 케이스 REST Docs 테스트(`*ErrorApiSpecTest`)만 커버하는데, 기존 성공 시나리오
문서화 테스트(`*ApiDocTest`)는 `resource()` wrapper를 안 써서 이 스펙에 안 잡힌다. 결과적으로
전환 당시 정적 스펙은 경로 38개(전체 124개+ 중 일부)뿐이었고 성공(2xx) 응답이 하나도 없었다 —
Swagger UI를 이걸로 전환하면 개발자가 실제로 필요한 정보(대다수 엔드포인트·모든 성공 스키마)가
사라지는, 원래 문제보다 심각한 회귀였다. 상세: `docs/testing/rest-docs-guide.md` §8.

- [x] `openapi3.yaml`을 정적 리소스로 서빙하도록 배선 — Gradle `copyOpenApiSpec` Copy 태스크로
      `build/api-spec/openapi3.yaml` → `src/main/resources/static/openapi3.yaml` (Spring Boot
      기본 static 서빙 활용, 별도 컨트롤러 안 만듦). `processResources`/`bootRun`에 자동 연결하지
      않음(수동 2단계: `./gradlew openapi3 -x test && ./gradlew copyOpenApiSpec`) — flaky 이슈로
      자동화하면 매 빌드가 막힐 위험 때문
- [x] **계획 수정**: `springdoc.api-docs.enabled=false`는 채택 불가로 확인됨 — springdoc-openapi
      3.0.3에서 swagger-ui 모듈이 api-docs 기능 빈에 강결합돼 있어 끄면 `/swagger-ui/index.html`
      자체가 404가 됨(실측). 대신 `springdoc.swagger-ui.url: /openapi3.yaml`만 설정 — 사람이 보는
      화면은 완전히 정적 스펙으로 전환되지만, `/v3/api-docs` 원본 엔드포인트는 기술적으로 계속
      살아있고 요청하면 여전히 옛 동적 스펙을 반환함(아래 남은 갭 참고)
- [x] `SecurityConfig`에 `/openapi3.yaml` permitAll 추가(최초 검증에서 401 발견해 수정)
- [x] `./gradlew bootRun` 재기동 후 실제 서빙 확인 — `/openapi3.yaml`(200, company/portfolio 등
      신규 커버 코드 전부 포함), `/swagger-ui/index.html`(200), `/v3/api-docs/swagger-config`(url이
      `/openapi3.yaml`을 가리킴) 확인. 프로세스 정상 종료 확인
- [x] `globalErrorResponseCustomizer` 제거 여부 판단 — **제거하지 않기로 판단**(제거 안 함, 코드
      유지). `/v3/api-docs`가 여전히 살아있어 완전한 dead code가 아니고, REST Docs 커버리지 갭이
      남아있는 동안 폴백 확인 수단으로 가치 있음. 팀이 REST Docs를 단일 진실 공급원으로 못박기로
      하면 `/v3/api-docs` 자체를 막는 결정이 먼저 필요함(남은 갭으로 기록)
- [x] `docs/conventions/swagger.md`, `docs/testing/rest-docs-guide.md`(§8) 갱신
- [x] **(2026-08-13 추가)** 위에서 기록한 회귀를 사용자에게 보고 → 롤백 승인받음 →
      `application.yml`의 `swagger-ui.url` 재정의 제거, `SecurityConfig`의 `/openapi3.yaml`
      permitAll 제거, `build.gradle`의 `copyOpenApiSpec` 태스크 및 `static/openapi3.yaml` 삭제.
      `openapi3` 태스크 자체는 유지(에러 메시지 정확성 검증 용도로는 계속 유효). 재기동 검증 —
      `/v3/api-docs`가 다시 경로 42개(operation 기준 124개+), 상태코드
      200/201/204/400/401/403/404/409/410/428/429/500/503 전부 포함해서 반환함을 확인, `/v3/api-docs/swagger-config`의
      `url`이 다시 `/v3/api-docs`를 가리킴 확인. `docs/conventions/swagger.md`,
      `docs/testing/rest-docs-guide.md` §8 정정
