package com.atcrew.company.internal.web.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record AddCompanyCareerRequest(
        @Schema(description = "작품 이름 (최대 100자)", example = "홍길동전")
        @NotBlank
        @Size(max = 100)
        String workTitle, // 작품 이름

        @Schema(description = "작업 시작일 (yyyy.MM.dd)", example = "2024.01.01")
        // 미래 날짜 여부는 회원 시간대 기준으로 도메인에서 검증한다 — @PastOrPresent는 서버 시간대(UTC)의 오늘을 쓴다
        @NotNull
        @JsonFormat(pattern = "yyyy.MM.dd")
        LocalDate startDate, // 작업 시작일

        // ISO 8601(yyyy-MM-dd)이 아닌 비표준 포맷 사용 + 연재중이면 null
        @Schema(description = "작업 종료일 (yyyy.MM.dd). 연재중이면 null", example = "2024.06.30", nullable = true)
        @JsonFormat(pattern = "yyyy.MM.dd")
        LocalDate endDate, // 작업 종료일

        @Schema(description = "연재중 여부. true면 종료일을 보낼 수 없다")
        boolean ongoing, // 연재중 여부

        @Schema(description = "작품 관련 링크나 설명 (최대 200자)")
        @Size(max = 200)
        String description // 작품 설명
) {
}
