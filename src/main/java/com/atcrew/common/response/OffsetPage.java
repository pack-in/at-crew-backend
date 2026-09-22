package com.atcrew.common.response;

import java.util.List;

/**
 * 오프셋 조회 결과 — 한 페이지의 항목과 조건에 맞는 전체 개수.
 *
 * <p>번호 페이지네이션 화면은 페이지마다 전체 개수가 필요하다. 목록과 개수를 서비스 메서드 두 개로
 * 나누면 조회 계층이 이미 센 개수를 버리고 같은 COUNT를 한 번 더 실행하게 되므로 한 번에 돌려준다.
 * 커서 목록은 {@link CursorPage}를 그대로 쓴다(docs/design/community-module-design.md §6).
 */
public record OffsetPage<T>(
        List<T> items,
        long totalCount
) {
    public static <T> OffsetPage<T> empty() {
        return new OffsetPage<>(List.of(), 0);
    }
}
