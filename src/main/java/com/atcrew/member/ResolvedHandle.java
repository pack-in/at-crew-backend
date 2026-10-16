package com.atcrew.member;

/**
 * handle 해석 결과 (docs/design/member-handle-design.md §2.1).
 *
 * <p>공개 URL의 handle은 원장(member_handles)으로 해석하므로 옛 handle도 현재 소유 회원으로 이어진다.
 * 탈퇴 회원 소유면 410 {@code MEMBER_WITHDRAWN}, 원장에 없으면 404 {@code MEMBER_NOT_FOUND}로 끝나 이 값이 만들어지지 않는다.
 */
public record ResolvedHandle(
        String memberId,      // handle을 소유한 활성 회원 ID
        String currentHandle, // 그 회원의 현재 handle — 저장된 대소문자 그대로(members.handle)
        boolean moved         // 요청 handle이 옛 handle이면 true. 대소문자만 다르면 false(리다이렉트 안 함)
) {
}
