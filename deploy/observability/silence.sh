#!/usr/bin/env bash
# Grafana 알람을 일정 시간 억제(silence)한다 — 사람이 손으로 인프라를 건드릴 때 쓴다.
# 설계: docs/design/observability-design.md §7.3
#
# 배경: 배포 중 억제는 .github/workflows/deploy.yml이 이미 한다. 그런데 그 경로는 CI 전용이라
# GRAFANA_API_KEY가 GitHub Secrets에만 있고, 운영자가 로컬에서 terraform apply 같은 걸 돌릴 때는
# 알람을 끌 수단이 없었다. 2026-09-11 NAT 탄력적 IP 재할당 때 실제로 못 걸고 진행했다.
# 이 스크립트가 그 공백을 메운다 — deploy.yml과 같은 API를 호출한다.
#
# 사용법:
#   deploy/observability/silence.sh 15m "NAT 탄력적 IP 재할당"   # 생성. ID를 출력한다
#   deploy/observability/silence.sh --end <silence ID>            # 조기 해제
#
# 기간은 s/m/h만 받는다(예: 90s, 15m, 2h). 끝나면 자동으로 풀리므로 --end를 잊어도 영구히
# 눈이 멀지는 않는다 — 다만 작업이 끝났으면 바로 푸는 게 맞다.
#
# 자격증명은 아래 순서로 찾는다.
#   1) ~/.config/at-crew/observability.env (있으면 source). 형식:
#        GRAFANA_URL=https://<스택>.grafana.net
#        GRAFANA_API_KEY=<Alerting write 권한 토큰>
#      이 파일은 레포 밖이다. 공개 저장소에 자격증명을 두지 않는다는 원칙(deploy/README.md)과
#      같은 이유로, 앱 서버 .env에도 두지 않는다 — 서버가 털렸을 때 알람을 끌 권한까지
#      넘어가면 안 된다.
#   2) 이미 export된 GRAFANA_URL / GRAFANA_API_KEY 환경변수(CI나 일회성 실행용)
#
# 시각 계산과 JSON 조립은 python3에 맡긴다. macOS의 BSD date에는 `date -d '+10 minutes'`가 없어
# deploy.yml의 GNU date 문법을 그대로 쓸 수 없고, 사유 문자열에 따옴표나 한글이 들어가면
# 셸에서 JSON을 손으로 만드는 것도 깨진다.
set -euo pipefail

CONFIG_FILE="${XDG_CONFIG_HOME:-$HOME/.config}/at-crew/observability.env"
if [ -f "$CONFIG_FILE" ]; then
  # shellcheck disable=SC1090
  . "$CONFIG_FILE"
fi

if [ -z "${GRAFANA_URL:-}" ] || [ -z "${GRAFANA_API_KEY:-}" ]; then
  cat >&2 <<EOF
[silence] GRAFANA_URL 또는 GRAFANA_API_KEY가 없다.

다음 중 하나로 채운다.

  1) $CONFIG_FILE 을 만든다(권장 — 장애 상황에 토큰을 찾아 헤매지 않는다)
       mkdir -p "\$(dirname "$CONFIG_FILE")"
       cat > "$CONFIG_FILE" <<'ENV'
       GRAFANA_URL=https://<스택 이름>.grafana.net
       GRAFANA_API_KEY=<토큰>
       ENV
       chmod 600 "$CONFIG_FILE"

  2) 한 번만 쓸 때는 export 해서 넘긴다
       GRAFANA_URL=... GRAFANA_API_KEY=... $0 15m "사유"

토큰은 Grafana Cloud > Administration > Users and access > Access policies에서
alerts:write 범위로 발급한다. CI가 쓰는 저장소 Secret과 같은 값을 써도 되고,
사람용으로 따로 발급해 두면 나중에 어느 쪽이 새는지 구분할 수 있다.
EOF
  exit 2
fi

GRAFANA_URL="${GRAFANA_URL%/}"
API_BASE="$GRAFANA_URL/api/alertmanager/grafana/api/v2"

# --- 해제 ---------------------------------------------------------------
if [ "${1:-}" = "--end" ]; then
  SILENCE_ID="${2:-}"
  [ -n "$SILENCE_ID" ] || { echo "[silence] 해제할 ID가 없다: $0 --end <silence ID>" >&2; exit 2; }

  # deploy.yml과 같은 엔드포인트다. 생성은 복수형(silences), 해제는 단수형(silence/<id>)이라
  # 헷갈리기 쉽다 — Grafana Alertmanager API 사양이 그렇다.
  if ! CODE=$(curl -sS -o /dev/null -w '%{http_code}' -X DELETE "$API_BASE/silence/$SILENCE_ID" \
    -H "Authorization: Bearer $GRAFANA_API_KEY"); then
    echo "[silence] Grafana에 닿지 못했다($GRAFANA_URL) — URL과 네트워크를 확인한다." >&2
    echo "[silence] 해제되지 않았다. 만료를 기다리거나 Grafana UI에서 직접 푼다: $SILENCE_ID" >&2
    exit 1
  fi
  case "$CODE" in
    2*) echo "[silence] 해제됨: $SILENCE_ID" ;;
    404) echo "[silence] 그런 ID가 없다(이미 만료됐거나 해제됨): $SILENCE_ID" >&2; exit 1 ;;
    *)   echo "[silence] 해제 실패 HTTP $CODE: $SILENCE_ID" >&2; exit 1 ;;
  esac
  exit 0
