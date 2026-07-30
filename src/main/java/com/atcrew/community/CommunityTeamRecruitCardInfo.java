package com.atcrew.community;

import com.atcrew.artwork.AgeRating;

import java.time.LocalDate;

/**
 * 커뮤니티 "팀원모집글" 탭 카드 응답. recruit 모듈이 아직 없어 {@link RecruitFeedPort}를 통해서만 채워진다.
 */
public record CommunityTeamRecruitCardInfo(
        String id,
        String publicId,
        String thumbnailUrl,
        String title,
        String authorName,
        LocalDate deadline,
        boolean closed,
        AgeRating ageRating
) {
}
