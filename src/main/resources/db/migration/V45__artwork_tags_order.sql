-- 작품 태그에 등록 순서를 저장한다(홈-R05 "작품 태그는 업로드 폼에서 등록한 태그 순서를 기준으로 노출", 이슈 #199).
--
-- 지금까지 artwork_tags는 (artwork_id, value)만 있어 등록 순서가 남지 않았고, 코드는 조회 시 문자열 정렬해
-- 내보냈다. Artwork.tags를 @OrderColumn(tag_order)을 쓰는 List로 바꾸면서 순서 컬럼을 추가한다.
--
-- 기존 행의 등록 순서는 복구할 수 없으므로 지금까지 노출되던 정렬 순서(value 오름차순)로 채운다 —
-- 배포 전후로 화면에 보이는 순서가 바뀌지 않는다. 새로 저장·수정하는 작품부터 등록 순서가 반영된다.
--
-- PK는 Hibernate 인덱스 리스트의 형태인 (artwork_id, tag_order)로 바꾼다. (artwork_id, value) 유일성은
-- 두지 않는다 — 순서만 바꾸는 수정이 행 단위 UPDATE로 반영될 때 값이 잠시 겹쳐 충돌할 수 있어서다.
-- 중복 태그는 도메인(Artwork)에서 등록 순서를 유지한 채 제거한다. 태그 검색용 idx_at_value는 그대로 둔다.
ALTER TABLE artwork_tags ADD COLUMN tag_order INT NOT NULL DEFAULT 0;

UPDATE artwork_tags t
JOIN (SELECT artwork_id, value,
             ROW_NUMBER() OVER (PARTITION BY artwork_id ORDER BY value) - 1 AS rn
      FROM artwork_tags) r
  ON r.artwork_id = t.artwork_id AND r.value = t.value
SET t.tag_order = r.rn;

ALTER TABLE artwork_tags
    DROP PRIMARY KEY,
    ADD PRIMARY KEY (artwork_id, tag_order),
    MODIFY tag_order INT NOT NULL;
