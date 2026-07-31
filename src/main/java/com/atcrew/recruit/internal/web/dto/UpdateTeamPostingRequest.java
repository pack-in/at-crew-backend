package com.atcrew.recruit.internal.web.dto;

import com.atcrew.recruit.TeamActivityDuration;
import com.atcrew.recruit.TeamWeeklyActivityTime;
import com.atcrew.recruit.TeamWorkLocationType;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

// 부분 업데이트 — null 필드는 기존 값 유지 (docs/design/recruit-module-design.md §4.2)
public record UpdateTeamPostingRequest(
        @Size(max = 200) String title,
        Boolean isBusinessRegistered,
        Boolean isResumeRequired,
        Boolean isCoverLetterRequired,
        @Size(max = 100) String authorName,
        @Size(max = 100) String contact,
        @Size(max = 5000) String authorDescription,
        @Size(max = 20) List<@NotBlank @Size(max = 50) String> recruitPurposes,
        TeamWorkLocationType workLocationType,
        @Size(max = 200) String activityRegion,
        @Size(max = 20) List<@NotBlank @Size(max = 100) String> roles,
        @Size(max = 20) List<@NotBlank @Size(max = 100) String> genres,
        Boolean hasParticipationFee,
        Boolean hasProfitSharing,
        @Size(max = 500) String extraCost,
        @FutureOrPresent LocalDate deadline,
        @Min(1) @Max(9999) Integer recruitCount,
        @Size(max = 5000) String selectionProcess,
        TeamActivityDuration activityDuration,
        TeamWeeklyActivityTime weeklyActivityTime,
        @Size(max = 5000) String projectDescription,
        @Size(max = 500) String thumbnailImage,
        @Size(max = 20) List<@NotBlank @Size(max = 500) String> referenceImages
) {
}
