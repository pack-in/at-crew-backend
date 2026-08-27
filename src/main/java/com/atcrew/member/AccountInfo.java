package com.atcrew.member;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 설정 &gt; 계정 정보 화면 응답(설정-R04·R05).
 *
 * <p>{@link MemberInfo}의 {@code loginEmail}은 {@code @JsonIgnore}라 공개 프로필 응답으로 새어나가지
 * 않게 막혀 있다. 계정 화면은 로그인 이메일을 보여줘야 하므로 본인 전용 응답 레코드를 따로 둔다.
 */
public record AccountInfo(
        String loginEmail,
        AuthProvider authProvider,

        @Schema(nullable = true, description = "주 사용 언어. 가입 후 변경 불가이며, 마이그레이션 이전 회원은 null")
        Language primaryLanguage,
        @Schema(description = "노출받을 게시물 언어 (복수)")
        List<Language> postLanguages,

        boolean marketingAgreed,
        boolean adultContentVisible
) {
}
