#!/usr/bin/env bash
# R2 이미지 백업 — 원본 버킷(at-crew-storage)의 객체를 백업 버킷으로 복사한다.
# 설계: docs/design/observability-design.md §10
#
# 배경: R2에는 오브젝트 버저닝이 없다(S3 API 호환 목록에서 PutBucketVersioning이 미구현).
# 그래서 객체가 한 번 지워지면 되돌릴 수단이 전혀 없는데, 앱은 작품 삭제 시 실제로 R2 객체를
# 지운다(media/internal/infra/storage/R2StorageAdapter.java의 deleteObjects). 잘못된 key가
# 넘어가는 버그 하나로 원본 이미지가 영구히 사라질 수 있어서, 별도 버킷에 사본을 둔다.
#
# 이 백업이 막는 것: 앱 버그·운영 실수로 인한 삭제.
# 막지 못하는 것: Cloudflare 계정 자체의 침해(같은 계정 안의 다른 버킷이므로).
#
# DB 백업(backup.sh)과 동작이 다르다:
#   - DB는 매일 새 이름으로 전체를 다시 뜬다 → 30일치 세대가 쌓인다.
#   - 이미지는 원본과 같은 key로 1:1 미러다 → 사본은 하나뿐이고, 이미 있는 객체는 건너뛴다.
#     key가 raw/<uuidv7>.<ext> 형태로 재사용되지 않고 내용도 바뀌지 않아 덮어쓸 일이 없다.
#
# 설치: deploy/bootstrap.sh가 타이머까지 등록한다.
# 실행 이력: journalctl -u atcrew-image-backup.service --since "-7 days"
set -euo pipefail

DEPLOY_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ENV_FILE="$DEPLOY_DIR/.env"
METRIC_DIR="${METRIC_DIR:-/var/lib/node_exporter/textfile_collector}"
METRIC_FILE="$METRIC_DIR/image-backup.prom"

[ -f "$ENV_FILE" ] || { echo "[image-backup] .env를 찾을 수 없다: $ENV_FILE" >&2; exit 1; }

# backup.sh와 같은 이유로 source 하지 않는다 — 셸이 값을 명령으로 해석해서 깨진다.
read_env() { sed -n "s/^$1=//p" "$ENV_FILE" | tail -1 | sed -e 's/^"//' -e 's/"$//'; }

R2_ENDPOINT="$(read_env R2_ENDPOINT)"
SOURCE_BUCKET="$(read_env R2_BUCKET)"
BACKUP_BUCKET="$(read_env R2_IMAGE_BACKUP_BUCKET)"
# 원본 버킷과 백업 버킷 양쪽에 권한이 있는 전용 키다. 서버측 복사(CopyObject)를 쓰려면 하나의
# 자격증명이 두 버킷을 모두 봐야 해서, DB 백업용 키(백업 버킷 전용)를 재사용할 수 없다.
ACCESS_KEY="$(read_env R2_IMAGE_BACKUP_ACCESS_KEY)"
SECRET_KEY="$(read_env R2_IMAGE_BACKUP_SECRET_KEY)"

: "${R2_ENDPOINT:?[image-backup] R2_ENDPOINT 없음}"
: "${SOURCE_BUCKET:?[image-backup] R2_BUCKET 없음 — 원본 이미지 버킷 이름}"
: "${BACKUP_BUCKET:?[image-backup] R2_IMAGE_BACKUP_BUCKET 없음 — .env에 실제 버킷 이름을 채울 것}"
: "${ACCESS_KEY:?[image-backup] R2_IMAGE_BACKUP_ACCESS_KEY 없음 — 두 버킷 권한이 있는 전용 키를 발급할 것}"
: "${SECRET_KEY:?[image-backup] R2_IMAGE_BACKUP_SECRET_KEY 없음 — 두 버킷 권한이 있는 전용 키를 발급할 것}"

[ "$SOURCE_BUCKET" != "$BACKUP_BUCKET" ] \
  || { echo "[image-backup] 원본과 백업 버킷이 같다($SOURCE_BUCKET) — 설정을 확인할 것" >&2; exit 1; }

