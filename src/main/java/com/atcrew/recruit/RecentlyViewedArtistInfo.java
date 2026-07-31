package com.atcrew.recruit;

import java.time.Instant;

/**
 * 기업 계정이 최근 조회한 작가 응답 (docs/design/recruit-module-design.md §2.7, §4.3).
 */
public record RecentlyViewedArtistInfo(
        String artistMemberId,  // 작가 Member ID
        String artistName,      // 작가 표시명 (member 모듈 조회 실패 시 null)
        Instant viewedAt        // 마지막 조회 시각
) {
}
