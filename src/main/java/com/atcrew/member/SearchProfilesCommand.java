package com.atcrew.member;

import java.util.List;

public record SearchProfilesCommand(
        List<EmploymentStatus> employmentStatuses, // null/empty면 전체
        ActivityField activityField,                // null이면 전체
        ProfileSort sort,                            // null이면 RECENTLY_UPDATED
        String cursor,
        int size
) {
}
