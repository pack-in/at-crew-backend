package com.atcrew.member;

import java.util.List;

public record UpdateInfoCommand(
        CreatorRole creatorRole,
        EmploymentStatus employmentStatus,
        List<ActivityField> activityFields,
        ExperienceLevel experienceLevel,
        List<ActiveRegion> activeRegions,
        Integer totalSlotCount,
        Integer availableSlotCount,
        List<TeamExperience> teamExperiences,
        String contact,
        String sns,
        String tools,
        String timezone,
        String countryCode
) {
}
