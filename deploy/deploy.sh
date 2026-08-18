#!/usr/bin/env bash
# 앱 서버(EC2 #1)에 새 빌드를 배포한다. laiteu의 수동 docker push/pull 흐름을 그대로 따르되,
# 이번엔 docker-compose.app.yml이 MariaDB 컨테이너까지 같이 관리한다.
#
# 사전 준비 (레포 밖 — 최초 1회):
#   1. Docker Hub 계정으로 로그인: docker login
#   2. EC2 #1에 SSH 접속해 deploy/ 디렉토리 전체와 .env를 올려둘 것(scp 또는 git clone).
#      .env는 절대 git에 커밋하지 말 것 — scp로만 옮긴다.
#   3. 아래 변수를 실제 값으로 채울 것(또는 셸 환경변수로 넘겨서 실행: DOCKERHUB_USER=xxx ./deploy.sh)
set -euo pipefail

DOCKERHUB_USER="${DOCKERHUB_USER:-<dockerhub-사용자명>}"
IMAGE_NAME="at-crew-backend"
IMAGE_TAG="${IMAGE_TAG:-latest}"
APP_IMAGE="${DOCKERHUB_USER}/${IMAGE_NAME}:${IMAGE_TAG}"

SSH_KEY="${SSH_KEY:-<pem-파일-경로>}"
APP_HOST="${APP_HOST:-ec2-user@<앱-서버-EC2-퍼블릭IP또는도메인>}"
REMOTE_DEPLOY_DIR="${REMOTE_DEPLOY_DIR:-~/at-crew-backend/deploy}"

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_ROOT"

echo "1/3 이미지 빌드 및 푸시: $APP_IMAGE"
docker build -t "$APP_IMAGE" .
docker push "$APP_IMAGE"

echo "2/3 원격(EC2 #1)에서 새 이미지 받고 재기동"
# 서버(Amazon Linux 2023)엔 docker compose 플러그인이 없어 standalone docker-compose(하이픈) 바이너리를
# 따로 설치해뒀다(deploy/README.md 참고) — 로컬에 docker compose(플러그인)가 있어도 원격 명령은 반드시
# 하이픈 버전으로 맞춘다.
ssh -i "$SSH_KEY" "$APP_HOST" "cd $REMOTE_DEPLOY_DIR && \
  APP_IMAGE=$APP_IMAGE docker-compose -f docker-compose.app.yml pull app && \
  APP_IMAGE=$APP_IMAGE docker-compose -f docker-compose.app.yml up -d"

echo "3/3 완료. 로그 확인: ssh -i $SSH_KEY $APP_HOST 'cd $REMOTE_DEPLOY_DIR && docker-compose -f docker-compose.app.yml logs -f app'"
