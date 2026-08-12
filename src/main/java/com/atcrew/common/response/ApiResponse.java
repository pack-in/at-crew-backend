package com.atcrew.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "공통 응답 봉투 — 모든 API가 이 형태로 감싸서 응답한다. 성공이면 code=SUCCESS·"
        + "message=null·data에 실제 값이, 실패면 code가 각 API 문서에 적힌 에러코드 이름(예: "
        + "MEMBER_NOT_FOUND)·message에 사람이 읽을 문구·data=null이 채워진다")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(
        @Schema(description = "응답 코드 — 성공 시 항상 \"SUCCESS\", 실패 시 각 API의 에러코드 이름",
                example = "SUCCESS")
        String code,
        @Schema(description = "에러 메시지 — 성공 응답이면 항상 null이라 응답 JSON에서 생략된다. "
                + "실패 시 값 예시: 존재하지 않는 회원입니다", nullable = true)
        String message,
        @Schema(description = "응답 데이터 — 에러 응답이면 항상 null(응답 JSON에서 생략됨)", nullable = true)
        T data
) {

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>("SUCCESS", null, data);
    }

    public static ApiResponse<Void> error(String code, String message) {
        return new ApiResponse<>(code, message, null);
    }
}
