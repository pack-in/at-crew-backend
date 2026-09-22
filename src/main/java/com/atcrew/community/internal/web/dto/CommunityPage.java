package com.atcrew.community.internal.web.dto;

import com.atcrew.common.response.OffsetPage;

import java.util.List;

/**
 * 커뮤니티 탭 목록 응답 봉투. 피그마 UI개편_커뮤니티가 하단에 번호 페이지네이션을 쓰므로
 * 커서가 아니라 오프셋(page·size) 방식이다(docs/design/community-module-design.md §6).
 *
 * <p>{@code totalCount}는 현재 필터·뷰어 조건에 맞는 전체 개수이고, 페이지 번호를 그리려면 매 요청
 * 필요하므로 모든 페이지 응답에 담는다.
 */
public record CommunityPage<T>(
        List<T> items,
        int page,
        int size,
        long totalCount,
        int totalPages,
        boolean hasNext
) {
    public static <T> CommunityPage<T> of(OffsetPage<T> found, int page, int size) {
        // size=0은 빈 목록을 돌려주는 기존 동작이라 페이지 수를 0으로 둔다(0으로 나누지 않는다).
        int totalPages = size == 0 ? 0 : (int) Math.ceil((double) found.totalCount() / size);
        return new CommunityPage<>(found.items(), page, size, found.totalCount(), totalPages, page < totalPages);
    }
}
