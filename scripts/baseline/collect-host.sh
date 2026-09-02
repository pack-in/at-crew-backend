#!/usr/bin/env bash
# 기준선 수집 — EC2 #1(앱 서버)에서 실행해 A(구성)·B(성능)·C(데이터) 군을 마크다운으로 stdout에 낸다.
# 설계: docs/operations/baseline/README.md
#
# 실행(로컬에서, 서버에 파일을 남기지 않는다):
#   ssh -i ~/.ssh/<키페어>.pem ec2-user@<EC2 #1> 'bash -s' < scripts/baseline/collect-host.sh > out.md
#
# 원칙
# - 읽기 전용이다. 컨테이너·설정·데이터를 건드리는 명령을 넣지 않는다.
# - 출력은 공개 저장소의 문서에 그대로 붙는다. 인스턴스 ID·탄력적 IP·프라이빗 IP·보안 그룹 ID는
#   절대 출력하지 않는다(deploy/README.md의 원칙). Elasticsearch 주소도 호스트를 찍지 않고
#   "분리/통합"이라는 배치 판정만 낸다.
# - 개별 항목이 실패해도 전체를 멈추지 않는다. 실패한 칸은 `측정 실패`로 남겨야 나중에 비교할 때
#   "안 쟀다"와 "0이었다"를 구분할 수 있다.

set -uo pipefail
export LC_ALL=C S_TIME_FORMAT=ISO

ENV_FILE="${ENV_FILE:-$HOME/at-crew-backend/deploy/.env}"
COMPOSE_FILE="${COMPOSE_FILE:-$HOME/at-crew-backend/deploy/docker-compose.app.yml}"
ACTUATOR="${ACTUATOR:-http://127.0.0.1:8081/actuator}"
PUBLIC_URL="${PUBLIC_URL:-https://api.at-crew.com}"
DB_NAME="${DB_NAME:-atcrew}"

# 값이 없거나 명령이 실패하면 빈 문자열 대신 눈에 띄는 표식을 남긴다.
na() { local v; v="$(cat)"; [ -n "$v" ] && printf '%s' "$v" || printf '측정 실패'; }
read_env() { [ -f "$ENV_FILE" ] && sed -n "s/^$1=//p" "$ENV_FILE" | tail -1 | sed -e 's/^"//' -e 's/"$//'; }

# 정렬된 수열에서 백분위 값을 고른다. 입력은 stdin의 한 줄 한 값.
pctl() { sort -n | awk -v p="$1" 'NR{a[NR]=$1} END{ if(!NR){print "측정 실패"; exit} i=int(p*NR+0.999); if(i<1)i=1; printf "%.2f", a[i] }'; }
maxv() { sort -n | tail -1 | awk 'NF{printf "%.2f", $1} !NF{print "측정 실패"}'; }
mean() { awk '{s+=$1; n++} END{ if(!n){print "측정 실패"; exit} printf "%.2f", s/n }'; }

# sar 데이터 행만 남긴다(헤더·Average·재시작 표시 제거). S_TIME_FORMAT=ISO라 첫 필드가 HH:MM:SS 한 덩어리다.
sarrows() { awk '$1 ~ /^[0-9][0-9]:[0-9][0-9]:[0-9][0-9]$/'; }

TOKEN="$(curl -s -m 3 -X PUT http://169.254.169.254/latest/api/token -H 'X-aws-ec2-metadata-token-ttl-seconds: 120')"
imds() { curl -s -m 3 -H "X-aws-ec2-metadata-token: $TOKEN" "http://169.254.169.254/latest/meta-data/$1"; }

MDB="$(docker ps --filter 'label=com.docker.compose.service=mariadb' --format '{{.Names}}' | head -1)"
MDB_PW="$(read_env MARIADB_ROOT_PASSWORD)"
# 비밀번호를 명령줄에 두면 컨테이너 안 프로세스 목록에 남는다 — MYSQL_PWD로 넘긴다.
sqlq() {
  [ -n "$MDB" ] || return 1
  docker exec -e MYSQL_PWD="$MDB_PW" "$MDB" mariadb -uroot -N -B -e "$1" 2>/dev/null
}

ES_URI="$(read_env ELASTICSEARCH_URIS)"
esq() { [ -n "$ES_URI" ] && curl -s -m 5 "${ES_URI%/}/$1"; }

