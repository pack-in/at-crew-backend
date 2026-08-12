package com.atcrew.artwork.internal.web.dto;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

@Schema(description = "북마크 폴더 이동 요청")
public record MoveBookmarkRequest(

        @ArraySchema(arraySchema = @Schema(
                description = "이동할 북마크의 작품 ID 목록 (1개 이상). 내 북마크에 없는 ID는 조용히 무시됩니다",
                example = "[\"019ff383-2c6e-7bd5-a131-b5feae3518ca\"]"))
        @NotEmpty
        List<String> artworkIds, // 이동할 작품 ID 목록

        @Schema(description = "이동 대상 폴더 ID. 생략하거나 null이면 기본 폴더(미분류)로 이동합니다",
                example = "019ff383-2ce0-725e-98b5-8be4f4764838", nullable = true)
        String targetFolderId // 이동 대상 폴더 ID
) {
}
