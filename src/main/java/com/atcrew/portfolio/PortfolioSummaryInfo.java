package com.atcrew.portfolio;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

/**
 * 내 포트폴리오 목록의 카드 1건 (docs/design/portfolio-module-design.md §4).
 */
@Schema(description = "내 포트폴리오 목록 카드 1건 — 상세와 달리 작품 전체가 아니라 커버 썸네일만 담는다")
public record PortfolioSummaryInfo(
        @Schema(description = "포트폴리오 ID (UUIDv7)", example = "019ff383-5804-73eb-955c-af12f650b4ed")
        String id,                                          // 포트폴리오 ID

        @Schema(description = "유형 — 작가 페이지 / 공유", example = "ARTIST_PAGE")
        PortfolioKind kind,                                 // 유형 — 작가 페이지 / 공유

        @Schema(description = "반영 유형 — 최신 반영형 / 고정형", example = "LIVE")
        ReflectionType reflectionType,                      // 반영 유형 — 최신 반영형 / 고정형

        @Schema(description = "제목 — 작가 페이지(ARTIST_PAGE)는 항상 null이며 화면 헤더에 사용자 이름을 쓴다",
                example = "라이브 공유 포트폴리오", nullable = true)
        String title,                                       // 제목 — 작가 페이지는 null(사용자 이름 헤더를 쓴다)

        @Schema(description = "공유 링크 슬러그(base64url 22자) — 공유 포트폴리오만 발급되고 작가 페이지는 null",
                example = "Ry-yvvhvG1lm-3DqjG64nQ", nullable = true)
        String shareSlug,                                   // 공유 링크 슬러그 — 공유 포트폴리오만, 작가 페이지는 null

        @Schema(description = "담긴 작품 수 — 카드의 \"N개\" 표기용", example = "3")
        int itemCount,                                      // 담긴 작품 수 — 카드의 "N개" 표기용

        @Schema(description = "카드 커버 2x2 썸네일 — 업로드 오래된순 최대 4개. 4개 미만이면 있는 만큼만, "
                + "담긴 작품이 없으면 빈 배열이다")
        List<PortfolioCoverThumbnailInfo> coverThumbnails,  // 카드 커버 2x2 썸네일 — 업로드 오래된순 최대 4개

        @Schema(description = "생성 시각 (UTC, ISO 8601) — OLDEST/LATEST 정렬 커서의 기준값",
                example = "2026-08-12T01:08:19.078822Z")
        Instant createdAt,                                  // 생성 시각

        @Schema(description = "최종 수정 시각 (UTC, ISO 8601) — UPDATED 정렬 커서의 기준값",
                example = "2026-08-12T01:08:19.187730Z")
        Instant updatedAt                                   // 최종 수정 시각
) {
}
