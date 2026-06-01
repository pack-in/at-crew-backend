# 커밋 컨벤션

[Conventional Commits](https://www.conventionalcommits.org/) 표준을 따른다.

## 형식

```
type(scope): 제목        ← 50자 이내, 마침표 없음, 명령형

본문                     ← 선택. 무엇을 했는지가 아닌 왜 했는지를 설명

footer                   ← 선택. 이슈 연결 등 (예: Closes #123)
```

## Type 정의

| type | 용도 |
|------|------|
| `feat` | 새로운 기능 추가 |
| `fix` | 버그 수정 |
| `docs` | 문서 변경 |
| `refactor` | 기능 변경 없는 코드 구조 개선 |
| `test` | 테스트 추가·수정 |
| `chore` | 빌드·도구·설정 변경 (기능·버그와 무관) |
| `ci` | CI/CD 설정 변경 |
| `perf` | 성능 개선 |

## 작성 규칙

- **scope**: 변경이 영향을 미치는 도메인 모듈 (예: `auth`, `portfolio`, `user`)
- **제목**: 현재 시제 명령형 (예: `추가한다` ✗ → `추가` ✓)
- **본문**: 72자 줄바꿈 권장, 선택 사항
- **WIP 커밋은 원격에 push하지 않는다**

## 예시

```
feat(auth): Google OAuth 2.0 로그인 구현

소셜 로그인 흐름에서 구글 계정으로 인증 후 JWT를 발급한다.
기존 이메일 로그인과 동일한 토큰 구조를 사용한다.
```

```
fix(portfolio): 이미지 업로드 시 확장자 검증 누락 수정
```

```
chore: .gitignore에 로컬 환경 설정 파일 추가
```

## 커밋 워크플로우

```sh
git status                         # 변경 파일 확인
git diff                           # 변경 내용 검토
git add <file>                     # 논리적 단위로 staging
git commit -m "type(scope): 제목"  # 커밋 (pre-commit hook 자동 실행)
```

- 하나의 커밋 = 하나의 논리적 변경
- `git add .` 보다 파일 단위로 명시적으로 staging
- 커밋 후 `git log --oneline`으로 히스토리 확인 권장
