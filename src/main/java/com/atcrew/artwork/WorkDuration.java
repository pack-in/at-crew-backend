package com.atcrew.artwork;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "작업 소요 시간. 네 항목 모두 선택 입력이며, 전부 미입력이면 작품의 workDuration 자체가 null이 됩니다")
public record WorkDuration(

        @Schema(description = "소요 개월 수. 미입력 시 null", example = "1", nullable = true)
        Integer months, // 소요 개월 수

        @Schema(description = "소요 일 수. 미입력 시 null", example = "2", nullable = true)
        Integer days, // 소요 일 수

        @Schema(description = "소요 시간(hour). 미입력 시 null", example = "3", nullable = true)
        Integer hours, // 소요 시간

        @Schema(description = "소요 분. 미입력 시 null", example = "30", nullable = true)
        Integer minutes // 소요 분
) {
}
