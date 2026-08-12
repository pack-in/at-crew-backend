package com.atcrew.common.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "커서 기반 페이지네이션 응답 봉투 — offset 대신 마지막 항목의 정렬 기준값을 커서로 쓴다. "
        + "다음 페이지 요청 시 이전 응답의 nextCursor를 그대로 cursor 파라미터에 넣으면 된다. "
        + "커서 인코딩은 엔드포인트마다 다를 수 있으므로 불투명한 값으로 취급하고 직접 파싱하지 말 것.")
public record CursorPage<T>(
        @Schema(description = "이번 페이지 항목 목록. 마지막 페이지면 size보다 적을 수 있다")
        List<T> items,
        @Schema(description = "다음 페이지 커서 — 마지막 페이지면 null", nullable = true)
        String nextCursor,
        @Schema(description = "다음 페이지 존재 여부 — items.size()가 size보다 작아도 hasNext로 종료를 판단할 것")
        boolean hasNext
) {
    public static <T> CursorPage<T> of(List<T> items, String nextCursor) {
        return new CursorPage<>(items, nextCursor, nextCursor != null);
    }

    public static <T> CursorPage<T> empty() {
        return new CursorPage<>(List.of(), null, false);
    }
}
