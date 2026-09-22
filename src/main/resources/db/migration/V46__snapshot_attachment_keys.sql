-- 기존 스냅샷의 자료 첨부 key를 보존 색인에 채운다(#190).
--
-- V41은 첨부 key를 일부러 뺐다. 그때는 업로드 key의 소유를 검증하지 않아, 남의 key를 첨부로 적어 그 파일의
-- 삭제를 무기한 막을 수 있었기 때문이다(#188). 이제 발급 key에 소유자 서명이 들어가 그 전제가 사라졌고,
-- 첨부도 영구 삭제 대상으로 되돌렸으므로 보존 판정이 필요하다.
--
-- 기존 컬럼과 JSON_TABLE 결과의 콜레이션을 맞춘다(V41과 같은 이유).
INSERT IGNORE INTO portfolio_snapshot_media_keys (snapshot_id, media_key)
SELECT k.snapshot_id, k.media_key FROM (
    SELECT s.id AS snapshot_id, att.k AS media_key FROM portfolio_item_snapshots s,
        JSON_TABLE(s.payload_json, '$.materials[*].attachmentKeys[*]'
            COLUMNS (k VARCHAR(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin PATH '$')) att
) k
WHERE k.media_key IS NOT NULL AND k.media_key <> '';
