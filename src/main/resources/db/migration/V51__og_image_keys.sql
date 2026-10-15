-- 공유 링크 미리보기(OG) 이미지 key (docs/design/og-image-design.md).
--
-- Worker가 카드 썸네일(thumb/·thumb-adult/)로 1200×630 JPEG를 합성해 og/·og-adult/에 저장한다. 변형본 key는
-- media_assets에만 둔다는 원칙(V47)을 따른다. 생성 전·실패·대상 아님(본문 이미지·구인글)이면 NULL이다.
ALTER TABLE media_assets
    ADD COLUMN og_key       VARCHAR(500) NULL,
    ADD COLUMN og_adult_key VARCHAR(500) NULL;

-- 고정형 스냅샷은 생성 시점의 연령 등급으로 고른 OG key 하나만 얼린다 — 등급도 얼려 두므로 나머지 한쪽은 쓰이지 않는다.
-- 기존 스냅샷은 Worker 백필 뒤 내부 API(POST /internal/portfolio/og/backfill-snapshots)가 채운다. 값이 Worker
-- 실행 뒤에야 생기므로 여기서 채우지 않는다.
ALTER TABLE portfolio_item_snapshots
    ADD COLUMN og_key VARCHAR(500) NULL;
