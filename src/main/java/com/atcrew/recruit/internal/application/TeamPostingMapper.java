package com.atcrew.recruit.internal.application;

import com.atcrew.recruit.CommunityTeamRecruitCardInfo;
import com.atcrew.recruit.TeamPostingInfo;
import com.atcrew.recruit.TeamPostingStatus;
import com.atcrew.recruit.internal.domain.TeamPosting;

class TeamPostingMapper {

    private TeamPostingMapper() {
    }

    // images가 null이면(자식 행이 없는 과거 데이터) 기존 컬럼으로 폴백한다(설계 §10.4).
    private static PostingImages resolve(TeamPosting teamPosting, PostingImages images) {
        return images != null ? images
                : PostingImages.legacy(teamPosting.getThumbnailImage(), teamPosting.getReferenceImages());
    }

    static TeamPostingInfo toInfo(TeamPosting teamPosting, String authorDisplayName, PostingImages images) {
        PostingImages resolved = resolve(teamPosting, images);
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
                resolved.thumbnailImage(),
                resolved.referenceImages(),
                teamPosting.getBookmarkCount(),
                teamPosting.getViewCount(),
                teamPosting.getBoostedUntil(),
                teamPosting.getStatus(),
                teamPosting.getDeletedAt(),
                teamPosting.getCreatedAt(),
                teamPosting.getUpdatedAt()
        );
    }

    static CommunityTeamRecruitCardInfo toCardInfo(TeamPosting teamPosting, String authorDisplayName,
            PostingImages images) {
        return new CommunityTeamRecruitCardInfo(
                teamPosting.getId(),
                resolve(teamPosting, images).thumbnailImage(),
                teamPosting.getTitle(),
                authorDisplayName,
                teamPosting.getDeadline(),
                teamPosting.getStatus() == TeamPostingStatus.CLOSED
        );
    }
}
