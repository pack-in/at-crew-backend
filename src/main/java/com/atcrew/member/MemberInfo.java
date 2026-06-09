package com.atcrew.member;

import java.time.LocalDateTime;
import java.util.List;

public record MemberInfo(

        // === 식별자 ===
        String id,         // MongoDB ObjectId
        String handle,     // @핸들 (URL 식별자, unique)
        String loginEmail, // 로그인 이메일 (탈퇴 시 null)

        // === 기본 프로필 ===
        String name,           // 사용자 이름·작가명 (max 16자)
        CreatorRole creatorRole, // 창작자 유형 (웹툰작가·일러스트작가·웹소설작가·기타)

        // === 구인구직 상태 [피그마: 구인구직 상태 칩 선택] ===
        EmploymentStatus employmentStatus, // 준비중·신규 작업 가능·협의 가능·마감

        // === 활동 분야 [피그마: 활동 분야 칩 복수 선택] ===
        List<ActivityField> activityFields, // 일러스트·웹툰·출판만화·애니메이션

        // === 활동 경력 [피그마: 활동 경력 칩 단일 선택] ===
        ExperienceLevel experienceLevel, // 신입·1-2년차·3-4년차·5-9년차·10년차 이상

        // === 활동 지역 [피그마: 활동 지역 칩 복수 선택] ===
        List<ActiveRegion> activeRegions, // 서울·경기도·대전·대구·광주·부산·기타

        // === 팀 작업 경험 [피그마: 팀 작업 경험 칩 복수 선택] ===
        List<TeamExperience> teamExperiences, // 없음·단기 협업 팀·분업형 팀·정기 마감 팀

        // === 슬롯 [피그마: 전체 슬롯 개수 / 작업 가능 슬롯] ===
        int totalSlotCount,     // 총 작업 슬롯 수 (1-5, 기본값 5)
        int availableSlotCount, // 현재 가능한 슬롯 수 (0-totalSlotCount)

        // === 연락처·SNS·툴 [피그마: 작가 정보 입력 섹션] ===
        String contact, // 연락처 (전화번호 또는 이메일 통합 단일 필드)
        String sns,     // SNS 링크 (인스타그램·X·포스타입 통합 단일 필드)
        String tools,   // 사용 가능한 툴 (예: 클립스튜디오, 포토샵)

        // === 경력 목록 [피그마: 작가 경력 섹션] ===
        List<CareerEntryInfo> careers,

        // === 계정 상태 ===
        boolean active,
        LocalDateTime deletedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
