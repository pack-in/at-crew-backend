package com.atcrew.portfolio.internal.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 포트폴리오에 작품 추가 요청 (docs/design/portfolio-module-design.md §4).
 */
public record AddPortfolioArtworksRequest(
        // 추가할 작품 ID 목록 — 이미 담긴 작품은 무시된다
        @NotEmpty @Size(max = 100) List<@NotBlank @Size(max = 36) String> artworkIds
) {
}
