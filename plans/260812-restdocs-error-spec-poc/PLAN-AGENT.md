# PLAN-AGENT — restdocs-api-spec 에러 문서 PoC

## 배경

포트폴리오 생성 API(`POST /api/portfolios`)의 `ARTWORK_NOT_FOUND` 에러에서 Swagger 문서 예시
메시지("담을 작품 없음 — 존재하지 않거나 본인 소유가 아니거나 삭제된 작품")와 실제 런타임 응답
메시지(`PortfolioErrorCode.ARTWORK_NOT_FOUND`의 "포트폴리오에 담을 작품을 찾을 수 없습니다")가
서로 다른 것을 발견했다. 원인은 `OpenApiConfig.globalErrorResponseCustomizer`가
`@ApiResponse.description` 문자열을 정규식으로 파싱해 예시를 만드는 구조라, 실제 ErrorCode enum의
message와는 별개의 source of truth라는 것.

`docs/testing/rest-docs-guide.md` §6에 "향후 계획"으로만 적혀 있던 `restdocs-api-spec` 도입
(REST Docs 테스트 실행 결과에서 OpenAPI 스펙을 자동 생성 — 테스트가 통과해야만 스펙이 나오므로
문서와 실제 동작이 항상 일치)을 실제로 검증한다.

## 결정 (그릴링 결과)

- 범위: PoC/점진 전환. 전체 10개 모듈 리트로핏(최소 100개 이상 엔드포인트에 에러케이스 REST Docs
  테스트 신규 작성 필요, 실측 확인됨)은 이번 계획에서 제외 — 별도 계획으로 분리.
- 대상: portfolio 모듈의 `ARTWORK_NOT_FOUND`(404) 케이스 하나로 파이프라인이 실제로 정확한 문서를
  만들어내는지 검증.
- 오늘 발견한 mismatch 자체는 지금 임시 패치하지 않는다 — 이 PoC(또는 실패 시 대안)의 결과로
  자연스럽게 해결한다.
- 호환성 리스크: `com.epages:restdocs-api-spec` 최신 릴리즈(0.20.1, 2026-04-20)는 Spring Boot 4
  지원을 표방하나, 메인테이너가 "0.20.x 라인은 Spring Boot 4에서 minor compatibility issue 가능"이라
  직접 경고함. 이 프로젝트는 Spring Boot `4.0.6` + `spring-restdocs-mockmvc 4.0.0` 조합 — 정확히 이
  조합의 검증 사례는 확인되지 않아 스파이크로 직접 검증해야 한다.
- 스파이크 실패 시: 공통 `ErrorCode` 인터페이스 + registry를 만들어 `OpenApiConfig`가 enum의 실제
  message를 직접 룩업하는 대안(PA-03)으로 전환한다.
- PoC 성공 기준: `openapi3` 태스크로 생성된 YAML의 `message`가 실제 런타임 응답과 일치하는지까지.
  **Swagger UI(`/swagger-ui/index.html`) 실제 서빙 전환은 이번 스코프 밖** — springdoc 동적 생성과
  정적 스펙을 병합 서빙하는 배선은 하지 않는다.

## 검증

- PA-01에서 생성된 YAML의 `ARTWORK_NOT_FOUND` 404 예시 `message` 값이
  `PortfolioErrorCode.ARTWORK_NOT_FOUND`의 실제 `message` 필드 문자열과 정확히 일치해야 한다.
- 각 태스크는 완료 시 `./gradlew build`(compile + test)가 그린이어야 다음 태스크로 넘어간다.

## 금지 범위

- portfolio 외 8개 모듈(artwork/billing/media/auth/community/member/recruit/search)의 에러케이스
  REST Docs 리트로핏 — 별도 계획 필요.
- Swagger UI 실제 서빙을 정적 스펙으로 전환하는 배선.
- CI 빌드 파이프라인 변경 — 이번 단계는 로컬 수동 실행(`./gradlew openapi3`)까지만.
- 커밋 — 파일 수정까지만 하고 커밋하지 않는다.

## PA-01. restdocs-api-spec 호환성 스파이크

depends on: (없음)

