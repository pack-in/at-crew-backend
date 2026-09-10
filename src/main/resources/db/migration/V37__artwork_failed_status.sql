-- 이미지가 전량 실패한 작품을 PROCESSING에서 FAILED로 정정한다.
--
-- READY 전환 조건은 "PENDING 없음 AND DONE 하나 이상"(Artwork.markImageProcessed)이라, 이미지가 한 장도
-- 성공하지 못하면 어느 분기에도 걸리지 않아 초기값 PROCESSING에 그대로 남았다. 재시도 스케줄러는 PENDING만
-- 대상으로 삼으므로(ImageRetryScheduler) 자력으로 빠져나올 수도 없다 — 프론트에는 "Processing"이 영원히 뜬다.
--
-- 코드 쪽은 같은 커밋에서 전량 실패를 FAILED로 끝내도록 고쳤지만, 이미 이벤트를 소비한 기존 행은
-- 다시 계산되지 않으므로 여기서 한 번 맞춘다. 2026-09-09 프로덕션에서 4건 발생(원본 용량 상한 초과가 원인).
--
-- artworks.status는 VARCHAR(30)이라 값 추가에 DDL 변경이 필요 없다.
--
-- 조건은 코드의 판정과 같게 둔다 — 이미지가 하나라도 있고(EXISTS), 그중 FAILED가 아닌 것이 없을 때만.
-- 아직 처리 중인 정상 PROCESSING 작품(PENDING이 남아 있음)은 건드리지 않는다.
UPDATE artworks a
SET a.status = 'FAILED',
    a.last_modified_by = 'SYSTEM'
WHERE a.status = 'PROCESSING'
  AND EXISTS (SELECT 1 FROM artwork_images i WHERE i.artwork_id = a.id)
  AND NOT EXISTS (SELECT 1 FROM artwork_images i WHERE i.artwork_id = a.id AND i.processing_status <> 'FAILED');
