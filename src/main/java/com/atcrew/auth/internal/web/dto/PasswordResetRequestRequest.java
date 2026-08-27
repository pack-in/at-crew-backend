package com.atcrew.auth.internal.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record PasswordResetRequestRequest(
        @NotBlank(message = "이메일을 입력해주세요")
        @Email(message = "올바른 이메일 형식으로 입력해주세요")
        String email
) {}
