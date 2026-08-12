package com.atcrew.portfolio;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/**
 * 공유 링크로 열람하는 포트폴리오 헤더 정보 (docs/design/portfolio-module-design.md §4).
 *
 * <p>비로그인 응답이라 소유자 식별자(회원 ID·핸들)나 공유 슬러그는 담지 않는다.
 * 담긴 작품은 별도 목록 API(`/shared/{identifier}/artworks`)로 페이지 조회한다.
 */
@Schema(description = "공유 링크로 열람하는 포트폴리오 헤더 — 비로그인 응답이라 소유자 식별자(회원 ID·핸들)와 "
        + "공유 슬러그는 담지 않는다")
public record PortfolioSharedInfo(
        @Schema(description = "포트폴리오 ID (UUIDv7)", example = "019ff383-5804-73eb-955c-af12f650b4ed")
        String id,                     // 포트폴리오 ID

        @Schema(description = "유형 — 작가 페이지 / 공유", example = "SHARED")
        PortfolioKind kind,            // 유형 — 작가 페이지 / 공유

        @Schema(description = "반영 유형 — 최신 반영형 / 고정형", example = "LIVE")
        ReflectionType reflectionType, // 반영 유형 — 최신 반영형 / 고정형

        @Schema(description = "제목 — 작가 페이지(ARTIST_PAGE)는 항상 null이며 화면 헤더에 ownerName을 쓴다",
                example = "라이브 공유 포트폴리오", nullable = true)
        String title,                  // 제목 — 작가 페이지는 null(사용자 이름 헤더를 쓴다)

        @Schema(description = "헤더용 작성자 이름 — 고정형은 생성 시점에 얼린 이름, 그 외는 현재 이름", example = "포폴테스터")
        String ownerName,              // 헤더용 작성자 이름 — 고정형은 생성 시점에 얼린 이름, 그 외는 현재 이름

        @Schema(description = "담긴 작품 수 — 원본이 삭제·휴지통 이동된 작품이 있으면 실제 목록 건수보다 클 수 있다",
                example = "3")
        int itemCount,                 // 담긴 작품 수

        @Schema(description = "생성 시각 (UTC, ISO 8601)", example = "2026-08-12T01:08:19.078822Z")
        Instant createdAt,             // 생성 시각

        @Schema(description = "최종 수정 시각 (UTC, ISO 8601)", example = "2026-08-12T01:08:19.187730Z")
        Instant updatedAt              // 최종 수정 시각
) {
}
