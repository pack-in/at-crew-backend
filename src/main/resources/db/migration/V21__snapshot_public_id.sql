-- 고정형 스냅샷의 외부 공개 식별자 (마이페이지_작가-R37·R39)
--
-- 스냅샷 상세는 portfolioId + snapshotId로 식별하는 독립 자원이라 외부 URL에 식별자가 노출된다.
-- 기존 PK(BIGINT AUTO_INCREMENT)는 "외부에 노출하지 않는 대리키" 전제로 설계된 값이라 그대로 쓸 수 없어
-- (연속 번호로 타인 스냅샷을 열거할 수 있다) 저장소 관례인 UUIDv7 문자열 식별자를 별도로 둔다.
--
-- 기존 행은 dev 데이터뿐이라 MariaDB UUID()로 백필한다 — 값의 정렬성(v7)은 신규 행에만 필요하다.
ALTER TABLE portfolio_item_snapshots
    ADD COLUMN snapshot_public_id VARCHAR(36) CHARACTER SET latin1 COLLATE latin1_bin NULL AFTER portfolio_id;

UPDATE portfolio_item_snapshots SET snapshot_public_id = UUID() WHERE snapshot_public_id IS NULL;

ALTER TABLE portfolio_item_snapshots
    MODIFY COLUMN snapshot_public_id VARCHAR(36) CHARACTER SET latin1 COLLATE latin1_bin NOT NULL;

-- 상세 조회는 (portfolio_id, snapshot_public_id)로 하지만, 유니크는 식별자 단독으로 건다 —
-- 이미 발급된 URL이 항상 같은 스냅샷 하나만 가리켜야 하기 때문이다(R37).
ALTER TABLE portfolio_item_snapshots
    ADD UNIQUE KEY uk_pis_public_id (snapshot_public_id);
