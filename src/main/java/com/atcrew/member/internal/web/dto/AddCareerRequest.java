package com.atcrew.member.internal.web.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

@Schema(description = """
        경력(참여작) 추가 요청. 날짜는 ISO 8601이 아닌 yyyy.MM.dd 포맷만 허용한다.
        ongoing은 생략하면 400(COMMON_INVALID_INPUT)이므로 항상 전송해야 한다.""")
public record AddCareerRequest(
        @NotBlank @Size(max = 100)
        @Schema(description = "참여작 이름 (1~100자)", example = "홍길동전",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String workTitle,

        @Size(max = 100)
        @Schema(description = "담당 업무 (최대 100자). 생략 가능", example = "작화 전공정", nullable = true)
        String role,

        @NotNull
        @PastOrPresent(message = "작업 시작일은 미래 날짜일 수 없습니다")
        @Schema(description = "작업 시작일 (yyyy.MM.dd). 미래 날짜는 400", example = "2023.01.15",
                type = "string", requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonFormat(pattern = "yyyy.MM.dd")
        LocalDate startDate,

        // ISO 8601(yyyy-MM-dd)이 아닌 비표준 포맷 사용 + 연재중이면 null
        @Schema(description = """
                작업 종료일 (yyyy.MM.dd). 연재중(ongoing=true)이면 null로 보낸다.
                ongoing=false인데 null이거나 startDate보다 앞서면 400 INVALID_CAREER_PERIOD""",
                example = "2024.06.30", type = "string", nullable = true)
        @PastOrPresent(message = "작업 종료일은 미래 날짜일 수 없습니다")
        @JsonFormat(pattern = "yyyy.MM.dd")
        LocalDate endDate,

        @Schema(description = "연재중 여부. true면 endDate를 보내지 않는다", example = "false",
                requiredMode = Schema.RequiredMode.REQUIRED)
        boolean ongoing,

        @Size(max = 200)
        @Schema(description = "작업 내용 (최대 200자). 생략 가능",
                example = "주 1회 연재, 총 60화 작화 담당", nullable = true)
        String description
) {
}
