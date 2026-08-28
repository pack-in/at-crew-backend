-- 커뮤니티 포트폴리오 목록 정렬(조회순·북마크순) 집계 컬럼 — 이슈 #78.
--
-- 조회수는 dedup 없는 단순 증가다(recruit의 job_postings.view_count와 동일 방식). 작품 상세를
-- 본인이 아닌 사람이 열 때마다 +1 한다. member의 24시간 dedup(V29 member_profile_views)을 쓰지
-- 않는 이유는 이번 요구가 "정렬 기준 추가"라 순위를 가를 수 있는 단조 증가 값이면 충분하고,
-- dedup 테이블은 작품 수 x 조회자 수만큼 행이 늘어 비용이 정렬 정확도에 비해 크기 때문이다.
--
-- 북마크 수는 bookmark_entries를 매번 COUNT 하지 않도록 artworks에 비정규화한다 — 정렬 키를
-- 서브쿼리로 만들면 인덱스로 커버되지 않는다. 저장/삭제 시 원자적 UPDATE로 증감한다.

ALTER TABLE artworks
    ADD COLUMN view_count     BIGINT NOT NULL DEFAULT 0 AFTER age_rating,
    ADD COLUMN bookmark_count BIGINT NOT NULL DEFAULT 0 AFTER view_count;

-- 커뮤니티 피드 정렬용 커버링 인덱스 — V29 idx_members_search_view_count와 동일한 형태로
-- 등가 조건(status·visibility)을 앞에 두고 정렬 키를 뒤에 붙인다.
-- blocked_at IS NULL은 등가 조건이 아니라 인덱스에 넣으면 뒤의 정렬 키가 range 뒤로 밀려
-- filesort가 되므로 제외한다(차단 작품은 소수라 필터로 걸러도 비용이 작다).
-- 동일 정렬값에서 순서가 흔들리지 않도록 tiebreaker인 id까지 인덱스에 포함한다 — 커서
-- 페이지네이션이 (정렬 키, id) 복합 부등식으로 다음 페이지를 찾기 때문이다.
CREATE INDEX idx_aw_feed_view_count
    ON artworks (status, visibility, view_count DESC, id DESC);

CREATE INDEX idx_aw_feed_bookmark_count
    ON artworks (status, visibility, bookmark_count DESC, id DESC);

-- 조회수는 이력 테이블이 없어 0부터 시작할 수밖에 없지만, 북마크는 V1부터 있던 bookmark_entries로
-- 정확히 복원 가능하다. 백필하지 않으면 기존 북마크를 가진 작품이 배포 직후 북마크순 최하위로
-- 밀리고, 이후 해제 시 decrementBookmarkCount의 0-하한 가드에 걸려 어긋남이 영구화된다.
UPDATE artworks a
    SET bookmark_count = (SELECT COUNT(*) FROM bookmark_entries b WHERE b.artwork_id = a.id);
