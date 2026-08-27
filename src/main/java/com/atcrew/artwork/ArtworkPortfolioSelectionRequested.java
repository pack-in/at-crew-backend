package com.atcrew.artwork;

import java.util.List;

/**
 * 작품의 라이브 포트폴리오 편입 재선언 요청 (업로드-R09).
 *
 * <p>업로드·공개 위치 재선택 트랜잭션 안에서 artwork가 발행하고 portfolio가 <b>동기</b> 리스너로 소비한다.
 * {@code artwork → portfolio} 직접 호출은 순환 의존이라 이벤트로 방향을 뒤집은 것이며, 비동기가 아닌
 * 이유는 검증 실패(타인 포트폴리오·고정형·플랜 게이팅) 시 예외를 그대로 전파해 업로드 트랜잭션까지
 * 되돌려야 하기 때문이다 — 작품만 저장되고 편입은 안 된 반쪽 상태를 만들지 않는다.
 *
 * <p>{@code portfolioIds}는 증분이 아니라 <b>전체 재선언</b>이다 — 목록에 없는 기존 편입은 해제된다.
 */
public record ArtworkPortfolioSelectionRequested(
        String memberId,           // 요청한 회원 ID — 포트폴리오 소유자 검증에 쓴다
        String artworkId,          // 대상 작품 ID
        List<String> portfolioIds  // 편입할 라이브 포트폴리오 ID 전체 목록 — null/빈 목록이면 전부 해제
) {
}
