package com.atcrew.member.internal.web.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record AddCareerRequest(
        @NotBlank
        String workTitle,

        String role,

        @NotNull
        @JsonFormat(pattern = "yyyy.MM.dd")
        LocalDate startDate,

        @JsonFormat(pattern = "yyyy.MM.dd")
        LocalDate endDate,

        boolean ongoing,

        @Size(max = 200)
        String description
) {
}
