package com.atcrew.auth.internal.web.dto;

import com.atcrew.member.Language;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record GoogleRegisterRequest(
        @NotBlank(message = "Google ID Token은 필수입니다") String googleIdToken,
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
        String countryCode,

        @NotNull(message = "주 사용 언어를 선택해주세요")
        @Schema(description = "주 사용 언어 — 가입 후 변경할 수 없습니다(로그인-R19)", example = "KO")
        Language primaryLanguage,

        // TODO(PA-14): FE 배포 후 필수(@NotBlank)로 전환한다. 형식은 member 모듈 HandleValidator가 검증한다.
        @Size(max = 50, message = "사용자 아이디가 너무 깁니다")
        @Schema(description = "사용자 아이디 — 영문·숫자·밑줄(_) 4~14자. 전환기 동안 선택이며 생략하거나 빈 값이면 서버가 생성합니다",
                example = "Pack_1n")
        String handle
) {}
