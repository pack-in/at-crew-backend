package com.atcrew.member;

import java.time.LocalDate;

public record AddCareerCommand(
        String workTitle,     // 참여작 이름
        String role,          // 담당 업무
        LocalDate startDate,  // 시작일
        LocalDate endDate,    // 종료일 (연재중이면 null)
        boolean ongoing,      // 연재중 여부
        String description    // 작업 내용
) {
}
