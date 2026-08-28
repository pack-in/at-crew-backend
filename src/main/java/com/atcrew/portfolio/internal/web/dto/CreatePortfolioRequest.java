package com.atcrew.portfolio.internal.web.dto;

import com.atcrew.portfolio.ReflectionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 공유 포트폴리오 생성 요청 (docs/design/portfolio-module-design.md §4).
 * 작가 페이지는 생성 API가 없다 — 최초 조회 시 lazy 생성된다.
 */
public record CreatePortfolioRequest(
        // 공유 포트폴리오 제목 — 작가 페이지와 달리 필수다
        @NotBlank @Size(max = 100) String title,
        // 반영 유형 — LIVE(최신 반영형) / SNAPSHOT(고정형)
        @NotNull ReflectionType reflectionType,
        // 담을 작품 ID 목록 — 최소 2개부터 생성할 수 있다(제품 결정, 2026-08-28). 개수 상한은 없다
        // (마이페이지_작가-R37·R38·R46: 상한 없음. 최소 개수는 서비스에서 PORTFOLIO_ARTWORK_MINIMUM으로 검증한다)
        List<@NotBlank @Size(max = 36) String> artworkIds
) {
}
