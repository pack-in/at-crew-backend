package com.atcrew.member;

import java.time.Instant;

/** handle 변경 결과 (PATCH /api/members/me/handle 응답, member-handle-design §1.3). */
public record HandleChangeInfo(
        String handle,                   // 변경된 현재 handle(저장된 대소문자 그대로)
        Instant handleChangeAvailableAt  // 다음 변경 가능 시각(UTC). 변경 직후라 항상 값이 있다
) {
}
