package com.atcrew.artwork;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "북마크 항목 정보")
public record BookmarkEntryInfo(

        @Schema(description = "북마크 ID (UUIDv7)", example = "019ff383-2cfd-767b-a0a2-ffb2af8ca611")
        String id, // 북마크 ID

        @Schema(description = "북마크한 작품 ID", example = "019ff383-2c6e-7bd5-a131-b5feae3518ca")
        String artworkId, // 작품 ID

        @Schema(description = "북마크가 속한 폴더 ID. 기본 폴더(미분류)에 있으면 null",
                example = "019ff383-2ce0-725e-98b5-8be4f4764838", nullable = true)
        String folderId, // 폴더 ID

        @Schema(description = "북마크 저장 시각 (UTC ISO-8601). 북마크 목록 커서 값은 이 시각의 epoch milli입니다",
                example = "2026-08-12T01:08:08.061855Z")
        Instant savedAt, // 저장 시각

        @Schema(description = "북마크한 작품의 요약 정보")
        ArtworkSummaryInfo artwork // 작품 요약 정보
) {
}
