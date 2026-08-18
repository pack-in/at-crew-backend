package com.atcrew.auth.internal.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ChangePasswordRequest(
        @NotBlank(message = "현재 비밀번호를 입력해주세요")
        @Schema(format = "password")
        String currentPassword,

        // 정책은 이메일 회원가입(EmailRegisterRequest)과 동일하게 유지한다.
        @NotBlank(message = "새 비밀번호를 입력해주세요")
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[^A-Za-z\\d\\s]).{8,64}$",
                 message = "비밀번호는 영문, 숫자, 특수문자를 포함해 8자 이상 입력해주세요")
        @Schema(format = "password")
        String newPassword,

        @Schema(format = "password")
        String newPasswordConfirm
) {
    @AssertTrue(message = "비밀번호가 일치하지 않습니다")
    public boolean isNewPasswordConfirmed() {
        return newPassword != null && newPassword.equals(newPasswordConfirm);
    }
}
