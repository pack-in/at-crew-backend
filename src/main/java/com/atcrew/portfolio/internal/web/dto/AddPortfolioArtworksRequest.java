package com.atcrew.portfolio.internal.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 포트폴리오에 작품 추가 요청 (docs/design/portfolio-module-design.md §4).
 */
@Schema(description = "포트폴리오에 작품 추가 요청 — 기존 구성에 이어 붙인 뒤 업로드순으로 다시 정렬된다")
public record AddPortfolioArtworksRequest(
        @Schema(description = "추가할 작품 ID 목록 (필수, 1~100개) — 이미 담긴 작품은 무시된다. "
                + "본인 소유의 삭제되지 않은 작품이어야 하며 하나라도 아니면 404(ARTWORK_NOT_FOUND)",
                example = "[\"019ff382-bd4a-7045-80ac-7430bd0832c7\",\"019ff382-bd56-7e7e-830b-40e3fbd6a0d3\"]",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotEmpty @Size(max = 100) List<@NotBlank @Size(max = 36) String> artworkIds
        // 추가할 작품 ID 목록 — 이미 담긴 작품은 무시된다
) {
}
