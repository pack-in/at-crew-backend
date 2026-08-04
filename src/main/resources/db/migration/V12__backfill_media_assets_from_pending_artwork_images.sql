-- 배포 시점(media 모듈 전환)에 이미 PROCESSING 중이던 작품의 이미지를 media_assets로 백필한다.
-- media 모듈 도입 이전에 업로드돼 아직 Worker 콜백을 받지 못한 이미지는 media_assets에 대응 행이
-- 없어, Worker가 신·구 콜백 경로 어느 쪽으로 응답하든 매칭에 실패해 무시된다
-- (docs/design/media-module-design.md §9.2 롤아웃 순서, §11 열린 이슈).
--
-- 조건: processing_status = 'PENDING'인 행만 대상으로 한다. DONE/FAILED는 이미 콜백을 받았거나
-- 더 이상 콜백을 기다리지 않으므로 백필이 필요 없다.
-- 이 마이그레이션이 실행되는 시점(빈 테스트 DB 포함)에 artwork_images가 비어 있거나 PENDING 행이
-- 없으면 INSERT ... SELECT는 0건을 삽입하는 무해한 no-op이다.
INSERT INTO media_assets (owner_type, owner_id, ordinal, original_key, thumb_key, thumb_adult_key,
    original_avif_key, variant_profile, processing_status, created_at, updated_at)
SELECT 'ARTWORK', ai.artwork_id, ai.ordinal, ai.original_key, ai.thumb_key, ai.thumb_adult_key,
    ai.original_avif_key, 'STANDARD_WITH_ADULT_BLUR', ai.processing_status, NOW(6), NOW(6)
FROM artwork_images ai
WHERE ai.processing_status = 'PENDING'
  AND ai.original_key IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM media_assets ma
      WHERE ma.owner_type = 'ARTWORK' AND ma.owner_id = ai.artwork_id AND ma.ordinal = ai.ordinal
  );
