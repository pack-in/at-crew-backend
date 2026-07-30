package com.atcrew.community;

import com.atcrew.artwork.AgeRating;

import java.time.LocalDate;

/**
 * 커뮤니티 "구인글" 탭 카드 응답. recruit 모듈이 아직 없어 {@link RecruitFeedPort}를 통해서만 채워진다.
 */
public record CommunityJobPostingCardInfo(
        String id,
        String publicId,
        String thumbnailUrl,
        String title,
        String companyName,
        String authorName,
        LocalDate deadline, // null이면 상시모집
        boolean closed,     // true면 카드에 "마감" 텍스트로 대체 표시
        AgeRating ageRating
) {
}
