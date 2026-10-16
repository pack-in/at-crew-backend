package com.atcrew.member;

/**
 * 회원이 handle을 직접 변경했을 때 발행되는 이벤트 (member-handle-design §1.3, §4.3).
 * search 모듈이 구독해 그 작가의 작품 문서를 재색인한다. 가입·운영자 변경(SQL)은 발행하지 않는다.
 */
public record MemberHandleChangedEvent(
        String memberId // handle을 변경한 회원 ID
) {
}
