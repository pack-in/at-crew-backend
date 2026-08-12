package com.atcrew.auth.internal.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "토큰 갱신 요청")
public record RefreshRequest(
        @NotBlank(message = "Refresh Token은 필수입니다")
        @Schema(description = "로그인·회원가입 응답으로 받은 Refresh Token. 1회만 사용할 수 있고 갱신 성공 시 폐기된다",
                example = "eyJhbGciOiJIUzUxMiJ9.eyJzdWIiOiIwMTlmZjM4MS04MTU2LTc3MjAtOGFmNy0zZmMzNDU3NGJjYzYiLCJ0eXBlIjoicmVmcmVzaCJ9.signature",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String refreshToken
) {}
