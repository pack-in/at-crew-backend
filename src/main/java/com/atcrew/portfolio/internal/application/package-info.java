// ServiceImpl + Mapper. 포트폴리오 CRUD는 다른 모듈이 동기 호출하지 않아 공개 서비스 인터페이스에 올리지 않고
// (docs/design/portfolio-module-design.md §3), 같은 모듈의 컨트롤러가 PortfolioServiceImpl을 직접 주입받는다.
// 다른 모듈에 열어 준 조회만 com.atcrew.portfolio.PortfolioService로 노출한다.
package com.atcrew.portfolio.internal.application;