export AWS_ACCESS_KEY_ID="$ACCESS_KEY"
export AWS_SECRET_ACCESS_KEY="$SECRET_KEY"
export AWS_DEFAULT_REGION=auto
export AWS_REQUEST_CHECKSUM_CALCULATION=when_required
export AWS_RESPONSE_CHECKSUM_VALIDATION=when_required

WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

echo "[image-backup] 동기화 시작: $SOURCE_BUCKET -> $BACKUP_BUCKET"

# --delete를 절대 붙이지 않는다. 붙이면 원본에서 지워진 객체를 백업에서도 지워서, 이 백업이
# 막으려는 사고(잘못된 삭제)가 그대로 백업까지 전파된다. 백업이 원본을 그대로 따라가면
# 백업이 아니다.
#
# 두 경로가 모두 s3://라 aws CLI가 CopyObject로 처리한다 — 데이터가 이 서버를 거치지 않아
# 대역폭도 디스크도 쓰지 않는다. R2는 CopyObject를 지원하고 egress가 무료다.
#
# 백업 버킷에는 수명주기 만료 규칙을 걸지 않는다. 이미지는 한 번 복사되면 다시 복사되지 않아서,
# "생성 후 N일" 기준으로 만료시키면 원본이 멀쩡한 이미지의 백업까지 사라진다. DB 덤프와 정반대다.
#
# R2는 객체 태깅을 구현하지 않는다. aws CLI의 --copy-props는 세 모드 모두 태깅을 건드려서 그대로는 못 쓴다.
#   - none / metadata-directive: 모든 CopyObject에 `x-amz-tagging-directive: REPLACE`를 붙인다 →
#     R2가 `NotImplemented`로 거부해 한 건도 복사되지 않는다(2026-09-17 실제 장애 — 새 객체가 없던
#     날은 복사 0건으로 "성공"해서 드러나지 않았다).
#   - default: 단일 CopyObject는 태깅 헤더 없이 보내고 메타데이터(Content-Type 등)도 서버가 그대로
#     복사한다. 그러나 멀티파트 복사(기본 임계값 8MB 이상)에서는 태그를 옮기려 GetObjectTagging을
#     불러 같은 이유로 실패한다(2026-09-11 실측).
# 그래서 default를 쓰되 멀티파트 임계값을 5GB(단일 CopyObject 상한)로 올려 모든 복사를 단일
# CopyObject로 만든다. 이 설정은 ~/.aws/config를 건드리지 않도록 이번 실행 전용 파일로 준다.
cat > "$WORK/aws-config" <<'CFG'
[default]
s3 =
  multipart_threshold = 5GB
CFG
export AWS_CONFIG_FILE="$WORK/aws-config"

# 대조 기준 시각. sync가 원본 목록을 다 받기 전에 올라온 객체는 이번 복사 대상에서 빠질 수 있으므로,
# 시작 시각보다 5분 이른 시각을 기준으로 삼는다(서버와 R2의 시계 오차도 여기서 흡수한다).
CUTOFF="$(date -u -d "@$(( $(date -u +%s) - 300 ))" +%Y-%m-%dT%H:%M:%S)"

aws s3 sync "s3://$SOURCE_BUCKET" "s3://$BACKUP_BUCKET" \
  --endpoint-url "$R2_ENDPOINT" --no-progress > "$WORK/sync.log"

COPIED=$(grep -c '^copy:' "$WORK/sync.log" || true)
echo "[image-backup] 새로 복사한 객체: ${COPIED}개"

# 대조 — sync가 exit 0으로 끝났다고 원본이 다 복사됐다는 보장은 없다(이슈 #268).
# 원본·백업 개수만 비교하면 안 된다. 백업은 --delete 없이 쌓이기만 해서 정상일 때도 백업이 더 많다.
# 원본에서 지워진 옛 객체가 백업에 남아 있으면 새 객체가 빠져도 개수로는 가려진다.
# 그래서 key 단위로 "기준 시각 이전에 생긴 원본 객체 중 백업에 없는 것"을 센다.
# 기준 시각 이후 객체는 다음 실행 때 센다. 지표가 다음 실행까지 24시간 유지되므로, 이런 객체를
# 세면 하루짜리 거짓 알람이 된다.
list_objects() {  # key<TAB>LastModified, 빈 버킷이면 아무것도 출력하지 않는다
  aws s3api list-objects-v2 --bucket "$1" --endpoint-url "$R2_ENDPOINT" \
    --query 'Contents[].[Key,LastModified]' --output text | { grep -v '^None$' || true; }
}
list_objects "$SOURCE_BUCKET" > "$WORK/source.tsv"
list_objects "$BACKUP_BUCKET" > "$WORK/backup.tsv"

