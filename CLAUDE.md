# 앳크루 백엔드

## 프로젝트 개요

기존 서비스 **라이트(Laiteu)** 의 기술 부채를 해소하기 위해 **모듈형 모놀리식(Modular Monolith)** 아키텍처로 전면 재작성한 프로젝트.
라이트 서비스 종료 전 데이터 마이그레이션을 계획하고 있으며, 데이터 모델 설계 시 호환성을 고려한다.

- **기술 스택**: Java 21, Spring Boot 4, Gradle, JPA
- **아키텍처**: Modular Monolith — 도메인 모듈 간 직접 의존 금지, 명시적 인터페이스를 통해서만 통신
- **마이그레이션 제약**: 라이트 → 앳크루 무중단 마이그레이션을 위해 데이터 모델 하위 호환성 유지

---

## 로컬 환경 설정

- [ ] `brew install gitleaks` — secrets 스캐너 설치
- [ ] `sh scripts/install-hooks.sh` — pre-commit hook 등록

---

## 문서 작성 규칙

- 설명·주석은 **한국어**로 작성 (코드, 기술 용어, 식별자는 영문 유지)
- Figma 파일 키, API 키 등 외부 서비스 식별자는 보안 정보로 간주 → `.gitignore` 처리

---

## 문서 목록

