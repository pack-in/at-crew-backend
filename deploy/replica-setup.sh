#!/usr/bin/env bash
# MariaDB 반동기 Replica 최초 시딩 + 복제 연결 — 1회만 실행한다.
# docs/design/infra-security-hardening-design.md D6, plans/260901-infra-upgrade/PLAN-HUMAN.md PH-08.
#
# 전제:
#   - EC2 #1: docker-compose.app.yml의 mariadb(Primary)가 이미 semisync_master로 기동 중(PA-07)
#   - EC2 #2(또는 그 자리): docker-compose.db-replica.yml의 mariadb-replica가 기동 중, 아직 빈 상태
#     (Primary와 같은 MARIADB_ROOT_PASSWORD로 이미 기동돼 있어야 한다)
#   - 이슈 #76(ES→EC2 #1 통합)이 끝나 EC2 #2가 실제로 비어 있음을 사람이 먼저 확인했다
#
# 실행 위치: EC2 #1 (Primary가 있는 곳). REPLICA_HOST로 EC2 #2의 프라이빗 IP를 넘긴다.
#   REPLICA_HOST=<EC2#2 프라이빗IP> REPL_PASSWORD=<새로 정할 복제전용 암호> ./deploy/replica-setup.sh
#
# 주의: Replica(mariadb-replica 컨테이너)는 EC2 #2에 있어 이 호스트에서 `docker exec`로 직접
# 붙일 수 없다. 로컬 컨테이너를 exec하는 대신 `docker run --rm mariadb:11.4`로 매번 독립된
# 클라이언트를 띄워 네트워크로 접속한다 — Primary 컨테이너 생사와 무관하게 동작해야 하는
# db-promote.sh와도 같은 패턴을 쓴다.
#
# mysqldump를 Primary에서 뜨는 동안 --single-transaction으로 잠금 없이 진행하지만, 데이터가
# 커지면 그만큼 스냅샷 시점과 복제 시작 시점 사이 간격이 벌어진다 — 트래픽이 적은 시간대에 실행할 것.
set -euo pipefail

DEPLOY_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ENV_FILE="$DEPLOY_DIR/.env"
read_env() { sed -n "s/^$1=//p" "$ENV_FILE" | tail -1 | sed -e 's/^"//' -e 's/"$//'; }

: "${REPLICA_HOST:?REPLICA_HOST(EC2 #2 프라이빗 IP)를 환경변수로 넘길 것}"
MARIADB_ROOT_PASSWORD="$(read_env MARIADB_ROOT_PASSWORD)"
: "${MARIADB_ROOT_PASSWORD:?[replica-setup] .env에 MARIADB_ROOT_PASSWORD 없음}"

REPL_USER="repl"
REPL_PASSWORD="${REPL_PASSWORD:?REPL_PASSWORD(복제 전용 계정 암호, 새로 정해서 넘길 것)}"

# Replica에 원격으로 SQL을 보낼 때 매번 쓰는 헬퍼 — 로컬에 mariadb-replica 컨테이너가 없으므로
# docker run으로 독립 클라이언트를 띄운다(이미지가 로컬에 캐시돼 있어 빠르다).
mysql_replica() {
  docker run --rm -i -e MYSQL_PWD="$MARIADB_ROOT_PASSWORD" mariadb:11.4 \
    mariadb -u root -h "$REPLICA_HOST" "$@"
}

PRIMARY_CONTAINER="$(docker ps --filter 'label=com.docker.compose.service=mariadb' --format '{{.Names}}' | head -1)"
[ -n "$PRIMARY_CONTAINER" ] || { echo "[replica-setup] 실행 중인 Primary mariadb 컨테이너가 없다" >&2; exit 1; }

PRIMARY_HOST="$(hostname -I | awk '{print $1}')"
echo "[replica-setup] Primary 프라이빗 IP: $PRIMARY_HOST"

echo "[replica-setup] 0) Replica가 응답하는지 먼저 확인"
mysql_replica -e "SELECT 1;" >/dev/null

echo "[replica-setup] 1) Primary에 복제 전용 계정 생성"
docker exec -e MYSQL_PWD="$MARIADB_ROOT_PASSWORD" "$PRIMARY_CONTAINER" mariadb -u root -e "
  CREATE USER IF NOT EXISTS '${REPL_USER}'@'%' IDENTIFIED BY '${REPL_PASSWORD}';
  GRANT REPLICATION SLAVE ON *.* TO '${REPL_USER}'@'%';
  FLUSH PRIVILEGES;
"

echo "[replica-setup] 2) 일관된 스냅샷 + binlog 좌표 확보"
WORK="$(mktemp -d)"
DUMP="$WORK/seed.sql.gz"
trap 'rm -rf "$WORK"' EXIT

# --master-data=2: 덤프 안에 CHANGE MASTER TO용 binlog 파일명/포지션을 주석으로 남긴다.
docker exec -e MYSQL_PWD="$MARIADB_ROOT_PASSWORD" "$PRIMARY_CONTAINER" \
  mariadb-dump --single-transaction --master-data=2 --routines --events -u root atcrew \
  | gzip -9 > "$DUMP"

COORDS="$(zcat "$DUMP" | grep -m1 -E "^-- CHANGE MASTER TO" || true)"
LOG_FILE="$(echo "$COORDS" | sed -n "s/.*MASTER_LOG_FILE='\([^']*\)'.*/\1/p")"
LOG_POS="$(echo "$COORDS" | sed -n "s/.*MASTER_LOG_POS=\([0-9]*\).*/\1/p")"
[ -n "$LOG_FILE" ] && [ -n "$LOG_POS" ] || { echo "[replica-setup] 덤프에서 binlog 좌표를 못 읽었다" >&2; exit 1; }
echo "[replica-setup] binlog 좌표: $LOG_FILE / $LOG_POS"

echo "[replica-setup] 3) Replica에 시딩(용량에 따라 시간이 걸릴 수 있다)"
zcat "$DUMP" | mysql_replica atcrew

echo "[replica-setup] 4) Replica에 복제 연결"
mysql_replica -e "
  STOP SLAVE;
  CHANGE MASTER TO
    MASTER_HOST='${PRIMARY_HOST}',
    MASTER_USER='${REPL_USER}',
    MASTER_PASSWORD='${REPL_PASSWORD}',
    MASTER_LOG_FILE='${LOG_FILE}',
    MASTER_LOG_POS=${LOG_POS};
  START SLAVE;
"

echo "[replica-setup] 5) 상태 확인 (Slave_IO_Running/Slave_SQL_Running이 Yes여야 정상)"
mysql_replica -e "SHOW SLAVE STATUS\G" \
  | grep -E "Slave_IO_Running|Slave_SQL_Running|Seconds_Behind_Master|Rpl_semi_sync"

echo "[replica-setup] 완료 — Seconds_Behind_Master가 0에 가까워지는지 몇 분 더 지켜볼 것"
