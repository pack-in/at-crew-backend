package com.atcrew.auth.internal.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterAuthRequest(
        @NotBlank(message = "Firebase ID Token은 필수입니다") String firebaseIdToken,
        @NotBlank(message = "사용자 이름은 필수입니다")
        @Size(max = 16, message = "사용자 이름은 16자 이하여야 합니다") String name,
        boolean agreePrivacy,
        boolean agreeService,
        boolean agreeMarketing
) {}
