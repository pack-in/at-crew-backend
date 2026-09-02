#!/usr/bin/env bash
# 앱 서버 호스트에 "컨테이너 밖에서 도는 것들"을 설치한다 — 관측 에이전트(Alloy)와 백업 타이머.
#
# 배경: 앱은 docker-compose.app.yml로 배포되지만 Alloy와 백업은 각각 별도 compose 파일과 systemd
# 유닛이라 앱 배포에 딸려 오지 않는다. 이 분리는 "배포가 실패해도 수집은 계속되게" 하려는 의도인데
# (docker-compose.observability.yml 상단 주석), 인스턴스를 교체할 때는 그 분리가 그대로 누락이 된다.
# 2026-09-02 v2 이전에서 실제로 둘 다 빠졌고, 관측이 죽은 채 백업도 함께 멈춰 있었다(이슈 #115).
#
# 그래서 설치 절차를 문서의 복사-붙여넣기 목록이 아니라 실행 가능한 스크립트로 둔다.
# 여러 번 돌려도 안전하다(멱등) — 인스턴스를 새로 만들 때마다 앱 배포 직후 한 번 실행하면 된다.
#
# 사전 조건: deploy/ 디렉토리와 .env가 호스트에 올라와 있고, docker와 docker-compose가 설치돼 있을 것
# (deploy/README.md "최초 1회 설정" 참고).
#
# 실행:
#   cd ~/at-crew-backend/deploy && ./bootstrap.sh
set -euo pipefail

DEPLOY_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$DEPLOY_DIR"

ENV_FILE="$DEPLOY_DIR/.env"
METRIC_DIR="/var/lib/node_exporter/textfile_collector"

fail() { echo "[bootstrap] $1" >&2; exit 1; }

# .env를 source 하지 않는다 — 셸은 값을 명령으로 해석해서 깨진다(backup.sh와 같은 이유).
read_env() { sed -n "s/^$1=//p" "$ENV_FILE" | tail -1 | sed -e 's/^"//' -e 's/"$//'; }

echo "[bootstrap] 1/4 사전 조건 확인"
[ -f "$ENV_FILE" ] || fail ".env가 없다: $ENV_FILE — .env.example을 복사해 값을 채울 것"
command -v docker >/dev/null || fail "docker가 없다"
command -v docker-compose >/dev/null || fail "docker-compose가 없다 (플러그인 아닌 standalone 바이너리)"
command -v aws >/dev/null || fail "aws CLI가 없다 — backup.sh가 R2 업로드에 쓴다"

# 값이 비면 Alloy는 뜨지만 아무것도 전송하지 못하고, 백업은 매번 실패한다. 둘 다 조용히 죽는
# 실패라 여기서 먼저 막는다.
MISSING=""
for KEY in GRAFANA_CLOUD_PROM_URL GRAFANA_CLOUD_PROM_USER GRAFANA_CLOUD_LOKI_URL \
           GRAFANA_CLOUD_LOKI_USER GRAFANA_CLOUD_TOKEN \
           MARIADB_ROOT_PASSWORD R2_ENDPOINT R2_BACKUP_BUCKET \
           R2_BACKUP_ACCESS_KEY R2_BACKUP_SECRET_KEY; do
  [ -n "$(read_env "$KEY")" ] || MISSING="$MISSING $KEY"
done
[ -z "$MISSING" ] || fail ".env에 값이 비어 있다:$MISSING"

echo "[bootstrap] 2/4 백업 지표 디렉토리 준비: $METRIC_DIR"
# backup.sh가 성공 시각을 여기에 남기고 Alloy의 textfile 컬렉터가 읽어 간다. 디렉토리가 없으면
# 백업은 돌지만 "언제 성공했는지"가 관측에 안 잡혀 백업 감시 알람이 영원히 NoData가 된다.
sudo mkdir -p "$METRIC_DIR"
sudo chown "$(id -un)" "$METRIC_DIR"

echo "[bootstrap] 3/4 관측 에이전트(Alloy) 기동"
docker-compose -f docker-compose.observability.yml up -d

echo "[bootstrap] 4/4 백업 타이머 설치"
chmod +x "$DEPLOY_DIR/backup.sh"
sudo cp systemd/atcrew-backup.service systemd/atcrew-backup.timer /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable --now atcrew-backup.timer

echo
echo "[bootstrap] 검증"
# Alloy가 컨테이너로 떠 있는 것과 실제로 수집·전송하는 것은 다르다 — 기동 직후엔 아직 스크레이프
# 전이라 전송량이 0일 수 있으므로, 여기서는 컴포넌트 상태와 스크레이프 대상까지만 확인한다.
sleep 5
if docker ps --filter 'label=com.docker.compose.service=alloy' --format '{{.Status}}' | grep -q Up; then
  echo "  - Alloy 컨테이너: 기동됨"
else
  fail "Alloy 컨테이너가 뜨지 않았다 — docker logs로 확인할 것"
fi

if curl -fsS --max-time 5 -o /dev/null http://127.0.0.1:8081/actuator/prometheus; then
  echo "  - 앱 관리 포트(8081) 스크레이프 대상: 응답함"
else
  echo "  - 경고: 앱 관리 포트(8081)가 응답하지 않는다. 앱이 아직 안 떴다면 무시해도 되지만," >&2
  echo "          앱이 떠 있는데도 이러면 Alloy가 수집하지 못해 [P1] 앱 메트릭 수집 불가가 울린다." >&2
fi

systemctl list-timers atcrew-backup.timer --all --no-pager | sed -n '2p;3p'

echo
echo "[bootstrap] 완료. 남은 확인:"
echo "  - 수집 전송 확인(1분 뒤): curl -s http://127.0.0.1:12345/metrics | grep prometheus_remote_storage_samples_total"
echo "  - 백업 즉시 검증: sudo systemctl start atcrew-backup.service && journalctl -u atcrew-backup.service -n 20 --no-pager"
