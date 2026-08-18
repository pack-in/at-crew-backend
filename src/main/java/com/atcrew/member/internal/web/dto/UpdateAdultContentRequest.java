package com.atcrew.member.internal.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record UpdateAdultContentRequest(
        // 원시 boolean이면 필드 누락이 false로 조용히 해석되므로 Boolean + @NotNull로 명시적 전송을 강제한다.
        @NotNull(message = "표시 여부를 입력해주세요")
        @Schema(description = "성인 콘텐츠 표시 여부", example = "true")
        Boolean visible
) {
}
