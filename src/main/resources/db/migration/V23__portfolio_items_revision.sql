-- 포트폴리오 구성 교체의 낙관적 락 검사 강제용 카운터 (docs/design/portfolio-module-design.md §8.9)
--
-- replaceItems()는 portfolio_items를 벌크 DELETE한 뒤 다시 넣는다. 이때 Portfolio 엔티티가 dirty가
-- 아니면(병합 결과 개수까지 우연히 그대로면) portfolios UPDATE 자체가 나가지 않아 @Version 검사가
-- 통째로 스킵되고, 그 상태의 벌크 DELETE가 동시에 커밋된 다른 트랜잭션의 구성을 조용히 지운다.
-- 구성 교체 때마다 이 값을 무조건 1 올려 버전 검사를 반드시 태운다 — 값 자체를 읽어 쓰는 곳은 없다.
ALTER TABLE portfolios ADD COLUMN items_revision BIGINT NOT NULL DEFAULT 0 AFTER item_count;
