package com.atcrew.member.internal.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Email
        String loginEmail,

        @NotBlank @Pattern(regexp = "^[a-zA-Z0-9_-]{3,30}$", message = "영문·숫자·_·- 3~30자")
        String handle,

        @NotBlank @Size(max = 16)
        String name
) {
}