fi

# --- 생성 ---------------------------------------------------------------
DURATION="${1:-}"
REASON="${2:-}"

if [ -z "$DURATION" ] || [ -z "$REASON" ]; then
  echo "[silence] 사용법: $0 <기간> \"<사유>\"   (예: $0 15m \"NAT 탄력적 IP 재할당\")" >&2
  echo "          해제:   $0 --end <silence ID>" >&2
  exit 2
fi

# 사유를 필수로 받는 이유: Grafana UI의 silence 목록에 그대로 보인다. "누가 왜 껐는지"가 없으면
# 나중에 본 사람이 풀어도 되는지 판단할 수 없다.
case "$DURATION" in
  *[0-9][smh]) : ;;
  *) echo "[silence] 기간 형식이 틀렸다: '$DURATION' — s/m/h만 쓴다(예: 90s, 15m, 2h)" >&2; exit 2 ;;
esac

PAYLOAD=$(DURATION="$DURATION" REASON="$REASON" WHO="$(whoami)" python3 -c '
import json, os, re, sys
from datetime import datetime, timedelta, timezone

m = re.fullmatch(r"(\d+)([smh])", os.environ["DURATION"])
if not m:
    sys.exit("[silence] 기간 파싱 실패")
n, unit = int(m.group(1)), m.group(2)
if n <= 0:
    sys.exit("[silence] 기간은 0보다 커야 한다")
delta = {"s": timedelta(seconds=n), "m": timedelta(minutes=n), "h": timedelta(hours=n)}[unit]

now = datetime.now(timezone.utc).replace(microsecond=0)
fmt = lambda t: t.strftime("%Y-%m-%dT%H:%M:%SZ")
print(json.dumps({
    # deploy.yml과 같은 매처 — alertname 전체를 정규식으로 잡아 이 스택의 알람을 모두 끈다.
    # 인프라 작업은 어느 알람이 울릴지 미리 알 수 없어 범위를 좁히지 않는다.
    "matchers": [{"name": "alertname", "value": ".*", "isRegex": True}],
    "startsAt": fmt(now),
    "endsAt": fmt(now + delta),
    "createdBy": os.environ["WHO"],
    "comment": os.environ["REASON"],
}))')

if ! RESPONSE=$(curl -sS -w '\n%{http_code}' -X POST "$API_BASE/silences" \
  -H "Authorization: Bearer $GRAFANA_API_KEY" -H 'Content-Type: application/json' \
  -d "$PAYLOAD"); then
  echo "[silence] Grafana에 닿지 못했다($GRAFANA_URL) — URL과 네트워크를 확인한다." >&2
  echo "[silence] 알람이 억제되지 않았다. 작업을 계속하면 P1이 울릴 수 있다." >&2
  exit 1
fi
CODE=$(printf '%s' "$RESPONSE" | tail -1)
BODY=$(printf '%s' "$RESPONSE" | sed '$d')

case "$CODE" in
  2*) : ;;
  401|403) echo "[silence] 인증 거부 HTTP $CODE — 토큰에 alerts:write 범위가 있는지 확인한다" >&2; exit 1 ;;
  *) echo "[silence] 생성 실패 HTTP $CODE: $BODY" >&2; exit 1 ;;
esac

SILENCE_ID=$(printf '%s' "$BODY" | python3 -c 'import sys, json; print(json.load(sys.stdin).get("silenceID", ""))')
[ -n "$SILENCE_ID" ] || { echo "[silence] 응답에 silenceID가 없다: $BODY" >&2; exit 1; }

ENDS=$(printf '%s' "$PAYLOAD" | python3 -c 'import sys, json; print(json.load(sys.stdin)["endsAt"])')
SELF="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/$(basename "${BASH_SOURCE[0]}")"

cat <<EOF
[silence] 생성됨 — 작업이 끝나면 만료를 기다리지 말고 바로 해제한다.
  ID   : $SILENCE_ID
  만료 : $ENDS ($DURATION)
  사유 : $REASON
  해제 : $SELF --end $SILENCE_ID
EOF
