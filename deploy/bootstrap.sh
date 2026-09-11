#!/usr/bin/env bash
# 앱 서버 호스트에 "컨테이너 밖에서 도는 것들"을 설치한다 — 관측 에이전트(Alloy), 백업 타이머
# 둘(DB 덤프·R2 이미지), 그리고 덤프 암호화에 쓰는 age.
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
# 버전을 고정한다 — latest로 받으면 인스턴스를 새로 만들 때마다 다른 버전이 깔린다.
# 체크섬은 해당 버전 배포물의 SHA-256이다. **버전을 올리면 이 값도 함께 갱신해야 한다** —
# 안 고치면 설치가 실패하고 백업이 서지 않는다(조용히 넘어가지 않는 쪽이 안전하다).
AGE_VERSION="${AGE_VERSION:-v1.3.2}"
AGE_SHA256_ARM64="6b8dc4333c53a5a57c9e5834e3a48f92605d7154014cd07269ff3327db5d37f4"
AGE_SHA256_AMD64="cbe24006683f8eb669266162894b9a522a1af52f2665fbc63a4bb032ed26ac10"

fail() { echo "[bootstrap] $1" >&2; exit 1; }

# .env를 source 하지 않는다 — 셸은 값을 명령으로 해석해서 깨진다(backup.sh와 같은 이유).
read_env() { sed -n "s/^$1=//p" "$ENV_FILE" | tail -1 | sed -e 's/^"//' -e 's/"$//'; }

echo "[bootstrap] 1/6 사전 조건 확인"
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
           R2_BACKUP_ACCESS_KEY R2_BACKUP_SECRET_KEY AGE_RECIPIENT \
           R2_BUCKET R2_IMAGE_BACKUP_BUCKET \
           R2_IMAGE_BACKUP_ACCESS_KEY R2_IMAGE_BACKUP_SECRET_KEY \
           MYSQL_EXPORTER_DSN; do
  [ -n "$(read_env "$KEY")" ] || MISSING="$MISSING $KEY"
done
[ -z "$MISSING" ] || fail ".env에 값이 비어 있다:$MISSING"

echo "[bootstrap] 2/6 백업 암호화 도구(age) 설치"
# backup.sh가 덤프를 age 공개키로 암호화한다. Amazon Linux 2023의 기본 저장소에는 age 패키지가
# 없어서 공식 릴리스의 정적 바이너리를 그대로 놓는다(의존성이 없는 단일 실행 파일이다).
if command -v age >/dev/null; then
  echo "  - 이미 설치돼 있다: $(age --version 2>&1 | head -1)"
else
  case "$(uname -m)" in
    aarch64) AGE_ARCH=arm64; AGE_SHA256="$AGE_SHA256_ARM64" ;;
    x86_64)  AGE_ARCH=amd64; AGE_SHA256="$AGE_SHA256_AMD64" ;;
    *) fail "age 바이너리가 없는 아키텍처다: $(uname -m)" ;;
  esac
  AGE_TMP="$(mktemp -d)"
  # 파이프로 바로 풀지 않는다 — 그러면 검증 전에 내용이 디스크에 풀린다. 받아서, 검증하고, 푼다.
  curl -fsSL -o "$AGE_TMP/age.tar.gz" \
    "https://github.com/FiloSottile/age/releases/download/${AGE_VERSION}/age-${AGE_VERSION}-linux-${AGE_ARCH}.tar.gz" \
    || fail "age 내려받기 실패 — 네트워크(NAT 경유)를 확인할 것"
  echo "${AGE_SHA256}  ${AGE_TMP}/age.tar.gz" | sha256sum -c - >/dev/null 2>&1 \
    || fail "age 체크섬 불일치 — 내려받은 파일을 신뢰할 수 없다(기대 ${AGE_SHA256}, 실제 $(sha256sum "$AGE_TMP/age.tar.gz" | awk '{print $1}'))"
  tar -xzf "$AGE_TMP/age.tar.gz" -C "$AGE_TMP" || fail "age 압축 해제 실패"
  sudo install -m 0755 "$AGE_TMP/age/age" "$AGE_TMP/age/age-keygen" /usr/local/bin/
  rm -rf "$AGE_TMP"
  echo "  - age ${AGE_VERSION} 설치 완료 (체크섬 확인됨)"
fi

echo "[bootstrap] 3/6 백업 지표 디렉토리 준비: $METRIC_DIR"
# backup.sh가 성공 시각을 여기에 남기고 Alloy의 textfile 컬렉터가 읽어 간다. 디렉토리가 없으면
# 백업은 돌지만 "언제 성공했는지"가 관측에 안 잡혀 백업 감시 알람이 영원히 NoData가 된다.
sudo mkdir -p "$METRIC_DIR"
# 소유자를 "실행자"가 아니라 서비스 계정으로 고정한다. 이 스크립트를 ssm-run.sh로 돌리면
# root로 실행되는데, 그때 `id -un`을 쓰면 디렉토리가 root 소유가 되고 User=ec2-user로 도는
# 백업 서비스가 지표를 쓰지 못한다(2026-09-11 실제 발생). 증상이 고약하다 — 덤프 생성과
# R2 업로드는 멀쩡히 끝나고 마지막 지표 기록에서만 죽어서, 서비스는 exit 1인데 백업 파일은
# 정상이고 알람은 "26시간 미실행"으로 뜬다.
sudo chown ec2-user "$METRIC_DIR"

