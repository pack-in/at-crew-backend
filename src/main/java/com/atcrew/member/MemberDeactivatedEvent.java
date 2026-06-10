package com.atcrew.member;

/**
 * 회원 탈퇴 시 발행되는 이벤트. 다른 모듈은 이 이벤트를 구독해 연관 데이터를 처리한다.
 */
public record MemberDeactivatedEvent(String memberId) {
}
