#!/usr/bin/env bash
# 배포 결과를 Discord 통지 등급(P1/P2)과 메시지로 분류한다. deploy.yml의 "Discord 통지" step이 부른다.
#
# 입력은 환경변수로 받는다(각 step의 outcome: success | failure | cancelled | skipped | 빈 값).
#   SYNC      deploy/ 동기화        — 서버 파일을 처음 건드리는 단계
#   RESTART   EC2 재기동            — 컨테이너 교체
#   HEALTH    헬스체크 대기
#   ROLLBACK  직전 이미지로 롤백
#   MIG_HAS   마이그레이션 포함 여부(true | false | 빈 값)
#   PREV_IMAGE  배포 직전 실행 중이던 이미지(빈 값이면 롤백 불가)
#   SHA_SHORT, ACTOR, RUN_URL  메시지에 넣을 값
#
# 출력: 첫 줄에 P1 또는 P2, 둘째 줄에 메시지(줄바꿈은 \n 문자열).
#
# 예전에는 헬스체크 이전 단계에서 실패해도 health·mig·rollback이 모두 skipped라 마지막 분기
# "롤백도 실패"로 떨어졌다. 테스트만 실패해 서버가 그대로인데도 "서비스가 내려가 있을 수 있다"는
# P1이 나갔다(#177). 그래서 "서버를 건드렸는가"를 먼저 가른다.
# 메시지의 \n은 글자 그대로 내보내고 deploy.yml이 printf '%b'로 펼친다.
# shellcheck disable=SC2028
set -eu

ran() { [ -n "${1:-}" ] && [ "$1" != "skipped" ]; }

head="\`${SHA_SHORT}\`"

if [ "${HEALTH:-}" = "success" ]; then
  echo "P2"
  echo "**배포 성공** ${head} by ${ACTOR}\n${RUN_URL}"
elif [ "${HEALTH:-}" = "failure" ]; then
  echo "P1"
  if [ "${ROLLBACK:-}" = "success" ]; then
    echo "**배포 실패 → 자동 롤백 완료** ${head}\n직전 이미지로 되돌렸고 liveness는 UP이다. 원인 확인 필요.\n${RUN_URL}"
  elif [ "${MIG_HAS:-}" = "true" ]; then
    echo "**배포 실패 — 자동 롤백 안 함(마이그레이션 포함)** ${head}\n스키마가 전진해 구버전 앱은 validate에서 죽는다. 수동 대응 필요: docs/operations/incident-runbook.md\n${RUN_URL}"
  elif [ "${ROLLBACK:-}" = "failure" ]; then
    echo "**배포 실패 — 롤백도 실패** ${head}\n서비스가 내려가 있을 수 있다. 즉시 확인 필요.\n${RUN_URL}"
  elif [ -z "${PREV_IMAGE:-}" ]; then
    echo "**배포 실패 — 자동 롤백 안 함(직전 이미지 모름)** ${head}\n헬스체크가 실패했고 되돌릴 이미지를 특정하지 못했다. 서비스가 내려가 있을 수 있다. 즉시 확인 필요.\n${RUN_URL}"
  else
    echo "**배포 실패 — 롤백 중단(${ROLLBACK:-미실행})** ${head}\n헬스체크 실패 후 롤백이 끝나지 않았다. 서비스가 내려가 있을 수 있다. 즉시 확인 필요.\n${RUN_URL}"
  fi
elif [ "${RESTART:-}" = "success" ]; then
  # 교체는 끝났는데 헬스체크 결과가 없다(헬스체크 도중 취소 등).
  echo "P1"
  echo "**배포 중단 — 교체 후 검증 전(헬스체크 ${HEALTH:-미실행})** ${head}\n새 이미지는 떠 있지만 헬스체크와 자동 롤백 판정을 거치지 않았다. 즉시 확인 필요.\n${RUN_URL}"
elif ran "${RESTART:-}"; then
  # 컨테이너 교체를 시작했지만 끝내지 못했다(교체 실패 또는 취소).
  echo "P1"
  echo "**배포 실패 — 컨테이너 교체 중 중단(${RESTART})** ${head}\n헬스체크와 자동 롤백 판정을 거치지 않았다. 새 이미지가 일부만 떠 있을 수 있다. 즉시 확인 필요.\n${RUN_URL}"
elif ran "${SYNC:-}"; then
  # deploy/는 새 커밋 기준으로 바뀌었을 수 있지만 컨테이너는 그대로다.
  echo "P1"
  echo "**배포 실패 — 서버 설정 반영 후 중단(동기화 ${SYNC})** ${head}\n컨테이너는 교체하지 않았다. deploy/와 nginx 설정은 새 커밋 기준일 수 있다. 확인 필요.\n${RUN_URL}"
else
  echo "P2"
  echo "**배포 중단 — 서버 변경 없음** ${head}\n빌드·테스트·이미지 푸시·SSM 연결 중 한 단계에서 멈췄다. 서비스는 그대로다.\n${RUN_URL}"
fi
