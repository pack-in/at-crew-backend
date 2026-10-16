package com.atcrew.member.internal.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Email
        String loginEmail,

        // 형식은 DevMemberController가 HandleValidator로 검증한다(가입·변경 API와 같은 오류 코드). 여기서는 길이 상한만.
        @Size(max = 50)
        String handle,

        @NotBlank @Size(max = 16)
        String name
) {
}
