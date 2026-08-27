package com.atcrew.member;

import java.time.Instant;
import java.util.List;

public record MemberProfileInfo(

        // === 식별자 ===
        String id,     // MongoDB ObjectId
        String handle, // @핸들 (URL 식별자, unique)

        // === 기본 프로필 ===
        String name, // 사용자 이름·작가명

        // === 구인구직 상태 ===
        EmploymentStatus employmentStatus, // 준비중·신규 작업 가능·협의 가능·마감

        // === 활동 분야 ===
        List<ActivityField> activityFields, // 일러스트·웹툰·출판만화·애니메이션

        // === 활동 경력 ===
        ExperienceLevel experienceLevel, // 신입·1-2년차·3-4년차·5-9년차·10년차 이상

        // === 활동 지역 ===
        ActiveRegion activeRegion, // 서울·경기도·강원도·충청북도·충청남도·전라북도·전라남도·경상북도·경상남도·제주도

        // === 팀 작업 경험 ===
        List<TeamExperience> teamExperiences, // 없음·단기 협업 팀·분업형 팀·정기 마감 팀

        // === 작화 스타일·작업 스타일 [피그마: 기본 정보 탭] ===
        List<DrawingStyle> drawingStyles,
        WorkPace workPace,

        // === 구직 정보 [피그마: 구직 정보 탭, 기획서 마이페이지_작가-R24] ===
        AvailableStartPeriod availableStartPeriod,
        List<DesiredRole> desiredRoles,
        List<DesiredGenre> desiredGenres,
        List<DesiredEmploymentType> desiredEmploymentTypes,
        DesiredWorkLocation desiredWorkLocation,
        List<FeedbackPreference> feedbackPreferences,
        DesiredMinimumGuarantee desiredMinimumGuarantee,
        DesiredAnnualSalary desiredAnnualSalary,

        // === 직접입력 태그 [기획서 업로드-R13] ===
        List<CustomTagInfo> customTags,

        // === 프로필 열람수 ===
        int profileViewCount, // 작가 찾기 조회순 정렬 기준 (마이페이지_작가-R03)

        // === 슬롯 ===
        int totalSlotCount,     // 총 작업 슬롯 수
        int availableSlotCount, // 현재 가능한 슬롯 수

        // === 연락처·SNS·툴 ===
        String contact, // 연락처
        String sns,     // SNS 링크
        String tools,   // 사용 가능한 툴

        // === 경력 목록 ===
        List<CareerEntryInfo> careers,

        Instant createdAt,
        Instant updatedAt
) {
}
