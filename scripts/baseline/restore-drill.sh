#!/usr/bin/env bash
# 복구 훈련 — R2의 최신 덤프를 로컬 MariaDB 컨테이너에 복원하고 단계별 소요시간을 잰다.
# 설계: docs/operations/baseline/README.md
#
# 왜 이걸 재나: 백업은 2026-08-24부터 매일 돌지만 **복구를 한 번도 실행해 본 적이 없다.**
# 복원되지 않는 백업은 백업이 아니므로, 시간을 재는 것보다 절차가 실제로 동작하는지 확인하는 게 목적이다.
#
# 실행:
#   ./scripts/baseline/restore-drill.sh --env-file <R2 자격증명이 있는 env 파일> \
#       [--age-key <개인키 파일>] [--expect <tsv>] [--keep]
#
# --env-file 이 읽는 키: R2_ENDPOINT, R2_BACKUP_BUCKET, R2_BACKUP_ACCESS_KEY, R2_BACKUP_SECRET_KEY
#   (값은 EC2 #1의 deploy/.env에만 있다. 이 저장소에 커밋하지 않는다)
# --age-key 는 덤프 복호화용 age 개인키 파일(AGE-SECRET-KEY-1... 한 줄). 2026-09-10부터 덤프가
#   암호화돼 올라가므로 `.age`로 끝나는 덤프를 복원하려면 반드시 필요하다. 개인키가 어디 있는지는
#   docs/operations/incident-runbook.md "DB 복원"에 적혀 있다. 이 저장소에 커밋하지 않는다.
#   **복호화가 되는지 확인하는 것이 이 훈련의 절반이다** — 암호화만 되고 복호화가 안 되는 상태는
#   백업 감시 알람에 정상으로 잡힌다.
# --expect 는 `테이블명<TAB>행수` 형식의 TSV. 원본과 복원본의 행 수를 대조한다.
#
# 실서버에서 실행하지 않는다. 로컬 도커에만 붙는다.
# 여기서 나오는 RTO는 "데이터 복원에 걸리는 시간"이고, 실제 장애 시의 RTO에는
# 인스턴스 재생성 시간이 더해진다.

set -euo pipefail

ENV_FILE=""; EXPECT=""; KEEP=0; AGE_KEY=""
while [ $# -gt 0 ]; do
  case "$1" in
    --env-file) ENV_FILE="$2"; shift 2 ;;
    --age-key)  AGE_KEY="$2";  shift 2 ;;
    --expect)   EXPECT="$2";   shift 2 ;;
    --keep)     KEEP=1;        shift ;;
    *) echo "알 수 없는 인자: $1" >&2; exit 2 ;;
  esac
done
[ -n "$ENV_FILE" ] && [ -f "$ENV_FILE" ] || { echo "--env-file 이 필요하다" >&2; exit 2; }

# backup.sh와 같은 이유로 source 하지 않는다 — 값에 든 <> 같은 문자를 셸이 리다이렉트로 해석한다.
read_env() { sed -n "s/^$1=//p" "$ENV_FILE" | tail -1 | sed -e 's/^"//' -e 's/"$//'; }
export AWS_ACCESS_KEY_ID="$(read_env R2_BACKUP_ACCESS_KEY)"
export AWS_SECRET_ACCESS_KEY="$(read_env R2_BACKUP_SECRET_KEY)"
export AWS_DEFAULT_REGION=auto
export AWS_REQUEST_CHECKSUM_CALCULATION=when_required
export AWS_RESPONSE_CHECKSUM_VALIDATION=when_required
R2_ENDPOINT="$(read_env R2_ENDPOINT)"
BUCKET="$(read_env R2_BACKUP_BUCKET)"
: "${AWS_ACCESS_KEY_ID:?R2_BACKUP_ACCESS_KEY 없음}" "${R2_ENDPOINT:?R2_ENDPOINT 없음}" "${BUCKET:?R2_BACKUP_BUCKET 없음}"

