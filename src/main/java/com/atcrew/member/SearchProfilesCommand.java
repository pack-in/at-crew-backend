package com.atcrew.member;

import java.util.List;

public record SearchProfilesCommand(
        List<EmploymentStatus> employmentStatuses, // null/empty면 전체
        ActivityField activityField,                // null이면 전체
        ProfileSort sort,                            // null이면 RECENTLY_UPDATED
        // 뷰어가 노출받기로 한 게시물 언어(로그인-R16). null/빈 목록이면 언어 필터 미적용(비로그인)
        List<Language> viewerLanguages,
        String cursor,
        int size
) {
}
