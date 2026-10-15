# 공유 링크 미리보기(OG) 이미지

작품·포트폴리오 링크를 카카오톡·X·슬랙 등에 붙였을 때 나오는 미리보기 이미지(og:image)를 서버가 만들어 준다.
기획 원본은 기획자가 전달한 "포트폴리오 공유 링크 미리보기(OG 카드)"·"작품 공유 링크 미리보기(OG 카드)"(2026-10)다.
OG 태그 자체(og:title·og:description·og:url)는 FE SSR이 만든다 — 서버 책임은 **이미지 파일과 그 key**다.
프론트 계약은 [og-image-frontend-integration.md](og-image-frontend-integration.md), 기존 작품 백필 절차는
[docs/operations/og-image-backfill.md](../operations/og-image-backfill.md).

## 1. 배경 (2026-10-15 운영 실측)

- 작품 상세 `/ko/artwork/{id}`에는 og 태그가 하나도 없었다.
- 포트폴리오 OG는 FE가 고른 `thumb/*.avif`(588×784 세로 AVIF)를 그대로 내보냈다. 기획서 규격(JPG·PNG, 1200×630 기준)과 다르다.
- 지정 썸네일은 FE가 588×784로 잘라 올리고(`at-crew-fe lib/domain/artwork-form.ts`), Worker는 변환 성공 후 raw를 지운다.
  OG를 만들 수 있는 소스는 `thumb/`·`thumb-adult/`(AVIF)뿐이다.

## 2. 결정

| # | 결정 | 근거 |
|---|---|---|
| D1 | **contain + 흐린 배경.** 썸네일 전체를 높이 630에 맞춰 가운데 두고(472×630), 좌우는 같은 이미지를 cover로 늘려 blur 50 | 3:4 썸네일을 1.91:1로 중앙 크롭하면 세로 60%가 잘리고 2배 확대가 필요하다. 기획자 합의로 기획서의 중앙 크롭 규칙을 바꿨다(2026-10-15) |
| D2 | **업로드 시 미리 생성해 R2에 저장** | 비용은 요청 시 생성과 차이가 없다(둘 다 월 0~수십 달러, 변환 과금이 대부분). 크롤링 시점의 변환 지연·한도 초과 실패를 없앤다 |
| D3 | **Images 바인딩(`env.IMAGES`)으로 합성** | `fetch`+`cf.image`는 AVIF 입력을 거부한다(`ERROR 9520`). 바인딩은 AVIF 입력과 `.draw()` 합성을 지원한다. 1200×630 JPEG 26~46KB, 0.2~0.4초(실측) |
| D4 | **성인물은 `thumb-adult/`를 소스로** 같은 합성 | 바인딩에서 오버레이에 blur를 걸면 9520으로 실패한다. `thumb-adult/`는 홈-R06 카드와 같은 `ADULT_BLUR=20`이라 기획서의 "같은 방식"과 맞는다 |
| D5 | **응답은 서버가 고른 key 하나(`ogImageKey`)** — 성인물인데 블러본이 없으면 일반본으로 대체하지 않고 null | 기획서 "blur 전 원본 썸네일은 og:image로 제공하지 않는다". FE에 판정을 맡기면 틀렸을 때 블러 안 된 성인물이 나간다(`ArtworkOgImage`) |
| D6 | 대상은 **지정 썸네일 자산**(`ARTWORK_THUMBNAIL`, `THUMBNAIL_WITH_ADULT_BLUR`)만 | 기획서 OG 원본은 "업로드-R05 지정 썸네일". 구인글·모집글 OG는 이후 출시 범위 |
| D7 | **OG 실패는 썸네일 처리 결과에 영향 없음** — og key가 null로 가고 FE가 기본 이미지 | OG는 부가 산출물이다. 빠진 OG는 백필 API로 회수한다 |
| D8 | 고정형 스냅샷은 생성 시점에 **고른 key 하나**(`og_key`)만 얼린다 | 스냅샷은 연령 등급도 얼린다. 처리 중 작품은 고정형에 못 담으므로(`ARTWORK_IMAGE_PROCESSING`) 얼릴 때 OG가 이미 있다 |
| D9 | OG 파일은 `Cache-Control: public, max-age=31536000, immutable`, 규격 변경 시 **접두사를 바꾼다**(`og2/`) | 썸네일 교체 시 업로드 key가 새로 생기고 등급 변경 시 `og`↔`og-adult`로 URL이 바뀐다 — 기획서의 `?v=`가 파라미터 없이 충족된다 |

