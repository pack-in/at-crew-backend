package com.atcrew.recruit.internal.web.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record PresignRequest(
        @Min(1) @Max(20) int count,
        @NotEmpty @Size(max = 20) List<String> contentTypes,
        // 업로드할 원본의 바이트 크기. 선택 입력이라 보내지 않아도 발급되지만, 보내면 20MB 초과를
        // 업로드 시작 전에 400으로 거른다(안 보내면 Worker가 변환 직전에 걸러 FAILED가 된다).
        @Size(max = 20) List<Long> fileSizes
) {
}
