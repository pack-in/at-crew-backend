package com.atcrew.member;

import java.util.List;

public record UpdateProfileCommand(
        String name,                          // 이름
        CreatorRole creatorRole,              // 크리에이터 역할
        EmploymentStatus employmentStatus,    // 고용 상태
        List<ActivityField> activityFields,   // 활동 분야
        ExperienceLevel experienceLevel,      // 경력 수준
        List<ActiveRegion> activeRegions,     // 활동 지역
        Integer totalSlotCount,               // 총 슬롯 수
        Integer availableSlotCount,           // 가용 슬롯 수
        List<TeamExperience> teamExperiences  // 팀 경험
) {
}
