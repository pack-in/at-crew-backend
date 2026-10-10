package com.atcrew.portfolio;

/**
 * portfolio 모듈의 공개 포트. 다른 모듈은 이 인터페이스로만 포트폴리오를 조회한다.
 *
 * <p>portfolio는 artwork·billing·member를 호출하는 쪽이므로, 이 포트는 반드시
 * "다른 모듈 → portfolio" 한 방향으로만 쓴다(docs/design/portfolio-module-design.md §1.2).
 */
public interface PortfolioService {

    /**
     * 회원이 소유한 포트폴리오 수. 회원마다 자동 생성되는 작가 페이지는 세지 않고
     * 직접 만든 공유 포트폴리오({@link PortfolioKind#SHARED})만 센다.
     */
    int countOwnedByMember(String memberId);
}
