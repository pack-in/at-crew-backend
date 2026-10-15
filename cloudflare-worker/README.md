# at-crew-media-worker

`media` 모듈(서버)이 트리거하는 이미지 후처리(원본 avif 변환, 3:4 썸네일 크롭, 성인물 블러, 공유 미리보기(OG) 합성)를 수행하는
Cloudflare Worker. 계약은 `docs/design/media-module-design.md` §6~7 참고.

- 트리거: 서버 → `POST <이 Worker URL>` (헤더 `X-Callback-Secret`, 바디 `{ownerType, ownerId, imageKeys, sourceUrls, variantProfile, qualityTier}`)
- 콜백: Worker → `POST {SERVER_CALLBACK_URL}` (헤더 `X-Internal-Secret`, 바디 `{ownerType, ownerId, imageKey, thumbKey, thumbAdultKey, originalAvifKey, ogKey, ogAdultKey, status}`)
- OG 전용 트리거(백필): 서버 → `POST <이 Worker URL>` (바디 `{ownerType, ownerId, imageKeys, thumbKeys, thumbAdultKeys, variantProfile: "OG_ONLY"}`)
- OG 전용 콜백: Worker → `POST {SERVER_CALLBACK_URL의 /images/processed를 /images/og-processed로 바꾼 주소}` (바디 `{ownerType, ownerId, imageKey, ogKey, ogAdultKey}`).
  `SERVER_CALLBACK_URL`이 `/internal/media/images/processed`로 끝나지 않으면 OG 전용 콜백을 보내지 않는다.

`variantProfile`이 만들 변형본을 정한다. 만들지 않은 변형본은 콜백에서 `null`이다.

| variantProfile | 용도 | 만드는 변형본 |
|---|---|---|
| `ORIGINAL` | 본문 이미지(작품·구인글 등) | `original/` |
| `THUMBNAIL` | 카드 썸네일 | `thumb/` (588×784) |
| `THUMBNAIL_WITH_ADULT_BLUR` | 작품 카드 썸네일 | `thumb/`, `thumb-adult/`, `og/`, `og-adult/` (1200×630 JPEG) |
| `OG_ONLY` | OG 백필(서버 enum에는 없음) | `og/`, `og-adult/` — 이미 저장된 썸네일로 만들고 원본은 검사·삭제하지 않는다 |

OG는 썸네일 전체를 가운데 두고(contain) 좌우를 같은 이미지의 흐린 확대본으로 채운 1200×630 JPEG다. 성인물용은
`thumb-adult/`를 소스로 쓴다. 합성만 Images 바인딩(`env.IMAGES`)을 쓴다 — `cf.image`는 AVIF 입력을 거부한다.
OG 생성이 실패해도 썸네일 처리는 DONE이고 `ogKey`가 `null`로 간다(서비스는 기본 이미지를 쓴다).
**OG 규격을 바꿔 다시 만들 때는 접두사를 바꾼다(`og2/`)** — OG 파일은 `Cache-Control: immutable`이라 같은 key로
덮으면 크롤러·CDN에 반영되지 않는다. 설계는 `docs/design/og-image-design.md`.

`STANDARD`·`STANDARD_WITH_ADULT_BLUR`는 이 구분 이전 이름이다. 서버보다 Worker를 먼저 배포하는 동안에만
오며, 예전처럼 original과 thumb(+블러)를 모두 만든다.

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
npx wrangler secret put SERVER_CALLBACK_URL   # 예: https://api.at-crew.com/internal/media/images/processed
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
