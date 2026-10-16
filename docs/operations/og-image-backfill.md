# OG 이미지 백필 절차

OG 이미지 기능([og-image-design.md](../design/og-image-design.md))을 배포하기 **전에** 올라온 작품은 OG가 없다.
이 절차는 그 작품들의 OG를 한 번에 만들어 채운다. 다시 실행해도 안전하다 — 이미 채운 행은 건너뛴다.

두 단계다.

1. **media 백필** — 지정 썸네일 자산마다 Worker에 OG 생성을 시킨다. 결과는 비동기 콜백으로 붙는다.
2. **고정형 스냅샷 백필** — 1단계 결과를 기존 고정형 포트폴리오의 스냅샷 행에 얼린다.

## 0. 선행 조건

- 서버와 Worker가 **둘 다** OG 기능 버전으로 배포됐다. Worker가 구버전이면 1단계 트리거를 400(`sourceUrls must match imageKeys`)으로
  거절한다 — 서버는 `OG Worker 트리거 실패` 로그만 남기고 데이터는 바뀌지 않지만 헛수고다. `npx wrangler deployments list`(팀 계정)로 최근 배포 시각을 확인한다.
- Cloudflare 팀 계정의 Images 요금제를 확인했다. 무료면 월 5,000건을 넘는 새 변환이 실패하는데, 백필은 지정 썸네일 수 × 2건을 쓴다.
  대상 건수는 아래 1단계를 `limit=1`로 한 번 부르면 `remaining`으로 나온다.

## 1. 접속과 시크릿

앱 서버는 SSM으로만 들어간다([incident-runbook.md](incident-runbook.md) §접속). SSM은 root로 실행되므로 경로는
`/home/ec2-user/...`로 직접 쓰고, 앱 포트 curl에는 `-4`를 붙인다(IPv6를 먼저 시도하면 간헐적으로 `HTTP 000`).

```bash
deploy/ssm-run.sh <<'EOF'
cd /home/ec2-user/at-crew-backend/deploy
SECRET=$(sed -n 's/^ARTWORK_INTERNAL_SECRET=//p' .env | tail -1)
curl -4 -s -X POST -H "X-Internal-Secret: $SECRET" "http://127.0.0.1:8080/internal/media/og/backfill?limit=1&afterId=0"
EOF
```

두 API 모두 `ARTWORK_INTERNAL_SECRET`을 쓴다(Worker 콜백과 같은 시크릿).

## 2. 1단계 — media 백필

`POST /internal/media/og/backfill?limit=<1~1000>&afterId=<마지막 id>` → `{queued, remaining, lastId}`

1. `limit=1&afterId=0`으로 한 번 부른다. 1분 뒤 그 자산의 OG가 생겼는지 확인한다.
   ```sql
   SELECT id, owner_id, og_key, og_adult_key FROM media_assets
   WHERE owner_type = 'ARTWORK_THUMBNAIL' ORDER BY id LIMIT 1;
   ```
   `https://asset.at-crew.com/<og_key>`가 1200×630 JPEG로 열리면 정상이다.
2. 응답의 `lastId`를 다음 호출의 `afterId`로 넘기며 `remaining`이 0이 될 때까지 반복한다(`limit=500` 권장).
   `afterId`로 이어 읽는 이유: 콜백은 비동기라 직후에 처음부터 다시 부르면 아직 OG가 비어 있는 행을 또 보낸다.
3. 1분 기다린 뒤 **`afterId=0`으로 한 번 더** 부른다. 이때 `queued`가 실패분이다(재시도된다).
   같은 수가 계속 남으면 Worker 로그(`npx wrangler tail`)에서 `OG 생성 실패`를 찾는다.

## 3. 2단계 — 고정형 스냅샷 백필

`POST /internal/portfolio/og/backfill-snapshots?limit=<1~1000>&afterId=<마지막 id>` → `{filled, unresolved, lastId, hasMore}`

1. 1단계가 끝나고 1분 뒤에 부른다. `hasMore`가 false가 될 때까지 `afterId=lastId`로 반복한다.
2. `afterId=0`으로 한 번 더 부른다. `unresolved`가 직전과 같으면 끝이다.
   남은 `unresolved`는 채울 수 없는 행이다 — 스냅샷 생성 뒤 썸네일을 교체해 자산 행이 없거나, 지정 썸네일이 없는 예전 작품이다.
   이 스냅샷은 기본 이미지로 나간다(설계 문서 §4 알려진 제약).

## 4. 실행 기록

| 일시 | 1단계 대상(remaining 최초) | 1단계 실패(재실행 queued) | 2단계 filled | 2단계 unresolved | 실행자 |
|---|---|---|---|---|---|
| 2026-10-16 (서버 c78c22f, Worker 347842a0 배포 직후) | 22 | 0 | 0 | 0 (스냅샷 행 0건) | danhandev(에이전트 실행) |
