package com.atcrew.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "공통 응답 봉투")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(
        String code,    // SUCCESS 또는 에러 코드
        String message, // 에러 메시지 (성공 시 null)
        T data          // 응답 데이터 (에러 시 null)
) {

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>("SUCCESS", null, data);
    }

    public static ApiResponse<Void> error(String code, String message) {
        return new ApiResponse<>(code, message, null);
    }
}
