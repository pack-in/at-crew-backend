package com.atcrew.company.internal.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateCompanyNameRequest(
        @Schema(description = "변경할 기업명 (최대 16자)", example = "앳크루스튜디오")
        @NotBlank
        @Size(max = 16)
        String companyName // 기업명
) {
}
