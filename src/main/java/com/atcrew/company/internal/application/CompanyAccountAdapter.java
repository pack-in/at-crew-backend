package com.atcrew.company.internal.application;

import com.atcrew.billing.CompanyAccountPort;
import com.atcrew.company.internal.persistence.CompanyRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** billing이 소유한 포트의 company 측 구현 — 기업 프로필 보유 = 기업 계정. */
@Component
class CompanyAccountAdapter implements CompanyAccountPort {

    private final CompanyRepository companyRepository;

    CompanyAccountAdapter(CompanyRepository companyRepository) {
        this.companyRepository = companyRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isCompanyAccount(String memberId) {
        return companyRepository.existsByMemberId(memberId);
    }
}
