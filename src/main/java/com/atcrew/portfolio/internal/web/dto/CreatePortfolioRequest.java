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
        // 담을 작품 ID 목록 — 0개로 생성할 수 있다(복제 시 자동 선택이 0개일 수 있음)
        @Size(max = 100) List<@NotBlank @Size(max = 36) String> artworkIds
) {
}
