-- 도메인 이미지 자식 행을 media_assets로 채운다 — 이미지 상태·변형본 key의 단일 저장소를 media로 옮기기
-- 위한 준비다(#193). V12는 PENDING 행만 옮겼으므로, 그전에 DONE·FAILED가 된 작품 이미지와 recruit 자식 행
-- 전체가 여기서 들어온다.
--
-- 소유자 단위로 판정한다: media_assets에 행이 하나라도 있는 소유자는 건너뛴다. 행 단위로 비교하면 이미
-- 들어와 있는 행과 ordinal이 겹쳐 uk_ma_owner_order에 걸릴 수 있다. media 행이 있는 소유자는 이미
-- media 경로로 등록된 것이라 백필 대상이 아니다.
--
-- 화질 등급은 자식 행에 없다. artwork은 컬럼 기본값과 같은 'ORIGINAL', recruit은 등록 경로가 쓰는 'WEB'을
-- 넣는다. 이미 처리가 끝난 이미지는 재변환되지 않아 값이 쓰이지 않고, 재시도가 걸리는 경우에만 쓰인다.
--
-- artwork_images.artwork_id는 latin1_bin이라 utf8mb4 컬럼과 비교할 때 변환과 콜레이션을 함께 명시한다.
-- CONVERT만 쓰면 결과가 서버 기본 콜레이션(MariaDB 11.4는 utf8mb4_uca1400_ai_ci)이 돼 컬럼과 섞인다.

INSERT INTO media_assets (owner_type, owner_id, ordinal, slot_role, original_key, thumb_key, thumb_adult_key,
    original_avif_key, variant_profile, quality_tier, processing_status, created_at, updated_at)
SELECT 'ARTWORK', CONVERT(ai.artwork_id USING utf8mb4), ai.ordinal, NULL, ai.original_key, ai.thumb_key,
    ai.thumb_adult_key, ai.original_avif_key, 'STANDARD_WITH_ADULT_BLUR', 'ORIGINAL', ai.processing_status,
    NOW(6), NOW(6)
FROM artwork_images ai
WHERE ai.original_key IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM media_assets ma
      WHERE ma.owner_type = 'ARTWORK'
        AND ma.owner_id = CONVERT(ai.artwork_id USING utf8mb4) COLLATE utf8mb4_unicode_ci
  );

INSERT INTO media_assets (owner_type, owner_id, ordinal, slot_role, original_key, thumb_key, thumb_adult_key,
    original_avif_key, variant_profile, quality_tier, processing_status, created_at, updated_at)
SELECT 'JOB_POSTING', i.posting_id, i.ordinal, i.role, i.original_key, i.thumb_key, NULL,
    i.original_avif_key, 'STANDARD', 'WEB', i.processing_status, NOW(6), NOW(6)
FROM job_posting_images i
WHERE NOT EXISTS (
      SELECT 1 FROM media_assets ma WHERE ma.owner_type = 'JOB_POSTING' AND ma.owner_id = i.posting_id
  );

INSERT INTO media_assets (owner_type, owner_id, ordinal, slot_role, original_key, thumb_key, thumb_adult_key,
    original_avif_key, variant_profile, quality_tier, processing_status, created_at, updated_at)
SELECT 'TEAM_POSTING', i.posting_id, i.ordinal, i.role, i.original_key, i.thumb_key, NULL,
    i.original_avif_key, 'STANDARD', 'WEB', i.processing_status, NOW(6), NOW(6)
FROM team_posting_images i
WHERE NOT EXISTS (
      SELECT 1 FROM media_assets ma WHERE ma.owner_type = 'TEAM_POSTING' AND ma.owner_id = i.posting_id
  );

INSERT INTO media_assets (owner_type, owner_id, ordinal, slot_role, original_key, thumb_key, thumb_adult_key,
    original_avif_key, variant_profile, quality_tier, processing_status, created_at, updated_at)
SELECT 'JOB_SEEKING_POST', i.posting_id, i.ordinal, i.role, i.original_key, i.thumb_key, NULL,
    i.original_avif_key, 'STANDARD', 'WEB', i.processing_status, NOW(6), NOW(6)
FROM job_seeking_post_images i
WHERE NOT EXISTS (
      SELECT 1 FROM media_assets ma WHERE ma.owner_type = 'JOB_SEEKING_POST' AND ma.owner_id = i.posting_id
  );
