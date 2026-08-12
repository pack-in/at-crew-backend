package com.atcrew.artwork.internal.web.dto;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

import java.util.List;

@Schema(description = "작품에 사용한 소재 입력")
public record MaterialRequest(

        @Schema(description = "소재명 (필수)", example = "겨울 배경 브러시셋")
        @NotBlank
        String name, // 소재명

        @ArraySchema(arraySchema = @Schema(description = "소재를 적용한 대상 목록 (선택)",
                example = "[\"배경\", \"이펙트\"]", nullable = true))
        List<String> targets, // 적용 대상 목록

        @ArraySchema(arraySchema = @Schema(
                description = "소재 첨부 이미지 R2 key 목록 (선택). Presigned URL로 업로드한 key를 넣습니다",
                example = "[\"raw/019ff383-2c63-765b-af15-8a2c870a29ec.jpg\"]", nullable = true))
        List<String> attachmentKeys, // 첨부 이미지 key 목록

        @ArraySchema(arraySchema = @Schema(description = "외부 소재 판매·출처 URL 목록 (선택)",
                example = "[\"https://www.acon3d.com/product/12345\"]", nullable = true))
        List<String> links // 외부 소재 URL 목록
) {
}
