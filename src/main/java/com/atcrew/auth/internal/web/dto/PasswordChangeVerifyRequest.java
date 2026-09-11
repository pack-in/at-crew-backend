package com.atcrew.auth.internal.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record PasswordChangeVerifyRequest(
        @NotBlank(message = "현재 비밀번호를 입력해주세요")
        @Schema(format = "password")
        String currentPassword
) {}
