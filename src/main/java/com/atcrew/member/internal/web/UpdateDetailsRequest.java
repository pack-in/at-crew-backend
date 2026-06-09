package com.atcrew.member.internal.web;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

import java.util.List;

record UpdateDetailsRequest(
        @Size(max = 100)
        String location,

        @Email
        String contactEmail,

        String socialMediaLink,
        String twitter,
        String creativeTools,

        @Size(max = 20)
        List<String> keywords
) {
}
