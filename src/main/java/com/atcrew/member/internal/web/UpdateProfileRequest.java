package com.atcrew.member.internal.web;

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

record UpdateProfileRequest(
        @Size(max = 16)
        String name,

        CreatorRole creatorRole,
        EmploymentStatus employmentStatus,
        List<ActivityField> activityFields,
        ExperienceLevel experienceLevel,
        List<ActiveRegion> activeRegions,

        @Min(1) @Max(5)
        int totalSlotCount,

        @Min(0) @Max(5)
        int availableSlotCount,

        List<TeamExperience> teamExperiences
) {
}
