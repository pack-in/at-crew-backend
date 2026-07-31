package com.atcrew.company;

import java.util.List;

public interface CompanyService {

    /** 기업 프로필 생성 — 한 회원당 1개만 생성할 수 있다. */
    CompanyInfo create(String memberId, String companyName);

    /**
     * 기업 프로필 공개 조회.
     *
     * @param viewerMemberId 조회자 회원 ID. 비로그인이면 null — 응답의 isOwner 판별에만 쓰인다.
     */
    CompanyInfo findById(String companyId, String viewerMemberId);

    /** 소유 회원 ID로 본인 기업 프로필을 조회한다. */
    CompanyInfo findByMemberId(String memberId);

    void updateName(String memberId, String companyName);

    /** 커맨드의 각 필드가 null이면 해당 항목은 변경하지 않는다. */
    void updateInfo(String memberId, UpdateCompanyInfoCommand command);

    CompanyCareerInfo addCareer(String memberId, AddCompanyCareerCommand command);

    /** 경력 목록 조회 — 공개 조회이므로 소유자 검증을 하지 않는다. */
    List<CompanyCareerInfo> listCareers(String companyId);
}
