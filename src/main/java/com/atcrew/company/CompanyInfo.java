package com.atcrew.company;

import java.time.Instant;
import java.util.List;

/**
 * 기업 프로필 공개 응답 DTO.
 *
 * <p>{@code verified}(기업 인증 완료 여부)는 로드맵 1번(본인/기업 인증 시스템) 연동 전까지
 * API로 노출하지 않는다 (docs/design/company-profile-module-design.md §2.1, §6.3).
 */
public record CompanyInfo(
        String id,                          // 기업 프로필 ID
        String memberId,                    // 소유 회원 ID
        String companyName,                 // 기업명 (최대 16자)
        String contact,                     // 연락처 (미기입 시 null)
        String sns,                         // SNS 링크 (미기입 시 null)
        RecruitStatus recruitStatus,        // 구인구직 상태
        CompanyType companyType,            // 회사 형태 (미기입 시 null)
        boolean hasBusinessRegistration,    // 사업자 등록 여부 (자기 신고)
        List<ActivityField> activityFields, // 활동 분야
        List<ActiveRegion> activeRegions,   // 활동 지역
        boolean hasOpenJobPosting,          // 공개 중인 구인글 보유 여부 — 구인글 업로드 카드 진입점 판단용(§6.2)
        boolean isOwner,                    // 조회자가 소유자인지 여부 — 수정/업로드/관리 액션 노출 판단용
        Instant createdAt,                  // 생성 일시
        Instant updatedAt                   // 최종 수정 일시
) {
}
