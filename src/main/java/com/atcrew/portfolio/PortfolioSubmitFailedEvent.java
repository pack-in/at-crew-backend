package com.atcrew.portfolio;

import java.time.Instant;

/**
 * 포트폴리오 생성/수정 실패(PA-11) — analytics가 PostHog {@code portfolio_submit_failed}로 옮긴다.
 *
 * @param mode {@code "create"} 또는 {@code "edit"}
 */
public record PortfolioSubmitFailedEvent(String memberId, String mode, String errorCode, Instant occurredAt) {
}
