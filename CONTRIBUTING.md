# 기여 가이드

이 문서는 이 저장소에서 **커밋·브랜치·PR·이슈를 어떤 형식으로 남기는지**를 정한다.
커밋 메시지 형식의 상세는 [docs/conventions/commit.md](docs/conventions/commit.md)에 따로 있고,
이 문서는 그 위에 나머지 규약을 얹는다.

## 로컬 준비

```bash
brew install gitleaks        # secrets 스캐너
sh scripts/install-hooks.sh  # pre-commit hook 등록
```

hook은 커밋 직전에 스테이징된 변경만 스캔한다. 키·토큰이 들어간 파일은 커밋되지 않는다.

## 브랜치

```
<type>/<slug>
```

- `type`은 커밋 타입과 같은 어휘를 쓴다 — `feat`, `fix`, `refactor`, `docs`, `chore`, `ci`, `test`.
- `slug`는 kebab-case로 무엇을 하는지 알 수 있게 쓴다. 이슈 번호가 있으면 뒤에 붙여도 된다.

```
feat/observability
fix/flyway-migration-order
docs/readme-portfolio
feat/recruit-boost-42
```

작성자 이름을 접두사로 넣지 않는다. 과거에 `<이름>/<이름>-feature-...`처럼 중복된 브랜치가 만들어진 적이
있고, 1인·소수 인원 저장소에서 이름 접두사는 정보를 더하지 않는다.

### 브랜치 흐름

```
feat/xxx ─┐
fix/yyy  ─┼─▶ dev ─▶ main ─▶ prod 자동 배포
docs/zzz ─┘   (통합)   (배포)
```

- **작업 브랜치는 `dev`에서 분기하고, PR도 `dev`로 보낸다.**
- **`dev` → `main`은 실제로 배포할 때만 연다.** main에 push되는 순간 CI가 빌드·테스트 후 prod EC2에
  자동 배포하고([.github/workflows/deploy.yml](.github/workflows/deploy.yml)), API 문서 사이트 갱신과
  릴리스 노트 생성도 함께 돈다. 즉 **main 병합 = 배포 실행**이다.
- 어느 쪽이든 직접 push하지 않고 PR로 병합한다. CI는 두 대상 브랜치의 PR을 모두 검증한다.

### 병합 방식

| 경로 | 방식 | 이유 |
|---|---|---|
| 작업 브랜치 → `dev` | **Squash merge** | PR 제목이 그대로 커밋이 되어 히스토리가 한 줄로 정리된다. 작업 중의 중간 커밋은 남길 가치가 없다 |
| `dev` → `main` | **Merge commit** | squash하면 개별 `feat`·`fix` 커밋이 하나로 뭉개져 릴리스 노트가 "배포"라는 한 줄이 된다. release-please는 main의 개별 커밋을 읽어 CHANGELOG를 만든다 |

### 예외 — Dependabot 보안 PR

Dependabot의 보안 업데이트는 **기본 브랜치(`main`)에만** PR을 연다. `.github/dependabot.yml`에
`target-branch`를 지정하면 대상은 바뀌지만 **보안 업데이트 자체가 동작하지 않게 되므로** 그 설정은 쓰지 않는다.

그래서 Dependabot 보안 PR은 위 흐름의 유일한 예외다. 받았을 때는 둘 중 하나를 고른다.

- 내용을 확인하고 그대로 `main`에 넣는다 — 곧 배포되므로 CI 통과를 반드시 확인한다
- 같은 변경을 `dev` 기준으로 다시 올리고 Dependabot PR은 닫는다 — 다른 변경과 함께 배포하고 싶을 때

**`dev`가 `main`보다 뒤처진 상태에서 `main`에만 넣으면 다음 `dev → main`에서 되돌아갈 수 있다.**
`main`에 직접 넣었다면 곧바로 `main`을 `dev`로 병합해 맞춘다.

