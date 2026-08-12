-- 포트폴리오 "업데이트순" 정렬 기준 컬럼 (마이페이지_작가-R37)
--
-- updated_at은 @LastModifiedDate라 엔티티가 더러워질 때마다 갱신된다 — 작품 추가/제거 API나
-- 원본 작품 변경에 따른 정합성 재계산(PortfolioMembershipReconciler)처럼 사용자가 [수정하기]를
-- 누르지 않은 변경에도 순서가 바뀌었다. [수정하기](PATCH /api/portfolios/{id}) 시점만 기록하는
-- 별도 컬럼을 두고 정렬·커서가 이 컬럼을 쓰게 한다.
--
-- 기존 행은 마지막으로 알려진 수정 시각(updated_at)으로 채운다 — 정렬 기준을 옮기는 것뿐이라
-- 이 값이 가장 근사하다.
ALTER TABLE portfolios ADD COLUMN last_edited_at DATETIME(6) NULL AFTER updated_at;
UPDATE portfolios SET last_edited_at = updated_at WHERE last_edited_at IS NULL;
ALTER TABLE portfolios MODIFY COLUMN last_edited_at DATETIME(6) NOT NULL;

-- 정렬·커서 조회용. updated_at 기준 인덱스는 더 이상 쓰이지 않으므로 함께 정리한다.
CREATE INDEX idx_pf_owner_edited ON portfolios (owner_member_id, last_edited_at DESC, id);
DROP INDEX idx_pf_owner_updated ON portfolios;
