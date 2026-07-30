package com.atcrew.company.internal.application;

import com.atcrew.company.AddCompanyCareerCommand;
import com.atcrew.company.CompanyCareerInfo;
import com.atcrew.company.CompanyInfo;
import com.atcrew.company.CompanyService;
import com.atcrew.company.UpdateCompanyInfoCommand;
import com.atcrew.company.internal.domain.Company;
import com.atcrew.company.internal.domain.CompanyCareer;
import com.atcrew.company.internal.exception.CompanyErrorCode;
import com.atcrew.company.internal.exception.CompanyException;
import com.atcrew.company.internal.persistence.CompanyCareerRepository;
import com.atcrew.company.internal.persistence.CompanyRepository;
import com.atcrew.member.MemberService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
class CompanyServiceImpl implements CompanyService {

    // member 모듈과 동일한 상한 (docs/design/company-profile-module-design.md §2.2)
    private static final int MAX_CAREER_COUNT = 50;

    private final CompanyRepository companyRepository;
    private final CompanyCareerRepository companyCareerRepository;
    private final MemberService memberService;

    CompanyServiceImpl(CompanyRepository companyRepository,
                       CompanyCareerRepository companyCareerRepository,
                       MemberService memberService) {
        this.companyRepository = companyRepository;
        this.companyCareerRepository = companyCareerRepository;
        this.memberService = memberService;
    }

    @Override
    @Transactional
    public CompanyInfo create(String memberId, String companyName) {
        memberService.findById(memberId); // 존재하지 않으면 예외 전파
        if (companyRepository.existsByMemberId(memberId)) {
            throw new CompanyException(CompanyErrorCode.COMPANY_ALREADY_EXISTS, "memberId=" + memberId);
        }
        try {
            // 동시 요청으로 위 검사를 통과한 경우 uk_companies_member 제약이 최종 방어선이 된다.
            Company saved = companyRepository.saveAndFlush(Company.create(memberId, companyName));
            return CompanyMapper.toInfo(saved, true);
        } catch (DataIntegrityViolationException e) {
            throw new CompanyException(CompanyErrorCode.COMPANY_ALREADY_EXISTS, "memberId=" + memberId);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public CompanyInfo findById(String companyId, String viewerMemberId) {
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new CompanyException(CompanyErrorCode.COMPANY_NOT_FOUND, companyId));
        return CompanyMapper.toInfo(company, company.isOwnedBy(viewerMemberId));
    }

    @Override
    @Transactional(readOnly = true)
    public CompanyInfo findByMemberId(String memberId) {
        return CompanyMapper.toInfo(loadOwnedCompany(memberId), true);
    }

    @Override
    @Transactional
    public void updateName(String memberId, String companyName) {
        loadOwnedCompany(memberId).updateName(companyName); // dirty checking으로 반영
    }

    @Override
    @Transactional
    public void updateInfo(String memberId, UpdateCompanyInfoCommand command) {
        loadOwnedCompany(memberId).updateInfo(command); // dirty checking으로 반영
    }

    @Override
    @Transactional
    public CompanyCareerInfo addCareer(String memberId, AddCompanyCareerCommand command) {
        Company company = loadOwnedCompany(memberId);
        if (companyCareerRepository.countByCompanyId(company.getId()) >= MAX_CAREER_COUNT) {
            throw new CompanyException(CompanyErrorCode.CAREER_LIMIT_EXCEEDED, "companyId=" + company.getId());
        }
        CompanyCareer career = CompanyCareer.create(company.getId(), command.workTitle(),
                command.startDate(), command.endDate(), command.ongoing(), command.description());
        return CompanyMapper.toCareerInfo(companyCareerRepository.save(career));
    }

    @Override
    @Transactional(readOnly = true)
    public List<CompanyCareerInfo> listCareers(String companyId) {
        if (!companyRepository.existsById(companyId)) {
            throw new CompanyException(CompanyErrorCode.COMPANY_NOT_FOUND, companyId);
        }
        return CompanyMapper.toCareerInfos(companyCareerRepository.findByCompanyIdOrderByStartDateDesc(companyId));
    }

    /** 소유 회원 기준으로 기업 프로필을 조회하고 소유자 불변식을 재확인한다. */
    private Company loadOwnedCompany(String memberId) {
        Company company = companyRepository.findByMemberId(memberId)
                .orElseThrow(() -> new CompanyException(CompanyErrorCode.COMPANY_NOT_FOUND, "memberId=" + memberId));
        company.assertOwner(memberId);
        return company;
    }
}
