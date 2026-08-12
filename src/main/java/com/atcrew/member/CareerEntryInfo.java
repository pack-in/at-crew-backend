package com.atcrew.member;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

@Schema(description = "경력(참여작) 항목. 날짜는 ISO 8601이 아닌 yyyy.MM.dd 포맷을 사용한다")
public record CareerEntryInfo(
        @Schema(description = "경력 ID (UUIDv7 문자열). 경력 삭제 시 이 값을 경로 변수로 사용한다",
                example = "019ff381-833e-7235-9568-402dcfcb6efb")
        String id,

        @Schema(description = "참여작 이름 (최대 100자)", example = "홍길동전")
        String workTitle,

        @Schema(description = "담당 업무 (최대 100자). 미입력이면 null",
                example = "작화 전공정", nullable = true)
        String role,

        @Schema(description = "작업 시작일 (yyyy.MM.dd)", example = "2023.01.15", type = "string")
        @JsonFormat(pattern = "yyyy.MM.dd") LocalDate startDate,

        @Schema(description = "작업 종료일 (yyyy.MM.dd). 연재중이면 null",
                example = "2024.06.30", type = "string", nullable = true)
        @JsonFormat(pattern = "yyyy.MM.dd") LocalDate endDate,

        @Schema(description = "연재중 여부. true면 endDate가 null이다", example = "false")
        boolean ongoing,

        @Schema(description = "작업 내용 (최대 200자). 미입력이면 null",
                example = "주 1회 연재, 총 60화 작화 담당", nullable = true)
        String description,

        @Schema(description = "화면 표시용 경력 기간 문자열 — 서버가 계산해 내려준다",
                example = "2023.01.15 ~ 2024.06.30 약 1년 5개월")
        String periodDisplay
) {
}
