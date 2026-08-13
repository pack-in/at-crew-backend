# PLAN-AGENT — MariaDB Testcontainer 재사용 통일

## 배경

`plans/260812-restdocs-error-spec-poc/`와 `plans/260812-restdocs-error-spec-expansion/` 작업 중
`SearchApiDocTest`/`EventPublicationRegistryTest`가 가끔 flaky하게 실패하는 문제를 겪었다. 조사
결과 설정 한 줄이 아니라 구조적 문제로 확인됨 — Testcontainer 재사용 전략이 리포지토리 안에 두
갈래로 혼재한다:

- `SharedContainersConfig`(`src/test/java/com/atcrew/SharedContainersConfig.java`, 싱글톤 공유,
  `@ImportTestcontainers`)를 쓰는 테스트군: `RestDocsIntegrationSupport` 기반 전체(12개+ `*ApiDocTest`
  /`*ErrorApiSpecTest`), `AuthPersistenceConcurrencyTest`, `EventPublicationRegistryTest`.
- 각자 자기만의 `static MariaDBContainer` 필드를 선언하는 테스트군: 8개 `@ApplicationModuleTest`
  (`RecruitModuleTests`, `ArtworkModuleTests`, `BookmarkModuleTests`, `MemberModuleTests`,
  `SearchModuleTests`, `PortfolioModuleTests`, `CommunityModuleTests`, `BillingModuleTests`) +
  `AtCrewBackendApplicationTests` + `PortfolioServiceTests`.

컨텍스트 캐시 키가 갈려 같은 MariaDB 컨테이너 하나에 서로 다른 Hikari 풀이 여러 개(3개 이상)
동시에 뜨고, `EventPublicationRegistryTest`는 재기동 검증용 별도 `SpringApplicationBuilder`
컨텍스트까지 하나 더 띄운다(agent 메모리 `project_restdocs_flaky_followup.md` 참고).

## 결정

- 마이그레이션 범위: **전체 통일**. 10개 미공유 테스트 클래스 전부 `SharedContainersConfig`로
  옮긴다(사용자 확정, 2026-08-12).
- `EventPublicationRegistryTest`의 재기동 검증용 별도 컨텍스트 자체는 재기동 검증이라는 목적상
  불가피할 수 있다 — 다만 그 컨텍스트가 가리키는 컨테이너는 `SharedContainersConfig`의 컨테이너를
  재사용해야 한다(새 컨테이너를 또 띄우면 안 됨).
- **(2026-08-13 추가 결정)** 전체 통일 직후 실제 회귀 발견: `@ApplicationModuleTest` 10개가 같은
  컨테이너/스키마를 공유하니 클래스 간 테스트 데이터가 서로 오염됨(`SearchModuleTests`가 다른
  클래스가 만든 orphan 데이터를 조회하다 NPE). `@Transactional` 롤백은 채택하지 않음 — 일부
  모듈이 `@ApplicationModuleListener`(`AFTER_COMMIT` 비동기)를 직접 검증해서 트랜잭션이 실제
  커밋되지 않으면 리스너가 발동하지 않음. 대신 **JUnit5 `AfterAllCallback` 기반 cleanup
  extension**(`DatabaseCleanupExtension`, 클래스 종료 시 도메인 테이블 전부 truncate)을 10개
  파일에 추가 — Testcontainers 싱글톤 패턴의 표준 관례.
- **(2026-08-13 추가 결정)** 위 회귀를 드러낸 실제 원인은 `ArtworkServiceImpl`/`BookmarkServiceImpl`의
  `Collectors.toMap`이 작성자 조회 실패 시 `null`을 반환하는 버그(`Collectors.toMap`은 값이 null이면
  NPE)였다 — 테스트 환경뿐 아니라 실제 운영에서도 작성자가 삭제된 작품이 있으면 재색인/피드 조회
  전체가 죽을 수 있는 진짜 결함이라 이번에 같이 고침(사용자 승인).

## 검증

- 마이그레이션한 각 테스트 클래스를 개별 실행해서 기존과 동일하게 통과하는지 확인한다(동작
  변경 없이 컨테이너 재사용 방식만 바뀌어야 함).
- 마지막에 전체 `./gradlew test`를 **2회 연속** 실행해서 이전에 flaky했던
  `SearchApiDocTest`/`EventPublicationRegistryTest`가 두 번 다 통과하는지 확인한다.

## 금지 범위

- 테스트 assertion 자체(기대값)는 바꾸지 않는다 — 컨테이너 재사용 방식과, 그로 인해 드러난 실제
  결함(아래 PA-04)만 손댄다.
- `SharedContainersConfig.java`의 컨테이너 버전/설정을 바꾸지 않는다(이미 다른 테스트들이 검증된
  상태로 쓰고 있음).
- 커밋.

## PA-01. 모듈 테스트 + 애플리케이션 테스트 마이그레이션

depends on: (없음)

