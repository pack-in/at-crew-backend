-- 소유자가 정하는 슬롯 이름 — recruit이 썸네일과 참고 이미지를 가르는 데 쓴다(#193 단일 저장소 전환).
--
-- recruit은 지금까지 자식 테이블의 role 컬럼과 "ordinal 0이 썸네일"이라는 관례를 함께 썼다. 썸네일이 없으면
-- 참고 이미지가 0번을 차지해 관례가 깨지므로, media로 옮기면서 값을 명시적으로 보관한다.
-- artwork은 슬롯 구분이 없어 NULL로 남는다.
ALTER TABLE media_assets
    ADD COLUMN slot_role VARCHAR(30) NULL AFTER ordinal;
