package com.atcrew.artwork.internal.web.dto;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

@Schema(description = "휴지통 작품 영구 삭제 요청")
public record DeleteArtworkRequest(

        @ArraySchema(arraySchema = @Schema(
                description = "영구 삭제할 작품 ID 목록 (1개 이상). 전부 본인 소유이면서 휴지통에 있어야 하며, "
                        + "하나라도 조건을 만족하지 않으면 전체가 삭제되지 않습니다",
                example = "[\"019ff383-2c6e-7bd5-a131-b5feae3518ca\"]"))
        @NotEmpty
        List<String> artworkIds // 영구 삭제할 작품 ID 목록
) {
}
