#!/usr/bin/env bash
# MariaDB Replica 복제 지연 측정 — 기존 backup.sh와 동일한 textfile collector 패턴으로
# Seconds_Behind_Master를 Alloy(PA-04)가 읽을 수 있게 남긴다.
# docs/design/infra-security-hardening-design.md D6, plans/260901-infra-upgrade/ PA-08.
#
# 설치(EC2 #1에서 1회, backup.sh와 같은 textfile 디렉토리 재사용):
#   chmod +x ~/at-crew-backend/deploy/replica-lag-check.sh
#   sudo cp systemd/atcrew-replica-lag.{service,timer} /etc/systemd/system/
#   sudo systemctl daemon-reload && sudo systemctl enable --now atcrew-replica-lag.timer
#
# EC2 #1에서 실행하면서 EC2 #2(Replica)에 원격으로 접속해 SHOW SLAVE STATUS를 읽는다 —
# Alloy가 이미 EC2 #1에서만 돌고 있어(docker-compose.observability.yml), Replica 쪽에
# 관측 스택을 통째로 복제하지 않고 기존 파이프라인에 얹는다.
#
# 주의: mariadb-replica 컨테이너는 EC2 #2에 있어 `docker exec`로 로컬에서 붙일 수 없다.
# `docker run --rm`으로 매번 독립된 클라이언트를 띄워 네트워크로 접속한다.
set -euo pipefail

DEPLOY_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ENV_FILE="$DEPLOY_DIR/.env"
METRIC_DIR="${METRIC_DIR:-/var/lib/node_exporter/textfile_collector}"
METRIC_FILE="$METRIC_DIR/replica_lag.prom"

read_env() { sed -n "s/^$1=//p" "$ENV_FILE" | tail -1 | sed -e 's/^"//' -e 's/"$//'; }
REPLICA_HOST="$(read_env REPLICA_HOST)"
MARIADB_ROOT_PASSWORD="$(read_env MARIADB_ROOT_PASSWORD)"
: "${REPLICA_HOST:?[replica-lag] .env에 REPLICA_HOST(EC2 #2 프라이빗 IP) 없음}"
: "${MARIADB_ROOT_PASSWORD:?[replica-lag] .env에 MARIADB_ROOT_PASSWORD 없음}"

TMP="$METRIC_FILE.tmp"

# Replica가 아예 응답하지 않는 경우(연결 실패)도 "지표 없음"이 아니라 "매우 큰 지연"으로 보이게
# 만든다 — 지표가 조용히 사라지면 알람 쪽에서 noData 처리를 별도로 신경 써야 하는데, 값 하나로
# 통일해두면 기존 threshold 알람 로직을 그대로 재사용할 수 있다.
STATUS="$(docker run --rm -e MYSQL_PWD="$MARIADB_ROOT_PASSWORD" mariadb:11.4 \
  mariadb -u root -h "$REPLICA_HOST" --connect-timeout=5 -e "SHOW SLAVE STATUS\G" 2>/dev/null || true)"

if [ -z "$STATUS" ]; then
  LAG=999999
  IO_RUNNING=0
  SQL_RUNNING=0
else
  LAG="$(echo "$STATUS" | sed -n 's/^\s*Seconds_Behind_Master:\s*//p' | head -1)"
  [ "$LAG" = "NULL" ] || [ -z "$LAG" ] && LAG=999999
  IO_RUNNING=0
  SQL_RUNNING=0
  echo "$STATUS" | grep -q "Slave_IO_Running: Yes" && IO_RUNNING=1
  echo "$STATUS" | grep -q "Slave_SQL_Running: Yes" && SQL_RUNNING=1
fi

{
  echo "# HELP atcrew_replica_seconds_behind_master 복제 지연(초). 연결 실패 시 999999"
  echo "# TYPE atcrew_replica_seconds_behind_master gauge"
  echo "atcrew_replica_seconds_behind_master $LAG"
  echo "# HELP atcrew_replica_io_running IO 스레드 정상 여부(1=정상)"
  echo "# TYPE atcrew_replica_io_running gauge"
  echo "atcrew_replica_io_running $IO_RUNNING"
  echo "# HELP atcrew_replica_sql_running SQL 스레드 정상 여부(1=정상)"
  echo "# TYPE atcrew_replica_sql_running gauge"
  echo "atcrew_replica_sql_running $SQL_RUNNING"
} > "$TMP"
mv "$TMP" "$METRIC_FILE"
