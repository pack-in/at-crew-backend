-- 이슈 #138 — 데이터 변경 주체("누가")를 행에 남긴다.
--
-- 지금까지 감사 정보는 created_at/updated_at으로 "언제"만 남았다. 주체는 요청 로그의 MDC에만
-- 찍혀서 로그 보존 기간이 지나면 사라졌고, 운영 차단처럼 앱을 거치지 않는 DB 직접 UPDATE는
-- 애초에 흔적이 없었다(docs/operations/moderation-block.md).
--
-- 컬럼은 nullable로 둔다. 기존 행을 'SYSTEM'으로 채우면 실제로 스케줄러가 건드린 행과
-- 구분되지 않으므로, "기록을 시작하기 전"이라는 사실 그대로 NULL로 남긴다.
--
-- VARCHAR(64)인 이유: 주체가 항상 회원(UUID 36자)은 아니다. 인증 주체 없는 변경은 'SYSTEM',
-- 운영자 수동 UPDATE는 'ops:<담당자>' 형식으로 들어간다.

ALTER TABLE artworks            ADD COLUMN last_modified_by VARCHAR(64) NULL;
ALTER TABLE billing_subscriptions ADD COLUMN last_modified_by VARCHAR(64) NULL;
ALTER TABLE banners             ADD COLUMN last_modified_by VARCHAR(64) NULL;
ALTER TABLE companies           ADD COLUMN last_modified_by VARCHAR(64) NULL;
ALTER TABLE media_assets        ADD COLUMN last_modified_by VARCHAR(64) NULL;
ALTER TABLE members             ADD COLUMN last_modified_by VARCHAR(64) NULL;
ALTER TABLE portfolios          ADD COLUMN last_modified_by VARCHAR(64) NULL;
ALTER TABLE job_postings        ADD COLUMN last_modified_by VARCHAR(64) NULL;
ALTER TABLE job_seeking_posts   ADD COLUMN last_modified_by VARCHAR(64) NULL;
ALTER TABLE team_postings       ADD COLUMN last_modified_by VARCHAR(64) NULL;

-- 운영 차단 SQL(docs/operations/moderation-block.md §2)이 artworks와 함께 반드시 건드리는 테이블이라
-- 같이 넣는다. 이쪽은 JPA 감사 대상 엔티티가 아니므로 앱이 채우지 않는다 — 값이 들어가는 유일한
-- 경로가 운영자 수동 UPDATE이고, 그게 이 컬럼을 만든 이유다.
ALTER TABLE portfolio_item_snapshots ADD COLUMN last_modified_by VARCHAR(64) NULL;
