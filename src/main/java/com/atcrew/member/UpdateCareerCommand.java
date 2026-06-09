package com.atcrew.member;

public record UpdateCareerCommand(
        String workTitle,   // 참여작 이름
        String role,        // 담당 업무
        String startDate,   // 시작일 (YYYY.MM)
        String endDate,     // 종료일 (YYYY.MM, 연재중이면 null)
        boolean ongoing,    // 연재중 여부
        String description  // 작업 내용
) {
}
