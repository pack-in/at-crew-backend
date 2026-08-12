package com.atcrew.portfolio.internal.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 포트폴리오 수정 요청 (docs/design/portfolio-module-design.md §4). 두 필드 모두 부분 업데이트라
 * null이면 해당 항목을 건드리지 않는다.
 */
@Schema(description = "포트폴리오 수정 요청 — 두 필드 모두 부분 업데이트라 null이면 해당 항목을 건드리지 않는다")
public record UpdatePortfolioRequest(
        @Schema(description = "제목 (최대 100자) — null이면 유지. 작가 페이지에 값을 보내면 "
                + "400(ARTIST_PAGE_TITLE_IMMUTABLE)으로 거부된다. 공백만 보내면 400(INVALID_PORTFOLIO_TITLE)",
                example = "라이브 제목 수정", nullable = true)
        @Size(max = 100) String title,
        // 제목 — null이면 유지. 작가 페이지에 값을 보내면 ARTIST_PAGE_TITLE_IMMUTABLE로 거부된다

        @Schema(description = "구성 작품 ID 목록 (최대 100개) — null이면 유지, 빈 배열이면 전부 비운다. "
                + "부분 수정이 아니라 전체 교체이며 본인 소유의 삭제되지 않은 작품이어야 한다(아니면 404). "
                + "실제 순서는 요청 순서가 아니라 작품 업로드순(오래된순)으로 정렬된다",
                example = "[\"019ff382-bd56-7e7e-830b-40e3fbd6a0d3\"]", nullable = true)
        @Size(max = 100) List<@NotBlank @Size(max = 36) String> artworkIds
        // 구성 작품 ID 목록 — null이면 유지, 빈 배열이면 전부 비운다
) {
}
