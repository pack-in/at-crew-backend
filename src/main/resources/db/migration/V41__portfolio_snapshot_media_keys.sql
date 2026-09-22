-- 고정형 스냅샷이 참조하는 R2 key 색인 (docs/design/portfolio-module-design.md §5.6).
--
-- 보존 판정은 "지우려는 key 중 활성 스냅샷이 참조하는 것"을 골라야 한다. 예전에는 후보 key가 카드 썸네일
-- 컬럼(thumb_key/thumb_adult_key)과 일치할 때만 스냅샷을 찾고 payload_json을 펼쳤다 — 후보에 썸네일이 없으면
-- (이미지 한 장의 변형본만 담은 고아 행, 사용자 지정 썸네일을 쓴 작품의 media 자산 행 등) 스냅샷을 못 찾아
-- 스냅샷 이미지가 지워졌다(PR #188 코드 리뷰). 스냅샷이 참조하는 key를 행 단위로 두고 key로 바로 조회한다.
--
-- 스냅샷은 생성 뒤 바뀌지 않으므로 생성 시점에 한 번 채운다. 스냅샷이 지워지면(포트폴리오 삭제) 함께 지워진다.
-- key는 대소문자를 구분해야 하므로 utf8mb4_bin이다.
CREATE TABLE portfolio_snapshot_media_keys (
    snapshot_id BIGINT NOT NULL,
    media_key   VARCHAR(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    PRIMARY KEY (snapshot_id, media_key),
    KEY idx_psmk_media_key (media_key),
    CONSTRAINT fk_psmk_snapshot FOREIGN KEY (snapshot_id)
        REFERENCES portfolio_item_snapshots (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- 기존 스냅샷 채우기: 카드 썸네일 2종, 상세 이미지 4종. 자료 첨부 key는 소유 검증이 생긴 뒤 V46에서 채운다(#190).
-- 기존 컬럼은 utf8mb4_unicode_ci라 UNION에서 JSON_TABLE 결과와 섞이지 않는다 — 모두 utf8mb4_bin으로 맞춘다.
INSERT IGNORE INTO portfolio_snapshot_media_keys (snapshot_id, media_key)
SELECT k.snapshot_id, k.media_key FROM (
    SELECT id AS snapshot_id, thumb_key COLLATE utf8mb4_bin AS media_key FROM portfolio_item_snapshots
    UNION ALL
    SELECT id, thumb_adult_key COLLATE utf8mb4_bin FROM portfolio_item_snapshots
    UNION ALL
    SELECT s.id, img.k FROM portfolio_item_snapshots s,
        JSON_TABLE(s.payload_json, '$.images[*]' COLUMNS (k VARCHAR(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin PATH '$.originalKey')) img
    UNION ALL
    SELECT s.id, img.k FROM portfolio_item_snapshots s,
        JSON_TABLE(s.payload_json, '$.images[*]' COLUMNS (k VARCHAR(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin PATH '$.thumbKey')) img
    UNION ALL
    SELECT s.id, img.k FROM portfolio_item_snapshots s,
        JSON_TABLE(s.payload_json, '$.images[*]' COLUMNS (k VARCHAR(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin PATH '$.thumbAdultKey')) img
    UNION ALL
    SELECT s.id, img.k FROM portfolio_item_snapshots s,
        JSON_TABLE(s.payload_json, '$.images[*]' COLUMNS (k VARCHAR(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin PATH '$.originalAvifKey')) img
) k
WHERE k.media_key IS NOT NULL AND k.media_key <> '';
