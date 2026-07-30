# company 모듈 설계

> 작성일: 2026-07-30
> 상태: 설계안 (구현 착수)
> 범위: 기업 마이페이지(Company Info Section, Career Section), 접근 권한(공개 열람/본인 기업만 수정), 구인글 업로드 카드 진입점(스텁)
> 범위 밖(추후 별도 설계): 구인글 CRUD(recruit 모듈), 기업 인증 실제 심사 플로우 및 요금제 안내(본인/기업 인증 시스템·결제 모듈), 회원 탈퇴 시 기업 프로필 처리 정책
> 피그마 근거: UI개편_마이페이지_기업(5154:41398), UI개편_마이페이지_기업_수정페이지(5244:25813)
> 로드맵 근거: docs/roadmap.md §3

---

## 0. 설계 요약 (TL;DR)

| 항목 | 결정 |
|------|------|
| 착수 순서 이탈 | 로드맵 확정 순서는 `0.MariaDB 마이그레이션 → 1.본인/기업 인증 → 2.recruit → 3.기업 프로필`이지만, 사용자 지시로 recruit(별도 워킹트리)과 기업 프로필(본 워킹트리)을 **본인/기업 인증 시스템보다 먼저** 병행 착수함. 인증에 의존하는 부분은 전부 스텁 처리(§4, §7.3) |
| 영속성 계층 | MariaDB/JPA 직접 사용 (Mongo 경유 안 함) — P1(인프라)·P2(Banner 파일럿)가 이미 완료된 상태를 그대로 이어감 |
| 기업 계정 식별 모델 | `Member`에 손대지 않음. **완전히 독립된 `Company` 엔티티** — 임의의 `Member`가 `POST /api/companies`로 자기 소유 기업 프로필 1개를 생성(1인 1기업, 애플리케이션 레벨 유니크 제약). member/auth 모듈 무변경 |
| 기업 인증(사업자등록증 진위 확인) | 이번 스코프 아님. Figma의 "사업자 등록 여부"는 **자기 신고형 boolean**(있음/없음)이며 실제 심사는 로드맵 1번(본인/기업 인증 시스템) 몫. `Company.verified` 필드를 미노출 스텁으로 추가해 자리만 확보 |
| Career Section | member의 `CareerEntry`와 동일 구조이나 **`role`(담당 업무) 필드 제외**, **삭제 엔드포인트 미제공**(로드맵 §3 명시 결정) — 피그마 수정페이지에 경력 삭제 확인 모달이 남아있으나 창작자 편집 컴포넌트를 그대로 복사해온 잔재로 판단, 로드맵의 명시적 결정을 우선함 |
| 접근 권한 | 비로그인/타 회원도 열람 가능(공개 GET), 본인 기업 계정으로 볼 때만 수정/업로드/관리 액션 노출 — artwork 모듈의 `assertOwner` 패턴 재사용 |
| recruit 연동 | recruit 모듈이 아직 없으므로 "구인글 업로드 카드"는 진입점 UI 훅만 두고 실제 업로드는 `CompanyRecruitPort`(§7.3) 스텁으로 501/빈 목록 처리 |
| 모듈 간 활동분야/지역 enum | member의 `ActivityField`/`ActiveRegion`을 import하지 않고 company 모듈에 동일 의미의 자체 enum을 새로 정의(모듈 간 직접 의존 금지 원칙, artwork/community도 동일 패턴) |

---

## 1. 모듈 분리 결정 근거

### 1.1 왜 별도 모듈로 분리하는가

기업 계정은 창작자(Member) 마이페이지와 화면 구조는 닮았지만 데이터 소유 주체가 다르다. 2026-06-11 auth/member 재설계 당시 `AccountType{CREATOR, COMPANY}`을 "기업은 완전히 다른 도메인이라 창작자 완성 후 별도 개발"이라는 사유로 의도적으로 제거한 이력이 있다(커밋 c3c0582). 지금이 그 "별도 개발" 시점이며, 원래 계획대로 기업 도메인은 member 내부 확장이 아니라 독립 모듈로 만든다.

### 1.2 기업 계정 소유 모델

