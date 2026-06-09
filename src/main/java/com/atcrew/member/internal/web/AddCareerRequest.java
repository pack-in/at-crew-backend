package com.atcrew.member.internal.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

record AddCareerRequest(
        @NotBlank
        String workTitle,

        String episodeCount,

        @NotBlank
        @Pattern(regexp = "^\\d{4}\\.\\d{2}$", message = "YYYY.MM 형식으로 입력해 주세요")
        String startDate,

        @Pattern(regexp = "^\\d{4}\\.\\d{2}$", message = "YYYY.MM 형식으로 입력해 주세요")
        String endDate,

        boolean ongoing,

        @Size(max = 200)
        String description
) {
}
