package com.atcrew.artwork.internal.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "북마크 저장 요청")
public record SaveBookmarkRequest(

        @Schema(description = "북마크할 작품 ID. 열람 가능한 READY 상태 작품만 저장할 수 있습니다",
                example = "019ff383-2c6e-7bd5-a131-b5feae3518ca")
        @NotBlank
        String artworkId, // 작품 ID

        @Schema(description = "저장할 폴더 ID (선택). 생략하거나 null이면 기본 폴더(미분류)에 저장됩니다",
                example = "019ff383-2ce0-725e-98b5-8be4f4764838", nullable = true)
        String folderId // 폴더 ID
) {
}
