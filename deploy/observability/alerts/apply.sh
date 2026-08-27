#!/usr/bin/env bash
# Grafana 알람 설정을 API로 반영한다 — docs/design/observability-design.md §7
#
# 알람 정의를 UI 클릭으로 만들면 코드와 실제 설정이 조용히 갈라진다. 이 스크립트가
# 유일한 반영 경로이고, .github/workflows/observability.yml이 이걸 실행한다.
#
# 필요한 환경변수(전부 GitHub Secrets):
#   GRAFANA_URL, GRAFANA_API_KEY, DISCORD_WEBHOOK_P1, DISCORD_WEBHOOK_P2
set -euo pipefail

: "${GRAFANA_URL:?GRAFANA_URL 없음}"
: "${GRAFANA_API_KEY:?GRAFANA_API_KEY 없음}"
: "${DISCORD_WEBHOOK_P1:?DISCORD_WEBHOOK_P1 없음}"
: "${DISCORD_WEBHOOK_P2:?DISCORD_WEBHOOK_P2 없음}"

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
API=(-sS -H "Authorization: Bearer ${GRAFANA_API_KEY}" -H "Content-Type: application/json")
# 프로비저닝 API로 만든 리소스는 기본적으로 UI에서 수정이 잠긴다. 긴급 상황에 손으로 끌 수
# 있어야 하므로 provenance를 붙이지 않는다(대신 여기 파일이 정본이라는 규율은 사람이 지킨다).
API+=(-H "X-Disable-Provenance: true")

echo "[1/4] 폴더 확인"
FOLDER_UID="at-crew"
if ! curl "${API[@]}" -f -o /dev/null "${GRAFANA_URL}/api/folders/${FOLDER_UID}" 2>/dev/null; then
  curl "${API[@]}" -X POST "${GRAFANA_URL}/api/folders" \
    -d "{\"uid\":\"${FOLDER_UID}\",\"title\":\"at-crew\"}" > /dev/null
  echo "  폴더 생성: ${FOLDER_UID}"
else
  echo "  폴더 존재: ${FOLDER_UID}"
fi

echo "[2/4] Prometheus 데이터소스 UID 조회"
PROM_UID=$(curl "${API[@]}" "${GRAFANA_URL}/api/datasources" \
  | jq -r '[.[] | select(.type=="prometheus")] | .[0].uid')
[ -n "$PROM_UID" ] && [ "$PROM_UID" != "null" ] || { echo "prometheus 데이터소스를 찾지 못했다" >&2; exit 1; }
echo "  PROM_UID=${PROM_UID}"

echo "[3/4] 연락처(Discord P1/P2)"
apply_contact_point() {
  local name="$1" webhook="$2" uid="$3"
  local payload
  payload=$(jq -n --arg name "$name" --arg url "$webhook" --arg uid "$uid" '{
    uid: $uid, name: $name, type: "discord",
    settings: { url: $url, use_discord_username: true },
    disableResolveMessage: false
  }')
  if curl "${API[@]}" -f -o /dev/null -X PUT "${GRAFANA_URL}/api/v1/provisioning/contact-points/${uid}" -d "$payload" 2>/dev/null; then
    echo "  갱신: $name"
  else
    curl "${API[@]}" -X POST "${GRAFANA_URL}/api/v1/provisioning/contact-points" -d "$payload" > /dev/null
    echo "  생성: $name"
  fi
}
apply_contact_point "discord-p1" "$DISCORD_WEBHOOK_P1" "atcrew-discord-p1"
apply_contact_point "discord-p2" "$DISCORD_WEBHOOK_P2" "atcrew-discord-p2"

# 알림 정책 — severity=p1은 P1 채널로, 나머지는 전부 P2 채널로. 재알림 간격은 P1 30분·P2 4시간.
curl "${API[@]}" -X PUT "${GRAFANA_URL}/api/v1/provisioning/policies" \
  -d @"${DIR}/notification-policy.json" > /dev/null
echo "  알림 정책 반영"

echo "[4/4] 알람 룰"
# _comment는 문서용 키라 API에 보내지 않는다.
RULES=$(sed -e "s/__FOLDER_UID__/${FOLDER_UID}/g" -e "s/__PROM_UID__/${PROM_UID}/g" "${DIR}/rules.json" \
  | jq 'map(del(._comment))')

echo "$RULES" | jq -c '.[]' | while read -r rule; do
  uid=$(echo "$rule" | jq -r '.uid')
  title=$(echo "$rule" | jq -r '.title')
  paused=$(echo "$rule" | jq -r '.isPaused')
  if curl "${API[@]}" -f -o /dev/null -X PUT "${GRAFANA_URL}/api/v1/provisioning/alert-rules/${uid}" -d "$rule" 2>/dev/null; then
    echo "  갱신: ${title}$([ "$paused" = "true" ] && echo ' (일시중지)')"
  else
    curl "${API[@]}" -f -X POST "${GRAFANA_URL}/api/v1/provisioning/alert-rules" -d "$rule" > /dev/null
    echo "  생성: ${title}$([ "$paused" = "true" ] && echo ' (일시중지)')"
  fi
done

echo "[5/5] 대시보드"
# 대시보드도 클릭으로 만들지 않는다 — 정의는 deploy/observability/dashboards/ 가 정본이고
# 여기서 덮어쓴다(UI에서 고친 내용은 다음 실행 때 사라진다).
for f in "${DIR}/../dashboards"/*.json; do
  [ -e "$f" ] || continue
  body=$(sed "s/__PROM_UID__/${PROM_UID}/g" "$f" \
    | jq -c --arg folder "$FOLDER_UID" '{dashboard: ., folderUid: $folder, overwrite: true, message: "provisioned by CI"}')
  title=$(echo "$body" | jq -r '.dashboard.title')
  curl "${API[@]}" -f -X POST "${GRAFANA_URL}/api/dashboards/db" -d "$body" > /dev/null
  echo "  반영: ${title}"
done

echo "완료."
echo "  알람: ${GRAFANA_URL}/alerting/list"
echo "  대시보드: ${GRAFANA_URL}/dashboards/f/${FOLDER_UID}"
