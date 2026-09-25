package com.atcrew.recruit.internal.web.dto;

import com.atcrew.artwork.ArtworkRole;
import com.atcrew.artwork.Genre;
import com.atcrew.recruit.TeamActivityDuration;
import com.atcrew.recruit.TeamWeeklyActivityTime;
import com.atcrew.recruit.TeamWorkLocationType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

public record CreateTeamPostingRequest(
        @NotBlank @Size(max = 200) String title,
        boolean isBusinessRegistered,
        boolean isResumeRequired,
        boolean isCoverLetterRequired,
        @Size(max = 100) String authorName,
        @Size(max = 100) String contact,
        @Size(max = 5000) String authorDescription,
        @Size(max = 20) List<@NotBlank @Size(max = 50) String> recruitPurposes,
        TeamWorkLocationType workLocationType,
        @Size(max = 200) String activityRegion,
        @Size(max = 20) List<@NotNull ArtworkRole> roles,
        @Size(max = 20) List<@NotNull Genre> genres,
        boolean hasParticipationFee,
        boolean hasProfitSharing,
        @Size(max = 500) String extraCost,
        // 과거 여부는 작성자 시간대 기준으로 도메인에서 검증한다 — @FutureOrPresent는 서버 시간대(UTC)의 오늘을 쓴다
        LocalDate deadline,
        @Min(1) @Max(9999) Integer recruitCount,
        @Size(max = 5000) String selectionProcess,
        TeamActivityDuration activityDuration,
        TeamWeeklyActivityTime weeklyActivityTime,
        @Size(max = 5000) String projectDescription,
        @Size(max = 500) String thumbnailImage,
        @Size(max = 20) List<@NotBlank @Size(max = 500) String> referenceImages
) {
}
