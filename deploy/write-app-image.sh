#!/usr/bin/env bash
# 서버 .env의 APP_IMAGE를 갱신한다. 원격 스크립트에서 `. deploy/write-app-image.sh` 대신
# 내용을 그대로 붙여 쓰는 곳이 있으니(SSM으로 넘기는 heredoc) 함수 본문을 짧게 유지한다.
#
# 왜 필요한가: 배포는 APP_IMAGE를 셸 변수로만 넘겼고 .env는 옛 이미지를 가리킨 채 남았다. 그 상태로
# 서버에서 compose로 컨테이너를 재생성하면 옛 이미지로 조용히 되돌아가고, 그 버전이 이미 적용된
# 마이그레이션과 맞지 않으면 기동조차 못 한다 — 2026-09-23에 artwork_images를 지운 뒤(V47) 8월 이미지가
# 올라와 3분간 중단됐다.
#
# 주의 두 가지:
#  1) SSM(AWS-RunShellScript)은 root로 돈다. 새 파일을 만들어 mv하면 .env가 root 소유로 바뀌고,
#     User=ec2-user로 도는 백업 서비스(deploy/systemd/atcrew-backup.service)가 .env를 읽지 못해
#     조용히 멈춘다 — 같은 계열의 사고가 2026-09-11에 있었다(deploy/bootstrap.sh 주석). 그래서
#     소유자·권한을 원본에서 그대로 승계한다.
#  2) .env에는 시크릿이 전부 들어 있다. 임시 파일이 한순간이라도 0644로 만들어지지 않게 umask를 조인다.
write_app_image() {
    env_file="$1"
    image="$2"
    tmp="$env_file.next"
    (
        umask 077
        { grep -v '^APP_IMAGE=' "$env_file" || true; printf 'APP_IMAGE=%s\n' "$image"; } > "$tmp"
    )
    chown --reference="$env_file" "$tmp"
    chmod --reference="$env_file" "$tmp"
    mv "$tmp" "$env_file"
}
