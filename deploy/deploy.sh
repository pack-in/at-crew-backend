#!/usr/bin/env bash
# 앱 서버에 새 빌드를 배포한다. laiteu의 수동 docker push/pull 흐름을 그대로 따르되,
# 이번엔 docker-compose.app.yml이 MariaDB 컨테이너까지 같이 관리한다.
#
# 원격 실행은 SSM이다(ssm-run.sh). 앱 서버가 프라이빗 서브넷에 있어 SSH가 닿지 않는다.
#
# 사전 준비 (레포 밖 — 최초 1회):
#   1. Docker Hub 계정으로 로그인: docker login
#   2. 앱 서버에 deploy/ 디렉토리 전체와 .env를 올려둘 것. .env는 절대 git에 커밋하지 말 것.
#   3. AWS 자격증명이 설정돼 있고 ssm:SendCommand 권한이 있을 것 (aws sts get-caller-identity로 확인)
#   4. 아래 변수를 실제 값으로 채울 것(또는 셸 환경변수로 넘겨서 실행:
#      DOCKERHUB_USER=xxx APP_INSTANCE_ID=i-xxx ./deploy.sh)
set -euo pipefail

DOCKERHUB_USER="${DOCKERHUB_USER:-<dockerhub-사용자명>}"
IMAGE_NAME="at-crew-backend"
IMAGE_TAG="${IMAGE_TAG:-latest}"
APP_IMAGE="${DOCKERHUB_USER}/${IMAGE_NAME}:${IMAGE_TAG}"

# 인스턴스 ID는 저장소 Secret APP_INSTANCE_ID와 같은 값이다(AWS 콘솔에서도 확인 가능).
export APP_INSTANCE_ID="${APP_INSTANCE_ID:?APP_INSTANCE_ID를 지정할 것 (i-...)}"
REMOTE_DEPLOY_DIR="${REMOTE_DEPLOY_DIR:-/home/ec2-user/at-crew-backend/deploy}"

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_ROOT"

echo "1/3 이미지 빌드 및 푸시: $APP_IMAGE"
docker build -t "$APP_IMAGE" .
docker push "$APP_IMAGE"

echo "2/3 원격에서 새 이미지 받고 재기동"
# 서버(Amazon Linux 2023)엔 docker compose 플러그인이 없어 standalone docker-compose(하이픈) 바이너리를
# 따로 설치해뒀다(deploy/README.md 참고) — 로컬에 docker compose(플러그인)가 있어도 원격 명령은 반드시
# 하이픈 버전으로 맞춘다.
"$REPO_ROOT/deploy/ssm-run.sh" <<EOF
set -e
cd $REMOTE_DEPLOY_DIR
export APP_IMAGE=$APP_IMAGE
docker-compose -f docker-compose.app.yml pull app
docker-compose -f docker-compose.app.yml up -d
EOF

# 이전 배포 이미지는 남기지 않는다 — 배포마다 500MB씩 쌓여 루트 볼륨이 찬다(.github/workflows/deploy.yml
# 동일 단계 참고). 실행 중 컨테이너가 쓰는 이미지는 prune 대상이 아니다.
echo "docker image prune -af" | "$REPO_ROOT/deploy/ssm-run.sh"

echo "3/3 완료. 로그 확인:"
echo "  echo \"cd $REMOTE_DEPLOY_DIR && docker logs --tail=100 \\\$(docker ps -q -f label=com.docker.compose.service=app)\" | deploy/ssm-run.sh"
