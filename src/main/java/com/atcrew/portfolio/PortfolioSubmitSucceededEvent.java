package com.atcrew.portfolio;

import java.time.Instant;

/**
 * 포트폴리오 생성/수정 성공(PA-11) — analytics가 PostHog {@code portfolio_submit_succeeded}로 옮긴다.
 *
 * <p>복제("복제해서 만들기")는 읽기 전용 프리필({@code getDuplicationSource})을 거쳐 일반 생성 호출로
 * 들어오므로 서버에서 구분할 수 없다 — PH-02 회신의 폴백대로 {@code mode}는 항상 {@code create}다.
 *
 * @param mode          {@code "create"} 또는 {@code "edit"}
 * @param portfolioOrder 이 포트폴리오를 포함해 이 회원이 소유한 공유 포트폴리오 수
 */
public record PortfolioSubmitSucceededEvent(String memberId, String portfolioId, String mode,
                                             PortfolioKind kind, ReflectionType reflectionType,
                                             int portfolioOrder, int artworkCount, Instant occurredAt) {
}