CONTAINER="atcrew-restore-drill"
PORT="${PORT:-13306}"
PW="drill"
WORK="$(mktemp -d)"
cleanup() {
  [ "$KEEP" = 1 ] || docker rm -f "$CONTAINER" >/dev/null 2>&1 || true
  rm -rf "$WORK"
}
trap cleanup EXIT

T0=$(date +%s); LAST=$T0
declare -a STAGES
mark() { local now; now=$(date +%s); STAGES+=("$1	$((now-LAST))"); LAST=$now; }

echo "## 복구 훈련 — $(date -u +%Y-%m-%dT%H:%M:%SZ)"
echo

# ── 1. 최신 덤프 찾기 ─────────────────────────────────────────
KEY="$(aws s3api list-objects-v2 --bucket "$BUCKET" --prefix db-backups/ --endpoint-url "$R2_ENDPOINT" \
        --query 'sort_by(Contents,&LastModified)[-1].Key' --output text)"
[ -n "$KEY" ] && [ "$KEY" != "None" ] || { echo "덤프를 찾지 못했다" >&2; exit 1; }
mark "R2 최신 덤프 조회"

# ── 2. 내려받기 ───────────────────────────────────────────────
# 파일명은 R2의 key를 그대로 따라간다 — `.age`인지 아닌지로 복호화 여부가 갈린다.
LOCAL="$WORK/$(basename "$KEY")"
aws s3 cp "s3://$BUCKET/$KEY" "$LOCAL" --endpoint-url "$R2_ENDPOINT" --only-show-errors
DUMP_BYTES=$(wc -c < "$LOCAL" | tr -d " ")
mark "덤프 내려받기"

# ── 3. 빈 MariaDB 기동 ────────────────────────────────────────
docker rm -f "$CONTAINER" >/dev/null 2>&1 || true
docker run -d --name "$CONTAINER" \
  -e MARIADB_ROOT_PASSWORD="$PW" -e MARIADB_DATABASE=atcrew -e TZ=UTC \
  -p "127.0.0.1:$PORT:3306" \
  mariadb:11.4 --character-set-server=utf8mb4 --collation-server=utf8mb4_unicode_ci >/dev/null
# healthcheck.sh는 mariadb 이미지가 제공한다. deploy/docker-compose.app.yml과 같은 조건을 쓴다.
until docker exec "$CONTAINER" healthcheck.sh --connect --innodb_initialized >/dev/null 2>&1; do sleep 1; done
mark "MariaDB 컨테이너 기동~접속 가능"

# ── 4. 복원 ───────────────────────────────────────────────────
# 2026-09-10 이전 덤프는 평문이라 그대로 풀린다. 보관기간(30일)이 지나 전부 `.age`로 바뀌면
# 이 분기는 지워도 된다.
if [ "${LOCAL%.age}" != "$LOCAL" ]; then
  [ -n "$AGE_KEY" ] && [ -f "$AGE_KEY" ] \
    || { echo "암호화된 덤프($KEY)를 받았는데 --age-key 가 없다" >&2; exit 1; }
  command -v age >/dev/null || { echo "age가 설치돼 있지 않다" >&2; exit 1; }
  ENCRYPTED="예"
  age -d -i "$AGE_KEY" "$LOCAL" | gunzip -c \
    | docker exec -i -e MYSQL_PWD="$PW" "$CONTAINER" mariadb -u root atcrew
else
  ENCRYPTED="아니오(암호화 도입 이전 덤프)"
  gunzip -c "$LOCAL" | docker exec -i -e MYSQL_PWD="$PW" "$CONTAINER" mariadb -u root atcrew
fi
mark "덤프 복호화·복원"

sqlq() { docker exec -e MYSQL_PWD="$PW" "$CONTAINER" mariadb -u root -N -B -e "$1" atcrew 2>/dev/null; }

