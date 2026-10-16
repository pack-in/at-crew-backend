package com.atcrew.member;

/**
 * handle 사용 가능 여부 사전 확인 결과 (GET /api/members/handles/availability, member-handle-design §1.4).
 * 오류 코드는 가입·변경 API와 같은 이름을 쓴다.
 */
public record HandleAvailabilityInfo(
        boolean available, // 사용 가능하면 true
        String errorCode   // 사용 불가 사유 오류 코드(예: HANDLE_TOO_SHORT, DUPLICATE_HANDLE). 사용 가능하면 null
) {
}
