package com.atcrew.auth.internal.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "이메일 회원가입 요청. boolean 필드는 값을 생략하면 400(COMMON_INVALID_INPUT)이므로 항상 전송해야 한다")
public record EmailRegisterRequest(
        @NotBlank(message = "이메일을 입력해주세요")
        @Email(message = "올바른 이메일 형식으로 입력해주세요")
        @Schema(description = """
                로그인 이메일. 이미 이메일 방식으로 가입된 주소면 409 DUPLICATE_EMAIL
                (중복은 가입 경로별로 판단하므로 Google로만 가입된 주소는 여기서 다시 가입할 수 있다)""",
                example = "creator@example.com", requiredMode = Schema.RequiredMode.REQUIRED)
        String email,

        @NotBlank(message = "비밀번호를 입력해주세요")
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[^A-Za-z\\d\\s]).{8,64}$",
                 message = "비밀번호는 영문, 숫자, 특수문자를 포함해 8자 이상 입력해주세요")
        @Schema(description = "비밀번호 (영문·숫자·특수문자 조합 8~64자)", example = "Secure1!",
                format = "password", requiredMode = Schema.RequiredMode.REQUIRED)
        String password,

        @Schema(description = "비밀번호 확인. password와 다르면 400", example = "Secure1!",
                format = "password", requiredMode = Schema.RequiredMode.REQUIRED)
        String passwordConfirm,

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
) {
    // 검증 전용 파생 메서드 — 요청 필드가 아니므로 Swagger 스키마에서 숨긴다
    @Schema(hidden = true)
    @AssertTrue(message = "비밀번호가 일치하지 않습니다")
    public boolean isPasswordConfirmed() {
        return password != null && password.equals(passwordConfirm);
    }
}
