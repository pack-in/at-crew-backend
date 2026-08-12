// ServiceImpl + Mapper. portfolio는 다른 모듈이 동기 호출하지 않는 리프 모듈이라 공개 서비스 인터페이스가 없고
// (docs/design/portfolio-module-design.md §3), 같은 모듈의 컨트롤러가 PortfolioServiceImpl을 직접 주입받는다.
package com.atcrew.portfolio.internal.application;
