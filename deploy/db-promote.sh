#!/usr/bin/env bash
# MariaDB Replica → Primary 승격. 사람이 판단해서 직접 실행한다 — 자동 트리거 없음.
# docs/design/infra-security-hardening-design.md D7("완전 자동 페일오버는 하지 않는다"),
# plans/260901-infra-upgrade/PLAN-HUMAN.md PH-09(드릴)·장애 시 실사용.
#
# 실행 전에 반드시 확인:
#   1) 기존 Primary가 정말 죽었는지 (네트워크 순간 단절이나 복제 지연 오탐이 아닌지) —
#      atcrew-replica-down 알람만 보고 판단하지 말고 EC2 #1 자체의 다른 알람(atcrew-app-down 등)도
#      같이 확인한다. Primary가 살아있는데 승격하면 두 DB가 동시에 쓰기를 받는 split-brain이 된다.
#   2) Seconds_Behind_Master가 0에 가까웠는지 — 지연이 컸다면 최근 데이터 일부가 유실될 수 있다.
#
# 이 스크립트는 Primary가 완전히 죽은 상태에서 실행되는 게 전제다 — 그래서 Replica 쪽 명령은
# 로컬 컨테이너(docker exec)가 아니라 `docker run --rm`으로 매번 독립된 클라이언트를 띄워
# 네트워크로 접속한다. Primary 컨테이너의 생사와 이 스크립트의 동작이 무관해야 한다.
#
# Route53 Private Hosted Zone(PA-10)이 아직 IAM 권한 부족으로 비활성 상태(route53_enabled=false)라
# 지금은 앱의 .env를 직접 바꾸고 재기동하는 방식으로 전환한다 — 권한이 열리면 이 스크립트를
# `aws route53 change-resource-record-sets`로 db.internal.at-crew.com만 바꾸는 방식으로 교체하고
# 앱 재기동 단계를 없앤다(그게 D8의 원래 의도).
set -euo pipefail

DEPLOY_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ENV_FILE="$DEPLOY_DIR/.env"
read_env() { sed -n "s/^$1=//p" "$ENV_FILE" | tail -1 | sed -e 's/^"//' -e 's/"$//'; }

: "${REPLICA_HOST:?REPLICA_HOST(승격 대상 EC2 #2 프라이빗 IP)를 환경변수로 넘길 것}"
MARIADB_ROOT_PASSWORD="$(read_env MARIADB_ROOT_PASSWORD)"
: "${MARIADB_ROOT_PASSWORD:?[promote] .env에 MARIADB_ROOT_PASSWORD 없음}"

mysql_replica() {
  docker run --rm -e MYSQL_PWD="$MARIADB_ROOT_PASSWORD" mariadb:11.4 \
    mariadb -u root -h "$REPLICA_HOST" --connect-timeout=5 "$@"
}

echo "[promote] 0) Replica가 응답하는지 먼저 확인"
mysql_replica -e "SELECT 1;" >/dev/null || {
  echo "[promote] Replica에 접속할 수 없다 — 승격할 대상 자체가 죽어있으면 진행 의미가 없다" >&2
  exit 1
}

echo "[promote] 1) Replica의 복제 스레드를 끊는다"
mysql_replica -e "
  STOP SLAVE;
  RESET SLAVE ALL;
  SET GLOBAL read_only = 0;
"

echo "[promote] 2) 앱이 새 Primary(구 Replica)를 보도록 전환"
if grep -q '^MARIADB_HOST=' "$ENV_FILE"; then
  sed -i.bak "s/^MARIADB_HOST=.*/MARIADB_HOST=${REPLICA_HOST}/" "$ENV_FILE"
else
  echo "MARIADB_HOST=${REPLICA_HOST}" >> "$ENV_FILE"
fi
echo "[promote] .env 백업: ${ENV_FILE}.bak"

echo "[promote] 3) 앱 재기동 — 여기서 짧은 다운타임 발생(수 초~수십 초)"
docker compose -f "$DEPLOY_DIR/docker-compose.app.yml" up -d app

echo "[promote] 4) 헬스체크"
sleep 5
curl -fsS http://127.0.0.1:8081/actuator/health/liveness || {
  echo "[promote] 경고: liveness 확인 실패 — 로그를 직접 확인할 것(docker logs app)" >&2
  exit 1
}

echo "[promote] 완료. 구 Primary는 원인 파악 후 필요하면 새 Replica로 재구성(replica-setup.sh 재실행)."
