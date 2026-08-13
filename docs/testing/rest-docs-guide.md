# MockMvc + Spring REST Docs 테스트 가이드

## 1. 개요

### 왜 MockMvc + Spring REST Docs를 선택했나?

앳크루 백엔드 테스트는 기존 `RestClient` 기반 통합 테스트에서 **MockMvc + Spring REST Docs** 방식으로 전환되었다.

| 항목 | 기존 (RestClient + RANDOM_PORT) | 현재 (MockMvc + MOCK) |
|---|---|---|
| 서버 기동 방식 | 실제 포트에서 서버 기동 | 서블릿 컨테이너 없이 Mock 환경 |
| 테스트 속도 | 느림 (네트워크 스택 포함) | 빠름 (직접 DispatcherServlet 호출) |
| API 문서 자동화 | 별도 도구 필요 | REST Docs 스니펫 자동 생성 |
| 응답 검증 표현력 | `assertThat(body).contains(...)` | `jsonPath`, `status()` 등 선언적 DSL |

Spring REST Docs는 **테스트가 통과해야만 스니펫이 생성**되는 방식으로, 실제 동작과 문서가 항상 일치한다는 것을 보장한다. Swagger 어노테이션처럼 코드와 문서가 따로 노는 문제를 원천 차단한다.

---

## 2. 테스트 계층 구조

```
src/test/java/com/atcrew/
├── support/
│   └── RestDocsIntegrationSupport.java   ← 통합 테스트 공통 기반 클래스
│
├── SecurityIntegrationTest.java           ← [통합] 보안 필터 체인 검증
│
├── auth/
│   ├── internal/web/
│   │   └── AuthControllerValidationTest.java  ← [검증] 입력 검증 단위 테스트
│   └── docs/
│       └── AuthApiDocTest.java                ← [문서화] 인증 API 스니펫 생성
│
└── member/
    ├── internal/web/
    │   └── MemberControllerValidationTest.java ← [검증] 입력 검증 단위 테스트
    └── docs/
        └── MemberApiDocTest.java               ← [문서화] 회원 API 스니펫 생성
```

### 계층별 역할

#### 통합 테스트 (`*IntegrationTest`)
- `RestDocsIntegrationSupport` 상속
- MongoDB Testcontainer + 전체 Spring 컨텍스트 기동
- 보안 필터 체인, 인증·인가 흐름 등 **전체 스택**을 검증
- 대표 케이스에만 REST Docs 스니펫 추가

#### 검증 테스트 (`*ValidationTest`)
- `standaloneSetup`으로 **컨트롤러만** 기동 (DB·보안 없음)
- Bean Validation (`@NotBlank`, `@Size`, `@Pattern` 등) 규칙 집중 검증
- 가볍고 빠르게 실행 (Testcontainer 불필요)
- `@ExtendWith(RestDocumentationExtension.class)`로 REST Docs 통합

#### 문서화 테스트 (`*ApiDocTest`)
- `RestDocsIntegrationSupport` 상속
- 실제 API를 호출해 **성공 시나리오 응답 스니펫** 생성
- `requestFields()`, `relaxedResponseFields()` 등으로 필드 설명 기재

---

## 3. RestDocsIntegrationSupport 사용법

```java
// 통합 테스트 또는 문서화 테스트 작성 시 상속
class MyFeatureDocTest extends RestDocsIntegrationSupport {

    @Autowired
    MyService myService; // 추가 빈 주입 가능

    @Test
    void 내_API_성공_문서화() throws Exception {
        mockMvc.perform(post("/api/my-endpoint")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"field\": \"value\"}"))
                .andExpect(status().isOk())
                .andDo(document("my-module/my-endpoint",
                        requestFields(
                                fieldWithPath("field").description("설명")
                        ),
                        relaxedResponseFields(
                                fieldWithPath("code").description("응답 코드")
                        )
                ));
    }
}
```

### 인증이 필요한 엔드포인트 테스트