`Company`는 `memberId`(소유자)를 갖는 별도 애그리거트다. 로그인/회원가입은 기존 `/api/auth/register`, `/api/auth/login`을 그대로 사용하고(변경 없음), 기업 프로필 생성은 로그인된 임의의 Member가 `POST /api/companies`를 호출해 만든다. 한 Member당 Company는 최대 1개(`uk_companies_member` 유니크 제약 + 서비스 레벨 검증).

**미결정 사항(TODO, 이번 스코프 아님)**: 로드맵 1번 "본인/기업 인증 시스템"이 구현되면 회원가입 시점에 "기업으로 가입"을 명시적으로 선택하게 할지, 아니면 지금처럼 가입 후 아무 Member나 기업 프로필을 만들 수 있게 둘지 재검토 필요. 현재는 후자로 열어둔다(가입 플로우 미변경이 최소 변경 원칙에 부합).

---

## 2. 도메인 모델

### 2.1 Company (테이블: `companies`)

```java
@Entity
@Table(name = "companies")
@EntityListeners(AuditingEntityListener.class)
public class Company implements Persistable<String> {

    @Id
    private String id;                     // UuidV7Generator, VARCHAR(36)

    private String memberId;               // 소유 회원(member 모듈 참조, FK 없음)

    private String companyName;            // 기업명, 최대 16자 (피그마 "3/16")
    private String contact;                // 연락처
    private String sns;                    // SNS 링크

    private RecruitStatus recruitStatus;   // 구인구직 상태, 기본 PREPARING
    private CompanyType companyType;       // 회사 형태, null 허용(미기입)
    private boolean hasBusinessRegistration; // 사업자 등록 여부(자기 신고), 기본 false

    private boolean verified;              // 기업 인증 완료 여부 — TODO: 로드맵 1번(본인/기업 인증 시스템) 연동 전까지 항상 false, API로 노출/변경 불가

    @ElementCollection
    @CollectionTable(name = "company_activity_fields", joinColumns = @JoinColumn(name = "company_id"))
    @Column(name = "activity_field")
    @Enumerated(EnumType.STRING)
    private Set<ActivityField> activityFields = new HashSet<>();

    @ElementCollection
    @CollectionTable(name = "company_active_regions", joinColumns = @JoinColumn(name = "company_id"))
    @Column(name = "value")
    @Enumerated(EnumType.STRING)
    private Set<ActiveRegion> activeRegions = new HashSet<>();

    @Version
    private Long version;

    @CreatedDate
    private Instant createdAt;
    @LastModifiedDate
    private Instant updatedAt;

    @Transient
    private boolean isNew = true;
    // Banner와 동일하게 Persistable 구현 — 앱이 ID를 미리 생성하므로 @PostPersist/@PostLoad로 isNew 관리
}
```

- `assertOwner(String memberId)` — `!this.memberId.equals(memberId)`면 `CompanyException(COMPANY_ACCESS_DENIED)`(403). artwork의 `Artwork.assertOwner()`와 동일 패턴.
- 소프트 삭제/탈퇴 처리 컬럼(`deletedAt` 등) 없음 — 이번 스코프 아님(§0 범위 밖 참고).

### 2.2 CompanyCareer (테이블: `company_careers`)

```java
@Entity
@Table(name = "company_careers")
public class CompanyCareer {

    @Id
    private String id;                 // UuidV7Generator

    private String companyId;

    private String workTitle;          // 작품 이름 (예: 홍길동전)
    private LocalDate startDate;
    private LocalDate endDate;         // ongoing=true면 null
    private boolean ongoing;           // "연재중" 표시
    private String description;        // 작품 관련 링크나 설명, 최대 200자

    // role(담당 업무) 필드 없음 — member.CareerEntry와의 유일한 구조적 차이(§0 참고)
}
```

- 추가(`POST /api/companies/me/careers`), 목록 조회만 제공. **삭제 엔드포인트 없음**(로드맵 §3 결정). 개수 제한은 member의 `CAREER_LIMIT_EXCEEDED`(최대 50) 패턴을 그대로 따른다.

---

## 3. Enum 정의

