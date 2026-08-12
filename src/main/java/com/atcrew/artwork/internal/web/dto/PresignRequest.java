package com.atcrew.artwork.internal.web.dto;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

@Schema(description = "이미지 Presigned URL 발급 요청")
public record PresignRequest(

        @Schema(description = "발급받을 URL 개수 (1~20). contentTypes 길이와 같아야 합니다", example = "2")
        @Min(1) @Max(20)
        int count, // 발급 개수

        @ArraySchema(arraySchema = @Schema(
                description = "업로드할 이미지의 Content-Type 목록 (image/jpeg, image/png, image/webp만 허용). "
                        + "count와 같은 길이여야 하며, 순서대로 응답의 각 Presigned URL에 대응합니다",
                example = "[\"image/jpeg\", \"image/png\"]"))
        @NotEmpty @Size(max = 20)
        List<String> contentTypes // Content-Type 목록
) {
}
