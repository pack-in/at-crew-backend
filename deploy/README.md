# 배포 (prod, self-hosted EC2)

`docs/design/mariadb-migration-design.md` §10-1에서 확정한 구성. AWS 계정은 laiteu와 동일(`sehandev`
소유). 상세 배경·비용 판단은 `docs/NEXT_STEPS.md` 2026-08-07 항목 참고.

## 인프라 레이아웃

| EC2 | 구성 | 비고 |
|---|---|---|
| #1 앱 서버 | `app`(스프링부트) + `mariadb` 컨테이너, `docker-compose.app.yml` | 퍼블릭 IP 있음, nginx가 80만 수신(Cloudflare Flexible이라 origin 구간은 평문) |
| #2 Elasticsearch | `elasticsearch` 컨테이너, `docker-compose.search.yml` | 퍼블릭 IP 없음(비용·보안), 보안 그룹으로 #1만 9200 접근 허용 |

도메인은 `api.at-crew.com` — Cloudflare DNS에서 #1의 탄력적 IP로 A레코드 연결.

## 프로비저닝된 리소스 (2026-08-07, ap-northeast-2)

이 레포는 공개되어 있으므로 **인스턴스 ID·탄력적 IP·프라이빗 IP·VPC/보안 그룹/AMI ID 같은 실제
식별자는 여기에 적지 않는다.** 그대로 계정 정찰과 origin 직접 타격의 출발점이 되기 때문이다.
실제 값은 AWS 콘솔과 저장소 Secrets에만 둔다.

| 항목 | 구성 |
|---|---|
| EC2 #1(앱 서버) | 퍼블릭(탄력적) IP 있음. 앱 + MariaDB 컨테이너 |
| EC2 #2(Elasticsearch) | 퍼블릭 IP 없음, 같은 VPC 프라이빗 IP로만 접근 |
| 키페어 | 로컬 `~/.ssh/`에만 보관(git 추적 안 됨, 최초 생성 시 한 번만 다운로드됨) |
| 보안 그룹(앱) | 22는 관리자 고정 IP + 배포 시 러너 IP 임시 개방, **80은 Cloudflare 엣지 대역만**(443 규칙 없음 — nginx가 80만 수신) |
| 보안 그룹(검색) | 22·9200 모두 앱 SG에서만 |
| AMI | Amazon Linux 2023 ARM64 — 패키지 관리자는 `dnf`(Ubuntu `apt` 아님) |

> **80을 전체 개방으로 되돌리지 말 것.** 전체 개방이면 origin IP를 알아낸 누구나 Cloudflare의 WAF·
> 레이트리밋·봇 차단을 건너뛰고 평문 HTTP로 직접 붙을 수 있다(Flexible 모드라 origin 구간은 평문이다).
> `nginx/api.at-crew.com.conf`가 애플리케이션 레벨에서 같은 차단을 하고 보안 그룹이 네트워크 레벨에서
> 한 번 더 막는 이중 방어다 — 둘 중 하나만 남기지 않는다.
>
> Cloudflare 대역은 바뀐다. 대역이 추가됐는데 두 곳 중 한쪽만 갱신하면 그쪽에서 트래픽이 막히므로
> **nginx 설정과 보안 그룹을 항상 함께 갱신한다.** 목록: https://www.cloudflare.com/ips/
>
> 검증 시 참고: 보안 그룹이 네트워크 레벨에서 먼저 끊으므로, Cloudflare 밖에서 origin IP로 직접
> 붙으면 **nginx의 444가 아니라 TCP 타임아웃**이 난다(`curl`의 `connect=0.000000s`). nginx 게이트
> 자체를 확인하려면 서버 안에서 `127.0.0.1`로 Host 헤더를 바꿔 가며 호출한다.

검색 서버는 퍼블릭 IP가 없어 직접 SSH 불가 — 앱 서버를 경유해서 접속한다. 개인키를 서버에 복사해두지
않고 SSH 에이전트 포워딩을 쓴다:
```bash
ssh-add ~/.ssh/<키페어>.pem
ssh -A ec2-user@<EC2 #1 탄력적 IP>
# 접속 후 서버 안에서
ssh ec2-user@<EC2 #2 프라이빗 IP>
```

## 이 디렉토리 파일

- `docker-compose.app.yml` — EC2 #1용
- `docker-compose.search.yml` — EC2 #2용
- `nginx/api.at-crew.com.conf` — EC2 #1에 설치할 nginx 리버스 프록시 설정
- `.env.example` — EC2 #1의 `.env` 템플릿(실제 값은 채워서 `.env`로 저장, git에 커밋 금지)
- `deploy.sh` — 로컬에서 빌드→Docker Hub 푸시→EC2 #1 재배포까지 한 번에

## 자동 배포 (`.github/workflows/deploy.yml`)

main push(=PR 머지) 시 빌드·테스트 → Docker Hub 푸시 → EC2 재기동까지 자동으로 돈다.

보안 그룹의 22번은 특정 IP만 허용하는데 GitHub Actions 러너 IP는 매 실행마다 바뀐다. 그래서 배포 직전
러너 IP만 임시로 열고 끝나면 회수한다(회수 단계는 `if: always()`라 앞 단계가 실패해도 규칙이 남지 않는다).

