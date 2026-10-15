package com.atcrew.member.internal.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

// 빈 값·형식 검사는 서비스가 설계 §1.3 우선순위(멱등 → 30일 → 미변경 → 형식)대로 판정하므로 여기서는 길이 상한만 둔다.
public record ChangeHandleRequest(
        @Size(max = 50, message = "사용자 아이디가 너무 깁니다")
        @Schema(description = "새 사용자 아이디 — 영문·숫자·밑줄(_) 4~14자", example = "Pack_1n")
        String handle // 새 사용자 아이디(앞뒤 공백은 서버가 제거)
) {
}