```java
// 1. 회원가입 API로 토큰 획득 (권장)
MvcResult result = mockMvc.perform(post("/api/auth/email/register")
        .contentType(MediaType.APPLICATION_JSON)
        .content(/* 가입 바디 */))
    .andExpect(status().isCreated())
    .andReturn();

String accessToken = objectMapper.readTree(result.getResponse().getContentAsString())
        .at("/data/accessToken").asText();

// 2. Authorization 헤더에 Bearer 토큰 포함
mockMvc.perform(patch("/api/members/me/name")
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
        .contentType(MediaType.APPLICATION_JSON)
        .content(/* 요청 바디 */))
    .andExpect(status().isNoContent());
```

---

## 4. 새 API 개발 시 테스트 작성 체크리스트

- [ ] **검증 테스트** (`*ValidationTest`) 작성
  - 필수 필드 blank/null 케이스
  - 길이·범위 초과 케이스
  - 형식 위반 케이스 (이메일·핸들·날짜 등)
  - 각 케이스에 `andDo(document("모듈/validation/케이스-이름"))` 추가
- [ ] **문서화 테스트** (`*ApiDocTest`) 작성
  - 성공 시나리오 최소 1개
  - `requestFields()` 또는 `relaxedRequestFields()`로 요청 필드 설명
  - `relaxedResponseFields()`로 핵심 응답 필드 설명
  - 인증 필요 엔드포인트: 회원가입 API로 토큰 획득 후 헤더에 포함
- [ ] **통합 테스트** (보안 관련 변경 시) 확인
  - 새 공개 엔드포인트: `핸들_조회_토큰_없이_401_아님` 패턴으로 추가
  - 새 보호 엔드포인트: `토큰_없음_401` 패턴으로 추가
- [ ] **(선택) 에러 케이스 REST Docs** — 새 도메인 에러 코드를 추가했다면
  `*ErrorApiSpecTest.java`(`MockMvcRestDocumentationWrapper.document()` + `resource()`, §6~§8
  참고)로 대표 케이스 1건을 문서화하고 `./gradlew openapi3 -x test`로 생성된 YAML의 `message`가
  실제 ErrorCode enum 값과 일치하는지 확인하면 좋다. Swagger UI 서빙과는 무관하다(§8) — 순수
  검증 목적.

---

## 5. 생성되는 스니펫 위치 및 내용

테스트 실행 후 스니펫은 `build/generated-snippets/` 아래에 생성된다.

```
build/generated-snippets/
├── auth/
│   ├── email-register/
│   │   ├── http-request.adoc       ← HTTP 요청 원문
│   │   ├── http-response.adoc      ← HTTP 응답 원문
│   │   ├── request-fields.adoc     ← 요청 필드 테이블
│   │   └── response-fields.adoc    ← 응답 필드 테이블
│   ├── email-login/
│   ├── token-refresh/
│   └── validation/
│       ├── email-login-blank-email/
│       └── ...
├── member/
│   ├── get-by-handle/
│   ├── update-name/
│   ├── add-career/
│   └── validation/
│       └── ...
└── security/
    └── unauthenticated/
```

`@AutoConfigureRestDocs`의 설정값:
- `uriScheme = "https"` — 스니펫에 표시되는 URI 스킴
- `uriHost = "api.atcrew.co.kr"` — 스니펫에 표시되는 호스트
- `uriPort = 443` — 스니펫에 표시되는 포트

---

## 6. 향후 Swagger 자동화 계획 (PoC 완료)

현재는 REST Docs 스니펫(`.adoc` 파일)을 생성하는 단계다. **스니펫 → OpenAPI 스펙 → Swagger UI** 파이프라인 구축을 위해 `restdocs-api-spec` 도입 PoC를 진행했고, 이 프로젝트의 스택 조합에서 정상 동작함을 확인했다.

### restdocs-api-spec 도입

