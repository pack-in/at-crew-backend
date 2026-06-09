package com.atcrew.member;

import java.time.LocalDateTime;
import java.util.List;

public record MemberInfo(

        // === 식별자 ===
        String id,           // MongoDB ObjectId
        String handle,       // @핸들 (URL 식별자, unique)
        String loginEmail,   // 로그인 이메일 (탈퇴 시 null)

        // === 기본 프로필 [피그마: 사용자 이름 입력 영역] ===
        String name,         // 활동명·작가명·기업명 (max 16자)
        String profileImage, // 프로필 이미지 URL

        // === 창작자 유형 [피그마: 계정 만들기 - 유형 선택] ===
        CreatorRole creatorRole,   // WEBTOON·ILLUSTRATOR·WEB_NOVELIST·OTHER

        // === 구인구직 상태 [피그마: 구인구직 정보 선택 섹션] ===
        EmploymentStatus employmentStatus,  // 구직중·구인중·구인구직중·준비중

        // === 작가 전용: 슬롯 [피그마: 작가 정보 - 슬롯] ===
        int totalSlotCount,       // 총 작업 슬롯 수 (1-5, 기본값 5)
        int availableSlotCount,   // 현재 가능한 슬롯 수 (0-totalSlotCount)

        // === 팀 작업 경험 [피그마: 팀 작업 경험 선택 섹션] ===
        // NONE·SHORT_TERM(단기 협업)·DIVISION(분업형)·REGULAR_DEADLINE(정기 마감), 복수 선택
        List<TeamExperience> teamExperiences,

        // === 연락처·SNS [피그마: 연락처/SNS 입력 필드] ===
        String contactEmail,     // 연락처 이메일
        String socialMediaLink,  // SNS 링크
        String twitter,          // 트위터

        // === 작가 정보 [피그마: 사용 가능한 툴 입력 필드] ===
        String creativeTools,    // 사용 가능한 툴

        // === 경력 목록 [피그마: 작가 경력 작성 섹션] ===
        // 작품명·참여화수·시작일(YYYY.MM)·종료일·연재중여부·세부내용(max 200자)
        List<CareerEntryInfo> careers,

        // === 키워드 ===
        List<String> keywords,

        // === 피그마에 없음 — 라이트(laiteu) 마이그레이션 호환성 유지 ===
        ExperienceLevel experienceLevel, // 경력 연차 (라이트 호환)
        String birthDate,                // 생년월일 (라이트 호환)
        String school,                   // 학교 (라이트 호환)
        String location,                 // 활동 지역 (기업: 복수 선택 예정)
        String desiredField,             // 희망 분야 (라이트 호환)

        // === 계정 상태 ===
        boolean active,
        LocalDateTime deletedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
