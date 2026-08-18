package com.atcrew.portfolio.internal.application;

/**
 * 고정형 생성 시점에 얼려두는 작성자 프로필 (docs/design/portfolio-module-design.md §5.4,
 * 마이페이지_작가-R44). OG 카드 문구("{작가명}님의 작품과 프로필을 확인해보세요")에 필요한 최소 필드만 담는다.
 *
 * <p>{@code portfolios.snapshot_owner_profile} 직렬화 전용 내부 타입이라 모듈 밖으로 노출하지 않는다.
 */
record OwnerProfileSnapshot(
        String name,   // 생성 시점 작가명
        String handle  // 생성 시점 @핸들
) {
}
