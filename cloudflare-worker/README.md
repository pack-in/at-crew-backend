# at-crew-media-worker

`media` 모듈(서버)이 트리거하는 이미지 후처리(원본 avif 변환, 3:4 썸네일 크롭, 성인물 블러)를 수행하는
Cloudflare Worker. 계약은 `docs/design/media-module-design.md` §6~7 참고.

- 트리거: 서버 → `POST <이 Worker URL>` (헤더 `X-Callback-Secret`, 바디 `{ownerType, ownerId, imageKeys, variantProfile}`)
- 콜백: Worker → `POST {SERVER_CALLBACK_URL}` (헤더 `X-Internal-Secret`, 바디 `{ownerType, ownerId, imageKey, thumbKey, thumbAdultKey, originalAvifKey, status}`)

## 최초 1회 설정

```bash
cd cloudflare-worker
npm install
npx wrangler login                      # 브라우저에서 Cloudflare 로그인
npx wrangler r2 bucket create at-crew-storage   # 버킷이 이미 있으면 생략
```

시크릿 등록 (값은 서버 `.env`의 `WORKER_CALLBACK_SECRET`/`ARTWORK_INTERNAL_SECRET`과 동일해야 함):

```bash
npx wrangler secret put CALLBACK_SECRET
npx wrangler secret put INTERNAL_SECRET
npx wrangler secret put SERVER_CALLBACK_URL   # 예: https://api.atcrew.com/internal/media/images/processed
```

로컬 개발용 시크릿은 `.dev.vars.example`을 `.dev.vars`로 복사해서 채운다(git 추적 안 됨).

## 로컬 실행

```bash
npm run dev            # --remote를 안 쓰면 blur/fit/gravity가 로컬 근사치로만 동작함(공식 문서 명시)
npx wrangler dev --remote   # 실제 Cloudflare Images 변환 결과를 그대로 확인하려면 이 옵션 필요
```

## 배포

```bash
npm run deploy
```

배포 후 출력되는 `https://at-crew-media-worker.<subdomain>.workers.dev` 를 서버 `WORKER_TRIGGER_URL` 환경변수에 넣는다.

## 로그 확인

```bash
npm run tail
```
