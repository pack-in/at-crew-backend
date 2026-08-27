package com.atcrew.auth.internal.web.dto;

import com.atcrew.member.Language;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record EmailRegisterRequest(
        @NotBlank(message = "이메일을 입력해주세요")
        @Email(message = "올바른 이메일 형식으로 입력해주세요")
        String email,

        @NotBlank(message = "비밀번호를 입력해주세요")
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[^A-Za-z\\d\\s]).{8,64}$",
                 message = "비밀번호는 영문, 숫자, 특수문자를 포함해 8자 이상 입력해주세요")
        @Schema(format = "password")
        String password,

        @Schema(format = "password")
        String passwordConfirm,

        @NotBlank(message = "사용자 이름은 필수입니다")
        @Size(max = 16, message = "사용자 이름은 16자 이하여야 합니다")
        String name,

        boolean agreeService,
        boolean agreePrivacy,
        boolean agreeThirdParty,
        boolean agreeMarketing,

        @NotBlank(message = "시간대를 입력해주세요")
        @Size(max = 64, message = "시간대 값이 올바르지 않습니다")
        @Schema(description = "IANA 시간대 ID, 클라이언트 자동감지값", example = "Asia/Seoul")
        String timezone,

        @NotBlank(message = "거주 국가를 입력해주세요")
        @Pattern(regexp = "^[A-Z]{2}$", message = "국가 코드는 ISO 3166-1 alpha-2 형식이어야 합니다")
        @Schema(description = "거주 국가 (ISO 3166-1 alpha-2)", example = "KR")
        String countryCode,

        @NotNull(message = "주 사용 언어를 선택해주세요")
        @Schema(description = "주 사용 언어 — 가입 후 변경할 수 없습니다(로그인-R19)", example = "KO")
        Language primaryLanguage
) {
    @AssertTrue(message = "비밀번호가 일치하지 않습니다")
    public boolean isPasswordConfirmed() {
        return password != null && password.equals(passwordConfirm);
    }
}
