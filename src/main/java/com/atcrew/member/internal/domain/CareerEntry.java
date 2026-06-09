package com.atcrew.member.internal.domain;

import java.time.LocalDate;

class CareerEntry {

    private String id;
    private String workTitle;   // 참여작 이름
    private String role;        // 담당 업무
    private LocalDate startDate;   // 작업 시작일
    private LocalDate endDate;     // 작업 종료일 (연재중이면 null)
    private boolean ongoing;       // 연재중 여부
    private String description;    // 작업 내용 (max 200자)

    protected CareerEntry() {
    }

    CareerEntry(String id, String workTitle, String role,
                LocalDate startDate, LocalDate endDate, boolean ongoing, String description) {
        this.id = id;
        this.workTitle = workTitle;
        this.role = role;
        this.startDate = startDate;
        this.endDate = endDate;
        this.ongoing = ongoing;
        this.description = description;
    }

    String getId() { return id; }
    String getWorkTitle() { return workTitle; }
    String getRole() { return role; }
    LocalDate getStartDate() { return startDate; }
    LocalDate getEndDate() { return endDate; }
    boolean isOngoing() { return ongoing; }
    String getDescription() { return description; }
}
