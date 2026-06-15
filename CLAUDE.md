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
| [docs/conventions/commit.md](docs/conventions/commit.md) | 커밋 컨벤션 및 워크플로우 |
| [docs/design/figma.md](docs/design/figma.md) | Figma 파일 링크 및 UI 페이지 목록 (**비공개**) |
| [docs/design/auth-email-custom-redesign.md](docs/design/auth-email-custom-redesign.md) | 이메일 자체 인증 재설계 (Firebase → Custom) 설계안 |
| [docs/design/artwork-module-design.md](docs/design/artwork-module-design.md) | artwork 모듈 설계 — 작품·북마크·휴지통, 이미지 업로드(Presigned URL + Worker) |
