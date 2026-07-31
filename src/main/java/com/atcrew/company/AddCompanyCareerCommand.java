package com.atcrew.company;

import java.time.LocalDate;

public record AddCompanyCareerCommand(
        String workTitle,    // 작품 이름 (예: 홍길동전)
        LocalDate startDate, // 작업 시작일
        LocalDate endDate,   // 작업 종료일 (연재중이면 null)
        boolean ongoing,     // 연재중 여부
        String description   // 작품 관련 링크나 설명 (최대 200자)
) {
}