## 3. 데이터 흐름

**신규 업로드** — Worker가 `THUMBNAIL_WITH_ADULT_BLUR` 처리 중 `thumb/`·`thumb-adult/`를 저장한 직후 그 파일로
`og/{base}.jpg`·`og-adult/{base}.jpg`를 만들고, 기존 콜백(`/internal/media/images/processed`)에 `ogKey`·`ogAdultKey`를
함께 싣는다. 서버는 `media_assets.og_key`·`og_adult_key`에 저장한다(V51).

**기존 작품(백필)** — 서버가 `OG_ONLY` 트리거로 썸네일 key를 보내면 Worker는 원본 검사·삭제 없이 OG만 만들어
별도 콜백(`/internal/media/images/og-processed`)으로 돌려준다. **엔드포인트를 나눈 이유**: 썸네일 콜백은 변형본 key를
통째로 덮어쓰므로, 썸네일 필드가 없는 OG 결과를 같은 경로로 받으면 썸네일 key가 null로 덮인다.
`OG_ONLY`는 `MediaVariantProfile`에 넣지 않았다 — 그 enum은 자산에 저장되는 값이고 OG 전용은 저장될 일이 없다.

**노출 key 선택**

- 작품 상세(`GET /api/artworks/{id}`) `ogImageKey` — 지정 썸네일 자산의 OG 쌍에 `ArtworkOgImage.select`를 적용한다.
  비공개·삭제·차단은 기존 `accessFor`가 이미 404·403·410으로 막는다(크롤러와 같은 비로그인 시점).
- 공유 포트폴리오 헤더(`GET /api/portfolios/shared/{identifier}`) `ogImageKey`
  - 최신 반영형·작가 페이지: 구성 행을 ordinal **내림차순**으로 훑어 `sharedViewable`(READY·차단 아님) 첫 작품의
    `ogImageKey`. ordinal은 모든 편입 경로에서 업로드순이라(`orderByUploadedAt`) 첫 통과 작품이 업로드 최신이다.
    성인물(블러본)·피드 비공개도 후보다. 고른 작품에 OG가 없으면 다음 후보로 넘어가지 않고 null이다.
  - 고정형: 차단되지 않은 스냅샷 중 ordinal 최대 행의 `og_key`.
  - 조회 시점 계산이라 작품 추가·제외·삭제·등급 변경이 다음 요청부터 바로 반영된다.

**정리·보존** — og key는 다른 변형본과 같은 경로를 탄다: 교체·고아 처리(`MediaServiceImpl.keysOf`), 늦은 콜백
(`MediaCallbackService.orphanDerivedKeys`), 영구 삭제(`ArtworkPurger`), 고정형 보존 색인(`portfolio_snapshot_media_keys`).

## 4. 알려진 제약

- 지정 썸네일 자산이 없는 예전 작품(대표 이미지로 카드를 만드는 경우)은 OG가 없다 → 기본 이미지.
- 스냅샷 생성 뒤 썸네일을 교체한 작품은 예전 자산 행이 지워져 백필이 OG를 찾지 못한다(`unresolved`) → 기본 이미지.
- 공유 헤더 후보 스캔은 20행 × 10청크 상한이 있다. 최신 쪽에 처리 중·차단 작품이 그보다 많으면 null → 기본 이미지.
- Worker의 OG 실패 경로는 자동 테스트가 없다(Worker에 테스트 환경이 없다). `createOg`가 던지지 않도록 함수로 분리했다.
- 외부 메신저·SNS가 이미 저장한 미리보기는 그 플랫폼의 캐시 정책을 따른다(우리 범위 밖, 기획서 명시).

## 5. 배포 순서

1. 서버 — V51, 콜백 확장, 응답 필드, 백필 API. 구버전 Worker 콜백에는 og가 없어 null로 저장되고 FE는 기본 이미지를 쓴다.
   V51은 컬럼 추가뿐이라 서버 롤백이 안전하다. 새 Worker가 og 필드를 실어 보내도 구버전 서버는 무시한다
   (`@JsonIgnoreProperties(ignoreUnknown = true)`).
2. Worker — 신규 업로드부터 OG 생성. 롤백하면 신규 업로드 og가 null일 뿐이다.
3. 백필 — [og-image-backfill.md](../operations/og-image-backfill.md). Worker 배포 전에 부르면 Worker가 400으로 거절한다(데이터 변화 없음).
4. FE — 1 이후 언제든. null이면 기본 이미지라 순서에 묶이지 않는다.
