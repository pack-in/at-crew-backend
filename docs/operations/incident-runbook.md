# 장애 대응 런북

> 작성일: 2026-08-26
> 대상: prod(`api.at-crew.com`, 앱 서버). 알람 정의는 `deploy/observability/alerts/rules.json`,
> 설계 배경은 `docs/design/observability-design.md`.

알람이 왔을 때 **무엇을 확인하고 무엇을 실행하는가**만 적는다. 원인 분석 방법론이 아니라 손 순서다.

## 0. 공통 — 접속과 첫 3분

**접속은 SSM이다. SSH는 닿지 않는다.** 앱 서버는 프라이빗 서브넷에 있어 퍼블릭 IP가 없다(#110).
SSH가 안 된다고 "접근할 수 없다"고 판단하고 멈추지 말 것 — 2026-09-02에 실제로 그렇게 판단해 복구가
지연됐다. 인스턴스 ID는 저장소 Secret `APP_INSTANCE_ID`에 있다.

```bash
aws ssm describe-instance-information \
  --query 'InstanceInformationList[].[InstanceId,PingStatus]' --output text   # Online이면 접속 가능
aws ssm start-session --target <인스턴스 ID>            # session-manager-plugin 필요
```

플러그인 없이 명령만 돌릴 때는 `send-command`를 쓴다. 출력은 `get-command-invocation`으로 받는다.

```bash
CMD=$(aws ssm send-command --instance-ids <인스턴스 ID> --document-name AWS-RunShellScript \
  --parameters 'commands=["docker ps"]' --query Command.CommandId --output text)
aws ssm get-command-invocation --command-id "$CMD" --instance-id <인스턴스 ID> \
  --query '[Status,StandardOutputContent]' --output text
```

첫 3분에 확인할 것 — 레포의 `deploy/ssm-run.sh`로 한 번에 보낸다.

```bash
export APP_INSTANCE_ID=<인스턴스 ID>
deploy/ssm-run.sh <<'EOF'
docker ps --format '{{.Names}}\t{{.Status}}'
CID=$(docker ps -a --filter 'label=com.docker.compose.service=app' --format '{{.ID}}' | head -1)
docker logs --tail=100 "$CID" 2>&1
curl -s http://127.0.0.1:8081/actuator/health/readiness
df -h /
EOF
```

관측 링크
- 대시보드: `<GRAFANA_URL>/dashboards/f/at-crew`
- 알람 상태: `<GRAFANA_URL>/alerting/list`
- 로그 조회: Explore → `grafanacloud-atcrew-logs` → `{service_name="app"} |= "<requestId>"`
- 에러 상세: Sentry 프로젝트 `at-crew-backend`

> `<인스턴스 ID>`·`<GRAFANA_URL>`은 실제 값을 적지 않는다(공개 저장소). 각각 저장소 Secret
> `APP_INSTANCE_ID`, `GRAFANA_URL`을 참조한다.

---

## P1

### [P1] API 전면 다운 (외부 프로브 실패)

사용자가 서비스를 못 쓰는 상태다. 위에서 아래로 좁힌다.

1. 앱이 죽었는가 — `docker-compose -f docker-compose.app.yml ps`. `Exit`이면 §크래시 루프로.
2. 앱은 살아 있는데 응답이 없는가 — `curl -v http://127.0.0.1:8080/api/community/banners`.
   200이면 앱은 정상이고 그 앞단(nginx·Cloudflare) 문제다.
3. nginx — `sudo systemctl status nginx`, `sudo nginx -t`, `sudo tail -50 /var/log/nginx/error.log`.
4. Cloudflare Tunnel — 유입 경로가 터널이므로 `cloudflared`가 죽으면 origin이 통째로 사라진다.
   `systemctl status cloudflared`, `sudo journalctl -u cloudflared -n 50`. 대시보드의 터널 상태와
   ingress 규칙(`api.at-crew.com` → 로컬 nginx)도 함께 확인한다. 권한이 없으면 root에게 요청해야 한다.
   과거 SSL/TLS 모드가 바뀌어 521(Web Server Is Down)이 난 적이 있다(2026-08-10, 2026-08-13).
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

컨테이너가 죽은 게 아니라 **애초에 없다면 인스턴스 교체 때 누락된 것이다**(2026-09-02 v2 이전에서
실제 발생, 이슈 #115). 이 경우 백업 타이머도 함께 빠져 있을 가능성이 높으므로 둘을 한 번에 세운다.

```bash
cd ~/at-crew-backend/deploy && ./bootstrap.sh
```

`[P1] API 전면 다운`은 조용한데 이 알람만 울린다면 **서비스는 살아 있고 수집만 끊긴 것이다.** 외부
프로브는 서비스에 직접 붙고 이 알람은 Alloy 파이프라인에 의존하기 때문에, 이 조합 자체가 판정 근거다.

### [P1] DB 백업 26시간 미실행

```bash
journalctl -u atcrew-backup.service --since "-3 days"   # 실행 이력과 실패 지점
systemctl list-timers atcrew-backup.timer               # 다음 실행 예정 시각
sudo systemctl start atcrew-backup.service              # 즉시 수동 실행
cat /var/lib/node_exporter/textfile_collector/backup.prom
```

흔한 원인: R2 토큰 만료·권한 변경, 디스크 부족으로 덤프 생성 실패, mariadb 컨테이너 이름 변경.

`list-timers`에 `atcrew-backup.timer`가 **아예 없으면** 타이머가 설치되지 않은 것이다(인스턴스 교체
때 누락). `./bootstrap.sh`로 설치한다 — 백업이 한 번도 돈 적 없는 상태이므로 설치 후 수동 실행까지
해서 실제로 R2에 올라가는지 확인한다.

`.env`에 `R2_BACKUP_BUCKET`·`R2_BACKUP_ACCESS_KEY`·`R2_BACKUP_SECRET_KEY`가 없으면 스크립트가 즉시
멈춘다(기본값·폴백을 두지 않는다 — 예전에는 없는 버킷이나 권한 없는 키로 떨어져 백업이 조용히
실패했다).

### [P1] DB Replica 복제 스레드 중단

`docs/design/infra-security-hardening-design.md` D6/D7 참고. `atcrew-replica-down` 알람.

```bash
docker exec mariadb-replica mariadb -u root -h "$REPLICA_HOST" -e "SHOW SLAVE STATUS\G"
```

`Slave_IO_Running`/`Slave_SQL_Running` 중 어느 쪽이 `No`인지 먼저 본다.

- **IO_Running=No** — 네트워크 단절이나 Primary 쪽 문제일 가능성. Primary가 살아있는지부터
  확인(`atcrew-app-down` 등 다른 알람이 같이 울렸는지). `Last_IO_Error` 확인.
- **SQL_Running=No** — 복제 스트림 자체는 오는데 적용에 실패(중복 키 등). `Last_SQL_Error` 확인,
  단순 재시도로 되는 경우(`STOP SLAVE; START SLAVE;`)와 수동 개입이 필요한 경우를 구분한다.

**Primary가 완전히 죽어서 승격이 필요하면 "DB Replica 승격" 섹션으로.** 승격은 이 알람만 보고
바로 하지 않는다 — split-brain 리스크(설계 문서 D7 참고).

---

## P2

| 알람 | 첫 확인 | 판단 기준 |
|---|---|---|
| 5xx 증가 | Sentry에서 해당 시각 이슈 → `requestId`로 Loki 조회 | 특정 엔드포인트 집중이면 그 기능 문제, 전방위면 DB·의존성 |
| p95 지연 2초 초과 | 대시보드 "요청량/지연" → 어떤 엔드포인트인지 | 검색·이미지 업로드가 흔한 원인. DB 커넥션 pending도 함께 본다 |
| 메일 발송 실패 | Resend 대시보드, `.env`의 `RESEND_API_KEY` 유효성 | 비밀번호 재설정이 막히므로 사용자 문의로 바로 이어진다 |
| 이미지 후처리 실패 | Cloudflare Worker 로그, `media_assets` 테이블의 FAILED 행 | 업로드는 됐는데 썸네일이 없는 상태 |
| 이미지 후처리 결과 미도착 | Worker 시크릿 `SERVER_CALLBACK_URL`이 `https://api.at-crew.com/internal/media/images/processed`인지 → nginx 액세스 로그에 그 경로 요청이 찍히는지 | 콜백이 실패로 오는 게 아니라 아예 안 오는 상태. 2026-08 실제 사고(이슈 #59)와 같은 유형 |
| 미완료 이벤트 누적 | `SELECT COUNT(*), EVENT_TYPE FROM EVENT_PUBLICATION WHERE COMPLETION_DATE IS NULL GROUP BY EVENT_TYPE` | 특정 타입만 쌓이면 그 리스너가 예외를 던지고 있다 |
| 구독 결제 실패 | Stripe 대시보드 → 해당 고객 | 여러 건이 몰리면 카드 문제가 아니라 연동 문제 |
| DB Replica 복제 지연 60초 초과 | `docker exec mariadb-replica mariadb -u root -h "$REPLICA_HOST" -e "SHOW SLAVE STATUS\G"`로 `Seconds_Behind_Master` 확인 | Primary 쓰기 폭주나 Replica 리소스 부족이 흔한 원인. 지연이 계속 늘기만 하면 승격 시 유실 범위가 커진다 |

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

**소요 시간: 약 25초** (2026-08-27 리허설 실측, 덤프 10KB 기준)

| 단계 | 시간 |
|---|---|
| R2에서 다운로드 | 1초 |
| MariaDB 기동(리허설은 임시 컨테이너) | 20초 |
| 복원 | 1초 |
| 검증 쿼리 | 1초 미만 |

실제 장애 시에는 MariaDB가 이미 떠 있으므로 **앱 정지 → 복원 → 앱 기동까지 1분 내외**로 보면 된다.
다만 이 수치는 데이터가 거의 없는 초기 상태의 것이다 — 데이터가 늘면 복원 시간은 덤프 크기에 비례해
늘어난다. 백업 파일 크기는 `atcrew_backup_size_bytes` 지표로 추적되므로 대시보드에서 추세를 볼 수 있다.

### 리허설 절차 (분기마다 한 번)

복원은 해본 적 없으면 신뢰할 수 없다. **prod DB를 건드리지 않고** 임시 컨테이너에 복원해 확인한다.

```bash
docker run -d --name restore-drill -e MARIADB_ROOT_PASSWORD=drill -e MARIADB_DATABASE=atcrew mariadb:11.4
until docker exec restore-drill healthcheck.sh --connect --innodb_initialized; do sleep 2; done
gunzip -c /tmp/restore.sql.gz | docker exec -i -e MYSQL_PWD=drill restore-drill mariadb -u root atcrew

# 스키마가 prod와 같은 지점까지 복원됐는지 확인 — version은 문자열이라 정렬이 아니라 적용 순서로 본다
docker exec -e MYSQL_PWD=drill restore-drill mariadb -u root -N -B atcrew \
  -e "SELECT version, description FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 3;"

docker rm -f restore-drill
```

2026-08-27 리허설에서는 복원본과 prod가 모두 V33까지 일치했고 테이블 57개가 복구됐다.

주의: 복원은 해당 시점 이후 데이터를 잃는다. 실행 전에 현재 DB를 먼저 덤프해 둔다
(`deploy/backup.sh` 수동 실행).

---

## DB Replica 승격

`docs/design/infra-security-hardening-design.md` D7 — 자동 페일오버는 하지 않는다. Primary 장애가
확인되면(위 "[P1] DB Replica 복제 스레드 중단" 절차로 오탐이 아님을 먼저 확인) 사람이 직접 실행한다.

```bash
cd ~/at-crew-backend/deploy
REPLICA_HOST=<EC2 #2 프라이빗 IP> ./db-promote.sh
```

스크립트가 하는 일: Replica의 복제 스레드 정지·`read_only` 해제 → 앱 `.env`의 `MARIADB_HOST`를
Replica로 전환 → 앱 컨테이너 재기동(수 초~수십 초 다운타임) → liveness 확인.

Route53 Private Hosted Zone(PA-10)이 IAM 권한 부족으로 아직 비활성이라(`deploy/terraform/README.md`
참고) 지금은 `.env` 전환 방식이다 — 권한이 열리면 DNS 레코드만 바꾸는 방식으로 교체해 앱 재기동
없이 승격할 수 있다(원래 설계 의도, D8).

승격 후 구 Primary는 원인 파악 전까지 그대로 둔다(전원을 끄지 않는다 — 로그·상태가 사고 조사에
필요할 수 있다). 원인이 해소되면 `deploy/replica-setup.sh`로 구 Primary를 새 Replica로 재구성한다.

### 드릴 절차 (분기마다 한 번)

복원 리허설과 마찬가지로, 승격도 해본 적 없으면 실제 장애 때 신뢰할 수 없다. 트래픽이 적은 시간대에
**실제로 한 번 승격**하고 RTO를 측정한다(plans/260901-infra-upgrade/PLAN-HUMAN.md PH-09).

1. 승격 직전 `Seconds_Behind_Master`가 0인지 확인
2. `db-promote.sh` 실행, 각 단계 소요 시간 기록
3. 승격된 DB로 로그인·조회 등 핵심 플로우 스모크 테스트
4. 문제 없으면 그대로 유지하거나, `replica-setup.sh`로 원래 역할로 되돌린다
5. 실측 RTO를 이 문서에 갱신(DB 복원 절의 "소요 시간" 표와 같은 형식)

---

## 알람이 시끄러울 때

정의는 `deploy/observability/alerts/rules.json`이 정본이다. UI에서 고치면 다음 프로비저닝 실행 때
덮어써진다. 임계값을 바꾸려면 그 파일을 고치고 main에 머지하거나 Actions에서
`Observability provisioning` 워크플로를 수동 실행한다.

긴급하게 특정 알람만 잠시 끄려면 Grafana UI에서 해당 룰을 pause하고, **같은 날 안에** 파일에도
반영한다 — 파일과 실제가 갈라진 채로 두면 다음 배포에서 조용히 되살아난다.
