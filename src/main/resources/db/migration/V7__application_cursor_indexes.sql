-- 지원자 목록은 커서 페이지네이션(id 기준, UUIDv7이라 지원 시각순과 동일)으로 조회하므로
-- 정렬 키에 맞춘 인덱스로 교체한다. applied_at 기준 인덱스는 이 쿼리에 쓰이지 않아 쓰기 비용만 남는다
-- (docs/design/recruit-module-design.md §8 — 커서 페이지네이션 전용 인덱스 원칙).

CREATE INDEX idx_job_applications_posting_cursor ON job_applications (job_posting_id, id);
DROP INDEX idx_job_applications_posting ON job_applications;

CREATE INDEX idx_team_applications_posting_cursor ON team_applications (team_posting_id, id);
DROP INDEX idx_team_applications_posting ON team_applications;
