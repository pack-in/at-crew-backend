-- 관심 작가 (docs/design/recruit-module-design.md §2.7) — 기업 계정이 작가를 저장·최근 조회한 이력.
-- company_member_id/artist_member_id는 Member 참조지만 모듈 경계상 FK를 걸지 않는다(다른 저장소).
-- 목록은 (저장/조회 시각 내림차순, artist_member_id 내림차순) 복합 커서로 페이지네이션한다.

CREATE TABLE company_liked_artists (
    company_member_id   VARCHAR(36) CHARACTER SET latin1 COLLATE latin1_bin NOT NULL,
    artist_member_id    VARCHAR(36) CHARACTER SET latin1 COLLATE latin1_bin NOT NULL,
    liked_at            DATETIME NOT NULL,
    PRIMARY KEY (company_member_id, artist_member_id)   -- 같은 작가 중복 저장 방지
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_company_liked_artists_cursor
    ON company_liked_artists (company_member_id, liked_at, artist_member_id);

CREATE TABLE company_recently_viewed_artists (
    company_member_id   VARCHAR(36) CHARACTER SET latin1 COLLATE latin1_bin NOT NULL,
    artist_member_id    VARCHAR(36) CHARACTER SET latin1 COLLATE latin1_bin NOT NULL,
    viewed_at           DATETIME NOT NULL,
    PRIMARY KEY (company_member_id, artist_member_id)   -- 재조회 시 viewed_at만 갱신(upsert)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_company_recently_viewed_artists_cursor
    ON company_recently_viewed_artists (company_member_id, viewed_at, artist_member_id);
