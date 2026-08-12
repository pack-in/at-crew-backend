-- 완전 비공개(피드 비공개 + 어느 포트폴리오에도 미포함) 판정을 artwork 안에서 끝내기 위한 비정규화 컬럼.
-- artwork → portfolio 참조는 순환 의존이 되므로, portfolio 모듈이 같은 트랜잭션에서
-- ArtworkService.updatePortfolioInclusion()으로 이 값을 동기 갱신한다
-- (docs/design/portfolio-module-design.md §1.2, §5.4).
-- 라이브 멤버십(작가 페이지 + 최신 반영형)만 반영하며 고정형(SNAPSHOT)은 반영하지 않는다.

ALTER TABLE artworks
    ADD COLUMN portfolio_included TINYINT(1) NOT NULL DEFAULT 0 AFTER visibility;
