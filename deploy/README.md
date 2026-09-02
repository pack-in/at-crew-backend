# 배포 (prod, self-hosted EC2)

`docs/design/mariadb-migration-design.md` §10-1에서 확정한 구성. AWS 계정은 laiteu와 동일(`sehandev`
소유). 상세 배경·비용 판단은 `docs/NEXT_STEPS.md` 2026-08-07 항목 참고.

## 인프라 레이아웃

2026-09-02 기준. #110 Phase 1로 전용 VPC(Public/Private 서브넷 분리)를 신설하고 앱 서버를 Private
서브넷으로 옮겼다. 이전 구성(퍼블릭 IP를 가진 EC2 #1 + 별도 Elasticsearch EC2 #2)은 종료됐다.

| 위치 | 구성 | 비고 |
|---|---|---|
| Private 서브넷 — 앱 서버 | `app` + `mariadb` + `elasticsearch` 컨테이너(`docker-compose.app.yml`), `alloy`(`docker-compose.observability.yml`), 호스트에 nginx·cloudflared | **퍼블릭 IP 없음.** Elasticsearch는 EC2 #2에서 이 인스턴스로 통합됐다(#107) |
| Public 서브넷 — NAT 인스턴스 | 아웃바운드 전용 | 관리형 NAT Gateway 대신 인스턴스로 비용을 줄였다(#110) |

외부 유입은 **Cloudflare Tunnel**이다. `cloudflared`가 앱 서버에서 아웃바운드로 터널을 열고, 들어온
요청을 호스트 nginx(80/443)가 받아 앱 컨테이너(8080)로 넘긴다. 인바운드 포트를 하나도 열지 않으므로
origin IP 직접 타격이 성립하지 않는다 — 이전 구성의 "80을 Cloudflare 대역만 허용" 보안 그룹 규칙이
하던 일을 터널이 대신한다.

원격 접속은 **SSM**이다. 퍼블릭 IP가 없어 SSH가 닿지 않는다. 배포·운영 명령은 `deploy/ssm-run.sh`를
거치고, 대화형 접속이 필요하면 `aws ssm start-session --target <인스턴스 ID>`를 쓴다.

> **아직 남은 것(#110):** 2 AZ 이중화와 ALB는 구성되지 않았다(현재 단일 AZ). MariaDB Replica도
> Phase 2로 남아 있다.

## 프로비저닝된 리소스 (2026-08-07, ap-northeast-2)

이 레포는 공개되어 있으므로 **인스턴스 ID·탄력적 IP·프라이빗 IP·VPC/보안 그룹/AMI ID 같은 실제
식별자는 여기에 적지 않는다.** 그대로 계정 정찰과 origin 직접 타격의 출발점이 되기 때문이다.
실제 값은 AWS 콘솔과 저장소 Secrets에만 둔다.

| 항목 | 구성 |
|---|---|
| 앱 서버 | Private 서브넷, 퍼블릭 IP 없음. 앱 + MariaDB + Elasticsearch 컨테이너 |
| NAT 인스턴스 | Public 서브넷. 앱 서버의 아웃바운드 경로 |
| 인스턴스 프로파일 | `AmazonSSMManagedInstanceCore` 부착 — 배포·운영 접속의 유일한 경로 |
| 인바운드 | **없다.** 외부 유입은 Cloudflare Tunnel의 아웃바운드 연결로만 이뤄진다 |
| AMI | Amazon Linux 2023 ARM64 — 패키지 관리자는 `dnf`(Ubuntu `apt` 아님) |

> **인바운드 포트를 열지 말 것.** 지금 앱 서버에 열려 있는 인바운드는 없고, 트래픽은 전부 cloudflared가
> 바깥으로 연 터널을 타고 들어온다. 포트를 하나 열면 그 순간 Cloudflare의 WAF·레이트리밋·봇 차단을
> 우회하는 경로가 생긴다. nginx는 터널이 넘긴 요청을 받는 로컬 게이트일 뿐 더 이상 유일한 방어선이 아니다.
>
> 터널의 ingress 규칙은 레포가 아니라 **Cloudflare 대시보드**에서 관리한다(호스트에 `config.yml`이 없는
> 관리형 터널이다). nginx 443은 Cloudflare Origin 인증서를 쓴다(`/etc/nginx/certs/`).

대화형 접속이 필요하면 SSM을 쓴다. 키페어도 Bastion도 없다.

```bash
aws ssm start-session --target <인스턴스 ID>     # session-manager-plugin 필요
```

## 이 디렉토리 파일

- `docker-compose.app.yml` — 앱 서버용(app·mariadb·elasticsearch)
- `docker-compose.observability.yml` — 관측 에이전트(Alloy)
- `docker-compose.search.yml` — Elasticsearch를 별도 인스턴스로 다시 분리할 때만 쓴다(현재 미사용)
- `nginx/api.at-crew.com.conf` — 앱 서버에 설치할 nginx 리버스 프록시 설정
- `.env.example` — 앱 서버 `.env` 템플릿(실제 값은 채워서 `.env`로 저장, git에 커밋 금지)
- `ssm-run.sh` — 앱 서버에서 원격 명령을 실행한다(SSH 대체). 자동·수동 배포가 공용으로 쓴다
- `bootstrap.sh` — 새 호스트에 관측 에이전트와 백업 타이머를 설치한다
- `backup.sh`, `systemd/` — MariaDB 일일 백업 스크립트와 타이머 유닛
- `deploy.sh` — 로컬에서 빌드→Docker Hub 푸시→앱 서버 재배포까지 한 번에

## 자동 배포 (`.github/workflows/deploy.yml`)

main push(=PR 머지) 시 빌드·테스트 → Docker Hub 푸시 → 앱 서버 재기동까지 자동으로 돈다.

원격 실행은 전부 **SSM**이다(`ssm-run.sh`). 앱 서버가 프라이빗 서브넷이라 러너에서 SSH가 닿지 않는다.
예전에는 배포마다 보안 그룹 22번을 러너 IP로 열었다 닫았는데, 지금은 포트를 열지 않고 IAM으로 통제하며
실행 이력이 CloudTrail에 남는다. 그래서 SSH 키를 CI에 두지 않는다.

필요한 저장소 Secrets: `DOCKERHUB_USERNAME`, `DOCKERHUB_TOKEN`, `APP_INSTANCE_ID`,
`AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`.

러너의 IAM 사용자에게는 `ssm:SendCommand`, `ssm:GetCommandInvocation`,
`ssm:DescribeInstanceInformation`이 필요하다. 인스턴스 쪽에는 `AmazonSSMManagedInstanceCore`가
부착돼 있어야 한다(둘 다 이미 갖춰져 있다).

## 최초 1회 설정

인스턴스·서브넷·인스턴스 프로파일은 이미 위 표대로 만들어져 있다 — 아래는 그 위에서 소프트웨어만
설치하면 된다.

1. **앱 서버**: Amazon Linux 2023이라 `dnf` 사용.
   ```bash
   sudo dnf install -y nginx docker
   sudo systemctl enable --now docker
   sudo usermod -aG docker ec2-user   # 재접속해야 반영됨
   DOCKER_COMPOSE_VER=v2.29.7
   sudo curl -L "https://github.com/docker/compose/releases/download/${DOCKER_COMPOSE_VER}/docker-compose-linux-aarch64" -o /usr/local/bin/docker-compose
   sudo chmod +x /usr/local/bin/docker-compose
   ```
   `nginx/api.at-crew.com.conf`를 `/etc/nginx/conf.d/`에 올리고 `sudo nginx -t && sudo systemctl enable --now nginx`.
   기본 `/etc/nginx/nginx.conf`의 `listen 80 default_server;` 서버 블록은 먼저 주석 처리한다
   (이 파일의 default_server와 충돌해 기동이 실패한다).
   이 레포의 `deploy/` 디렉토리를 서버에 올리고(`git clone`), `.env.example`을 `.env`로
   복사해 값을 채운다. Firebase 서비스 계정 JSON도 별도로 올려 `FIREBASE_CREDENTIALS_PATH`에 지정.

   앱을 배포한 뒤 **`./bootstrap.sh`를 반드시 한 번 실행한다.** 관측 에이전트(Alloy)와 백업 타이머는
   앱 compose에 들어 있지 않아 앱 배포만으로는 설치되지 않는다(아래 "인스턴스를 교체할 때" 참고).
2. **Cloudflare Tunnel**: `cloudflared`를 설치하고 대시보드에서 발급한 토큰으로 서비스로 등록한다
   (`/etc/cloudflared/token`). ingress 규칙은 대시보드에서 `api.at-crew.com` → 로컬 nginx로 건다.
3. **Cloudflare**: 배포된 Worker의
   `SERVER_CALLBACK_URL` 시크릿도 `https://api.at-crew.com/internal/media/images/processed`를
   가리켜야 한다(`cloudflare-worker/README.md` 참고). 이 값이 틀리면 콜백이 서버에 도착하지 않아
   업로드는 되는데 썸네일이 영영 생기지 않는다 — 2026-08-18~08-27에 임시 tunnel 주소를 가리킨 채로
   방치돼 콜백이 한 건도 도착하지 않았다(이슈 #59). 도메인 오타도 같은 결과를 낳으므로 하이픈까지
   확인할 것.
4. **Docker Hub**: 로컬에서 `docker login`.

## 이후 배포

```bash
DOCKERHUB_USER=<본인 계정> APP_INSTANCE_ID=<인스턴스 ID> ./deploy/deploy.sh
```

## 인스턴스를 교체할 때

앱 서버를 새 인스턴스로 옮기면(blue-green 이전, AMI 교체, 리전 이동 등) **컨테이너 밖에서 도는 것들은
따라오지 않는다.** 앱은 `docker-compose.app.yml`로 배포되지만 아래 둘은 각각 별도 compose 파일과
systemd 유닛이다.

| 항목 | 실체 | 빠뜨렸을 때 |
|---|---|---|
| 관측 에이전트 | `docker-compose.observability.yml`(Alloy) | 메트릭·로그 수집이 끊긴다. `[P1] 앱 메트릭 수집 불가`가 계속 울리는데 서비스는 멀쩡한 상태가 된다 |
| DB 백업 | `systemd/atcrew-backup.{service,timer}` | 백업이 조용히 멈춘다. 관측도 함께 죽어 있으면 백업 감시 알람마저 울리지 않는다 |
| 스왑 | `/swapfile` + `/etc/fstab` | 메모리 완충이 없어 OOM 킬러가 곧바로 돈다. 앱·MariaDB·Elasticsearch가 한 인스턴스를 나눠 쓰므로 한 컨테이너의 폭주가 다른 컨테이너를 죽인다(이슈 #116) |

앱을 새 인스턴스에 올린 직후 **`./bootstrap.sh`를 실행하면 둘 다 설치·기동된다.** 멱등하므로 이미
설치된 호스트에서 다시 돌려도 안전하다.

```bash
cd ~/at-crew-backend/deploy && ./bootstrap.sh
```

이전 완료 체크리스트:

- [ ] `./bootstrap.sh` 실행 — 스왑 구성 + Alloy 기동 + 백업 타이머 등록
- [ ] 스왑 확인 — `swapon --show`에 `/swapfile`이 보일 것. **`/dev/zram0`만 있으면 안 된다** —
      zram은 RAM을 압축해 쓰는 것이라 OOM 완충이 되지 못한다(Amazon Linux 2023 기본값)
- [ ] 1분 뒤 수집 확인 — `curl -s http://127.0.0.1:12345/metrics | grep prometheus_remote_storage_samples_total`이 증가하고 `samples_failed_total`이 0
- [ ] 백업 1회 수동 검증 — `sudo systemctl start atcrew-backup.service` 후 `journalctl -u atcrew-backup.service -n 20`
- [ ] **`APP_INSTANCE_ID` 저장소 Secret을 새 인스턴스 ID로 갱신** — 갱신하지 않으면 다음 main
      병합에서 자동 배포가 없어진 인스턴스로 SSM 명령을 보내 실패한다
- [ ] 새 인스턴스에 `AmazonSSMManagedInstanceCore` 인스턴스 프로파일이 붙어 있는지 확인 —
      `aws ssm describe-instance-information`에 `Online`으로 나와야 배포가 된다
- [ ] Grafana에 걸어둔 silence 해제

> 2026-09-02에 이 목록이 없어서 v2 이전 때 관측과 백업이 함께 누락됐다(이슈 #115). 관측 스택을 앱과
> 분리한 것은 "배포가 실패해도 수집은 계속되게" 하려는 의도였는데, 인스턴스를 교체할 때는 그 분리가
> 그대로 누락으로 이어진다.
