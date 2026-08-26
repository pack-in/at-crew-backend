#!/usr/bin/env bash
# MariaDB 일일 백업 — 덤프를 R2에 올리고, 성공 시각을 관측 파이프라인이 읽을 수 있는 파일로 남긴다.
# 설계: docs/design/observability-design.md §10
#
# 배경: 2026-08-24까지 이 서비스에는 백업이 전혀 없었다. 사용자 데이터가 EC2 #1의 도커 볼륨
# 하나에만 있어서, 인스턴스를 잃으면 회원·작품·결제 데이터가 그대로 사라지는 상태였다.
#
# 설치(EC2 #1에서 1회):
#   chmod +x ~/at-crew-backend/deploy/backup.sh
#   sudo mkdir -p /var/lib/node_exporter/textfile_collector && sudo chown ec2-user /var/lib/node_exporter/textfile_collector
#   crontab -e  →  0 18 * * * /home/ec2-user/at-crew-backend/deploy/backup.sh >> /home/ec2-user/backup.log 2>&1
#   (18:00 UTC = 03:00 KST, 트래픽 최저 시간대)
#
# 실패 감지는 이 스크립트가 하지 않는다 — 성공했을 때만 타임스탬프를 갱신하고, "26시간 넘게 갱신이
# 없으면" 알람을 울리는 쪽(PA-09)이 판단한다. 스크립트가 죽어서 아무 신호도 못 보내는 경우까지
# 잡으려면 그 방향이어야 한다.
set -euo pipefail

DEPLOY_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ENV_FILE="$DEPLOY_DIR/.env"
RETENTION_DAYS="${RETENTION_DAYS:-30}"
# 백업 버킷은 이미지용 버킷(cloudflare.r2.bucket = at-crew-storage)과 분리한다 — 같은 버킷에 두면
# 그 버킷에 공개 접근 설정이 한 번 잘못 걸리는 것만으로 회원·결제 데이터 전체가 노출된다.
# 값은 아래에서 .env(R2_BACKUP_BUCKET) 또는 기본값 at-crew-backups로 정해진다.
PREFIX="db-backups"
METRIC_DIR="${METRIC_DIR:-/var/lib/node_exporter/textfile_collector}"
METRIC_FILE="$METRIC_DIR/backup.prom"

[ -f "$ENV_FILE" ] || { echo "[backup] .env를 찾을 수 없다: $ENV_FILE" >&2; exit 1; }

# .env를 source 하지 않는다 — docker-compose의 env_file 파서와 달리 셸은 값을 명령으로 해석한다.
# 실제로 MAIL_FROM_ADDRESS="AT-CREW <onboarding@...>" 같은 줄이 리다이렉트로 해석돼 깨진다.
# 필요한 키만 그대로 읽어온다.
read_env() { sed -n "s/^$1=//p" "$ENV_FILE" | tail -1 | sed -e 's/^"//' -e 's/"$//'; }

MARIADB_ROOT_PASSWORD="$(read_env MARIADB_ROOT_PASSWORD)"
R2_ENDPOINT="$(read_env R2_ENDPOINT)"
R2_ACCESS_KEY="$(read_env R2_ACCESS_KEY)"
R2_SECRET_KEY="$(read_env R2_SECRET_KEY)"
BACKUP_BUCKET="${R2_BACKUP_BUCKET:-$(read_env R2_BACKUP_BUCKET)}"
BACKUP_BUCKET="${BACKUP_BUCKET:-at-crew-backups}"

: "${MARIADB_ROOT_PASSWORD:?[backup] MARIADB_ROOT_PASSWORD 없음}"
: "${R2_ENDPOINT:?[backup] R2_ENDPOINT 없음}"
: "${R2_ACCESS_KEY:?[backup] R2_ACCESS_KEY 없음}"
: "${R2_SECRET_KEY:?[backup] R2_SECRET_KEY 없음}"

