package com.atcrew.auth.internal.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PasswordResetVerifyRequest(
        @NotBlank(message = "이메일을 입력해주세요")
        @Email(message = "올바른 이메일 형식으로 입력해주세요")
        String email,

        @NotBlank(message = "코드를 입력해주세요")
        @Size(min = 6, max = 6, message = "6자리 코드를 입력해주세요")
        String code
) {}
