package com.atcrew.member.internal.web.dto;

import jakarta.validation.constraints.Size;

public record UpdateDetailsRequest(
        @Size(max = 100)
        String contact,

        @Size(max = 200)
        String sns,

        String tools
) {
}
