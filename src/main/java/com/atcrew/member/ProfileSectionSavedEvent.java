package com.atcrew.member;

import java.time.Instant;

/**
 * 프로필 정보(기본정보·구직정보) 저장 성공 시 발행되는 이벤트(plans/261005-posthog-analytics-events PA-04).
 *
 * <p>{@code sectionType}은 현재 {@code updateInfo} API가 기본정보·구직정보 탭을 한 엔드포인트로
 * 처리해 구분이 없으므로 {@code "profile_info"} 고정값이다. TODO: 프론트 회신(PH-02)으로 탭 분리
 * 여부가 확정되면 분기 필요.
 *
 * <p>{@code completionRate}·{@code isComplete}·{@code isFirstCompletion}은 이 저장으로 반영된
 * 회원의 전체 프로필 상태(저장 후 값) 기준이다 — 이번 요청 바디만의 채움 비율이 아니라,
 * {@code totalSlotCount}처럼 항상 기본값을 갖는 필드까지 포함한 저장소상 실제 완성도를 가리킨다.
 */
public record ProfileSectionSavedEvent(
        String memberId,           // 저장한 회원 ID
        String sectionType,        // 저장한 프로필 섹션 구분(현재는 "profile_info" 고정)
        double completionRate,     // 저장 후 프로필 완성도(0.0~1.0) = 채워진 필드 수 / 23
        boolean isComplete,        // completionRate == 1.0 인지
        int changedFieldCount,     // 이번 저장으로 저장 전과 비교해 실제 값이 바뀐 필드 수
        boolean isFirstCompletion, // 이번 저장으로 처음 completionRate 1.0에 도달했는지
        Instant occurredAt         // 저장 발생 시각(UTC) — $insert_id 구분용(같은 회원이 반복 저장 가능)
) {
}