echo "[bootstrap] 4/6 스왑 파일 구성"
# 앱·MariaDB·Elasticsearch가 한 인스턴스 메모리를 나눠 쓰는데 스왑이 없으면 완충 구간 없이
# 곧바로 OOM 킬러가 돈다 — 한 컨테이너의 폭주가 다른 컨테이너를 죽인다(이슈 #116).
# 컨테이너 메모리 상한(docker-compose.app.yml)과 짝을 이루는 호스트 쪽 방어다.
SWAP_FILE="${SWAP_FILE:-/swapfile}"
SWAP_SIZE_MB="${SWAP_SIZE_MB:-2048}"
# zram은 스왑으로 잡히지만 RAM을 압축해 쓰는 것이라 OOM 완충이 되지 못한다 — 메모리가 부족한
# 상황에서 메모리를 더 쓰는 구조다. Amazon Linux 2023은 zram을 기본으로 켜 두므로(실측: t4g.nano
# 에서 /dev/zram0 412MB), 이걸 "스왑 있음"으로 세면 디스크 스왑이 영영 만들어지지 않는다.
DISK_SWAP="$(swapon --show=NAME --noheadings 2>/dev/null | grep -v '^/dev/zram' || true)"
if [ -n "$DISK_SWAP" ]; then
  echo "  - 이미 디스크 스왑이 있다 — 건너뛴다: $(echo "$DISK_SWAP" | tr '\n' ' ')"
else
  # 루트 사용률 85% 초과는 [P1] 알람 대상이다 — 스왑 파일이 그 알람을 유발하면 안 된다.
  AVAIL_MB="$(df -Pm / | awk 'NR==2{print $4}')"
  [ "$AVAIL_MB" -gt "$((SWAP_SIZE_MB + 2048))" ] \
    || fail "루트 여유가 부족하다(${AVAIL_MB}MB) — 스왑 ${SWAP_SIZE_MB}MB에 더해 2GB는 남겨야 한다. SWAP_SIZE_MB로 줄이거나 디스크를 늘릴 것"
  # fallocate를 쓰지 않는다 — xfs에서 미기록 익스텐트가 생겨 swapon이 "swapfile has holes"로
  # 거부한다(Amazon Linux 2023의 루트 파일시스템이 xfs다). dd로 실제 블록을 채운다.
  sudo dd if=/dev/zero of="$SWAP_FILE" bs=1M count="$SWAP_SIZE_MB" status=none
  sudo chmod 600 "$SWAP_FILE"
  sudo mkswap "$SWAP_FILE" >/dev/null
  sudo swapon "$SWAP_FILE"
  # 재부팅 후에도 살아 있어야 한다 — 없으면 인스턴스가 재시작되는 순간 조용히 사라진다.
  grep -q "^${SWAP_FILE} " /etc/fstab || echo "${SWAP_FILE} none swap sw 0 0" | sudo tee -a /etc/fstab >/dev/null
  echo "  - 스왑 ${SWAP_SIZE_MB}MB 생성·활성화, /etc/fstab 등록 완료"
fi
# 스왑은 성능 경로가 아니라 OOM 완충이다. 기본값 60은 메모리에 여유가 있어도 적극적으로
# 스왑아웃해 지연을 늘린다 — 정말 부족할 때만 쓰도록 낮춘다.
# /etc/sysctl.d가 없는 호스트에서 tee가 실패하면 set -e로 스크립트 전체가 죽는다 — 먼저 만든다.
sudo mkdir -p /etc/sysctl.d
printf 'vm.swappiness = 10\n' | sudo tee /etc/sysctl.d/99-atcrew-swappiness.conf >/dev/null
sudo sysctl -q -w vm.swappiness=10

echo "[bootstrap] 5/6 관측 에이전트(Alloy) 기동"
docker-compose -f docker-compose.observability.yml up -d

echo "[bootstrap] 6/6 백업 타이머 설치 (DB 덤프·R2 이미지)"
chmod +x "$DEPLOY_DIR/backup.sh" "$DEPLOY_DIR/r2-image-backup.sh"
sudo cp systemd/atcrew-backup.service systemd/atcrew-backup.timer \
        systemd/atcrew-image-backup.service systemd/atcrew-image-backup.timer /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable --now atcrew-backup.timer atcrew-image-backup.timer

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

if swapon --show=NAME --noheadings 2>/dev/null | grep -qv '^/dev/zram'; then
  echo "  - 디스크 스왑: $(swapon --show=NAME,SIZE --noheadings | grep -v zram | tr '\n' ' '), swappiness=$(cat /proc/sys/vm/swappiness)"
else
  fail "디스크 스왑이 활성화되지 않았다 — zram만으로는 OOM 완충이 되지 않는다"
fi

systemctl list-timers 'atcrew-*backup.timer' --all --no-pager | sed -n '2p;3p;4p'

echo
echo "[bootstrap] 완료. 남은 확인:"
echo "  - 수집 전송 확인(1분 뒤): curl -s http://127.0.0.1:12345/metrics | grep prometheus_remote_storage_samples_total"
echo "  - 백업 즉시 검증: sudo systemctl start atcrew-backup.service && journalctl -u atcrew-backup.service -n 20 --no-pager"
echo "  - 이미지 백업 즉시 검증: sudo systemctl start atcrew-image-backup.service && journalctl -u atcrew-image-backup.service -n 20 --no-pager"
echo "  - 덤프가 실제로 복호화되는지: scripts/baseline/restore-drill.sh --env-file <env> --age-key <개인키 파일>"
