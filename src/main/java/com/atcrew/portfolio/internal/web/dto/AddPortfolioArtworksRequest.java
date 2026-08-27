package com.atcrew.portfolio.internal.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 포트폴리오에 작품 추가 요청 (docs/design/portfolio-module-design.md §4).
 */
public record AddPortfolioArtworksRequest(
        // 추가할 작품 ID 목록 — 이미 담긴 작품은 무시된다. 개수 상한은 없다
        // (마이페이지_작가-R37·R38·R46: 포트폴리오·작품 선택 개수 제한 없음)
        @NotEmpty List<@NotBlank @Size(max = 36) String> artworkIds
) {
}