PROM="$(curl -s -m 5 "$ACTUATOR/prometheus")"
# 프로메테우스 노출에서 라벨이 붙은 한 줄의 값만 꺼낸다.
prom() { printf '%s\n' "$PROM" | grep -E "$1" | awk '{print $NF}' | head -1; }

# curl로 같은 요청을 n번 보내고 total time(ms)의 중앙값을 낸다.
timeit() {
  local n="$1"; shift
  local i
  for ((i=0;i<n;i++)); do
    curl -s -o /dev/null -m 10 -w '%{time_total}\n' "$@" 2>/dev/null
  done | awk '{printf "%.1f\n", $1*1000}' | pctl 0.5
}

echo "<!-- scripts/baseline/collect-host.sh 출력. 수집 시각(UTC): $(date -u +%Y-%m-%dT%H:%M:%SZ) -->"
echo

# ─────────────────────────────────────────────────────────────
echo "## A. 구성 기준선"
echo
echo "| 항목 | 값 |"
echo "|---|---|"
echo "| 인스턴스 타입 | $(imds instance-type | na) |"
echo "| vCPU | $(nproc | na) |"
echo "| 메모리 총량 | $(free -m | awk '/^Mem:/{print $2" MB"}' | na) |"
echo "| 가용 영역 | $(imds placement/availability-zone | na) |"
echo "| 커널 / OS | $(uname -r) / $(. /etc/os-release 2>/dev/null; echo "${PRETTY_NAME:-불명}") |"
echo "| 루트 디스크 | $(lsblk -ndo SIZE,TYPE 2>/dev/null | awk '$2=="disk"{print $1}' | head -1 | na) ($(df -h / | awk 'NR==2{print $3" 사용 / "$2" 중 "$5}' | na)) |"
echo "| 스왑 | $(free -m | awk '/^Swap:/{print ($2==0 ? "없음" : $2" MB")}' | na) |"
echo "| Docker | $(docker --version 2>/dev/null | awk '{print $3}' | tr -d , | na) / compose $(docker-compose version --short 2>/dev/null | na) |"
# Elasticsearch가 앱 compose 파일 안에 있으면 통합, 밖이면 별도 인스턴스에 있는 것이다.
if grep -qE '^\s{2}elasticsearch:' "$COMPOSE_FILE" 2>/dev/null; then
  ES_PLACEMENT='통합 — EC2 #1 compose 내 `elasticsearch` 서비스'
else
  ES_PLACEMENT='분리 — EC2 #2의 별도 컨테이너(앱 compose에 서비스 없음)'
fi
echo "| **Elasticsearch 배치** | $ES_PLACEMENT |"
echo
echo "### 컨테이너"
echo
echo "| 컨테이너 | 이미지 | 상태 | 메모리 상한 |"
echo "|---|---|---|---|"
for c in $(docker ps --format '{{.Names}}' | sort); do
  img="$(docker inspect -f '{{.Config.Image}}' "$c" 2>/dev/null)"
  st="$(docker inspect -f '{{.State.Status}} ({{.State.StartedAt}})' "$c" 2>/dev/null)"
  lim="$(docker inspect -f '{{.HostConfig.Memory}}' "$c" 2>/dev/null)"
  [ "${lim:-0}" = "0" ] && lim='없음(호스트 전체 공유)' || lim="$((lim/1024/1024)) MB"
  echo "| \`$c\` | \`$img\` | $st | $lim |"
