package com.atcrew.artwork;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "북마크 폴더 정보")
public record BookmarkFolderInfo(

        @Schema(description = "폴더 ID (UUIDv7)", example = "019ff383-2ce0-725e-98b5-8be4f4764838")
        String id, // 폴더 ID

        @Schema(description = "폴더명 (최대 20자, 회원 내 중복 불가)", example = "관심 작품")
        String name, // 폴더명

        @Schema(description = "정렬 순서. 생성 시점의 보유 폴더 수가 부여되며 목록은 이 값 오름차순입니다", example = "0")
        int sortOrder, // 정렬 순서

        @Schema(description = "폴더 생성 시각 (UTC ISO-8601)", example = "2026-08-12T01:08:08.032624Z")
        Instant createdAt // 생성 시각
) {
}
