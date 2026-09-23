-- 도메인 이미지 자식 테이블을 지운다(#193 마지막 단계).
--
-- 이미지의 상태와 변형본 key는 media_assets 한 곳에만 있다. 코드는 V42~V46 배포(2026-09-22)부터 이 테이블을
-- 읽지도 쓰지도 않으며, 그 배포가 운영에서 정상 동작하는 것을 확인한 뒤에 지운다 — 배포에 마이그레이션이
-- 있으면 자동 롤백이 돌지 않으므로 전환과 삭제를 같은 배포에 넣지 않았다.
--
-- 데이터는 V43이 media_assets로 옮겼다. 되돌릴 일이 생기면 DB 백업(일 단위)에서 복원한다.
DROP TABLE IF EXISTS artwork_images;
DROP TABLE IF EXISTS job_posting_images;
DROP TABLE IF EXISTS team_posting_images;
DROP TABLE IF EXISTS job_seeking_post_images;
