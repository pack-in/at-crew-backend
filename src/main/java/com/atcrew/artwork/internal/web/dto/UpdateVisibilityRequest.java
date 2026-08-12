package com.atcrew.artwork.internal.web.dto;

import com.atcrew.artwork.Visibility;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "작품 공개 상태 변경 요청")
public record UpdateVisibilityRequest(

        @Schema(description = "변경할 공개 범위 (PUBLIC: 전체 공개, LINK_ONLY: 링크를 아는 사람만, PRIVATE: 비공개)",
                example = "PUBLIC")
        @NotNull
        Visibility visibility // 변경할 공개 범위
) {
}
