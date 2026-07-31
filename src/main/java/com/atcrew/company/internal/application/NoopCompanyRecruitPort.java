package com.atcrew.company.internal.application;

import com.atcrew.company.CompanyRecruitPort;
import org.springframework.stereotype.Component;

/**
 * recruit 모듈이 생기기 전까지 사용하는 임시 구현체 — 항상 false를 반환한다.
 * recruit 모듈 완성 후 이 클래스를 실제 구현체로 교체(또는 삭제)한다
 * (docs/design/company-profile-module-design.md §6.2).
 */
@Component
class NoopCompanyRecruitPort implements CompanyRecruitPort {

    @Override
    public boolean hasOpenJobPosting(String companyId) {
        return false;
    }
}
