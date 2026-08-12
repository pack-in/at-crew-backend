package com.atcrew.community;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "커뮤니티 상단 배너")
public record BannerInfo(
        @Schema(description = "배너 ID (UUIDv7)", example = "019ff382-ccdc-71bb-bccb-6a3c35d33978")
        String id,

        @Schema(description = "배너를 등록한 회원 ID", example = "019ff382-ccc3-7c5d-b937-385d1da00d6f")
        String memberId,

        @Schema(description = "배너 이미지 URL", example = "https://cdn.atcrew.com/banners/spring-event.png")
        String imageUrl,

        @Schema(description = "배너 클릭 시 이동할 링크 URL", example = "https://atcrew.com/events/spring")
        String linkUrl,

        @Schema(description = "노출 순서 — 0부터 시작하며 목록은 이 값 오름차순으로 정렬된다. "
                + "삭제된 배너의 자리는 회수되지 않아 번호가 비어 있을 수 있다", example = "0")
        int sortOrder,

        @Schema(description = "배너 상태 — 목록 조회 응답에는 ACTIVE만 포함된다", example = "ACTIVE")
        BannerStatus status,

        @Schema(description = "등록 시각 (UTC, ISO 8601)", example = "2026-08-12T01:07:43.452980Z")
        Instant createdAt
) {
}
