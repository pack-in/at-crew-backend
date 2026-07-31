package com.atcrew.recruit;

import java.time.LocalDate;

/**
 * 커뮤니티 "구인글" 탭 카드 응답. community 모듈이 {@link RecruitService}를 통해 소비한다
 * (docs/design/recruit-module-design.md §6.1 — community 소유였던 이 record를 recruit으로 이관).
 * recruit 콘텐츠는 성인물 게이팅 대상이 아니므로(설계 §7) ageRating 필드를 두지 않는다.
 */
public record CommunityJobPostingCardInfo(
        String id,             // JobPosting ID
        String thumbnailUrl,   // 썸네일 이미지 URL
        String title,          // 공고 제목
        String companyName,    // 회사명
        String authorName,     // 작성자(작성 계정) 표시명
        LocalDate deadline,    // 마감일, null이면 상시모집
        boolean closed         // true면 카드에 "마감" 텍스트로 대체 표시
) {
}