# ── 5. 정합성 검증 ────────────────────────────────────────────
# TABLE_ROWS는 InnoDB에서 추정치라 검증에 쓸 수 없다 — 테이블마다 실제 COUNT(*)를 센다.
TABLES="$(sqlq "SELECT TABLE_NAME FROM information_schema.TABLES WHERE TABLE_SCHEMA='atcrew' AND TABLE_TYPE='BASE TABLE'")"
: > "$WORK/actual.tsv"
while read -r t; do
  [ -n "$t" ] || continue
  printf '%s\t%s\n' "$t" "$(sqlq "SELECT COUNT(*) FROM \`$t\`")" >> "$WORK/actual.tsv"
done <<< "$TABLES"
mark "복원본 행 수 집계"

TOTAL=$(date +%s); TOTAL=$((TOTAL-T0))

echo "| 단계 | 소요(초) |"
echo "|---|---|"
for s in "${STAGES[@]}"; do printf '| %s | %s |\n' "${s%%	*}" "${s##*	}"; done
printf '| **합계 (데이터 복원 RTO)** | **%s** |\n' "$TOTAL"
echo
echo "| 항목 | 값 |"
echo "|---|---|"
echo "| 덤프 객체 | \`$KEY\` |"
echo "| 덤프 크기 | $DUMP_BYTES B |"
echo "| 암호화 | $ENCRYPTED |"
echo "| 복원된 테이블 수 | $(wc -l < "$WORK/actual.tsv" | tr -d ' ') |"
echo "| 복원된 총 행 수 | $(awk -F'\t' '{s+=$2} END{print s+0}' "$WORK/actual.tsv") |"
echo "| Flyway 마이그레이션 이력 | $(sqlq 'SELECT COUNT(*) FROM flyway_schema_history') 건 (마지막 적용 버전 $(sqlq 'SELECT version FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 1')) |"
echo

if [ -n "$EXPECT" ] && [ -f "$EXPECT" ]; then
  echo "### 원본 대조"
  echo
  echo "| 테이블 | 원본 | 복원본 | 판정 |"
  echo "|---|---|---|---|"
  DIFF=0
  # 원본에만 있고 복원본에 없는 테이블도 잡아야 하므로 원본 목록을 기준으로 돈다.
  while IFS=$'\t' read -r t n; do
    [ -n "$t" ] || continue
    a="$(awk -F'\t' -v k="$t" '$1==k{print $2}' "$WORK/actual.tsv")"
    if [ -z "$a" ]; then a="없음"; v="**불일치 — 테이블 누락**"; DIFF=1
    elif [ "$a" = "$n" ]; then v="일치"
    else v="**불일치**"; DIFF=1; fi
    # 0건끼리 일치하는 건 표를 채우기만 하므로 접는다 — 차이와 데이터 있는 것만 남긴다.
    if [ "$n" != "0" ] || [ "$v" != "일치" ]; then printf '| `%s` | %s | %s | %s |\n' "$t" "$n" "$a" "$v"; fi
  done < "$EXPECT"
  echo
  ONLY_ZERO=$(awk -F'\t' '$2==0' "$EXPECT" | wc -l | tr -d ' ')
  echo "> 원본에서 0건인 테이블 ${ONLY_ZERO}개는 모두 일치해 표에서 생략했다."
  echo
  [ "$DIFF" = 0 ] && echo "**정합성: 통과** — 모든 테이블의 행 수가 원본과 같다." \
                  || echo "**정합성: 실패** — 위 불일치 항목을 확인할 것."
fi

echo
echo "> 이 RTO는 **데이터 복원 시간**이다. 실제 장애 시에는 인스턴스·보안그룹·nginx·"
echo "> 애플리케이션 재구성 시간이 앞에 붙는다. 앱 기동은 별도로 측정된 값을 쓴다"
echo "> (기준선 문서의 \`기동~ready\` 항목)."
