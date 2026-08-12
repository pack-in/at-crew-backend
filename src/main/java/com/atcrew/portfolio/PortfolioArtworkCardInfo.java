package com.atcrew.portfolio;

import com.atcrew.artwork.AgeRating;
import com.atcrew.artwork.ArtworkField;
import com.atcrew.artwork.Visibility;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/**
 * 포트폴리오에 담긴 작품 1건의 카드 표시 정보 (docs/design/portfolio-module-design.md §4).
 *
 * <p>최신 반영형(LIVE)·작가 페이지는 원본 작품에서 조회 시점에 채운다.
 */
@Schema(description = "포트폴리오에 담긴 작품 1건의 카드 정보 — 최신 반영형·작가 페이지는 원본 작품을 조회 시점에 "
        + "읽고, 고정형은 생성 시점에 복사해 둔 값을 그대로 쓴다")
public record PortfolioArtworkCardInfo(
        @Schema(description = "원본 작품 ID (UUIDv7) — 작품 상세 API 호출에 쓴다",
                example = "019ff382-bd4a-7045-80ac-7430bd0832c7")
        String artworkId,          // 원본 작품 ID

        @Schema(description = "작품 제목 — 고정형은 포트폴리오 생성 시점의 제목", example = "작품 1")
        String title,              // 작품 제목

        @Schema(description = "카드 썸네일 R2 키 — 사용자 지정 썸네일이 있으면 그 값, 없으면 대표 이미지의 썸네일. "
                + "이미지 처리(Worker)가 끝나기 전이면 null",
                example = "thumb/019ff382-bd4a-7045-80ac-7430bd0832c7.avif", nullable = true)
        String thumbKey,           // 카드 썸네일 R2 키 — 사용자 지정 썸네일 우선, 없으면 대표 이미지 썸네일

        @Schema(description = "성인 블러 썸네일 R2 키 — 사용자 지정 썸네일을 쓰거나 이미지 처리 전이면 null",
                example = "thumb-adult/019ff382-bd4a-7045-80ac-7430bd0832c7.avif", nullable = true)
        String thumbAdultKey,      // 성인 블러 썸네일 R2 키 — 사용자 지정 썸네일을 쓰는 경우 null

        @Schema(description = "연령 등급", example = "ALL")
        AgeRating ageRating,       // 연령 등급

        @Schema(description = "작품 분야", example = "ILLUSTRATION")
        ArtworkField artworkField, // 작품 분야

        @Schema(description = "원본 작품의 공개 범위 — 고정형은 원본 변경에 영향받지 않아야 하므로 항상 PUBLIC으로 내려간다",
                example = "PUBLIC")
        Visibility visibility,     // 원본 작품의 공개 범위

        @Schema(description = "원본 작품 등록 시각 (UTC, ISO 8601) — 포트폴리오 내 정렬(업로드순) 기준",
                example = "2026-08-12T01:07:39.466565Z")
        Instant createdAt          // 원본 작품 등록 시각 — 포트폴리오 내 정렬(업로드순) 기준
) {
}
