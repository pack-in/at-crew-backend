package com.atcrew.company;


/** 각 필드가 null이면 변경 없음 — 부분 업데이트(PATCH) 커맨드. */
public record UpdateCompanyInfoCommand(
        RecruitStatus recruitStatus,        // 구인구직 상태
        CompanyType companyType,            // 회사 형태
        ActivityField activityField,        // 활동 분야 (단일 선택)
        String contact,                     // 연락처
        String sns,                         // SNS 링크
        Boolean hasBusinessRegistration     // 사업자 등록 여부 (자기 신고)
) {
}
