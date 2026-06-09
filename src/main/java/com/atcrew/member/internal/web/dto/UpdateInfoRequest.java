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
import jakarta.validation.constraints.Size;

import java.util.List;

public record UpdateInfoRequest(
        CreatorRole creatorRole,
        EmploymentStatus employmentStatus,
        List<ActivityField> activityFields,
        ExperienceLevel experienceLevel,
        List<ActiveRegion> activeRegions,

        @Min(1) @Max(5)
        Integer totalSlotCount,

        @Min(0) @Max(5)
        Integer availableSlotCount,

        List<TeamExperience> teamExperiences,

        // 전화번호 또는 이메일을 단일 필드로 통합 수신
        @Schema(description = "연락처 (전화번호 또는 이메일)", example = "010-1234-5678")
        @Size(max = 100)
        String contact,

        @Size(max = 200)
        String sns,

        String tools
) {
}