SOURCE_COUNT=$(wc -l < "$WORK/source.tsv" | tr -d ' ')
BACKUP_COUNT=$(wc -l < "$WORK/backup.tsv" | tr -d ' ')
# LastModified의 앞 19자(YYYY-MM-DDTHH:MM:SS)는 UTC 고정 형식이라 문자열 비교가 곧 시각 비교다.
awk -F'\t' -v c="$CUTOFF" 'substr($2, 1, 19) < c { print $1 }' "$WORK/source.tsv" | LC_ALL=C sort > "$WORK/source.keys"
cut -f1 "$WORK/backup.tsv" | LC_ALL=C sort > "$WORK/backup.keys"
LC_ALL=C comm -23 "$WORK/source.keys" "$WORK/backup.keys" > "$WORK/missing.keys"
MISSING=$(wc -l < "$WORK/missing.keys" | tr -d ' ')

echo "[image-backup] 원본 ${SOURCE_COUNT}개, 백업 ${BACKUP_COUNT}개, 백업에 없는 원본(${CUTOFF}Z 이전) ${MISSING}개"
if [ "$MISSING" -gt 0 ]; then
  echo "[image-backup] 백업에 없는 key(최대 50개):" >&2
  head -50 "$WORK/missing.keys" | sed 's/^/  /' >&2
fi

# 지표를 남긴다 — 성공 시각은 DB 백업과 같은 26시간 규칙으로, 누락 수는 0보다 크면 바로 알람에
# 태운다. 지표가 없으면 동기화가 조용히 멈추거나 빠뜨려도 아무도 모른다.
if [ -d "$METRIC_DIR" ]; then
  TMP="$METRIC_FILE.tmp"
  {
    echo "# HELP atcrew_image_backup_last_success_timestamp 마지막 이미지 백업 성공 시각(Unix epoch)"
    echo "# TYPE atcrew_image_backup_last_success_timestamp gauge"
    echo "atcrew_image_backup_last_success_timestamp $(date -u +%s)"
    echo "# HELP atcrew_image_backup_copied_objects 마지막 실행에서 새로 복사한 객체 수"
    echo "# TYPE atcrew_image_backup_copied_objects gauge"
    echo "atcrew_image_backup_copied_objects $COPIED"
    echo "# HELP atcrew_image_backup_source_objects 원본 버킷 객체 수"
    echo "# TYPE atcrew_image_backup_source_objects gauge"
    echo "atcrew_image_backup_source_objects $SOURCE_COUNT"
    echo "# HELP atcrew_image_backup_backup_objects 백업 버킷 객체 수(원본에서 지워진 객체도 남아 있어 원본보다 많은 게 정상)"
    echo "# TYPE atcrew_image_backup_backup_objects gauge"
    echo "atcrew_image_backup_backup_objects $BACKUP_COUNT"
    echo "# HELP atcrew_image_backup_missing_objects sync 시작 5분 전까지 생긴 원본 객체 중 백업에 없는 수"
    echo "# TYPE atcrew_image_backup_missing_objects gauge"
    echo "atcrew_image_backup_missing_objects $MISSING"
  } > "$TMP"
  mv "$TMP" "$METRIC_FILE"   # 원자적 교체 — 수집 중 반쪽짜리 파일을 읽지 않게
else
  echo "[image-backup] 경고: $METRIC_DIR 가 없어 지표를 남기지 못했다(Alloy 설치 전이면 정상)"
fi

# 누락이 있으면 실패로 끝낸다 — systemctl status·journalctl에서도 실패로 보이게. 지표는 위에서 이미
# 남겼으므로 알람은 그쪽으로 울린다.
if [ "$MISSING" -gt 0 ]; then
  echo "[image-backup] 실패: 백업에 없는 원본 ${MISSING}개" >&2
  exit 1
fi

echo "[image-backup] 완료"
