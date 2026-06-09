package com.atcrew.member;

public record CareerEntryInfo(
        String id,
        String workTitle,
        String episodeCount,
        String startDate,
        String endDate,
        boolean ongoing,
        String description
) {
}
