package com.atcrew.member.internal.web.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record AddCareerRequest(
        @NotBlank
        String workTitle,

        String role,

        @NotNull
        @PastOrPresent(message = "작업 시작일은 미래 날짜일 수 없습니다")
        @JsonFormat(pattern = "yyyy.MM.dd")
        LocalDate startDate,

        // ISO 8601(yyyy-MM-dd)이 아닌 비표준 포맷 사용 + 연재중이면 null
        @Schema(description = "작업 종료일 (yyyy.MM.dd). 연재중이면 null", example = "2024.06.30", nullable = true)
        @JsonFormat(pattern = "yyyy.MM.dd")
        LocalDate endDate,

        boolean ongoing,

        @Size(max = 200)
        String description
) {
}