[`ePages-de/restdocs-api-spec`](https://github.com/ePages-de/restdocs-api-spec)을 사용하면 REST Docs 테스트에서 OpenAPI 3.0 스펙을 자동 생성할 수 있다.

```groovy
// build.gradle
plugins {
    id 'com.epages.restdocs-api-spec' version '0.20.1'
}

testImplementation 'com.epages:restdocs-api-spec-mockmvc:0.20.1'

// openapi3 태스크 설정
openapi3 {
    server = 'https://api.atcrew.co.kr'
    title = '앳크루 API'
    version = project.version
    format = 'yaml'
}
```

**호환성 참고**: `restdocs-api-spec` 메인테이너는 0.20.x 라인에서 Spring Boot 4 관련 minor issue 가능성을 경고한 바 있으나, 이 프로젝트의 Spring Boot 4.0.6 + spring-restdocs-mockmvc 4.0.0 + restdocs-api-spec-mockmvc 0.20.1 조합에서는 `ClassCastException`, `NoSuchMethodError` 등 라이브러리 내부 오류 없이 정상 동작함을 확인했다.

### PoC 검증 내역

portfolio 생성 API의 `ARTWORK_NOT_FOUND`(404) 에러 케이스로 검증했다.

- 테스트: `src/test/java/com/atcrew/portfolio/docs/PortfolioErrorApiSpecTest.java`
- 일반 `document()` 대신 `MockMvcRestDocumentationWrapper.document(...)` + `ResourceDocumentation.resource(...)`를 사용해야 `resource.json`이 생성되고, `openapi3` 태스크가 이 값을 읽는다
- 생성된 스니펫: `build/generated-snippets/portfolio/create-shared-artwork-not-found/` (http-request.adoc, http-response.adoc, resource.json 등)
- 재현 방법: `./gradlew openapi3` (스니펫이 이미 최신 상태면 `./gradlew openapi3 -x test`로 test 태스크를 건너뛸 수 있다)
- 생성된 산출물: `build/api-spec/openapi3.yaml`
- 검증 결과: YAML의 `/api/portfolios` POST 404 응답 예시(`portfolio/create-shared-artwork-not-found`) `message` 값이 `PortfolioErrorCode.ARTWORK_NOT_FOUND`의 실제 message("포트폴리오에 담을 작품을 찾을 수 없습니다")와 공백까지 완전히 일치

`openapi3` 태스크는 `test` 태스크를 의존성으로 재실행한다. PoC 시점 전체 스위트에는 이번 작업과 무관한 기존 flaky 실패(MariaDB Testcontainer 커넥션 풀 부족으로 인한 SearchApiDocTest 2건, EventPublicationRegistryTest 3건)가 있었고, 이 때문에 `test` 태스크가 FAILED로 끝나면 `openapi3`가 실행되지 않는 문제가 있다. 이 flaky 원인 자체를 해결하는 것은 이번 PoC 범위 밖이다.

### 적용 후 워크플로우

1. 테스트 실행 → `build/generated-snippets/` 생성
2. `./gradlew openapi3` → `build/api-spec/openapi3.yaml` 생성
3. 생성된 YAML을 springdoc 정적 파일로 서빙하거나 CI에서 배포

---

## 7. 8개 모듈 확장 (2026-08-12)

PoC 검증 이후 artwork/billing/media/auth/community/member/recruit/search 8개 모듈 담당자가 병렬로 에러 케이스 REST Docs 테스트(`*ErrorApiSpecTest.java`, `MockMvcRestDocumentationWrapper.document()` + `resource()` 형식)를 작성했다. `./gradlew openapi3 -x test`로 생성한 `build/api-spec/openapi3.yaml`을 기준으로 각 모듈이 보고한 `errorCodesCovered`가 실제 YAML에 반영됐는지, `message` 값이 각 모듈 ErrorCode enum과 공백까지 일치하는지 검증했다.

### 모듈별 커버리지

| 모듈 | ErrorCode 고유 코드 수 | 커버 보고 | YAML 실제 반영 | 비고 |
|---|---|---|---|---|
| artwork | 16 | 14 | 14 | code/message 전부 일치 |
| auth | 8 | 5 | 5 | code/message 전부 일치 |
| community | 3 | 2 | 2 | code/message 전부 일치 |
| member | 15 | 9 | 9 | code/message 전부 일치 |
| search | 4 | 3 | 3 | code/message 전부 일치 |
| media | 자체 ErrorCode 없음(공통 `HTTP_401`) | 1 | 1 | code/message 일치 |
| billing | 1 | 0 | - | 자기 컨트롤러로 트리거 불가라 스킵 |
| recruit | 13 | 13 | 13 | 재검증 후 code/message 전부 일치(아래 참고) |
| company | 6 | 4 | 4 | 8개 모듈 확장과 별도로 추가 진행(2026-08-12). `COMPANY_ACCESS_DENIED`(공개 API로 도달 불가한 구조적 방어 코드)·`CAREER_NOT_FOUND`(throw 지점 자체가 없는 미사용 코드)는 스킵 |

각 모듈이 건너뛴 코드(방어 코드, dead code, 다른 모듈 경유로만 도달하는 코드, 테스트 환경 제약, TOCTOU 레이스 전용 등)의 개별 사유는 이 문서에 옮기지 않는다 — 모듈 담당자의 작업 보고에 근거와 함께 기록돼 있다.

### 확장 작업 중 발견·수정한 결함 (2026-08-12)

1. **recruit 스니펫 미생성**: 확장 직후 통합 검증 시점에는 recruit 모듈 담당자가 "3회 실행 모두 Testcontainer 기동 단계에서 실패해 실제 assertion에 도달하지 못했다"고 보고한 대로(`testsPassed: false`) `build/api-spec/openapi3.yaml`에 recruit 13개 코드가 전혀 반영되지 않았었다. 호스트 부하가 가라앉은 뒤 4개 테스트 클래스를 재실행해 해결됨(인프라 문제였을 뿐 코드 결함 아님).
2. **`JobSeekingPostErrorApiSpecTest`의 `INVALID_CURSOR` 테스트가 실제로는 실패하는 코드였음**: `GET /api/recruit/job-seeking-posts`의 `getPublished()`는 커서를 raw ID로 그대로 써서 형식 검증 자체가 없어(`JobSeekingPostService.java:139-147`), 잘못된 커서를 보내도 400이 아니라 200이 반환된다. `RecruitErrorCode.INVALID_CURSOR`를 실제로 던지는 곳은 `CompositeCursor.decode`를 쓰는 `GET /api/recruit/job-postings`/`GET /api/recruit/team-recruits` 쪽이다. 테스트를 `JobPostingErrorApiSpecTest`로 옮겨 수정했다 — 재실행 결과 recruit 13개 코드(구인글/구직글/팀원모집글/지원 4개 컨트롤러) 전부 통과, `openapi3.yaml`에 반영 확인.
3. **YAML 전체 44개 고유 code에 대해 실제 ErrorCode enum 소스와 자동 교차검증**: 소스에 없는 code 0건, 실제 message와 다른 값 0건. `ARTWORK_NOT_FOUND`(artwork/portfolio)와 `INVALID_CURSOR`(artwork/member/recruit 등)가 YAML 안에서 서로 다른 message로 두 번 이상 등장하는데, 이는 버그가 아니라 같은 이름의 코드를 쓰는 서로 다른 모듈의 ErrorCode enum이 각자 다른 문구를 갖고 있기 때문이다(모듈별로 정상).

### 남은 갭

- **portfolio 모듈은 PoC 1건만 존재**: `PortfolioErrorApiSpecTest`(`ARTWORK_NOT_FOUND` 1건) 외 `PortfolioErrorCode`의 나머지 코드는 이번 확장에 포함되지 않았다 — portfolio는 애초에 8개 모듈 목록에 없었다.
- **company 모듈 2개 코드 스킵**: 위 표 참고 — 둘 다 공개 API로 도달 불가능한 코드라 REST Docs로 문서화할 방법이 구조적으로 없다.
- **media의 `@Hidden` 컨트롤러**: springdoc 애노테이션 자체가 문서에서 숨기도록 표시돼 있던 컨트롤러라, REST Docs 에러 테스트를 작성해도 애초에 API 소비자가 볼 필요가 없는 내부 전용 엔드포인트다. 정적 전환 이후에도 스펙에 나타나지 않는 것이 의도된 동작이다.
- 위 갭들의 공통 결과: 정적 전환(§8) 이후 Swagger UI에는 **REST Docs 에러 테스트로 커버된 코드만** 노출된다. 위에 나열되지 않은, 아직 `*ErrorApiSpecTest`가 없는 코드는 스펙에서 아예 빠진다 — 이건 이번 전환이 만들어낸 트레이드오프이지 버그가 아니다. 커버리지를 넓히려면 해당 모듈에 에러 케이스 REST Docs 테스트를 추가하고 `./gradlew openapi3 -x test && ./gradlew copyOpenApiSpec`을 재실행하면 된다.

이 방식으로 **테스트 → 문서 → API 스펙**이 항상 동기화되며, 수동으로 Swagger 어노테이션을 관리하지 않아도 된다.

---

## 8. Swagger UI 정적 스펙 서빙 전환 시도 → 회귀 발견 → 롤백 (2026-08-12~13)

`/swagger-ui/index.html`을 springdoc 애노테이션 기반 동적 생성 대신 REST Docs 기반 정적 스펙
(`build/api-spec/openapi3.yaml`)으로 서빙하도록 전환했다가(`application.yml`의
`springdoc.swagger-ui.url` 재정의, `SecurityConfig`에 `/openapi3.yaml` permitAll, `build.gradle`의
`copyOpenApiSpec` 태스크), **심각한 회귀를 발견해 롤백했다.** `springdoc.api-docs.enabled=false`를
쓸 수 없었던 사정(swagger-ui 모듈이 api-docs 기능 빈에 강결합돼 있어 끄면 UI 자체가 404가 됨)은
여전히 유효한 사실이라 기록만 남긴다 — 아래 참고.

### 발견한 회귀

정적 스펙은 **에러 케이스 REST Docs 테스트(`*ErrorApiSpecTest`)만 커버**한다. 기존에 있던 성공
시나리오 문서화 테스트(`*ApiDocTest`, auth/member/company/community/search/recruit/portfolio
각 모듈)는 일반 `document()`를 쓰고 `resource()` wrapper를 쓰지 않아 `openapi3` 태스크가 읽지
못한다. 그 결과 서빙 전환 직후의 `build/api-spec/openapi3.yaml`은:

- **경로 38개뿐**(전체 API는 124개 이상 엔드포인트)
- **성공(2xx) 응답이 단 하나도 없음** — 400/401/403/404/409/410/503 에러 예시만 존재

즉 Swagger UI를 이 정적 스펙으로 전환하면 개발자가 실제로 필요한 정보(대다수 엔드포인트의 존재
자체, 모든 성공 응답 스키마, 요청 필드 설명)가 통째로 사라진다 — "에러 메시지 문구가 가끔 틀리는"
원래 문제보다 훨씬 나쁜 상태였다. PoC 단계에서 검토했던 "병행 서빙"(일부만 정적, 나머지 동적) 옵션을
커스텀 merge 컨트롤러가 필요해 난이도가 높다는 이유로 기각했었는데, 그 판단이 맞았다는 게 이번에
실증된 셈이다.

### 롤백 내역 (2026-08-13)

- `application.yml`의 `springdoc.swagger-ui.url` 재정의 제거 — Swagger UI가 다시 애노테이션 기반
  동적 생성(`/v3/api-docs`)을 읽는다.
- `SecurityConfig`의 `/openapi3.yaml` permitAll 항목 제거.
- `build.gradle`의 `copyOpenApiSpec` 태스크 제거, `src/main/resources/static/openapi3.yaml` 삭제.
- `openapi3` 태스크 자체는 유지한다 — Swagger UI 서빙용이 아니라 **에러 케이스 테스트의
  code/message가 실제 ErrorCode enum과 일치하는지 검증하는 용도**로는 여전히 유효하다(§6~§7의
  PoC·확장 검증이 바로 이 용도로 이 태스크를 썼다).

롤백 후 재기동 검증: `GET /v3/api-docs` — 경로 42개(메서드별 operation 기준으로는 124개+), 상태
코드 `200/201/204/400/401/403/404/409/410/428/429/500/503` 전부 포함 확인. `GET
/v3/api-docs/swagger-config`의 `url`이 다시 `/v3/api-docs`를 가리킴 확인.

### 남은 갭 (다음에 이걸 다시 시도한다면)

성공 시나리오 문서화 테스트(`*ApiDocTest`)를 전부 `resource()` wrapper 형식으로 전환해서 정적
스펙이 전체 API 표면(성공+에러, 모든 엔드포인트)을 커버하게 만들기 전까지는 정적 서빙 전환을
다시 시도하지 않는다. 그 전환 작업량은 이번 에러 케이스 커버리지 작업(9개 모듈, 47개 케이스)과
비슷하거나 더 클 수 있다 — 기존 `*ApiDocTest`가 성공 케이스 1개씩만 다루는 게 아니라 요청 필드
설명(`requestFields`)까지 상세히 기재하기 때문이다.
