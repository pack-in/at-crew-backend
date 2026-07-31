package com.atcrew.company.internal.domain;

import com.atcrew.common.id.UuidV7Generator;
import com.atcrew.company.internal.exception.CompanyErrorCode;
import com.atcrew.company.internal.exception.CompanyException;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import org.springframework.data.domain.Persistable;

import java.time.LocalDate;

/**
 * 기업 경력(참여작). member의 경력과 달리 담당 업무(role) 필드가 없고 삭제 기능도 제공하지 않는다
 * (docs/design/company-profile-module-design.md §2.2).
 */
@Entity
@Table(name = "company_careers")
public class CompanyCareer implements Persistable<String> {

    @Id
    private String id;

    private String companyId;

    private String workTitle;
    private LocalDate startDate;
    private LocalDate endDate;
    private boolean ongoing;
    private String description;

    // Company와 동일 — 애플리케이션이 ID를 미리 발급하므로 Persistable로 신규 여부를 명시한다.
    @Transient
    private boolean isNew = false;

    protected CompanyCareer() {
    }

    public static CompanyCareer create(String companyId, String workTitle, LocalDate startDate,
                                       LocalDate endDate, boolean ongoing, String description) {
        validatePeriod(startDate, endDate, ongoing);
        CompanyCareer career = new CompanyCareer();
        career.id = UuidV7Generator.generate();
        career.companyId = companyId;
        career.workTitle = workTitle;
        career.startDate = startDate;
        career.endDate = endDate;
        career.ongoing = ongoing;
        career.description = description;
        career.isNew = true;
        return career;
    }

    private static void validatePeriod(LocalDate startDate, LocalDate endDate, boolean ongoing) {
        if (ongoing && endDate != null) {
            throw new CompanyException(CompanyErrorCode.INVALID_CAREER_PERIOD, "연재중 상태에서는 종료일을 입력할 수 없습니다");
        }
        if (!ongoing && endDate == null) {
            throw new CompanyException(CompanyErrorCode.INVALID_CAREER_PERIOD, "종료일 누락");
        }
        if (!ongoing && endDate.isBefore(startDate)) {
            throw new CompanyException(CompanyErrorCode.INVALID_CAREER_PERIOD, startDate + " ~ " + endDate);
        }
    }

    @Override
    public String getId() { return id; }
    public String getCompanyId() { return companyId; }
    public String getWorkTitle() { return workTitle; }
    public LocalDate getStartDate() { return startDate; }
    public LocalDate getEndDate() { return endDate; }
    public boolean isOngoing() { return ongoing; }
    public String getDescription() { return description; }

    @Override
    public boolean isNew() { return isNew; }

    @PostPersist
    @PostLoad
    void markNotNew() {
        this.isNew = false;
    }
}