### 브랜치 흐름은 설정으로 강제되지 않는다

GitHub 브랜치 보호에는 "PR의 base를 제한"하는 규칙이 없다. 작업 브랜치에서 `main`으로 바로 PR을
여는 것을 막을 방법이 없으므로 **규율로 지킨다.** `main`에는 상태 검사·force push 금지만 걸려 있다.

## 커밋

[Conventional Commits](https://www.conventionalcommits.org/)를 따른다. 상세는
[docs/conventions/commit.md](docs/conventions/commit.md).

```
type(scope): 제목        ← 50자 이내, 마침표 없음, 명령형

본문 — 무엇을 했는지가 아니라 왜 했는지
```

본문은 선택이지만, **되돌리기 어려운 결정이나 겉보기와 다른 이유가 있는 변경에는 반드시 쓴다.**
"무엇"은 diff에 이미 있다. 6개월 뒤에 사라지는 것은 "왜"다.

커밋 타입은 릴리스 노트 생성에도 쓰인다([release-please-config.json](release-please-config.json)) —
`feat`·`fix`·`perf`만 CHANGELOG에 노출되고 나머지는 숨겨진다.

## Pull Request

- **제목은 커밋과 같은 형식**(`type(scope): 제목`)을 쓴다. squash merge 시 그대로 커밋 메시지가 된다.
- 템플릿([.github/PULL_REQUEST_TEMPLATE.md](.github/PULL_REQUEST_TEMPLATE.md))의 네 항목을 채운다 —
  개요, 변경 사항, 관련 이슈(`Closes #N`), 체크리스트.
- `area: *` 라벨은 변경 경로에 따라 자동으로 붙는다([.github/labeler.yml](.github/labeler.yml)). 손으로 달지 않는다.
- CI(빌드·전체 테스트)가 통과해야 병합한다.
- 오래 열어두지 않는다. 리뷰가 막혀 있으면 PR을 쪼개거나 draft로 내린다.

## 이슈

- 템플릿 두 종([버그](.github/ISSUE_TEMPLATE/bug_report.yml) /
  [기능](.github/ISSUE_TEMPLATE/feature_request.yml))으로만 만든다. 빈 이슈는 막혀 있다.
- **제목은 템플릿이 넣어주는 `type: ` 접두사를 유지한다.** `[검토]`, `[인프라]` 같은 임의 접두사를
  쓰지 않는다 — 그런 분류는 제목이 아니라 라벨의 일이다.
- 분류는 라벨로 한다.
  - `feat` / `fix` — 이슈의 성격
  - `area: *` — 영향 범위(모듈·인프라·문서)
  - `needs-triage` — 아직 검토되지 않음. 처리 방향이 정해지면 뗀다.
- 설계 판단이 필요한 이슈는 결론을 이슈 코멘트에 남기지 말고 `docs/design/`에 문서로 남기고 링크한다.
  이슈는 닫히면 잘 읽히지 않는다.

## 문서

- 설명·주석·문서는 한국어로 쓴다(코드·식별자·기술 용어는 영문 유지).
- 설계 문서는 `docs/design/`, 운영 절차는 `docs/operations/`, 규약은 `docs/conventions/`.
- 새 문서를 만들면 [CLAUDE.md](CLAUDE.md)의 문서 목록 표에 한 줄 추가한다. 그 표가 문서 인덱스다.
- Figma 파일 키·API 키 등 외부 서비스 식별자는 보안 정보로 취급해 커밋하지 않는다.

## 테스트

- 새 API는 MockMvc + Spring REST Docs 테스트를 함께 작성한다
  ([docs/testing/rest-docs-guide.md](docs/testing/rest-docs-guide.md)).
- 통합 테스트는 Testcontainers(MariaDB·Elasticsearch)를 쓴다. 로컬 환경에 의존하는 테스트를 만들지 않는다.
- 병합 전 `./gradlew build`가 전부 통과해야 한다.
