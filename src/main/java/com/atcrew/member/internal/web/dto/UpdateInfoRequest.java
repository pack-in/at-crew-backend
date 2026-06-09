package com.atcrew.member.internal.web.dto;

import com.atcrew.member.ActiveRegion;
import com.atcrew.member.ActivityField;
import com.atcrew.member.CreatorRole;
import com.atcrew.member.EmploymentStatus;
import com.atcrew.member.ExperienceLevel;
import com.atcrew.member.TeamExperience;
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

        @Size(max = 100)
        String contact,

        @Size(max = 200)
        String sns,

        String tools
) {
}
