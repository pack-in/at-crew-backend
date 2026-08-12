package com.atcrew.auth.internal.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "Google 회원가입 요청. boolean 필드는 값을 생략하면 400(COMMON_INVALID_INPUT)이므로 항상 전송해야 한다")
public record GoogleRegisterRequest(
        @NotBlank(message = "Firebase ID Token은 필수입니다")
        @Schema(description = "Firebase Google 로그인으로 발급받은 ID Token (JWT). 이메일은 토큰에서 추출하므로 별도 전송하지 않는다",
                example = "eyJhbGciOiJSUzI1NiIsImtpZCI6IjE2N...",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String firebaseIdToken,

        @NotBlank(message = "사용자 이름은 필수입니다")
        @Size(max = 16, message = "사용자 이름은 16자 이하여야 합니다")
        @Schema(description = "사용자 이름·작가명 (최대 16자)", example = "김창작",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String name,

        @Schema(description = "서비스 이용약관 동의 (필수). false면 400 TERMS_NOT_AGREED", example = "true",
                requiredMode = Schema.RequiredMode.REQUIRED)
        boolean agreeService,

        @Schema(description = "개인정보 처리방침 동의 (필수). false면 400 TERMS_NOT_AGREED", example = "true",
                requiredMode = Schema.RequiredMode.REQUIRED)
        boolean agreePrivacy,

        @Schema(description = "개인정보 제3자 제공 동의 (필수). false면 400 TERMS_NOT_AGREED", example = "true",
                requiredMode = Schema.RequiredMode.REQUIRED)
        boolean agreeThirdParty,

        @Schema(description = "마케팅 정보 수신 동의 (선택). false여도 가입되지만 값 자체는 전송해야 한다",
                example = "false", requiredMode = Schema.RequiredMode.REQUIRED)
        boolean agreeMarketing,

        @NotBlank(message = "시간대를 입력해주세요")
        @Size(max = 64, message = "시간대 값이 올바르지 않습니다")
        @Schema(description = "IANA 시간대 ID, 클라이언트 자동감지값. 목록에 없는 값이면 400 INVALID_TIMEZONE",
                example = "Asia/Seoul", requiredMode = Schema.RequiredMode.REQUIRED)
        String timezone,

        @NotBlank(message = "거주 국가를 입력해주세요")
        @Pattern(regexp = "^[A-Z]{2}$", message = "국가 코드는 ISO 3166-1 alpha-2 형식이어야 합니다")
        @Schema(description = "거주 국가 (ISO 3166-1 alpha-2 대문자). 실존하지 않는 코드면 400 INVALID_COUNTRY",
                example = "KR", requiredMode = Schema.RequiredMode.REQUIRED)
        String countryCode
) {}
