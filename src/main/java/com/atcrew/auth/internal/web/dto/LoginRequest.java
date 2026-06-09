package com.atcrew.auth.internal.web.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank(message = "Firebase ID Token은 필수입니다") String firebaseIdToken
) {}
