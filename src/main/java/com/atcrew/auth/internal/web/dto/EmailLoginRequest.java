package com.atcrew.auth.internal.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

@Schema(description = "이메일 로그인 요청")
public record EmailLoginRequest(
        @NotBlank(message = "이메일을 입력해주세요")
        @Email(message = "올바른 이메일 형식으로 입력해주세요")
        @Schema(description = "가입 시 사용한 로그인 이메일", example = "creator@example.com",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String email,

        @NotBlank(message = "비밀번호를 입력해주세요")
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[^A-Za-z\\d\\s]).{8,64}$",
                 message = "비밀번호는 영문, 숫자, 특수문자를 포함해 8자 이상 입력해주세요")
        @Schema(description = "비밀번호 (영문·숫자·특수문자 조합 8~64자). 형식이 어긋나면 400, 계정 불일치는 401",
                example = "Secure1!", format = "password",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String password
) {}
