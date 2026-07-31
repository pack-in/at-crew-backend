package com.atcrew.recruit.internal.application;

import com.atcrew.recruit.CommunityTeamRecruitCardInfo;
import com.atcrew.recruit.TeamPostingInfo;
import com.atcrew.recruit.TeamPostingStatus;
import com.atcrew.recruit.internal.domain.TeamPosting;

class TeamPostingMapper {

    private TeamPostingMapper() {
    }

    static TeamPostingInfo toInfo(TeamPosting teamPosting, String authorDisplayName) {
        return new TeamPostingInfo(
                teamPosting.getId(),
                teamPosting.getAuthorMemberId(),
                authorDisplayName,
                teamPosting.getTitle(),
                teamPosting.isBusinessRegistered(),
                teamPosting.isResumeRequired(),
                teamPosting.isCoverLetterRequired(),
                teamPosting.getAuthorName(),
                teamPosting.getContact(),
                teamPosting.getAuthorDescription(),
                teamPosting.getRecruitPurposes(),
                teamPosting.getWorkLocationType(),
                teamPosting.getActivityRegion(),
                teamPosting.getRoles(),
                teamPosting.getGenres(),
                teamPosting.isHasParticipationFee(),
                teamPosting.isHasProfitSharing(),
                teamPosting.getExtraCost(),
                teamPosting.getDeadline(),
                teamPosting.getRecruitCount(),
                teamPosting.getSelectionProcess(),
                teamPosting.getActivityDuration(),
                teamPosting.getWeeklyActivityTime(),
                teamPosting.getProjectDescription(),
                teamPosting.getThumbnailImage(),
                teamPosting.getReferenceImages(),
                teamPosting.getBookmarkCount(),
                teamPosting.getViewCount(),
                teamPosting.getBoostedUntil(),
                teamPosting.getStatus(),
                teamPosting.getDeletedAt(),
                teamPosting.getCreatedAt(),
                teamPosting.getUpdatedAt()
        );
    }

    static CommunityTeamRecruitCardInfo toCardInfo(TeamPosting teamPosting, String authorDisplayName) {
        return new CommunityTeamRecruitCardInfo(
                teamPosting.getId(),
                teamPosting.getThumbnailImage(),
                teamPosting.getTitle(),
                authorDisplayName,
                teamPosting.getDeadline(),
                teamPosting.getStatus() == TeamPostingStatus.CLOSED
        );
    }
}
