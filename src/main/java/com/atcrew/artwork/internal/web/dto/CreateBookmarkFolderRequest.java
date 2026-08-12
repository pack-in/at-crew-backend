package com.atcrew.artwork.internal.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "북마크 폴더 생성 요청")
public record CreateBookmarkFolderRequest(

        @Schema(description = "폴더명 (최대 20자). 앞뒤 공백은 제거되어 저장되며, 같은 회원 안에서 중복될 수 없습니다",
                example = "관심 작품")
        @NotBlank @Size(max = 20)
        String name // 폴더명
) {
}
