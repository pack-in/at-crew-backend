package com.atcrew.member.internal.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "이름 수정 요청")
public record UpdateNameRequest(
        @NotBlank
        @Size(max = 16)
        @Schema(description = "변경할 사용자 이름·작가명 (1~16자, 공백만 입력 불가)", example = "김창작",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String name
) {
}
