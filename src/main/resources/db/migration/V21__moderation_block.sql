-- 운영 차단(모더레이션) 컬럼 (마이페이지_작가-R39·R41·R42·R46, 휴지통-R04)
--
-- 저작권 침해 신고·운영자 강제삭제·불법 콘텐츠 판정 등 운영 정책/법적 조치로 외부 노출을 중단한 상태다.
-- 사용자 삭제(휴지통, artworks.status/deleted_at)와는 구분되며 "고정형 상태 고정" 설정보다 우선한다.
--
-- 관리자 Role·API는 로드맵 8번(다음 마일스톤) 범위라 이번에는 컬럼과 판정 로직만 둔다 —
-- 실제 차단은 DB 직접 UPDATE로 수행한다(운영 절차는 docs/operations/moderation-block.md).
-- portfolios.blocked_at(V17)은 회원 탈퇴 차단용이라 별개 축이다.

ALTER TABLE artworks
    ADD COLUMN blocked_at DATETIME(6) NULL AFTER portfolio_included;

-- 스냅샷 행에도 비정규화한다 — 원본 역조회 방식은 원본을 영구 삭제하면 차단 판정 근거가 사라진다.
ALTER TABLE portfolio_item_snapshots
    ADD COLUMN blocked_at DATETIME(6) NULL AFTER payload_json;
