package com.atcrew.member;

import java.time.LocalDateTime;

public record MemberInfo(
        Long id,
        String handle,
        String loginEmail,
        String name,
        String profileImage,
        CreatorRole creatorRole,
        EmploymentStatus employmentStatus,
        ExperienceLevel experienceLevel,
        boolean active,
        LocalDateTime deletedAt
) {
}
