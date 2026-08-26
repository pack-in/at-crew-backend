package com.atcrew.member;

import java.util.List;

public record UpdateInfoCommand(
        EmploymentStatus employmentStatus,
        List<ActivityField> activityFields,
        ExperienceLevel experienceLevel,
        ActiveRegion activeRegion,
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