```java
public enum RecruitStatus { PREPARING, RECRUITING, ALWAYS_RECRUITING, CLOSED }
// 준비중 / 모집중 / 상시 모집 / 모집 마감 — 기본값 PREPARING (Member.EmploymentStatus와 동일 관례)

public enum CompanyType {
    STUDIO, PLATFORM, AGENCY, PUBLISHER, INDIVIDUAL_STUDIO, SMALL_TEAM, OTHER
}
// 제작사 / 플랫폼 / 에이전시 / 출판사 / 개인스튜디오 / 소규모팀 / 기타

public enum ActivityField { ILLUSTRATION, WEBTOON, ANIMATION, WEB_NOVEL, OTHER }
// 일러스트 / 웹툰 / 애니메이션 / 웹소설 / 기타 — member.ActivityField와 값은 같지만 모듈 경계상 별도 정의(§0)

public enum ActiveRegion { ... }
// 활동 지역 — 피그마 수정페이지에서 정확한 편집 UI를 특정하지 못함(설계 시점 조사 한계).
// member.ActiveRegion과 동일한 값 집합으로 우선 정의하고, 실제 화면 확인 시 조정. TODO 표시.
```

---

## 4. 피그마 비즈니스 규칙

- **페이지 접근 권한** (피그마 원문): "비로그인 사용자도 기업 마이페이지 열람 가능함. 로그인 사용자도 다른 기업의 마이페이지 열람 가능함. 본인 기업 계정으로 본인 페이지를 볼 때만 수정/업로드/관리 액션 노출함. 다른 사용자가 보는 기업 페이지에서는 수정 아이콘, 구인글 업로드 카드, 카드 더보기 메뉴 미노출함."
- **Company Info Section** 표시 항목(읽기 전용, 순서): 구인구직 상태 → 활동 분야 → 회사 형태 → 활동 지역 → 연락처 → SNS → 사업자 등록 여부. 미기입 항목은 `-`로 표시(클라이언트 책임, API는 null 반환).
- **구인글 업로드 모달_기업 인증 전 / 기업 인증 후_요금제 안내 로직**: 구인글 업로드 카드 진입 시 기업 인증 여부에 따라 분기하는 화면이 피그마에 존재하나, 인증 시스템과 결제/요금제 모듈이 모두 미구현이므로 이번 스코프에서는 업로드 카드를 recruit 모듈 완성 전까지 비활성 상태(스텁 메시지)로만 노출한다(§7.3).

---

## 5. API 설계

| 메서드 | 경로 | 인증 | 설명 |
|---|---|---|---|
| POST | /api/companies | 필수 | 기업 프로필 생성 (1 Member당 1회) |
| GET | /api/companies/{companyId} | 불필요 | 공개 조회 — 본인 여부는 옵셔널 뷰어로 판단해 응답에 `isOwner` 포함 |
| GET | /api/companies/me | 필수 | 본인 기업 프로필 조회 |
| PATCH | /api/companies/me/name | 본인 기업만 | 기업명 변경 |
| PATCH | /api/companies/me/info | 본인 기업만 | 연락처/SNS/구인구직상태/회사형태/활동분야/활동지역/사업자등록여부 일괄 변경 (member의 `UpdateInfoRequest`와 동일하게 각 필드 null이면 변경 없음) |
| POST | /api/companies/me/careers | 본인 기업만 | 경력 추가 |
| GET | /api/companies/{companyId}/careers | 불필요 | 경력 목록 조회 |

- 삭제(DELETE) 엔드포인트 없음(경력, 기업 프로필 모두) — §0/§2.2 참고.
- 응답 포맷은 기존 모듈과 동일한 `ApiResponse.success(...)` 봉투 사용.

---

## 6. 모듈 경계

### 6.1 company → member

Member를 직접 참조하지 않고 `memberId` 문자열만 저장(FK 없음, artwork/community와 동일). 소유자 존재 검증이 필요하면 `MemberService.existsById(...)` 등 공개 인터페이스만 호출.

### 6.2 company → recruit (스텁)

