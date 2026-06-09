package com.atcrew.member.internal.web;

import jakarta.validation.constraints.Size;

record UpdateDetailsRequest(
        @Size(max = 100)
        String contact,

        @Size(max = 200)
        String sns,

        String tools
) {
}
