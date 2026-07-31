package com.atcrew.member;

import java.time.Instant;

/**
 * 회원이 다른 회원(작가)의 마이페이지(핸들 프로필)를 조회했을 때 발행되는 이벤트.
 *
 * <p>recruit 모듈이 이 이벤트를 구독해 "최근 본 작가" 목록을 기록한다
 * (docs/design/recruit-module-design.md §2.7). 본인이 본인 프로필을 조회한 경우는 발행하지 않는다 —
 * "작가를 조회했다"는 신호로서 의미가 없기 때문이다. 반면 비로그인 조회는 이벤트 자체는 발행하되
 * {@code viewerMemberId}를 null로 실어 보낸다 — "기록 대상에서 제외할지"는 구독자의 정책(기업 계정
 * 전용 기능인지 등)에 맡기고, 발행 측(member 모듈)은 조회 사실만 전달한다.
 */
public record ArtistProfileViewedEvent(
        String viewerMemberId, // 조회자 회원 ID. 비로그인 조회면 null
        String artistMemberId, // 조회 대상 작가(회원) ID
        Instant occurredAt     // 조회 발생 시각(UTC)
) {
}
