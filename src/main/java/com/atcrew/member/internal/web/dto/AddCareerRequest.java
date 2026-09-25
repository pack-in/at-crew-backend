package com.atcrew.member.internal.web.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record AddCareerRequest(
        @NotBlank @Size(max = 100)
        String workTitle,

        @Size(max = 100)
        String role,

        // 미래 날짜 여부는 회원 시간대 기준으로 도메인에서 검증한다 — @PastOrPresent는 서버 시간대(UTC)의 오늘을 쓴다
        @NotNull
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
