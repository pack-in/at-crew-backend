package com.atcrew.member;

public record CareerEntryInfo(
        String id,            // 경력 ID
        String workTitle,     // 참여작 이름 (예: 홍길동전)
        String role,          // 담당 업무 (예: 작화 전공정, 리깅, 콘티)
        String startDate,     // 작업 시작일 (YYYY.MM)
        String endDate,       // 작업 종료일 (YYYY.MM, 연재중이면 null)
        boolean ongoing,      // 연재중 여부
        String description,   // 작업 내용 (max 200자)
        String periodDisplay  // 경력 기간 표시 (예: "2023.01 ~ 연재중", "2023.01 ~ 2024.06 약 1년 5개월")
) {
}
