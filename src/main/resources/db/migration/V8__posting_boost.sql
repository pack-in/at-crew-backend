-- 끌어올리기(boost, docs/design/recruit-module-design.md §2.1.1) — 구인글/팀원모집글 공통.
-- boosted_until은 UTC 기준 Instant로 저장하며, now < boosted_until인 동안 목록 상단에 고정된다.
-- 적용 기간과 쿨다운이 모두 48시간으로 동일해 별도 쿨다운 컬럼은 두지 않는다.

ALTER TABLE job_postings  ADD COLUMN boosted_until DATETIME NULL AFTER view_count;
ALTER TABLE team_postings ADD COLUMN boosted_until DATETIME NULL AFTER view_count;
