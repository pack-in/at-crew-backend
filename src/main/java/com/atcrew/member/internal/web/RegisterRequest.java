package com.atcrew.member.internal.web;

import com.atcrew.member.CreatorRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

record RegisterRequest(
        @NotBlank @Email
        String loginEmail,

        @NotBlank @Pattern(regexp = "^[a-zA-Z0-9_-]{3,30}$", message = "영문·숫자·_·- 3~30자")
        String handle,

        @NotBlank @Size(max = 16)
        String name,

        @NotNull
        CreatorRole creatorRole
) {
}