| 문서 | 설명 |
|------|------|
| [docs/AT-CREW_서비스기획서_전체_20260728.xlsx](docs/AT-CREW_서비스기획서_전체_20260728.xlsx) | 정식 서비스 기획서 — 요구사항·정책·화면 목록·사용자 플로우·기능/화면 명세·QA 6개 시트. 개별 설계 문서와 상충 시 이 문서가 정본 |
| [README.md](README.md) | 프로젝트 개요 — 아키텍처·모듈 맵·기술 스택·설계 결정 요약·운영 구성. 공개 저장소의 진입점 |
| [CONTRIBUTING.md](CONTRIBUTING.md) | 브랜치·커밋·PR·이슈 규약과 병합 방식. 커밋 형식 상세는 docs/conventions/commit.md |
| [docs/conventions/commit.md](docs/conventions/commit.md) | 커밋 컨벤션 및 워크플로우 |
| [docs/design/figma.md](docs/design/figma.md) | Figma 파일 링크 및 UI 페이지 목록 (**비공개**) |
| [docs/design/auth-email-custom-redesign.md](docs/design/auth-email-custom-redesign.md) | 이메일 자체 인증 재설계 (Firebase → Custom) 설계안 |
| [docs/design/artwork-module-design.md](docs/design/artwork-module-design.md) | artwork 모듈 설계 — 작품·북마크·휴지통, 이미지 업로드(Presigned URL + Worker) |
| [docs/design/artwork-module-summary.md](docs/design/artwork-module-summary.md) | artwork 모듈 총 정리 — API·도메인·플로우·인덱스·에러코드 완전 정리 |
| [docs/design/global-timezone-strategy.md](docs/design/global-timezone-strategy.md) | 글로벌 시간대 관리 설계 — 일본·중국·영미·유럽 확장 시 UTC 저장/Member timezone 필드/직렬화 정책 변경안 |
| [docs/design/mariadb-migration-design.md](docs/design/mariadb-migration-design.md) | MongoDB → MariaDB 전면 전환 설계 — ID 전략(String/UUIDv7), 스키마 정규화, 원자 연산 재설계, Flyway, Modulith JDBC 레지스트리, ETL·컷오버 계획 |
| [docs/design/community-module-design.md](docs/design/community-module-design.md) | community 모듈 설계 — 커뮤니티 피드(배너·포트폴리오·작가 찾아보기), 구인글/팀원모집글은 recruit 모듈 스텁 |
| [docs/design/search-module-design.md](docs/design/search-module-design.md) | search 모듈 설계 — Elasticsearch 기반 포트폴리오 검색, 태그 기반 다중선택 필터, 색인 동기화(이벤트+전체 재색인), 구인글/구직글/팀원모집글은 recruit 모듈 스텁 |
| [docs/design/recruit-module-design.md](docs/design/recruit-module-design.md) | recruit 모듈 설계 — 구인글·팀원모집글·구직글 CRUD, 지원 접수/지원자 관리, 끌어올리기(boost), 관심 작가(기업 전용) |
| [docs/design/portfolio-module-design.md](docs/design/portfolio-module-design.md) | portfolio 모듈 설계(신규 도메인) — 작가 페이지/공유 포트폴리오(고정형·최신반영형), 복제, 공유 링크, artwork와의 순환 의존 회피 |
| [docs/design/portfolio-snapshot-spec.md](docs/design/portfolio-snapshot-spec.md) | 포트폴리오 고정형(SNAPSHOT) 스냅샷 정책 기능 명세서 발췌(2026-08-12, Notion 원본) — xlsx보다 최신 소스, 상충 시 이 문서 우선 |
| [docs/design/billing-module-design.md](docs/design/billing-module-design.md) | billing 모듈 설계 — Stripe 직결 결제/구독(Checkout·Customer Portal·웹훅), USD 단일 통화, entitlement 원장, 플랜 게이팅 |
| [docs/design/billing-frontend-integration.md](docs/design/billing-frontend-integration.md) | 결제/구독 프론트 연동 요청서 — 카탈로그 렌더링, Checkout 리다이렉트, 복귀 페이지 폴링, Portal 위임, 403 기반 게이팅 모달 |
| [docs/design/settings-i18n-design.md](docs/design/settings-i18n-design.md) | 설정 API 전체(로그아웃·비밀번호 변경·마케팅 동의·성인 콘텐츠 토글)·이메일 발송 인프라(Resend)·i18n 필드·언어 세그먼트 설계 |
| [docs/design/media-module-design.md](docs/design/media-module-design.md) | media 모듈 설계 — artwork에 내장돼 있던 Presigned URL 발급·Worker 트리거·webhook·재시도·고아파일 정리를 범용 모듈로 추출, artwork·recruit이 공용 소비 |
| [docs/design/global-country-plan-design.md](docs/design/global-country-plan-design.md) | 거주 국가(Member.countryCode) 설계 — Phase 1. Pro 플랜 노출국가(Artwork.targetCountryCodes)는 로드맵 5순위(결제/구독) 도달 후 Phase 2로 보류 |
| [docs/design/ha-expansion-path.md](docs/design/ha-expansion-path.md) | 고가용성 확장 경로 — 앱 2대 + RDS Multi-AZ를 Terraform으로 정의만 하고 끄둔 상태(`ha_enabled=false`), 전환 트리거·절차·비용. ALB 대신 Cloudflare Tunnel replica를 쓰는 근거 |
| [docs/design/observability-design.md](docs/design/observability-design.md) | 관측·알람 설계 — Grafana Cloud(메트릭·로그·업타임) + Sentry(에러) + Discord 2단계 알람, 배포 헬스체크·조건부 롤백, DB 백업과 백업 실패 감지 |
| [docs/design/infra-security-hardening-design.md](docs/design/infra-security-hardening-design.md) | 프로덕션 인프라 보안 강화 설계 — VPC Public/Private 분리·자체 NAT 인스턴스·Cloudflare Tunnel 기반 인바운드 제거, RDS/ALB 대신 저비용 대안 채택 근거. DB 이중화(D6/D7/D11)는 `ha-expansion-path.md`로 대체됨 |
| [docs/operations/incident-runbook.md](docs/operations/incident-runbook.md) | 장애 대응 런북 — 알람별 확인·복구 절차, 배포 롤백, DB 복원 |
| [docs/operations/moderation-block.md](docs/operations/moderation-block.md) | 운영 차단(모더레이션) 절차 — 차단 SQL·재색인 호출 세트·보관 정책. 관리자 API 도입 전까지 DB 직접 UPDATE로 수행 |
| [docs/testing/rest-docs-guide.md](docs/testing/rest-docs-guide.md) | MockMvc + Spring REST Docs 테스트 전략, 계층 구조, 새 API 개발 체크리스트. §6에 API 문서 자동 퍼블리시 파이프라인(https://at-crew-api-docs.pages.dev/, main push마다 자동 갱신) |
| [docs/roadmap.md](docs/roadmap.md) | 모듈 개발 로드맵 — Figma 대비 미구현 영역 및 착수 우선순위(인증 시스템 → recruit → 기업 프로필 → 검색 → 결제 → 설정) |
