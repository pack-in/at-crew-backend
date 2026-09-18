-- Spring Modulith 이벤트 레지스트리의 직렬화 칸을 넓힌다(V13은 공식 스키마 그대로 VARCHAR(4000)).
--
-- ArtworkPermanentlyDeletedEvent는 작품의 R2 key 전체(이미지 4종 × 최대 30장 + 지정 썸네일)를 싣는다.
-- 처리된 이미지가 20장쯤 되면 직렬화 결과가 4000자를 넘어 'Data too long'으로 영구 삭제 트랜잭션 전체가
-- 롤백되고, 휴지통 자동 영구 삭제 배치는 같은 작품에서 매시간 멈춘다(PR #188 코드 리뷰).
-- 이벤트 크기에 상한을 두지 않기 위해 LONGTEXT로 바꾼다.
ALTER TABLE EVENT_PUBLICATION MODIFY SERIALIZED_EVENT LONGTEXT NOT NULL;
