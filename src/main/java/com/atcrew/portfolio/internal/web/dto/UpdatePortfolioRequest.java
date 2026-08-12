package com.atcrew.portfolio.internal.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 포트폴리오 수정 요청 (docs/design/portfolio-module-design.md §4). 두 필드 모두 부분 업데이트라
 * null이면 해당 항목을 건드리지 않는다.
 */
public record UpdatePortfolioRequest(
        // 제목 — null이면 유지. 작가 페이지에 값을 보내면 ARTIST_PAGE_TITLE_IMMUTABLE로 거부된다
        @Size(max = 100) String title,
        // 구성 작품 ID 목록 — null이면 유지, 빈 배열이면 전부 비운다. 개수 상한은 없다
        // (마이페이지_작가-R37·R38·R46: 포트폴리오·작품 선택 개수 제한 없음)
        List<@NotBlank @Size(max = 36) String> artworkIds
) {
}
