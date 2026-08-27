# 장애 대응 런북

> 작성일: 2026-08-26
> 대상: prod(`api.at-crew.com`, EC2 #1). 알람 정의는 `deploy/observability/alerts/rules.json`,
> 설계 배경은 `docs/design/observability-design.md`.

알람이 왔을 때 **무엇을 확인하고 무엇을 실행하는가**만 적는다. 원인 분석 방법론이 아니라 손 순서다.

## 0. 공통 — 접속과 첫 3분

`<APP_HOST>`는 EC2 #1의 퍼블릭 IP다(AWS 콘솔 또는 저장소 Secret `APP_HOST`).
`~/.ssh/config`에 별칭을 만들어 두면 장애 대응 중에 자리표시자를 치환할 필요가 없다.

```
Host atcrew-prod
  HostName <APP_HOST>
  User ec2-user
  IdentityFile ~/.ssh/<키페어>.pem
```

```bash
ssh atcrew-prod
cd ~/at-crew-backend/deploy
docker-compose -f docker-compose.app.yml ps          # 컨테이너 상태
docker-compose -f docker-compose.app.yml logs --tail=100 app
curl -s http://127.0.0.1:8081/actuator/health/readiness | jq .   # 의존성별 상태
df -h /                                               # 디스크
```

관측 링크
- 대시보드: `<GRAFANA_URL>/dashboards/f/at-crew`
- 알람 상태: `<GRAFANA_URL>/alerting/list`
- 로그 조회: Explore → `grafanacloud-atcrew-logs` → `{service_name="app"} |= "<requestId>"`
- 에러 상세: Sentry 프로젝트 `at-crew-backend`

> `<APP_HOST>`·`<키페어>`·`<GRAFANA_URL>`은 실제 값을 적지 않는다(공개 저장소). 각각 저장소 Secret
> `APP_HOST`, 로컬 `~/.ssh/`의 키 파일, Secret `GRAFANA_URL`을 참조한다.

---

## P1

### [P1] API 전면 다운 (외부 프로브 실패)

사용자가 서비스를 못 쓰는 상태다. 위에서 아래로 좁힌다.

1. 앱이 죽었는가 — `docker-compose -f docker-compose.app.yml ps`. `Exit`이면 §크래시 루프로.
2. 앱은 살아 있는데 응답이 없는가 — `curl -v http://127.0.0.1:8080/api/community/banners`.
   200이면 앱은 정상이고 그 앞단(nginx·Cloudflare) 문제다.
3. nginx — `sudo systemctl status nginx`, `sudo nginx -t`, `sudo tail -50 /var/log/nginx/error.log`.
4. Cloudflare — 대시보드에서 `api.at-crew.com` A레코드와 SSL/TLS 모드가 **Flexible**인지 확인.
   과거 이 값이 바뀌어 521(Web Server Is Down)이 난 적이 있다(2026-08-10, 2026-08-13).
   권한이 없으면 root에게 요청해야 한다.
5. 직전 배포가 원인으로 의심되면 §배포 실패·롤백으로.

### [P1] 앱 컨테이너 크래시 루프

`restart: always` 때문에 죽어도 계속 살아나 겉으로는 도는 것처럼 보인다.

```bash
docker-compose -f docker-compose.app.yml logs --tail=200 app | grep -iE "error|exception|failed"
```

가장 흔한 원인 두 가지.
- **Flyway 검증 실패** — 마이그레이션 순서·번호 충돌(2026-08-19 실제 발생). 로그에 `Validate failed`.
  구버전 이미지로 되돌려도 스키마는 되돌아가지 않으므로 **롤백으로 해결되지 않는다.** 앞으로 굴러가는
  수정(누락 마이그레이션 추가 또는 수동 SQL 보정)이 필요하다.
- **필수 환경변수 누락** — `.env`에 값이 없으면 기동 시점에 실패한다(fail-fast 설계). 로그에
  `Could not resolve placeholder` 형태로 나온다. `.env`에 키를 채우고 `up -d`.

### [P1] 루트 디스크 사용률 85% 초과

```bash
df -h /
docker system df                  # 회수 가능량 확인
docker image prune -af            # 실행 중 이미지 외 전부 삭제
```

배포 파이프라인이 매 배포 후 자동으로 prune하지만, 수동 배포나 실패한 배포가 쌓이면 다시 찬다.
정리 후에도 여유가 4GB 미만이면 EBS 볼륨을 8GB → 30GB로 확장한다(프리 티어 한도 내).

```bash
# 콘솔에서 볼륨 크기 변경 후 인스턴스에서
sudo growpart /dev/nvme0n1 1 && sudo xfs_growfs /
```

### [P1] 앱 메트릭 수집 불가

Alloy가 8081을 스크레이프하지 못하는 상태. 앱이 죽었거나(위 항목), Alloy가 죽었거나 둘 중 하나다.

```bash
docker ps --filter "label=com.docker.compose.service=alloy"
docker-compose -f docker-compose.observability.yml logs --tail=50 alloy
curl -s http://127.0.0.1:12345/metrics | grep prometheus_remote_storage_samples_failed_total
```

Alloy만 죽었다면 사용자 영향은 없지만 **다른 모든 알람이 함께 눈이 먼 상태**이므로 즉시 되살린다.
`docker-compose -f docker-compose.observability.yml up -d`

### [P1] DB 백업 26시간 미실행

```bash
journalctl -u atcrew-backup.service --since "-3 days"   # 실행 이력과 실패 지점
systemctl list-timers atcrew-backup.timer               # 다음 실행 예정 시각
sudo systemctl start atcrew-backup.service              # 즉시 수동 실행
cat /var/lib/node_exporter/textfile_collector/backup.prom
```

흔한 원인: R2 토큰 만료·권한 변경, 디스크 부족으로 덤프 생성 실패, mariadb 컨테이너 이름 변경.

---

## P2

| 알람 | 첫 확인 | 판단 기준 |
|---|---|---|
| 5xx 증가 | Sentry에서 해당 시각 이슈 → `requestId`로 Loki 조회 | 특정 엔드포인트 집중이면 그 기능 문제, 전방위면 DB·의존성 |
| p95 지연 2초 초과 | 대시보드 "요청량/지연" → 어떤 엔드포인트인지 | 검색·이미지 업로드가 흔한 원인. DB 커넥션 pending도 함께 본다 |
| 메일 발송 실패 | Resend 대시보드, `.env`의 `RESEND_API_KEY` 유효성 | 비밀번호 재설정이 막히므로 사용자 문의로 바로 이어진다 |
| 이미지 후처리 실패 | Cloudflare Worker 로그, `media_assets` 테이블의 FAILED 행 | 업로드는 됐는데 썸네일이 없는 상태 |
| 미완료 이벤트 누적 | `SELECT COUNT(*), EVENT_TYPE FROM EVENT_PUBLICATION WHERE COMPLETION_DATE IS NULL GROUP BY EVENT_TYPE` | 특정 타입만 쌓이면 그 리스너가 예외를 던지고 있다 |
| 구독 결제 실패 | Stripe 대시보드 → 해당 고객 | 여러 건이 몰리면 카드 문제가 아니라 연동 문제 |

---

## 배포 실패·롤백

배포 워크플로는 재기동 후 liveness를 최대 3분 기다리고, 실패하면 이번 배포에 **새 Flyway
마이그레이션이 있었는지**로 갈라진다.

- **마이그레이션 없음** → 직전 이미지로 자동 롤백하고 재검증한다. Discord에 "자동 롤백 완료"가 온다.
  이때 서비스는 복구된 상태이므로 원인 분석은 업무시간에 해도 된다.
- **마이그레이션 있음** → 자동 롤백하지 않고 P1만 울린다. 스키마가 전진한 뒤 구버전 앱은
  `ddl-auto: validate`에서 다시 죽기 때문이다. 사람이 판단한다.

수동 롤백이 필요하면(이미지는 서버에 남아 있지 않으므로 레지스트리에서 받는다):

```bash
cd ~/at-crew-backend/deploy
APP_IMAGE=<dockerhub계정>/at-crew-backend:<이전SHA> docker-compose -f docker-compose.app.yml pull app
APP_IMAGE=<dockerhub계정>/at-crew-backend:<이전SHA> docker-compose -f docker-compose.app.yml up -d
curl -s http://127.0.0.1:8081/actuator/health/liveness
```

이전 SHA는 GitHub Actions의 직전 성공 배포 로그 또는 Docker Hub 태그 목록에서 찾는다.

---

## DB 복원

백업은 매일 R2 `at-crew-backups/db-backups/atcrew-<UTC타임스탬프>.sql.gz`에 올라간다.

```bash
cd ~/at-crew-backend/deploy
set -a; PW=$(sed -n 's/^MARIADB_ROOT_PASSWORD=//p' .env | tail -1); set +a
export AWS_ACCESS_KEY_ID=$(sed -n 's/^R2_ACCESS_KEY=//p' .env | tail -1)
export AWS_SECRET_ACCESS_KEY=$(sed -n 's/^R2_SECRET_KEY=//p' .env | tail -1)
export AWS_DEFAULT_REGION=auto
ENDPOINT=$(sed -n 's/^R2_ENDPOINT=//p' .env | tail -1)

# 1) 목록 확인 후 원하는 시점 파일 받기
aws s3 ls s3://at-crew-backups/db-backups/ --endpoint-url "$ENDPOINT"
aws s3 cp s3://at-crew-backups/db-backups/<파일명> /tmp/restore.sql.gz --endpoint-url "$ENDPOINT"

# 2) 앱을 멈춘 상태에서 복원한다 — 복원 중 쓰기가 들어오면 정합성이 깨진다
docker-compose -f docker-compose.app.yml stop app
gunzip -c /tmp/restore.sql.gz | docker exec -i -e MYSQL_PWD="$PW" deploy-mariadb-1 mariadb -u root atcrew
docker-compose -f docker-compose.app.yml start app
curl -s http://127.0.0.1:8081/actuator/health/liveness
```

**소요 시간: (PH-09 리허설에서 실측 후 기입)**

주의: 복원은 해당 시점 이후 데이터를 잃는다. 실행 전에 현재 DB를 먼저 덤프해 둔다
(`deploy/backup.sh` 수동 실행).

---

## 알람이 시끄러울 때

정의는 `deploy/observability/alerts/rules.json`이 정본이다. UI에서 고치면 다음 프로비저닝 실행 때
덮어써진다. 임계값을 바꾸려면 그 파일을 고치고 main에 머지하거나 Actions에서
`Observability provisioning` 워크플로를 수동 실행한다.

긴급하게 특정 알람만 잠시 끄려면 Grafana UI에서 해당 룰을 pause하고, **같은 날 안에** 파일에도
반영한다 — 파일과 실제가 갈라진 채로 두면 다음 배포에서 조용히 되살아난다.