STAMP="$(date -u +%Y%m%dT%H%M%SZ)"
WORK="$(mktemp -d)"
DUMP="$WORK/atcrew-$STAMP.sql.gz"
trap 'rm -rf "$WORK"' EXIT

CONTAINER="$(docker ps --filter 'label=com.docker.compose.service=mariadb' --format '{{.Names}}' | head -1)"
[ -n "$CONTAINER" ] || { echo "[backup] 실행 중인 mariadb 컨테이너가 없다" >&2; exit 1; }

echo "[backup] 덤프 시작 ($CONTAINER)"
# --single-transaction: InnoDB를 잠그지 않고 일관된 스냅샷을 뜬다(서비스 중단 없음).
# --routines/--events: 스토어드 프로시저·이벤트까지 포함.
docker exec -e MYSQL_PWD="$MARIADB_ROOT_PASSWORD" "$CONTAINER" \
  mariadb-dump --single-transaction --quick --routines --events -u root atcrew \
  | gzip -9 > "$DUMP"

SIZE=$(stat -c%s "$DUMP")
[ "$SIZE" -gt 1024 ] || { echo "[backup] 덤프가 비정상적으로 작다(${SIZE}B) — 업로드하지 않는다" >&2; exit 1; }
echo "[backup] 덤프 완료: $(basename "$DUMP") ($((SIZE/1024))KB)"

export AWS_ACCESS_KEY_ID="$R2_ACCESS_KEY"
export AWS_SECRET_ACCESS_KEY="$R2_SECRET_KEY"
export AWS_DEFAULT_REGION=auto
export AWS_REQUEST_CHECKSUM_CALCULATION=when_required
export AWS_RESPONSE_CHECKSUM_VALIDATION=when_required

aws s3 cp "$DUMP" "s3://$BACKUP_BUCKET/$PREFIX/$(basename "$DUMP")" --endpoint-url "$R2_ENDPOINT" --only-show-errors
echo "[backup] 업로드 완료: $PREFIX/$(basename "$DUMP")"

# 보관 기간 초과분 삭제. R2는 수명주기 규칙 설정이 콘솔 의존이라 여기서 직접 정리한다.
CUTOFF=$(date -u -d "-${RETENTION_DAYS} days" +%s)
aws s3api list-objects-v2 --bucket "$BACKUP_BUCKET" --prefix "$PREFIX/" --endpoint-url "$R2_ENDPOINT" \
  --query 'Contents[].[Key,LastModified]' --output text 2>/dev/null | while read -r KEY MODIFIED; do
  [ -n "${KEY:-}" ] || continue
  if [ "$(date -u -d "$MODIFIED" +%s)" -lt "$CUTOFF" ]; then
    aws s3 rm "s3://$BACKUP_BUCKET/$KEY" --endpoint-url "$R2_ENDPOINT" --only-show-errors
    echo "[backup] 보관기간 초과 삭제: $KEY"
  fi
done

# 성공 시각을 Prometheus 텍스트 포맷으로 남긴다 — Alloy의 textfile 컬렉터(PA-04)가 수집한다.
if [ -d "$METRIC_DIR" ]; then
  TMP="$METRIC_FILE.tmp"
  {
    echo "# HELP atcrew_backup_last_success_timestamp 마지막 백업 성공 시각(Unix epoch)"
    echo "# TYPE atcrew_backup_last_success_timestamp gauge"
    echo "atcrew_backup_last_success_timestamp $(date -u +%s)"
    echo "# HELP atcrew_backup_size_bytes 마지막 백업 파일 크기"
    echo "# TYPE atcrew_backup_size_bytes gauge"
    echo "atcrew_backup_size_bytes $SIZE"
  } > "$TMP"
  mv "$TMP" "$METRIC_FILE"   # 원자적 교체 — 수집 중 반쪽짜리 파일을 읽지 않게
else
  echo "[backup] 경고: $METRIC_DIR 가 없어 지표를 남기지 못했다(Alloy 설치 전이면 정상)"
fi

echo "[backup] 완료"
