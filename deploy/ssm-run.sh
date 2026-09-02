#!/usr/bin/env bash
# 앱 서버에서 셸 스크립트를 실행하고 출력을 그대로 흘린다. SSH를 대체하는 유일한 원격 실행 경로로,
# 자동 배포(.github/workflows/deploy.yml)와 수동 배포(deploy.sh)가 함께 쓴다.
#
# 왜 SSH가 아닌가: 앱 서버가 프라이빗 서브넷으로 옮겨가면서(#110) 퍼블릭 IP가 없어져 러너에서
# SSH가 닿지 않는다. 예전 방식은 배포마다 보안 그룹 22번을 러너 IP로 열었다 닫았는데, SSM은
# 포트를 열지 않고 IAM으로 통제하며 실행 이력이 CloudTrail에 남는다.
#
# 사용법 — 실행할 스크립트는 stdin으로 넘긴다(따옴표 이스케이프를 피하려고 인자로 받지 않는다):
#   echo 'docker ps' | deploy/ssm-run.sh
#   deploy/ssm-run.sh <<'EOF'
#   cd ~/at-crew-backend/deploy && docker-compose ps
#   EOF
#
# 필요한 환경변수: APP_INSTANCE_ID. 선택: SSM_POLL_SECONDS(기본 300 — 폴링 상한).
#
# 원격 명령이 0이 아닌 코드로 끝나면 이 스크립트도 같은 코드로 끝난다.
set -euo pipefail

INSTANCE_ID="${APP_INSTANCE_ID:?APP_INSTANCE_ID가 없다}"
POLL_LIMIT="${SSM_POLL_SECONDS:-300}"

REMOTE_SCRIPT="$(cat)"
[ -n "$REMOTE_SCRIPT" ] || { echo "[ssm-run] 실행할 스크립트가 비어 있다" >&2; exit 2; }

# commands는 JSON 배열이라 줄바꿈·따옴표를 직접 넣으면 깨진다. 스크립트 전체를 한 원소로 담되
# 이스케이프는 python에 맡긴다.
PARAMS=$(REMOTE_SCRIPT="$REMOTE_SCRIPT" python3 -c '
import json, os
print(json.dumps({"commands": [os.environ["REMOTE_SCRIPT"]]}))')

CMD_ID=$(aws ssm send-command \
  --instance-ids "$INSTANCE_ID" \
  --document-name AWS-RunShellScript \
  --parameters "$PARAMS" \
  --query Command.CommandId --output text)

# send-command 직후엔 invocation이 아직 없어 조회가 실패한다 — 그 구간은 Pending으로 취급한다.
STATUS=Pending
ELAPSED=0
while [ "$ELAPSED" -lt "$POLL_LIMIT" ]; do
  STATUS=$(aws ssm get-command-invocation --command-id "$CMD_ID" --instance-id "$INSTANCE_ID" \
    --query Status --output text 2>/dev/null || echo Pending)
  case "$STATUS" in
    Success|Failed|Cancelled|TimedOut) break ;;
  esac
  sleep 3
  ELAPSED=$((ELAPSED + 3))
done

# 출력은 SSM이 24KB에서 자른다(그 이상은 S3 연동이 필요하다). 배포 로그는 그 안에 들어가지만,
# 잘린 흔적이 보이면 인스턴스에서 직접 확인해야 한다.
#
# --output text는 내용이 이미 개행으로 끝나는데 거기에 개행을 하나 더 붙인다. 그대로 흘리면 호출부의
# `$(ssm-run.sh | tail -1)`이 값 대신 빈 줄을 잡는다(실측). $(...)로 후행 개행을 전부 떨어내고
# printf로 하나만 다시 붙여, 원격 명령의 출력과 이 스크립트의 출력이 같은 모양이 되게 한다.
OUT=$(aws ssm get-command-invocation --command-id "$CMD_ID" --instance-id "$INSTANCE_ID" \
  --query StandardOutputContent --output text || true)
[ -z "$OUT" ] || printf '%s\n' "$OUT"
ERR=$(aws ssm get-command-invocation --command-id "$CMD_ID" --instance-id "$INSTANCE_ID" \
  --query StandardErrorContent --output text || true)
[ -z "$ERR" ] || echo "$ERR" >&2

if [ "$STATUS" != "Success" ]; then
  CODE=$(aws ssm get-command-invocation --command-id "$CMD_ID" --instance-id "$INSTANCE_ID" \
    --query ResponseCode --output text 2>/dev/null || echo 1)
  echo "[ssm-run] 원격 실행 실패: status=$STATUS code=$CODE commandId=$CMD_ID" >&2
  # ResponseCode는 명령이 시작되지도 못한 경우 -1이다. 그대로 exit 코드로 쓰면 셸이 255로 바꾸므로 1로 맞춘다.
  [ "$CODE" -gt 0 ] 2>/dev/null && exit "$CODE" || exit 1
fi
