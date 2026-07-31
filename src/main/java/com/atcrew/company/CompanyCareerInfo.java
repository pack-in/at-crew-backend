package com.atcrew.company;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDate;

/**
 * 기업 경력(참여작) 응답 DTO.
 *
 * <p>member의 경력과 달리 담당 업무(role) 필드가 없다
 * (docs/design/company-profile-module-design.md §2.2).
 */
public record CompanyCareerInfo(
        String id,           // 경력 ID
        String workTitle,    // 작품 이름 (예: 홍길동전)
        @JsonFormat(pattern = "yyyy.MM.dd") LocalDate startDate, // 작업 시작일
        @JsonFormat(pattern = "yyyy.MM.dd") LocalDate endDate,   // 작업 종료일 (연재중이면 null)
        boolean ongoing,     // 연재중 여부
        String description   // 작품 관련 링크나 설명 (최대 200자)
) {
}
