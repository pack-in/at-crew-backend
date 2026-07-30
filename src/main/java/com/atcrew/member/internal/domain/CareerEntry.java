package com.atcrew.member.internal.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

@Entity
@Table(name = "member_careers")
class CareerEntry {

    private static final DateTimeFormatter DATE_DISPLAY_FORMAT = DateTimeFormatter.ofPattern("yyyy.MM.dd");

    @Id
    private String id;

    // 양방향 매핑(Member.careers의 mappedBy) — 단방향 @JoinColumn은 Hibernate가 INSERT 시
    // FK 없이 먼저 쓰고 뒤이어 UPDATE로 채우는 2단계 패턴이라 NOT NULL 컬럼과 충돌한다.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    private String workTitle;   // 참여작 이름
    private String role;        // 담당 업무
    private LocalDate startDate;   // 작업 시작일
    private LocalDate endDate;     // 작업 종료일 (연재중이면 null)
    private boolean ongoing;       // 연재중 여부
    private String description;    // 작업 내용 (max 200자)

    protected CareerEntry() {
    }

    CareerEntry(String id, Member member, String workTitle, String role,
                LocalDate startDate, LocalDate endDate, boolean ongoing, String description) {
        this.id = id;
        this.member = member;
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

    String periodDisplay() {
        String start = startDate.format(DATE_DISPLAY_FORMAT);
        if (ongoing || endDate == null) return start + " ~ 연재중";

        String end = endDate.format(DATE_DISPLAY_FORMAT);
        long months = ChronoUnit.MONTHS.between(startDate, endDate);

        String duration;
        if (months <= 0) {
            long days = ChronoUnit.DAYS.between(startDate, endDate);
            duration = days <= 1 ? "하루" : days + "일";
        } else if (months < 12) {
            duration = "약 " + months + "개월";
        } else {
            long years = months / 12;
            long rem = months % 12;
            duration = rem == 0 ? "약 " + years + "년" : "약 " + years + "년 " + rem + "개월";
        }
        return start + " ~ " + end + " " + duration;
    }
}
