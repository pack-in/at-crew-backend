# 배포 (prod, self-hosted EC2)

`docs/design/mariadb-migration-design.md` §10-1에서 확정한 구성. AWS 계정은 laiteu와 동일(`sehandev`
소유). 상세 배경·비용 판단은 `docs/NEXT_STEPS.md` 2026-08-07 항목 참고.

## 인프라 레이아웃

| EC2 | 구성 | 비고 |
|---|---|---|
| #1 앱 서버 | `app`(스프링부트) + `mariadb` 컨테이너, `docker-compose.app.yml` | 퍼블릭 IP 있음, nginx가 443/80 처리 |
| #2 Elasticsearch | `elasticsearch` 컨테이너, `docker-compose.search.yml` | 퍼블릭 IP 없음(비용·보안), 보안 그룹으로 #1만 9200 접근 허용 |

도메인은 `api.at-crew.com` — Cloudflare DNS에서 #1의 탄력적 IP로 A레코드 연결.

## 이 디렉토리 파일

- `docker-compose.app.yml` — EC2 #1용
- `docker-compose.search.yml` — EC2 #2용
- `nginx/api.at-crew.com.conf` — EC2 #1에 설치할 nginx 리버스 프록시 설정
- `.env.example` — EC2 #1의 `.env` 템플릿(실제 값은 채워서 `.env`로 저장, git에 커밋 금지)
- `deploy.sh` — 로컬에서 빌드→Docker Hub 푸시→EC2 #1 재배포까지 한 번에

## 최초 1회 설정

1. **EC2 #1(앱 서버)**: 보안 그룹에 22(SSH, 본인 IP만)/80·443(nginx) 열기. nginx 설치
   (`apt install nginx`), `nginx/api.at-crew.com.conf`를 `/etc/nginx/sites-available/`에 올리고
   `sites-enabled`에 심볼릭 링크, `nginx -t && systemctl reload nginx`. Docker/Docker Compose 설치.
   이 레포의 `deploy/` 디렉토리를 EC2에 올리고(`git clone` 또는 `scp`), `.env.example`을 `.env`로
   복사해 값을 채운다. Firebase 서비스 계정 JSON도 별도로 올려 `FIREBASE_CREDENTIALS_PATH`에 지정.
2. **EC2 #2(Elasticsearch)**: 보안 그룹에 9200 포트를 **EC2 #1의 보안 그룹만** 허용하도록 열기(전체
   공개 금지). 퍼블릭 IP는 붙이지 않는다. Docker 설치 후
   `docker compose -f docker-compose.search.yml up -d`.
3. **Cloudflare**: `api.at-crew.com` A레코드를 EC2 #1 탄력적 IP로, 프록시(오렌지 클라우드) 켜고
   SSL/TLS 모드는 "Flexible"로 설정(`nginx/api.at-crew.com.conf` 상단 주석 참고). 배포된 Worker의
   `SERVER_CALLBACK_URL` 시크릿도 `https://api.at-crew.com/internal/media/images/processed`로
   재등록(`cloudflare-worker/README.md` 참고, 지금은 임시 tunnel 주소를 가리키고 있음).
4. **Docker Hub**: 로컬에서 `docker login`.

## 이후 배포

```bash
DOCKERHUB_USER=<본인 계정> SSH_KEY=<pem 경로> APP_HOST=ec2-user@<EC2 #1 IP> ./deploy/deploy.sh
```