필요한 저장소 Secrets: `DOCKERHUB_USERNAME`, `DOCKERHUB_TOKEN`, `APP_HOST`, `EC2_SSH_KEY`,
`AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`, `APP_SECURITY_GROUP_ID`.

**TODO(인프라): SSM Session Manager 전환.** 위 방식은 22번을 잠깐이라도 열고 CI에 SSH 키·AWS 키를
함께 두는 구성이다. 정석은 22번을 아예 닫고 SSM으로 접속해 IAM으로 통제하고 접속 이력을 CloudTrail에
남기는 것이다. 인스턴스에 `AmazonSSMManagedInstanceCore` 역할 부착과 워크플로의 `session-manager-plugin`
설치가 선행 조건이며, 결제 sandbox 검증이 끝난 뒤 착수한다.

## 최초 1회 설정

보안 그룹·인스턴스·키페어는 이미 위 표대로 만들어져 있다 — 아래는 그 위에서 소프트웨어만 설치하면 된다.

1. **EC2 #1(앱 서버)**: Amazon Linux 2023이라 `dnf` 사용.
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
   이 레포의 `deploy/` 디렉토리를 EC2에 올리고(`git clone` 또는 `scp`), `.env.example`을 `.env`로
   복사해 값을 채운다. Firebase 서비스 계정 JSON도 별도로 올려 `FIREBASE_CREDENTIALS_PATH`에 지정.

   앱을 배포한 뒤 **`./bootstrap.sh`를 반드시 한 번 실행한다.** 관측 에이전트(Alloy)와 백업 타이머는
   앱 compose에 들어 있지 않아 앱 배포만으로는 설치되지 않는다(아래 "인스턴스를 교체할 때" 참고).
2. **EC2 #2(Elasticsearch)**: 위와 동일하게 Docker 설치(nginx는 불필요) 후
   `docker compose -f docker-compose.search.yml up -d`.
3. **Cloudflare**: `api.at-crew.com` A레코드를 EC2 #1 탄력적 IP로, 프록시(오렌지 클라우드) 켜고
   SSL/TLS 모드는 "Flexible"로 설정(`nginx/api.at-crew.com.conf` 상단 주석 참고). 배포된 Worker의
   `SERVER_CALLBACK_URL` 시크릿도 `https://api.at-crew.com/internal/media/images/processed`를
   가리켜야 한다(`cloudflare-worker/README.md` 참고). 이 값이 틀리면 콜백이 서버에 도착하지 않아
   업로드는 되는데 썸네일이 영영 생기지 않는다 — 2026-08-18~08-27에 임시 tunnel 주소를 가리킨 채로
   방치돼 콜백이 한 건도 도착하지 않았다(이슈 #59). 도메인 오타도 같은 결과를 낳으므로 하이픈까지
   확인할 것.
4. **Docker Hub**: 로컬에서 `docker login`.

## 이후 배포

```bash
DOCKERHUB_USER=<본인 계정> SSH_KEY=~/.ssh/<키페어>.pem APP_HOST=ec2-user@<EC2 #1 탄력적 IP> ./deploy/deploy.sh
```

## 인스턴스를 교체할 때

앱 서버를 새 인스턴스로 옮기면(blue-green 이전, AMI 교체, 리전 이동 등) **컨테이너 밖에서 도는 것들은
따라오지 않는다.** 앱은 `docker-compose.app.yml`로 배포되지만 아래 둘은 각각 별도 compose 파일과
systemd 유닛이다.

| 항목 | 실체 | 빠뜨렸을 때 |
|---|---|---|
| 관측 에이전트 | `docker-compose.observability.yml`(Alloy) | 메트릭·로그 수집이 끊긴다. `[P1] 앱 메트릭 수집 불가`가 계속 울리는데 서비스는 멀쩡한 상태가 된다 |
| DB 백업 | `systemd/atcrew-backup.{service,timer}` | 백업이 조용히 멈춘다. 관측도 함께 죽어 있으면 백업 감시 알람마저 울리지 않는다 |

앱을 새 인스턴스에 올린 직후 **`./bootstrap.sh`를 실행하면 둘 다 설치·기동된다.** 멱등하므로 이미
설치된 호스트에서 다시 돌려도 안전하다.

```bash
cd ~/at-crew-backend/deploy && ./bootstrap.sh
```

이전 완료 체크리스트:

- [ ] `./bootstrap.sh` 실행 — Alloy 기동 + 백업 타이머 등록
- [ ] 1분 뒤 수집 확인 — `curl -s http://127.0.0.1:12345/metrics | grep prometheus_remote_storage_samples_total`이 증가하고 `samples_failed_total`이 0
- [ ] 백업 1회 수동 검증 — `sudo systemctl start atcrew-backup.service` 후 `journalctl -u atcrew-backup.service -n 20`
- [ ] **`APP_HOST` 저장소 Secret을 새 인스턴스 주소로 갱신** — 갱신하지 않으면 다음 main 병합에서
      자동 배포가 옛 주소로 SSH를 시도해 실패한다
- [ ] Grafana에 걸어둔 silence 해제

> 2026-09-02에 이 목록이 없어서 v2 이전 때 관측과 백업이 함께 누락됐다(이슈 #115). 관측 스택을 앱과
> 분리한 것은 "배포가 실패해도 수집은 계속되게" 하려는 의도였는데, 인스턴스를 교체할 때는 그 분리가
> 그대로 누락으로 이어진다.
