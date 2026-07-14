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

## 6. 향후 Swagger 자동화 계획

현재는 REST Docs 스니펫(`.adoc` 파일)을 생성하는 단계다. 향후 아래 도구를 도입해 **스니펫 → OpenAPI 스펙 → Swagger UI** 파이프라인을 구축할 계획이다.

### restdocs-api-spec 도입

[`ePages-de/restdocs-api-spec`](https://github.com/ePages-de/restdocs-api-spec)을 사용하면 REST Docs 테스트에서 OpenAPI 3.0 스펙을 자동 생성할 수 있다.

```groovy
// build.gradle 추가 예정
testImplementation 'com.epages:restdocs-api-spec-mockmvc:0.19.4'

// openapi3 태스크 설정
openapi3 {
    server = 'https://api.atcrew.co.kr'
    title = '앳크루 API'
    version = project.version
    format = 'yaml'
}
```

### 적용 후 워크플로우

1. 테스트 실행 → `build/generated-snippets/` 생성
2. `./gradlew openapi3` → `build/api-spec/openapi3.yaml` 생성
3. 생성된 YAML을 springdoc 정적 파일로 서빙하거나 CI에서 배포

이 방식으로 **테스트 → 문서 → API 스펙**이 항상 동기화되며, 수동으로 Swagger 어노테이션을 관리하지 않아도 된다.
