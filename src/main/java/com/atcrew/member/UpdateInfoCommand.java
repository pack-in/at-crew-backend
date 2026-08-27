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
        List<DrawingStyle> drawingStyles,
        WorkPace workPace,
        AvailableStartPeriod availableStartPeriod,
        List<DesiredRole> desiredRoles,
        List<DesiredGenre> desiredGenres,
        List<DesiredEmploymentType> desiredEmploymentTypes,
        DesiredWorkLocation desiredWorkLocation,
        List<FeedbackPreference> feedbackPreferences,
        DesiredMinimumGuarantee desiredMinimumGuarantee,
        DesiredAnnualSalary desiredAnnualSalary,
        List<CustomTagInfo> customTags,
        String contact,
        String sns,
        String tools,
        String timezone,
        String countryCode
) {
}