done
echo
echo "### 메모리 관련 설정"
echo
echo "| 항목 | 값 |"
echo "|---|---|"
# 힙 최대치는 영역별로 -1(무제한 표기)이 섞여 나오므로 양수만 더한다.
HEAP="$(printf '%s\n' "$PROM" | awk '/^jvm_memory_max_bytes\{area="heap"/{v=$NF+0; if(v>0) s+=v} END{ if(s>0) printf "%.0f MB", s/1024/1024 }')"
echo "| 앱 JVM 힙 최대 | $(printf '%s' "$HEAP" | na) |"
APP_C="$(docker ps --filter 'label=com.docker.compose.service=app' --format '{{.Names}}' | head -1)"
JAVA_FLAGS="$( { docker inspect -f '{{range .Config.Env}}{{println .}}{{end}}' "$APP_C" 2>/dev/null | grep -E '^JAVA_(TOOL_)?OPTIONS=' | cut -d= -f2-; docker inspect -f '{{join .Config.Cmd " "}} {{join .Config.Entrypoint " "}}' "$APP_C" 2>/dev/null | tr ' ' '\n' | grep -E '^-X|^-XX'; } | tr '\n' ' ')"
echo "| 앱 JVM 힙 플래그 | $( [ -n "${JAVA_FLAGS// /}" ] && printf '%s' "$JAVA_FLAGS" || printf '명시 설정 없음 — 기본값(MaxRAMPercentage 25%%)이 적용된다' ) |"
echo "| MariaDB innodb_buffer_pool_size | $(sqlq 'SELECT ROUND(@@innodb_buffer_pool_size/1024/1024)' 2>/dev/null | awk 'NF{print $1" MB"}' | na) |"
echo "| Elasticsearch 힙 최대 | $(esq '_nodes/stats/jvm' | jq -r '[.nodes[].jvm.mem.heap_max_in_bytes][0] // empty' 2>/dev/null | awk 'NF{printf "%d MB", $1/1024/1024}' | na) |"
echo

# ─────────────────────────────────────────────────────────────
echo "## B. 성능 기준선 (정상 상태)"
echo
YDAY_SA="/var/log/sa/sa$(date -u -d yesterday +%d 2>/dev/null)"
if [ -r "$YDAY_SA" ]; then
  echo "24시간 구간은 어제(UTC $(date -u -d yesterday +%Y-%m-%d)) sysstat 기록 전체다. 평균과 p95를 함께 낸다."
else
  echo "> 어제자 sysstat 파일(\`$YDAY_SA\`)을 읽을 수 없어 24시간 구간을 비운다."
fi
echo
echo "| 지표 | 24h 평균 | 24h p95 | 24h 최대 | 현재 |"
echo "|---|---|---|---|---|"
if [ -r "$YDAY_SA" ]; then
  CPU_S="$(sar -u -f "$YDAY_SA" 2>/dev/null | sarrows | awk '{print 100-$NF}')"
  MEM_S="$(sar -r -f "$YDAY_SA" 2>/dev/null | sarrows | awk '{print $5}')"
  LOAD_S="$(sar -q -f "$YDAY_SA" 2>/dev/null | sarrows | awk '{print $4}')"
  IO_S="$(sar -b -f "$YDAY_SA" 2>/dev/null | sarrows | awk '{print $2}')"
else
  CPU_S=""; MEM_S=""; LOAD_S=""; IO_S=""
fi
echo "| CPU 사용률 (%) | $(printf '%s\n' "$CPU_S" | mean) | $(printf '%s\n' "$CPU_S" | pctl 0.95) | $(printf '%s\n' "$CPU_S" | maxv) | $(sar -u 1 3 2>/dev/null | awk '/^Average/{printf "%.2f", 100-$NF}' | na) |"
echo "| 메모리 사용률 (%) | $(printf '%s\n' "$MEM_S" | mean) | $(printf '%s\n' "$MEM_S" | pctl 0.95) | $(printf '%s\n' "$MEM_S" | maxv) | $(free | awk '/^Mem:/{printf "%.2f", $3/$2*100}') |"
echo "| 로드 애버리지 (1m) | $(printf '%s\n' "$LOAD_S" | mean) | $(printf '%s\n' "$LOAD_S" | pctl 0.95) | $(printf '%s\n' "$LOAD_S" | maxv) | $(awk '{print $1}' /proc/loadavg) |"
echo "| 디스크 IO (tps) | $(printf '%s\n' "$IO_S" | mean) | $(printf '%s\n' "$IO_S" | pctl 0.95) | $(printf '%s\n' "$IO_S" | maxv) | $(iostat -d 1 2 2>/dev/null | awk '/^nvme|^xvd/{v=$2} END{printf "%.2f", v}') |"
echo "| 스왑 사용 (MB) | - | - | - | $(free -m | awk '/^Swap:/{print $3}') |"
echo
echo "### 컨테이너별 메모리 실사용"
echo
echo "| 컨테이너 | 메모리 | 메모리 % | CPU % |"
echo "|---|---|---|---|"
docker stats --no-stream --format '| `{{.Name}}` | {{.MemUsage}} | {{.MemPerc}} | {{.CPUPerc}} |' 2>/dev/null | sort
echo
echo "### 애플리케이션"
echo
echo "| 항목 | 값 |"
echo "|---|---|"
echo "| 기동~ready 소요 | $(prom '^application_ready_time_seconds' | awk 'NF{printf "%.2f s", $1}' | na) |"
echo "| 기동~started 소요 | $(prom '^application_started_time_seconds' | awk 'NF{printf "%.2f s", $1}' | na) |"
echo "| 프로세스 가동 시간 | $(prom '^process_uptime_seconds' | awk 'NF{printf "%.1f h", $1/3600}' | na) |"
echo "| 누적 요청 수 | $(printf '%s\n' "$PROM" | awk '/^http_server_requests_seconds_count/{s+=$NF} END{if(s)printf "%d", s}' | na) |"
echo "| 누적 5xx 수 | $(printf '%s\n' "$PROM" | awk '/^http_server_requests_seconds_count.*outcome="SERVER_ERROR"/{s+=$NF} END{printf "%d", s+0}' | na) |"
echo
echo "### 커넥션 풀 (HikariCP)"
echo
echo "| 항목 | 값 |"
echo "|---|---|"
echo "| 풀 크기 (min / max) | $(prom '^hikaricp_connections_min' | awk 'NF{printf "%d", $1}' | na) / $(prom '^hikaricp_connections_max' | awk 'NF{printf "%d", $1}' | na) |"
echo "| 현재 active / idle / pending | $(prom '^hikaricp_connections_active' | awk '{printf "%d", $1}') / $(prom '^hikaricp_connections_idle' | awk '{printf "%d", $1}') / $(prom '^hikaricp_connections_pending' | awk '{printf "%d", $1}') |"
echo "| 커넥션 획득 대기 최대 | $(prom '^hikaricp_connections_acquire_seconds_max' | awk 'NF{printf "%.3f ms", $1*1000}' | na) |"
echo "| 커넥션 타임아웃 누적 | $(prom '^hikaricp_connections_timeout_total' | awk 'NF{printf "%d", $1}' | na) |"
echo "| 누적 대여 횟수 | $(prom '^hikaricp_connections_usage_seconds_count' | awk 'NF{printf "%d", $1}' | na) |"
echo
echo "> 최대 동시 사용 커넥션 수는 Micrometer가 노출하지 않는다 — 부하 테스트 중 \`hikaricp_connections_active\`를"
echo "> 주기적으로 긁어 최댓값을 따로 잡아야 한다."
echo
echo "### 엔드포인트별 누적 응답시간"
echo
echo "> Actuator가 내는 분위수는 p95·p99뿐이라 p50은 여기서 얻을 수 없다. 값이 0인 것은"
echo "> 분위수 창(기본 2분)에 트래픽이 없었다는 뜻이지 응답이 0초라는 뜻이 아니다."
echo
echo "| 엔드포인트 | 메서드 | 상태 | 호출 수 | 평균(ms) | p95(ms) | p99(ms) |"
echo "|---|---|---|---|---|---|---|"
printf '%s\n' "$PROM" | awk '
  match($0, /uri="[^"]*"/) { uri=substr($0,RSTART+5,RLENGTH-6) }
  match($0, /method="[^"]*"/) { m=substr($0,RSTART+8,RLENGTH-9) }
  match($0, /status="[^"]*"/) { st=substr($0,RSTART+8,RLENGTH-9) }
  /^http_server_requests_seconds_count/ { cnt[uri"|"m"|"st]=$NF }
  /^http_server_requests_seconds_sum/   { sum[uri"|"m"|"st]=$NF }
  /^http_server_requests_seconds\{.*quantile="0.95"/ { q95[uri"|"m"|"st]=$NF }
  /^http_server_requests_seconds\{.*quantile="0.99"/ { q99[uri"|"m"|"st]=$NF }
  END {
    for (k in cnt) {
      split(k, p, "|")
      avg = (cnt[k]>0 ? sum[k]/cnt[k]*1000 : 0)
      printf "| `%s` | %s | %s | %d | %.1f | %.1f | %.1f |\n", p[1], p[2], p[3], cnt[k], avg, q95[k]*1000, q99[k]*1000
    }
  }' | sort -t'|' -k5 -rn
echo
echo "### 엣지 구간 오버헤드"
echo
echo "같은 헬스체크 요청을 세 경로로 9회씩 보내 중앙값을 비교한다. 세 경로 모두 \`/actuator/health/liveness\`로 끝나 같은 일을 한다. 서버 안에서 재므로 클라이언트"
echo "네트워크는 빠지고, 세 값의 차이가 곧 nginx 구간과 Cloudflare 구간의 비용이다."
echo
echo "| 경로 | 중앙값(ms) |"
echo "|---|---|"
echo "| 앱 직접 (\`127.0.0.1:8081\`) | $(timeit 9 "$ACTUATOR/health/liveness") |"
echo "| nginx 경유 (\`127.0.0.1:80\`) | $(timeit 9 -H 'Host: api.at-crew.com' http://127.0.0.1/healthz) |"
echo "| Cloudflare 경유 (\`$PUBLIC_URL\`) | $(timeit 9 "$PUBLIC_URL/healthz") |"
echo

# ─────────────────────────────────────────────────────────────
echo "## C. 데이터 기준선"
echo
echo "| 항목 | 값 |"
echo "|---|---|"
echo "| DB 총 크기 | $(sqlq "SELECT ROUND(SUM(DATA_LENGTH+INDEX_LENGTH)/1024,1) FROM information_schema.TABLES WHERE TABLE_SCHEMA='$DB_NAME'" | awk 'NF{print $1" KB"}' | na) |"
echo "| 테이블 수 | $(sqlq "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA='$DB_NAME'" | na) |"
echo "| 총 행 수(추정) | $(sqlq "SELECT SUM(TABLE_ROWS) FROM information_schema.TABLES WHERE TABLE_SCHEMA='$DB_NAME'" | na) |"
echo "| slow_query_log | $(sqlq "SELECT @@slow_query_log" | awk 'NF{print ($1==1 ? "ON" : "OFF")}' | na) |"
echo "| long_query_time | $(sqlq 'SELECT @@long_query_time' | awk 'NF{print $1" s"}' | na) |"
echo "| 누적 슬로우 쿼리 | $(sqlq "SHOW GLOBAL STATUS LIKE 'Slow_queries'" | awk 'NF{print $2}' | na) |"
echo "| 최대 동시 커넥션(서버 가동 이래) | $(sqlq "SHOW GLOBAL STATUS LIKE 'Max_used_connections'" | awk 'NF{print $2}' | na) |"
echo "| 마지막 백업 성공 | $(awk '/^atcrew_backup_last_success_timestamp/{print strftime("%Y-%m-%dT%H:%M:%SZ", $2)}' /var/lib/node_exporter/textfile_collector/backup.prom 2>/dev/null | na) |"
echo "| 마지막 백업 크기 | $(awk '/^atcrew_backup_size_bytes/{printf "%.1f KB", $2/1024}' /var/lib/node_exporter/textfile_collector/backup.prom 2>/dev/null | na) |"
echo "| 백업 주기 | $(systemctl show atcrew-backup.timer -p TimersCalendar --value 2>/dev/null | na) |"
echo
echo "### 테이블별 크기 (상위 20, 행 수 있는 것 우선)"
echo
echo "| 테이블 | 행 수(추정) | 데이터 | 인덱스 |"
echo "|---|---|---|---|"
sqlq "SELECT TABLE_NAME, IFNULL(TABLE_ROWS,0), DATA_LENGTH, INDEX_LENGTH FROM information_schema.TABLES WHERE TABLE_SCHEMA='$DB_NAME' ORDER BY TABLE_ROWS DESC, DATA_LENGTH+INDEX_LENGTH DESC LIMIT 20" \
  | awk -F'\t' '{printf "| `%s` | %s | %.1f KB | %.1f KB |\n", $1, $2, $3/1024, $4/1024}'
echo
echo "### Elasticsearch 인덱스"
echo
echo "| 인덱스 | 문서 수 | 저장 크기 | 상태 |"
echo "|---|---|---|---|"
esq '_cat/indices?format=json&bytes=b' \
  | jq -r '.[] | select(.index | startswith(".") | not) | "| `\(.index)` | \(."docs.count") | \((."store.size"|tonumber)/1024|floor) KB | \(.health) |"' 2>/dev/null \
  || echo "| 측정 실패 | - | - | - |"
