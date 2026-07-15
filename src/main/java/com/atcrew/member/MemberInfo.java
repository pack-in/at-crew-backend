package com.atcrew.member;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record MemberInfo(

        // === 식별자 ===
        String id,         // MongoDB ObjectId
        String handle,     // @핸들 (URL 식별자, unique)
        @JsonIgnore
        String loginEmail, // 로그인 이메일 (탈퇴 시 null)

        // === 인증 ===
        @Schema(nullable = true)
        AuthProvider authProvider, // 가입 경로 (EMAIL | GOOGLE)

        // === 기본 프로필 ===
        String name,           // 사용자 이름·작가명 (max 16자)
        @Schema(nullable = true)
        CreatorRole creatorRole, // 창작자 유형 (웹툰작가·일러스트작가·웹소설작가·기타)

        // === 구인구직 상태 [피그마: 구인구직 상태 칩 선택] ===
        EmploymentStatus employmentStatus,

        // === 활동 분야 [피그마: 활동 분야 칩 복수 선택] ===
        List<ActivityField> activityFields,

        // === 활동 경력 [피그마: 활동 경력 칩 단일 선택] ===
        ExperienceLevel experienceLevel,

        // === 활동 지역 [피그마: 활동 지역 칩 복수 선택] ===
        List<ActiveRegion> activeRegions,

        // === 팀 작업 경험 [피그마: 팀 작업 경험 칩 복수 선택] ===
        List<TeamExperience> teamExperiences,

        // === 슬롯 [피그마: 전체 슬롯 개수 / 작업 가능 슬롯] ===
        int totalSlotCount,
        int availableSlotCount,

        // === 연락처·SNS·툴 [피그마: 작가 정보 입력 섹션] ===
        String contact,
        String sns,
        String tools,

        // === 경력 목록 [피그마: 작가 경력 섹션] ===
        List<CareerEntryInfo> careers,

        // === 계정 상태 ===
        boolean active,
        Instant deletedAt,
        Instant lastLoginAt,
        Instant createdAt,
        Instant updatedAt,

        // === 시간대 ===
        String timezone // IANA tz ID, 예: "Asia/Tokyo"
) {
}
