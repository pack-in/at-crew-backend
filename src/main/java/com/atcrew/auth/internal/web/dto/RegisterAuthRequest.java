package com.atcrew.auth.internal.web.dto;

import com.atcrew.member.AccountType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RegisterAuthRequest(
        @NotBlank(message = "Firebase ID Token은 필수입니다") String firebaseIdToken,
        @NotNull(message = "계정 유형은 필수입니다") AccountType accountType,
        @Size(max = 50, message = "기업명은 50자 이하여야 합니다") String companyName,
        boolean agreePrivacy,
        boolean agreeService,
        boolean agreeMarketing
) {}
