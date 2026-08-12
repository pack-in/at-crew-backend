# Swagger(springdoc-openapi) 작성 규약

프론트엔드가 Swagger 문서만 보고 API를 붙일 수 있어야 한다. 2026-08 마일스톤 검증 과정에서
에러 응답이 전부 `code: "SUCCESS"`·`message: "string"`으로, 성공 응답이 `data: null`로 뜨는
버그가 발견됐다(원인·수정: `feat/launch-milestone-mvp` 브랜치 커밋 `64b274c`). 이 문서는 같은
문제가 재발하지 않도록 앞으로 컨트롤러를 작성할 때 지켜야 할 규칙을 정리한다.

## 1. 비200 `@ApiResponse`의 `description`은 반드시 에러코드를 포함한다

`com.atcrew.common.config.OpenApiConfig`의 `globalErrorResponseCustomizer`가 모든 컨트롤러의
비2xx `@ApiResponse.description`을 **파싱해서** 실제 code/message 값이 채워진 example을 자동으로
붙인다. `content`를 직접 선언할 필요 없다 — 어차피 이 customizer가 덮어쓴다. 대신
`description`이 아래 3가지 형식 중 하나로, **에러코드 이름(`[A-Z][A-Z0-9_]{2,}` 패턴)을 반드시
포함**해야 한다.

| 형식 | 예시 | 용도 |
|---|---|---|
| `CODE — 설명` (dash) | `"AUTHENTICATION_FAILED — 이메일 또는 비밀번호 불일치"` | 원인이 하나뿐인 응답 |
| `CODE(설명), CODE(설명)` | `"ARTWORK_NOT_FOUND(존재하지 않는 작품), BOOKMARK_FOLDER_NOT_FOUND(존재하지 않는 폴더)"` | 한 응답코드에 원인이 여럿 |
| `설명(CODE) 또는 설명(CODE)` | `"타인 소유 포트폴리오(PORTFOLIO_ACCESS_DENIED) 또는 프로 플랜 아님(PRO_PLAN_REQUIRED)"` | 위와 동일, 서술형 선호 시 |

세 형식 모두 파서가 지원하니 모듈마다 기존에 쓰던 스타일을 유지해도 된다. 다만 **코드 이름이
아예 없는 서술형 문장**(`"인증 필요"`, `"포트폴리오 ID 형식 오류"` 같은)은 파싱이 안 돼서
`OpenApiConfig`의 `DEFAULT_BY_STATUS` 기본값(400→`COMMON_INVALID_INPUT`, 401→
`UNAUTHENTICATED`, 500→`COMMON_INTERNAL_SERVER_ERROR`)이나 `HTTP_<상태코드>`로 대체된다 —
실제로 그 코드가 맞다면 상관없지만, 도메인 예외라면 반드시 코드 이름을 명시할 것.

```java
// 좋음 — 파싱되어 실제 code/message 예시가 붙는다
@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
        description = "PORTFOLIO_NOT_FOUND — 존재하지 않는 포트폴리오")

// 나쁨 — 코드가 없어서 기본값(HTTP_404)으로 대체된다
@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
        description = "포트폴리오를 찾을 수 없음")
```

401은 인증이 필요한 엔드포인트에서만 개별 선언한다(공개 API에 401을 잘못 문서화하지 않도록).
400/404/409/500은 메서드가 선언하지 않아도 `OpenApiConfig`가 자동으로 채워 넣는다 — 다만 이
자동 채움은 일반 설명(`"리소스 없음"` 등)만 붙으므로, 그 엔드포인트가 실제로 반환하는 구체적인
에러코드가 있다면 메서드에 직접 선언하는 편이 더 정확하다.

## 2. 제네릭 타입 필드에 `@Schema(nullable = true)`를 쓰지 않는다

`common.response.ApiResponse<T>`의 `data` 필드에 `nullable = true`를 붙였더니, springdoc이
제네릭을 구체 타입(예: `AuthInfo`)으로 해석하는 과정에서 `"data": {"type": "null", "$ref":
"#/components/schemas/AuthInfo"}`처럼 `type`과 `$ref`가 동시에 들어간 스펙을 만들어냈다.
Swagger UI는 이 조합을 보면 `$ref`를 펼치지 않고 그냥 `null`을 표시한다 — 그 결과 **모든**
`ApiResponseXxx` 성공 응답 예시가 `data: null`로 떴다.

**규칙**: 제네릭 타입 파라미터를 감싸는 필드(`ApiResponse<T>.data`처럼)에는 `nullable`을 쓰지
않는다. 실제로 상황에 따라 null일 수 있다는 사실은 `description` 텍스트로만 설명한다. 구체
타입(`String`, `Instant` 등) 필드의 `nullable = true`는 이 문제가 없으므로 그대로 써도 된다.

## 3. 수정 후 검증은 반드시 브라우저 Swagger UI로 한다

`/v3/api-docs` JSON을 직접 파싱해서 예시를 재구성하는 스크립트는 springdoc/Swagger UI의 실제
렌더링 로직과 미묘하게 다를 수 있다(이번에도 재구성 스크립트는 `data`가 채워진 것처럼 보였지만
실제 UI는 `null`을 표시했다). Swagger 응답 예시를 고친 뒤에는:

1. `./gradlew bootRun`으로 재기동
2. 실제 Swagger UI(`/swagger-ui/index.html`)에서 해당 오퍼레이션을 펼쳐 200과 대표 에러코드
   2~3개의 "Example Value"를 눈으로 확인
3. 코드가 여러 개인 응답은 "Examples" 드롭다운에서 각 항목이 올바른 code/message를 갖는지 확인

## 참고

- 파서·기본값 구현: `src/main/java/com/atcrew/common/config/OpenApiConfig.java`
- 공통 응답 봉투: `src/main/java/com/atcrew/common/response/ApiResponse.java`
