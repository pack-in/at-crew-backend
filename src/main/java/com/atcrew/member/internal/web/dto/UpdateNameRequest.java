package com.atcrew.member.internal.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateNameRequest(
        @NotBlank
        @Size(max = 16)
        String name
) {
}
