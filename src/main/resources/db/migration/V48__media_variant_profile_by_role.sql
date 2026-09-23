-- 변형본 프로필을 이미지 역할 기준으로 바꾼다 — 본문은 original만, 카드 썸네일만 thumb(+블러)를 만든다.
--
-- 예전 값 STANDARD(원본+썸네일)·STANDARD_WITH_ADULT_BLUR(원본+썸네일+블러)는 전부 본문 이미지였다. 이미 처리된
-- 행은 변환 결과 key를 그대로 가지므로 값만 바뀐다. 아직 PENDING인 행은 재시도 때 original만 만든다 — 카드는
-- 사용자 지정 썸네일(또는 이전에 만들어진 thumb)을 쓰므로 본문에 썸네일이 없어도 된다.
UPDATE media_assets
SET variant_profile = 'ORIGINAL'
WHERE variant_profile IN ('STANDARD', 'STANDARD_WITH_ADULT_BLUR');
