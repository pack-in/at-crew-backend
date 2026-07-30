package com.atcrew.auth.internal.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record GoogleRegisterRequest(
        @NotBlank(message = "Firebase ID Token은 필수입니다") String firebaseIdToken,
        @NotBlank(message = "사용자 이름은 필수입니다")
        @Size(max = 16, message = "사용자 이름은 16자 이하여야 합니다") String name,
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
        String countryCode
) {}
