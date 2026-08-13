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

## 8. Swagger UI 정적 스펙 서빙 전환 완료 (2026-08-12)

`/swagger-ui/index.html`이 이제 springdoc의 애노테이션 기반 동적 생성이 아니라, REST Docs 기반 정적
스펙(`build/api-spec/openapi3.yaml`)을 서빙한다. §6~§7에서 "남은 갭"으로 남아 있던 실제 서빙 전환을
완료했다.

### 적용 내역

1. **정적 파일 배치**: `build.gradle`에 `copyOpenApiSpec`(Copy 태스크)을 추가해
   `build/api-spec/openapi3.yaml` → `src/main/resources/static/openapi3.yaml`로 복사한다. Spring
   Boot의 기본 정적 리소스 서빙(`classpath:/static/**`)이 그대로 `/openapi3.yaml` 경로로 노출한다.
   별도 `@Controller`를 작성하지 않았다 — 이 리포지토리에 기존 정적 파일 서빙 관례가 없었고, 스프링
   기본 동작이 가장 자연스러운 방식이었다.
   - 이 태스크는 **의도적으로 `processResources`/`bootRun`에 자동 연결하지 않았다.** `openapi3`
     태스크가 `test` 태스크를 재실행하는데, 기존 flaky Testcontainer 실패(§6) 때문에 매 빌드·기동마다
     전체 테스트가 자동으로 돌면 개발 흐름이 막힌다. 스펙을 갱신하려면 수동으로
     `./gradlew openapi3 -x test && ./gradlew copyOpenApiSpec`을 순서대로 실행한다.
2. **springdoc 설정** (`application.yml`): `springdoc.swagger-ui.url: /openapi3.yaml`로 Swagger UI가
   읽는 스펙을 정적 파일로 바꿨다. `/v3/api-docs/swagger-config` 응답의 `url` 필드가 `/openapi3.yaml`을
   가리키는 것으로 실측 확인했다.
3. **보안 설정** (`SecurityConfig`): non-prod 프로파일 한정 permitAll 목록에 `/openapi3.yaml`을
   추가했다(`/swagger-ui/**`, `/v3/api-docs/**`와 동일한 취급). 없으면 기본 `anyRequest().authenticated()`에
   걸려 401이 난다 — 실제로 이 문제로 최초 시도에서 401이 확인됐다.

### `springdoc.api-docs.enabled=false`를 쓰지 않은 이유 (중요한 제약)

당초 계획은 `springdoc.api-docs.enabled=false`로 동적 생성 자체를 끄는 것이었다. 실제로 적용해보니
**springdoc-openapi 3.0.3에서는 이 설정이 Swagger UI 전체를 함께 죽인다.** `--debug`로 조건 평가
리포트를 떠 보면:

```
SwaggerConfig:
   Did not match:
      - @ConditionalOnBean (types: org.springdoc.core.configuration.SpringDocConfiguration;
        SearchStrategy: all) did not find any beans of type
        org.springdoc.core.configuration.SpringDocConfiguration (OnBeanCondition)

SpringDocConfiguration:
   Did not match:
      - @ConditionalOnProperty (springdoc.api-docs.enabled) found different value in property
        'springdoc.api-docs.enabled' (OnPropertyCondition)
```

즉 `swagger-ui` 모듈(`SwaggerConfig`)이 `api-docs` 기능의 공용 빈(`SpringDocConfiguration`)에
`@ConditionalOnBean`으로 강하게 결합돼 있어서, `api-docs.enabled=false`로 그 빈을 끄면
`swagger-ui.url` 설정과 무관하게 `/swagger-ui/index.html` 자체가 404로 사라진다(실측: `api-docs.enabled=false`
상태에서 `/swagger-ui/index.html` → 404, `/openapi3.yaml` → 401(보안 미설정 시)/200(보안 설정 후) —
UI 자체가 뜨지 않아 무의미했다). "동적 생성만 끄고 UI는 정적 스펙으로 유지"가 이 버전에서는 두
프로퍼티 조합으로 불가능하다.

**결정**: `api-docs.enabled`는 기본값(`true`)을 유지하고, `swagger-ui.url` 재정의만으로 전환했다.
결과적으로 `/v3/api-docs`(구 동적 스펙 JSON) 엔드포인트 자체는 계속 살아 있고 직접 요청하면 여전히
애노테이션 기반 스펙을 반환하지만, **Swagger UI는 더 이상 그 경로를 쓰지 않는다** — 사람이 보는
화면 기준으로는 전환이 완료된 것과 동일하다. `/v3/api-docs`를 완전히 차단하려면 별도로 Spring
Security에서 막아야 하는데, 이번 전환 범위에서는 하지 않았다.

### 서빙 검증 (2026-08-12)

`MARIADB_PORT=3307`(로컬 `turban-mariadb-local` 컨테이너)로 `./gradlew bootRun
--args='--spring.profiles.active=local'` 기동 후 확인:

| 경로 | 결과 |
|---|---|
| `GET /openapi3.yaml` | 200, `application/octet-stream`, 56170 bytes, `ARTWORK_NOT_FOUND`/`COMPANY_NOT_FOUND`/`COMPANY_ALREADY_EXISTS`/`CAREER_LIMIT_EXCEEDED`/`INVALID_CAREER_PERIOD` 전부 응답 본문에 포함 확인 |
| `GET /swagger-ui/index.html` | 200, `text/html` |
| `GET /v3/api-docs/swagger-config` | 200, `"url":"/openapi3.yaml"` — Swagger UI가 정적 스펙을 읽도록 재확인 |
| `GET /v3/api-docs` | 200(여전히 살아 있음, 위 제약 참고) |

검증 후 프로세스는 종료했다.

이 방식으로 **테스트 → 문서 → API 스펙 → Swagger UI**가 항상 동기화되며, 수동으로 Swagger
어노테이션을 관리하지 않아도 된다.
