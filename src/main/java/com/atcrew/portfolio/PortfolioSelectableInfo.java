package com.atcrew.portfolio;

/**
 * 작품 업로드·수정 화면에서 고를 수 있는 포트폴리오 1건 (docs/design/portfolio-module-design.md §4).
 *
 * <p>작가 페이지와 최신 반영형만 대상이며 고정형은 제외된다 — 고정형은 생성 시점에 구성이 얼어붙는다.
 * 플랜별 선택 가능 여부는 프론트가 판단하므로 여기서는 게이팅 없이 본인 소유 전부를 내려준다.
 */
public record PortfolioSelectableInfo(
        String id,           // 포트폴리오 ID
        PortfolioKind kind,  // 유형 — 작가 페이지 / 공유
        String title,        // 제목 — 작가 페이지는 null(사용자 이름 헤더를 쓴다)
        int itemCount        // 담긴 작품 수
) {
}
