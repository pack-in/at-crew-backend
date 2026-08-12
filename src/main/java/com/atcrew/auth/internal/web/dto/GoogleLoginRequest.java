package com.atcrew.auth.internal.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Google 로그인 요청")
public record GoogleLoginRequest(
        @NotBlank(message = "Firebase ID Token은 필수입니다")
        @Schema(description = "Firebase Google 로그인으로 발급받은 ID Token (JWT). 검증 실패 시 401 INVALID_FIREBASE_TOKEN",
                example = "eyJhbGciOiJSUzI1NiIsImtpZCI6IjE2N...",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String firebaseIdToken
) {}
