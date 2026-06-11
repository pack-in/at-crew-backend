package com.atcrew.member.internal.web.dto;

import com.atcrew.member.ActiveRegion;
import com.atcrew.member.ActivityField;
import com.atcrew.member.CreatorRole;
import com.atcrew.member.EmploymentStatus;
import com.atcrew.member.ExperienceLevel;
import com.atcrew.member.TeamExperience;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public record UpdateInfoRequest(
        @Schema(description = "창작자 유형. null이면 변경 없음")
        CreatorRole creatorRole,

        @Schema(description = "구인구직 상태. null이면 변경 없음")
        EmploymentStatus employmentStatus,

        @Schema(description = "활동 분야 (최대 4개). null이면 변경 없음, []이면 전체 삭제")
        @Size(max = 4)
        List<@NotNull ActivityField> activityFields,

        @Schema(description = "경력 연차. null이면 변경 없음")
        ExperienceLevel experienceLevel,

        @Schema(description = "활동 지역 (최대 7개). null이면 변경 없음, []이면 전체 삭제")
        @Size(max = 7)
        List<@NotNull ActiveRegion> activeRegions,

        @Schema(description = "전체 슬롯 수 (1~5). null이면 변경 없음")
        @Min(1) @Max(5)
        Integer totalSlotCount,

        @Schema(description = "가용 슬롯 수 (0~5, totalSlotCount 이하). null이면 변경 없음")
        @Min(0) @Max(5)
        Integer availableSlotCount,

        @Schema(description = "팀 작업 경험 (최대 4개). null이면 변경 없음, []이면 전체 삭제")
        @Size(max = 4)
        List<@NotNull TeamExperience> teamExperiences,

        // 전화번호 또는 이메일을 단일 필드로 통합 수신
        @Schema(description = "연락처 (전화번호 또는 이메일, 빈 문자열로 전송 시 삭제)", example = "010-1234-5678")
        @Size(max = 100)
        @Pattern(
                regexp = "^$|^(01[016789]-\\d{3,4}-\\d{4}|[\\w.%+\\-]+@[\\w.\\-]+\\.[a-zA-Z]{2,})$",
                message = "전화번호(010-0000-0000) 또는 이메일 형식으로 입력해주세요"
        )
        String contact,

        @Size(max = 200)
        String sns,

        @Size(max = 200)
        String tools
) {
}
