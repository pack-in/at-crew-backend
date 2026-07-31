package com.atcrew.recruit;

import java.time.Instant;

/**
 * recruit 검색 결과 항목. 유형(구인글·구직글·팀원모집글)에 관계없이 공통으로 노출되는 최소 필드만 담는다.
 * recruit 콘텐츠는 연령 등급 게이팅 대상이 아니므로(docs/design/recruit-module-design.md §7) 등급 필드가 없다.
 */
public record RecruitSearchResultInfo(
        String id,                // 게시글 ID
        RecruitPostType postType, // 게시글 유형
        String title,             // 제목
        String thumbnailUrl,      // 썸네일 이미지 URL (구직글은 썸네일이 없어 항상 null)
        String authorMemberId,    // 작성자 Member ID
        String authorName,        // 작성자 표시명 (member 모듈 조회 실패 시 null)
        Instant createdAt         // 작성 일시 — 커서 정렬 키
) {
}