`RecruitModuleTests`/`ArtworkModuleTests`/`BookmarkModuleTests`/`MemberModuleTests`/
`SearchModuleTests`/`PortfolioModuleTests`/`CommunityModuleTests`/`BillingModuleTests`/
`AtCrewBackendApplicationTests`/`PortfolioServiceTests` 10개 파일에서 개별 `static MariaDBContainer`
필드/`@Container` 선언을 제거하고 `@ImportTestcontainers(SharedContainersConfig.class)`로 교체한다.
`RestDocsIntegrationSupport`가 이미 이 방식을 쓰고 있으니 그 코드를 참고한다.

- [x] 10개 파일 각각의 현재 컨테이너 선언 방식 확인 — 전부 `SharedContainersConfig`와 동일한
      `mariadb:11.4` 이미지, 의도적으로 다른 설정 없음
- [x] 10개 파일 전부 `@ImportTestcontainers(SharedContainersConfig.class)`로 교체
- [x] 10개 파일 개별 실행(최초)은 통과했으나, **함께 실행하면 회귀 발견**(PA-04 참고) — 수정 후
      10개 클래스 그룹 재실행으로 최종 확인(114/114 통과, 실패 0)

## PA-02. EventPublicationRegistryTest 컨테이너 재사용 조정

depends on: (없음, PA-01과 병렬 가능)

`EventPublicationRegistryTest`(이미 `SharedContainersConfig` 사용 중)의 재기동 검증용 별도
`SpringApplicationBuilder` 컨텍스트가 새 컨테이너를 띄우지 않고 `SharedContainersConfig`의
컨테이너 연결 정보를 그대로 재사용하도록 조정한다.

- [x] 재기동 컨텍스트 추적 결과 — **이미 올바르게 재사용 중**이었음(`spring.datasource.url` 등을
      `SharedContainersConfig`의 컨테이너 정보로 명시 주입). 새 컨테이너를 띄우는 코드 없음 확인,
      코드 변경 불필요
- [x] (해당 없음 — 이미 올바른 상태)
- [x] 개별 실행 — 3개 테스트 전부 통과

## PA-03. 전체 스위트 검증

depends on: PA-01, PA-02, PA-04

`./gradlew test`를 2회 연속 실행해서 flaky 재발 여부를 확인한다.

- [x] 1회차 전체 실행 — `BUILD SUCCESSFUL`, 실패 0건
- [x] 2회차 전체 실행 — 첫 시도는 Gradle이 `UP-TO-DATE`로 스킵해 무효, `./gradlew cleanTest test`로
      강제 재실행해 재확인 — `BUILD SUCCESSFUL`, 435개 테스트 전부 통과, 실패 0건
- [x] 두 번 다 통과 확인 — 이전에 flaky했던 `SearchApiDocTest`/`EventPublicationRegistryTest` 포함
      전부 안정적으로 통과

## PA-04. 클래스 간 데이터 오염 수정 (전체 통일이 드러낸 회귀)

depends on: PA-01

PA-01 마이그레이션 직후 10개 클래스를 **함께** 실행하면 `SearchModuleTests`의 "전체 재색인"
테스트가 `NullPointerException`으로 실패함을 발견 — 단독 실행 시엔 통과. 원인: 여러
`@ApplicationModuleTest`가 이제 같은 MariaDB 스키마를 공유하는데, `ArtworkServiceImpl.
getArtworksForReindex()`가 스키마 안의 모든 작품을 조회할 때 다른 테스트 클래스가 남긴 작품의
작성자(member)가 없어 `memberService.findById()`가 실패 → `Collectors.toMap`이 값 `null`에서
NPE(Java 표준 동작). 즉 컨테이너 통일이 테스트 클래스 간 데이터 격리를 깨서 생긴 진짜 회귀.

- [x] `@Transactional` 롤백 방식 검토 후 기각 — `@ApplicationModuleListener`(`AFTER_COMMIT`)를
      직접 검증하는 테스트가 있어 트랜잭션 롤백 시 리스너가 발동하지 않음
      (`ArtworkModuleTests`의 `awaitReady()` 폴링 패턴 등)
- [x] `DatabaseCleanupExtension`(`src/test/java/com/atcrew/support/`) 신규 작성 — JUnit5
      `AfterAllCallback`, 클래스 종료 시 `flyway_schema_history`를 제외한 모든 테이블 truncate.
      Testcontainers 싱글톤 패턴의 표준 관례
- [x] 10개 파일 전부에 `@ExtendWith(DatabaseCleanupExtension.class)` 추가
- [x] `ArtworkServiceImpl.getArtworksForReindex()`/`toSummaryPage()`,
      `BookmarkServiceImpl`(동일 패턴 3곳)의 `Collectors.toMap` null 버그를 `HashMap` + 명시적
      루프로 수정(조회 실패 시 매핑에서 제외 — 기존과 동일한 최종 동작, NPE만 제거). 실제 운영에서도
      작성자가 삭제된 작품이 있으면 재색인/피드 조회 전체가 죽을 수 있었던 결함이라 함께 수정
      (사용자 승인)
- [x] 수정 후 10개 클래스 그룹 재실행 — 114/114 통과, 실패 0건
