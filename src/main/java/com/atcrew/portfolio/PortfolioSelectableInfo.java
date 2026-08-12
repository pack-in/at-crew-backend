package com.atcrew.portfolio;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 작품 업로드·수정 화면에서 고를 수 있는 포트폴리오 1건 (docs/design/portfolio-module-design.md §4).
 *
 * <p>작가 페이지와 최신 반영형만 대상이며 고정형은 제외된다 — 고정형은 생성 시점에 구성이 얼어붙는다.
 * 플랜별 선택 가능 여부는 프론트가 판단하므로 여기서는 게이팅 없이 본인 소유 전부를 내려준다.
 */
@Schema(description = "작품 업로드·수정 화면의 포트폴리오 선택 항목 1건")
public record PortfolioSelectableInfo(
        @Schema(description = "포트폴리오 ID (UUIDv7)", example = "019ff383-5804-73eb-955c-af12f650b4ed")
        String id,           // 포트폴리오 ID

        @Schema(description = "유형 — 작가 페이지 / 공유. 작가 페이지가 항상 목록 맨 앞에 온다", example = "ARTIST_PAGE")
        PortfolioKind kind,  // 유형 — 작가 페이지 / 공유

        @Schema(description = "제목 — 작가 페이지(ARTIST_PAGE)는 항상 null이며 화면 헤더에 사용자 이름을 쓴다",
                example = "라이브 공유 포트폴리오", nullable = true)
        String title,        // 제목 — 작가 페이지는 null(사용자 이름 헤더를 쓴다)

        @Schema(description = "담긴 작품 수", example = "3")
        int itemCount        // 담긴 작품 수
) {
}
