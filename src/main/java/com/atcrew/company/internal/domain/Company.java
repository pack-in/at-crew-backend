package com.atcrew.company.internal.domain;

import com.atcrew.common.id.UuidV7Generator;
import com.atcrew.company.ActivityField;
import com.atcrew.company.CompanyType;
import com.atcrew.company.RecruitStatus;
import com.atcrew.company.UpdateCompanyInfoCommand;
import com.atcrew.company.internal.exception.CompanyErrorCode;
import com.atcrew.company.internal.exception.CompanyException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.persistence.Version;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.domain.Persistable;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

@Entity
@Table(name = "companies")
@EntityListeners(AuditingEntityListener.class)
public class Company implements Persistable<String> {

    @Id
    private String id;

    // 소유 회원 ID — member 모듈 참조이므로 FK를 걸지 않는다(모듈 경계 정책).
    private String memberId;

    private String companyName;
    private String contact;
    private String sns;

    @Enumerated(EnumType.STRING)
    private RecruitStatus recruitStatus = RecruitStatus.PREPARING;

    @Enumerated(EnumType.STRING)
    private CompanyType companyType;

    // 사업자 등록 여부 — 자기 신고형이며 실제 심사는 로드맵 1번(본인/기업 인증 시스템) 몫이다.
    private boolean hasBusinessRegistration;

    // TODO: 기업 인증 완료 여부 — 로드맵 1번(본인/기업 인증 시스템) 연동 전까지 항상 false이며
    // API로 노출하거나 변경할 수 없다 (docs/design/company-profile-module-design.md §6.3).
    private boolean verified;

    // 단일 선택 — 피그마 5779:32101, 기획서 마이페이지_기업-R07("활동 분야(4)는 단일 칩").
    @Enumerated(EnumType.STRING)
    private ActivityField activityField;

    @Version
    private Long version;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;
    // 변경 주체(이슈 #138). 회원 ID이거나 SYSTEM, 운영자 수동 UPDATE는 "ops:<담당자>".
    // 기록 시작 전 행은 NULL로 남는다.
    @LastModifiedBy
    @Column(name = "last_modified_by", length = 64)
    private String lastModifiedBy;

    // Banner와 동일 — 애플리케이션이 ID를 미리 발급하므로 Persistable로 신규 여부를 명시해
    // save()가 merge(선행 SELECT) 대신 persist(INSERT)로 동작하게 한다.
    @Transient
    private boolean isNew = false;

    protected Company() {
    }

    public static Company create(String memberId, String companyName) {
        Company company = new Company();
        company.id = UuidV7Generator.generate();
        company.memberId = memberId;
        company.companyName = companyName;
        company.isNew = true;
        // recruitStatus(PREPARING)·hasBusinessRegistration(false)·verified(false)는 필드 기본값을 그대로 사용한다.
        return company;
    }

    public void assertOwner(String memberId) {
        if (!this.memberId.equals(memberId)) {
            throw new CompanyException(CompanyErrorCode.COMPANY_ACCESS_DENIED);
        }
    }

    public boolean isOwnedBy(String memberId) {
        return memberId != null && this.memberId.equals(memberId);
    }

    public void updateName(String companyName) {
        this.companyName = companyName;
    }

    /** 커맨드의 각 필드가 null이면 해당 항목은 변경하지 않는다. */
    public void updateInfo(UpdateCompanyInfoCommand command) {
        if (command.recruitStatus() != null) this.recruitStatus = command.recruitStatus();
        if (command.companyType() != null) this.companyType = command.companyType();
        if (command.activityField() != null) this.activityField = command.activityField();
        if (command.contact() != null) this.contact = command.contact();
        if (command.sns() != null) this.sns = command.sns();
        if (command.hasBusinessRegistration() != null) {
            this.hasBusinessRegistration = command.hasBusinessRegistration();
        }
    }

    @Override
    public String getId() { return id; }
    public String getMemberId() { return memberId; }
    public String getCompanyName() { return companyName; }
    public String getContact() { return contact; }
    public String getSns() { return sns; }
    public RecruitStatus getRecruitStatus() { return recruitStatus; }
    public CompanyType getCompanyType() { return companyType; }
    public boolean hasBusinessRegistration() { return hasBusinessRegistration; }
    public boolean isVerified() { return verified; }
    public ActivityField getActivityField() { return activityField; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    @Override
    public boolean isNew() { return isNew; }

    @PostPersist
    @PostLoad
    void markNotNew() {
        this.isNew = false;
    }
}
