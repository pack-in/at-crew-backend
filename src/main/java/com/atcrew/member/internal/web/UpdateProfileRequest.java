package com.atcrew.member.internal.web;

import com.atcrew.member.CreatorRole;
import com.atcrew.member.EmploymentStatus;
import com.atcrew.member.ExperienceLevel;
import jakarta.validation.constraints.Size;

record UpdateProfileRequest(
        @Size(max = 50)
        String name,

        String profileImage,
        CreatorRole creatorRole,
        EmploymentStatus employmentStatus,
        ExperienceLevel experienceLevel
) {
}