`build.gradle`에 `com.epages:restdocs-api-spec-mockmvc:0.20.1` 의존성과 Gradle 플러그인을
배선하고, portfolio 생성 API의 `ARTWORK_NOT_FOUND`(404) 케이스에 REST Docs 테스트를 작성해
스니펫을 생성한 뒤, `./gradlew openapi3`로 OpenAPI YAML을 뽑아 그 안의 `message`가 실제
`PortfolioErrorCode.ARTWORK_NOT_FOUND.message`와 일치하는지 확인한다. 이 태스크의 성공/실패
판정이 PA-02/PA-03 중 어느 쪽으로 진행할지를 결정한다.

- [x] `com.epages:restdocs-api-spec-mockmvc:0.20.1` 의존성 + `com.epages.restdocs-api-spec` Gradle
      플러그인 배선, `openapi3` 태스크 정상 등록 확인(`./gradlew tasks --all`), 빌드가 깨지지 않음
- [x] Portfolio 생성 API `ARTWORK_NOT_FOUND` 케이스 REST Docs 테스트 작성·통과 —
      `src/test/java/com/atcrew/portfolio/docs/PortfolioErrorApiSpecTest.java`, 단독 재실행으로
      재검증 완료(`BUILD SUCCESSFUL`)
- [x] `./gradlew openapi3` 실행 성공, 생성된 YAML(`build/api-spec/openapi3.yaml`)에서 해당 예시 확인
- [x] YAML의 `message`("포트폴리오에 담을 작품을 찾을 수 없습니다")와
      `PortfolioErrorCode.ARTWORK_NOT_FOUND`의 실제 message 문자열 공백까지 완전히 일치 확인
- [x] 성공 판정 — 라이브러리 자체의 Spring Boot 4 비호환 문제 없음(`technicalBlocker: false`).
      유일한 마찰은 `openapi3`가 `test` 태스크에 의존해 전체 스위트를 도는데, 이번 작업과 무관한 기존
      flaky 실패(MariaDB Testcontainer 커넥션 풀 부족, `SearchApiDocTest` 2건/`EventPublicationRegistryTest`
      3건)로 `test` 태스크가 FAILED로 끝날 수 있다는 점 — `-x test`로 우회 가능, 근본 해결은 범위 밖

## PA-02. [조건부: PA-01 성공 시] PoC 결과 문서화

depends on: PA-01 (성공 판정)

`docs/testing/rest-docs-guide.md` §6을 "향후 계획"에서 "PoC 완료" 상태로 갱신한다. 실제 검증된
라이브러리 버전, 남은 한계(Swagger UI 실서빙 미전환, portfolio 외 모듈 미적용), 나머지 모듈
확장은 별도 계획이 필요하다는 점을 명시한다.

- [x] `docs/testing/rest-docs-guide.md` §6 갱신 — 검증된 버전·한계·다음 단계 명시("PoC 완료" 반영 확인)
- [x] 생성된 YAML 산출물 경로를 문서에 남김(재현 방법 포함)

## PA-03. [조건부: PA-01 실패 시] 공통 ErrorCode 인터페이스 + registry 대안

depends on: PA-01 (실패 판정)

**PA-01이 성공 판정으로 끝나 이 태스크는 실행되지 않음.**

`common` 패키지에 공통 `ErrorCode` 계약을 만들고 `OpenApiConfig`가 description 파싱 대신 이
registry에서 message를 직접 룩업하도록 바꾼다. 모듈 경계(내부 패키지 직접 참조 금지) 규칙을
지키기 위해, 각 모듈이 자신의 ErrorCode 값을 공개 빈(예: `List<ErrorCodeCatalog>` 수집 패턴)으로
등록하는 방식으로 설계한다 — `common` 모듈이 각 모듈의 `internal` 패키지를 직접 import하지 않는다.

- [ ] `common` 패키지에 공통 `ErrorCode`/`ErrorCodeCatalog` 인터페이스 설계
- [ ] 10개 모듈 ErrorCode enum이 해당 인터페이스를 구현하도록 개정, 각 모듈이 공개 빈으로 등록
- [ ] `OpenApiConfig.globalErrorResponseCustomizer`가 registry lookup으로 message를 채우도록 변경
- [ ] Portfolio `ARTWORK_NOT_FOUND` 사례로 Swagger UI에서 실제 message 일치 확인(브라우저로 직접)
- [ ] `ModularStructureTests` 등 모듈 경계 검증 테스트 통과 확인
