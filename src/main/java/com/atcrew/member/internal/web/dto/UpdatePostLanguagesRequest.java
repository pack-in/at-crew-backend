package com.atcrew.member.internal.web.dto;

import com.atcrew.member.Language;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.Set;

public record UpdatePostLanguagesRequest(
        // 주 사용 언어는 해제할 수 없으므로(설정-R14) 빈 배열은 항상 오류다.
        @NotEmpty(message = "게시물 언어를 1개 이상 선택해주세요")
        @Size(max = 4, message = "게시물 언어는 최대 4개까지 선택할 수 있습니다")
        @Schema(description = "노출받을 게시물 언어 (주 사용 언어 포함 필수)", example = "[\"KO\", \"JA\"]")
        Set<Language> languages
) {
}
