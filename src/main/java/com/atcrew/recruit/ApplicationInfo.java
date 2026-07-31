package com.atcrew.recruit;

import java.time.Instant;

/**
 * 지원 내역 응답 (docs/design/recruit-module-design.md §2.4, §2.5).
 * 구인글/팀원모집글 지원의 필드가 동일하므로 응답 record는 하나를 공유하고 {@code postingId}로 대상을 구분한다.
 */
public record ApplicationInfo(
        String id,                              // 지원 ID
        String postingId,                       // 지원 대상 글 ID (구인글 또는 팀원모집글)
        String applicantMemberId,               // 지원자 Member ID
        String applicantName,                   // 지원자 표시명 (member 모듈 조회 실패 시 null)
        SerialExperience serialExperience,      // 연재 경험
        boolean assistantExperience,            // 어시스턴트 경험 여부
        String resumeUrl,                       // 이력서 URL
        ApplicationReviewStatus reviewStatus,   // 채용 단계 상태
        Instant appliedAt                       // 지원 시각
) {
}
