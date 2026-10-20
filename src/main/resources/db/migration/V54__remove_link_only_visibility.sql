-- 레거시 공개 상태 LINK_ONLY 제거 — docs/roadmap.md §11
--
-- LINK_ONLY는 라이트(Laiteu) 데이터 이관용으로만 남겨 둔 값이었다. 라이트를 종료하지 않고 별개 서비스로
-- 운영하기로 하면서(2026-09-10) 이관이 없어져, 이번 배포에서 Visibility enum 상수를 지운다.
-- 상수가 사라진 뒤 이 값이 남은 행을 읽으면 JPA가 enum 변환에 실패하므로 먼저 PRIVATE로 옮긴다.
-- 접근 판정(Artwork.accessFor)은 이미 LINK_ONLY를 PRIVATE와 똑같이 다뤘으므로 동작 변화는 없다.
-- 검색 색인은 PUBLIC 작품만 담으므로 재색인할 필요가 없다.
--
-- 값이 들어 있는 컬럼은 셋이다: 현재 공개 상태, 휴지통 복구용 직전 상태, 북마크 저장 당시 상태.

UPDATE artworks SET visibility = 'PRIVATE' WHERE visibility = 'LINK_ONLY';
UPDATE artworks SET visibility_before_delete = 'PRIVATE' WHERE visibility_before_delete = 'LINK_ONLY';
UPDATE bookmark_entries SET artwork_visibility_at_save = 'PRIVATE' WHERE artwork_visibility_at_save = 'LINK_ONLY';
