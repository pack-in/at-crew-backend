#!/bin/sh
# Git hooks 설치 스크립트
# 새로운 팀원이 클론 후 최초 1회 실행: sh scripts/install-hooks.sh

set -e

HOOKS_DIR=".git/hooks"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"

# gitleaks 설치 여부 확인
if ! command -v gitleaks >/dev/null 2>&1; then
    echo "오류: gitleaks가 설치되어 있지 않습니다."
    echo "설치 방법: brew install gitleaks"
    exit 1
fi

# pre-commit hook 생성
cat > "$PROJECT_ROOT/$HOOKS_DIR/pre-commit" << 'EOF'
#!/bin/sh
# gitleaks — 커밋 전 secrets 스캔

echo "🔍 gitleaks: secrets 스캔 중..."

if ! gitleaks protect --staged --config=.gitleaks.toml -v; then
    echo ""
    echo "❌ 민감 정보가 감지되었습니다. 커밋이 차단됩니다."
    echo "   해당 파일을 .gitignore에 추가하거나 내용을 제거한 후 다시 시도하세요."
    exit 1
fi

echo "✅ secrets 스캔 통과"
EOF

chmod +x "$PROJECT_ROOT/$HOOKS_DIR/pre-commit"
echo "✅ pre-commit hook 설치 완료: $PROJECT_ROOT/$HOOKS_DIR/pre-commit"
