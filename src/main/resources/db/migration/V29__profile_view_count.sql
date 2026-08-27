-- 프로필 열람수 집계 — 기획서 마이페이지_작가-R03, 홈-R02("작가찾기=업데이트순·조회순·경력순").
--
-- 동일 조회자의 24시간 이내 반복 조회는 최초 1회만 집계한다. 판단에 필요한 건 "직전 조회가 24시간
-- 안이었는가" 하나뿐이라 조회 이력을 누적하지 않고 (작가, 조회자)당 한 행만 두고 덮어쓴다.
--
-- 비로그인 조회는 집계 대상이 아니다 — 기획서가 "비로그인 사용자의 동일 사용자 판단 기준"을
-- 홈-R14 확인 필요 항목으로 남겨둬 중복 제외 키가 없다. 키 없이 세면 새로고침만으로 열람수를
-- 부풀릴 수 있다.

ALTER TABLE members ADD COLUMN profile_view_count INT NOT NULL DEFAULT 0 AFTER experience_rank;

CREATE TABLE member_profile_views (
    artist_member_id VARCHAR(36) CHARACTER SET latin1 COLLATE latin1_bin NOT NULL,
    viewer_member_id VARCHAR(36) CHARACTER SET latin1 COLLATE latin1_bin NOT NULL,
    viewed_at        DATETIME(6) NOT NULL,
    PRIMARY KEY (artist_member_id, viewer_member_id)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- 조회순 정렬용 커버링 인덱스 — 경력순(idx_members_search_experience)과 동일한 형태.
CREATE INDEX idx_members_search_view_count
    ON members (is_active, employment_status, profile_view_count DESC, updated_at DESC);