```java
// com.atcrew.company.CompanyRecruitPort
public interface CompanyRecruitPort {
    boolean hasOpenJobPosting(String companyId);
    // recruit 모듈이 생기면 그 모듈이 구현체를 제공하도록 이관한다.
}

// com.atcrew.company.internal.application.NoopCompanyRecruitPort
@Component
class NoopCompanyRecruitPort implements CompanyRecruitPort {
    public boolean hasOpenJobPosting(String companyId) { return false; }
}
```

구인글 업로드 카드 진입점은 이 포트로 "업로드 가능 여부"만 확인하고, 실제 CRUD는 recruit 모듈 완성 후 연결한다(community의 `RecruitFeedPort` 패턴 그대로 재사용).

### 6.3 company → verification (스텁, 로드맵 1번 선행 필요)

`Company.verified`는 이번 모듈에서 읽기 전용 스텁(`false` 고정, API 미노출)이다. 로드맵 1번 인증 시스템이 구현되면 해당 모듈이 이 필드를 갱신하는 경로(이벤트 또는 서비스 호출)를 새로 연결해야 한다. **TODO로 명확히 남겨둠** — 로드맵에 "이번엔 미루던 패턴을 반복하지 않기로 결정"이라 기록되어 있으므로, 후속 인증 모듈 작업 시 이 문서와 `Company.verified`부터 확인할 것.

---

## 7. 인덱스 설계

```sql
CREATE TABLE companies (
    id                          VARCHAR(36) CHARACTER SET latin1 COLLATE latin1_bin NOT NULL,
    member_id                   VARCHAR(36) CHARACTER SET latin1 COLLATE latin1_bin NOT NULL,
    company_name                VARCHAR(16)  NULL,
    contact                     VARCHAR(255) NULL,
    sns                         VARCHAR(255) NULL,
    recruit_status              VARCHAR(30)  NOT NULL DEFAULT 'PREPARING',
    company_type                VARCHAR(30)  NULL,
    has_business_registration   TINYINT(1)   NOT NULL DEFAULT 0,
    verified                    TINYINT(1)   NOT NULL DEFAULT 0,
    version                     BIGINT       NOT NULL DEFAULT 0,
    created_at                  DATETIME(6)  NOT NULL,
    updated_at                  DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_companies_member (member_id)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE company_activity_fields (
    company_id      VARCHAR(36) CHARACTER SET latin1 COLLATE latin1_bin NOT NULL,
    activity_field  VARCHAR(30) NOT NULL,
    PRIMARY KEY (company_id, activity_field)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE company_active_regions (
    company_id  VARCHAR(36) CHARACTER SET latin1 COLLATE latin1_bin NOT NULL,
    value       VARCHAR(30) NOT NULL,
    PRIMARY KEY (company_id, value)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE company_careers (
    id            VARCHAR(36) CHARACTER SET latin1 COLLATE latin1_bin NOT NULL,
    company_id    VARCHAR(36) CHARACTER SET latin1 COLLATE latin1_bin NOT NULL,
    work_title    VARCHAR(255) NULL,
    start_date    DATE NULL,
    end_date      DATE NULL,
    ongoing       TINYINT(1) NOT NULL DEFAULT 0,
    description   VARCHAR(200) NULL,
    PRIMARY KEY (id),
    KEY idx_cc_company (company_id)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

Flyway `V2__company_module.sql`로 커밋(V1 베이스라인 이후 첫 신규 모듈 스키마).

---

## 8. 라이트(laiteu) 대비 차이점

라이트에 기업 계정 도메인 자체가 없어 직접 비교 대상 없음(신규 도메인).

---

## 9. 구현 결정 사항 / 범위 밖

- 기업 인증 실사(사업자등록번호 진위 확인 등 외부 연동) — 로드맵 1번
- 구인글 업로드 실제 CRUD — recruit 모듈
- 요금제/결제 안내 로직 — 로드맵 5번
- 회원 탈퇴 시 소유 기업 프로필 처리(고아 데이터 방지) — 미결정, TODO
- `활동 지역(ActiveRegion)` 편집 UI의 정확한 옵션 값 — 피그마 조사 시 특정 못함, 구현 시 member와 동일 값으로 우선 진행 후 재확인 필요
