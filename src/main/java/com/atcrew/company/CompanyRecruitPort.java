package com.atcrew.company;

/**
 * 기업 마이페이지의 "구인글 업로드 카드" 진입점 판단 포트.
 *
 * <p>recruit 모듈이 아직 구현되지 않아, company 모듈이 임시로 이 인터페이스를 소유한다.
 * recruit 모듈이 생기면 해당 모듈이 구현체를 제공하도록 이관한다
 * (docs/design/company-profile-module-design.md §6.2). 그 전까지는 {@code NoopCompanyRecruitPort}가
 * 항상 false를 반환한다.
 */
public interface CompanyRecruitPort {

    boolean hasOpenJobPosting(String companyId);
}
