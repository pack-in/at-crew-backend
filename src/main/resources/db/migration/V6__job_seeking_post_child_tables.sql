-- 구직글 자식 테이블(job_seeking_post_roles/genres)을 JPA @ElementCollection 기본 매핑에 맞춘다.
-- V5에서는 서로게이트 id 컬럼을 두었으나, @ElementCollection은 서로게이트 키 없이
-- (부모 FK, 값) 복합 PK로 매핑되므로 job_posting_roles/team_posting_roles와 동일한 형태로 정렬한다.
-- (V5는 수정하지 않고 이 마이그레이션에서 변경한다 — Flyway 마이그레이션 불변 원칙.)
--
-- 복합 PK를 먼저 만든 뒤 중복 인덱스를 제거해야 FK(fk_job_seeking_post_roles_post)를 커버하는
-- 인덱스가 어느 시점에도 사라지지 않는다.

ALTER TABLE job_seeking_post_roles
    DROP PRIMARY KEY,
    DROP COLUMN id,
    ADD PRIMARY KEY (job_seeking_post_id, role);

DROP INDEX idx_job_seeking_post_roles_post ON job_seeking_post_roles;   -- 복합 PK 선두 컬럼이 대체

ALTER TABLE job_seeking_post_genres
    DROP PRIMARY KEY,
    DROP COLUMN id,
    ADD PRIMARY KEY (job_seeking_post_id, genre);

DROP INDEX idx_job_seeking_post_genres_post ON job_seeking_post_genres;  -- 복합 PK 선두 컬럼이 대체
