# 배포 (prod, self-hosted EC2)

`docs/design/mariadb-migration-design.md` §10-1에서 확정한 구성. AWS 계정은 laiteu와 동일(`sehandev`
소유). 상세 배경·비용 판단은 `docs/NEXT_STEPS.md` 2026-08-07 항목 참고.

## 인프라 레이아웃

| EC2 | 구성 | 비고 |
|---|---|---|
| #1 앱 서버 | `app`(스프링부트) + `mariadb` 컨테이너, `docker-compose.app.yml` | 퍼블릭 IP 있음, nginx가 443/80 처리 |
| #2 Elasticsearch | `elasticsearch` 컨테이너, `docker-compose.search.yml` | 퍼블릭 IP 없음(비용·보안), 보안 그룹으로 #1만 9200 접근 허용 |

도메인은 `api.at-crew.com` — Cloudflare DNS에서 #1의 탄력적 IP로 A레코드 연결.

## 프로비저닝된 리소스 (2026-08-07, ap-northeast-2, VPC `vpc-9f11ccf4` — laiteu와 동일 기본 VPC)

| 항목 | 값 |
|---|---|
| EC2 #1(앱 서버) | `i-0987d8df61c4b84d3`, 탄력적 IP `43.201.142.212` |
| EC2 #2(Elasticsearch) | `i-07b421fdc2d3f5aff`, 프라이빗 IP `172.31.25.215`(퍼블릭 IP 없음) |
| 키페어 | `at-crew-key` — 로컬 `~/.ssh/at-crew-key.pem`(git 추적 안 됨, 최초 생성 시 한 번만 다운로드됨) |
| 보안 그룹 | 앱 `sg-062af5fc0a1f5af5c`(22 본인IP/80·443 전체), 검색 `sg-017f063fc146bdaf6`(22는 앱 SG에서만, 9200도 앱 SG에서만) |
| AMI | Amazon Linux 2023 ARM64(`ami-0ab38814a224c51c1`) — 패키지 관리자는 `dnf`(Ubuntu `apt` 아님) |

검색 서버는 퍼블릭 IP가 없어 직접 SSH 불가 — 앱 서버를 경유해서 접속한다. 개인키를 서버에 복사해두지
않고 SSH 에이전트 포워딩을 쓴다:
```bash
ssh-add ~/.ssh/at-crew-key.pem
ssh -A ec2-user@43.201.142.212
# 접속 후 서버 안에서
ssh ec2-user@172.31.25.215
```

## 이 디렉토리 파일

- `docker-compose.app.yml` — EC2 #1용
- `docker-compose.search.yml` — EC2 #2용
- `nginx/api.at-crew.com.conf` — EC2 #1에 설치할 nginx 리버스 프록시 설정
- `.env.example` — EC2 #1의 `.env` 템플릿(실제 값은 채워서 `.env`로 저장, git에 커밋 금지)
- `deploy.sh` — 로컬에서 빌드→Docker Hub 푸시→EC2 #1 재배포까지 한 번에

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
   이 레포의 `deploy/` 디렉토리를 EC2에 올리고(`git clone` 또는 `scp`), `.env.example`을 `.env`로
   복사해 값을 채운다. Firebase 서비스 계정 JSON도 별도로 올려 `FIREBASE_CREDENTIALS_PATH`에 지정.
2. **EC2 #2(Elasticsearch)**: 위와 동일하게 Docker 설치(nginx는 불필요) 후
   `docker compose -f docker-compose.search.yml up -d`.
3. **Cloudflare**: `api.at-crew.com` A레코드를 EC2 #1 탄력적 IP로, 프록시(오렌지 클라우드) 켜고
   SSL/TLS 모드는 "Flexible"로 설정(`nginx/api.at-crew.com.conf` 상단 주석 참고). 배포된 Worker의
   `SERVER_CALLBACK_URL` 시크릿도 `https://api.at-crew.com/internal/media/images/processed`로
   재등록(`cloudflare-worker/README.md` 참고, 지금은 임시 tunnel 주소를 가리키고 있음).
4. **Docker Hub**: 로컬에서 `docker login`.

## 이후 배포

```bash
DOCKERHUB_USER=<본인 계정> SSH_KEY=~/.ssh/at-crew-key.pem APP_HOST=ec2-user@43.201.142.212 ./deploy/deploy.sh
```
