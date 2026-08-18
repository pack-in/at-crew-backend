# recruit 모듈 설계

> 작성일: 2026-07-30 / 갱신: 2026-07-31 (정식 기획서 `docs/AT-CREW_서비스기획서_전체_20260728.xlsx` 대조, 스코프 확대)
> 상태: 설계 확정, 구현 진행 중 (워킹트리 `recruit-module`, 브랜치 `worktree-recruit-module`)
> 병행 진행: 기업 계정/프로필 모듈(로드맵 3번)은 완료·머지됨(`ab61508`).

> **2026-07-31 스코프 정정**: 정식 서비스 기획서 전체를 대조한 결과, §9(범위 밖)에서 제외했던
> **끌어올리기(boost)**와 **관심 작가(좋아요/최근 본)**가 실제로는 P0 요구사항(REQ-015·REQ-017)이었음이
> 확인됨. laiteu 참고 자료 부재·`roadmap.md` 구버전을 근거로 제외했던 결정을 철회하고 이번 스코프에
> 포함한다(기획서가 정본, [[feedback_figma_source_of_truth]] 원칙). §2.1·§2.2·§9·§4를 갱신했다. 결제
> 연동(Polar, 로드맵 5번)은 아직 없으므로 끌어올리기 적용 API 자체는 결제 게이팅 없이 먼저 구현하고,
> "구매한 끌어올리기 개수 차감" 로직은 Polar 모듈 완성 후 연결한다(다른 게이팅 스텁과 동일 패턴, §7).

---

## 0. 설계 요약 (TL;DR)

- 로드맵상 순서(MariaDB 전환 완료 → 인증 시스템 → recruit)를 앞당겨 착수한다. **사용자 결정**: recruit은 처음부터 JPA/MariaDB로 짓는다(Mongo로 먼저 만들고 나중에 이관하지 않음 — 이중 작업 회피). 인증(verification) 게이팅과 기업 계정 검증은 아직 없으므로 **스텁으로 미루고 CRUD/도메인을 먼저 완성**한다.
- 스코프: `JobPosting`(구인글) · `TeamPosting`(팀원모집글) · `JobSeekingPost`(구직글) · `Application`(지원) CRUD + 지원자 관리 + **끌어올리기(boost)** + **관심 작가(기업 전용)**.
- laiteu 대비 확인된 기술부채 2건은 **재현하지 않는다**: (1) `JobApplication.artworkPublicId` 오명명 → `jobPostingId`로 정명, (2) TeamPosting 지원자 조회 소유권 검증 누락 → 처음부터 검증 포함.
- **끌어올리기(48시간 상단고정)는 이번 스코프에 포함**한다(2026-07-31 정정, §2.1·§2.2·§9 참고) — JobPosting·TeamPosting 둘 다 대상, 각각 별도 유료 단건상품이지만 결제 게이팅은 Polar 모듈 완성 전까지 스텁(§7).
- **관심 작가(좋아요/최근 본, 기업 전용)도 이번 스코프에 포함**한다(2026-07-31 정정, §2.7 참고).
- `community` 모듈의 `RecruitFeedPort`/`NoopRecruitFeedPort` 스텁은 폐기하고, recruit이 공개 API(`RecruitService` + `CommunityJobPostingCardInfo`/`CommunityTeamRecruitCardInfo` 이관)를 직접 제공한다 — artwork↔community 연동과 동일한 패턴으로 통일(§6.1).
- 기업 프로필 모듈이 완료되었으므로(`ab61508`) `authorMemberId`(Member 참조) 대신 `company` 모듈과 연동해 "기업 계정만 구인글 작성 가능" 검증을 지금 붙일 수 있다 — 다만 이번 스코프는 여전히 인증(verification, 로드맵 1번) 완료 전이므로 기업 인증 게이팅 자체는 스텁으로 유지한다(§7).

---

## 1. 영속성 계층 결정

recruit 모듈은 처음부터 JPA/MariaDB로 짓는다. `docs/design/mariadb-migration-design.md`의 결정 사항을 그대로 따른다:

> **2026-07-31 병합 반영**: 설계 시점에는 recruit이 이 저장소의 첫 JPA/Flyway 도입 모듈이었으나,
> 병렬로 진행된 MariaDB 전환 P1~P4(community·member·auth·artwork)가 먼저 `main`에 머지되면서
> JPA 인프라(`UuidV7Generator`, `@EnableJpaRepositories`/`@EnableJpaAuditing`, Flyway 베이스라인)는
> 이미 공용으로 존재한다. 아래 항목 중 "신규 추가"로 적힌 부분은 기존 공용 인프라 재사용으로 대체됐다.

- **ID**: `String`, 컬럼 `VARCHAR(36) CHARACTER SET latin1 COLLATE latin1_bin`, 애플리케이션에서 UUIDv7 생성 (§3.1). 공통 유틸 `com.atcrew.common.id.UuidV7Generator`를 그대로 사용(MariaDB 전환 P1에서 이미 `common`에 추가됨).
- **정규화 기준**(§3.2): WHERE 절/검색에 쓰이는 리스트는 자식 테이블, 표시 전용 리스트는 JSON 컬럼.
  - `roles`(`List<ArtworkRole>`), `genres`(`List<Genre>`) → 자식 테이블 (커뮤니티 피드 필터·검색 모듈에서 쓰일 가능성 높음, artwork의 `artwork_roles`/`artwork_genres`와 동일 패턴). 최초 구현은 자유 문자열이었으나 검색 필터와 어휘가 맞지 않아 2026-08-07에 정본 enum으로 고정함(`V15`)
  - `benefitKeywords`, `referenceImages`, `recruitPurposes` → JSON 컬럼 (표시 전용, 검색 대상 아님)
- **동시성**: `bookmarkCount`/`viewCount` 증가는 원자적 UPDATE(`UPDATE ... SET count = count + 1`)로 처리 — Mongo `$inc` 자리를 대체하는 §3.3 패턴을 그대로 따름.
- **낙관적 락**: `JobPosting`/`TeamPosting`에 `@Version` 적용 (상태 전이 동시 수정 방지 + `Persistable` 없이 assigned-ID 신규/기존 판별 겸용, §3.1 JPA 주의점).
- **Flyway**: `V5__recruit_schema.sql` ~ `V9__company_liked_artists.sql`. `V1`~`V4`는 MariaDB 전환 P1~P4가 선점했으므로 recruit 마이그레이션은 `V5`부터 이어붙인다(recruit 테이블만 포함, 다른 모듈 스키마와 FK로 얽지 않음).
- **JPA 설정**: 별도 설정 클래스를 두지 않는다 — `AtCrewBackendApplication`에 이미 `@EnableJpaAuditing`이 선언돼 있고 리포지토리 스캔은 `@SpringBootApplication` 기본 동작으로 충분하다. 데이터소스(`spring.datasource`, `docker-compose.yml`의 `mariadb` 서비스 대상)도 `application.yml`에 이미 구성돼 있다.

---

## 2. 도메인 모델

### 2.1 JobPosting (테이블: `job_postings`)

laiteu `JobPosting` 필드를 기준으로 하되, 부스트 필드는 제외.

| 필드 | 타입 | 설명 |
|---|---|---|
| id | VARCHAR(36) PK | UUIDv7 |
| authorMemberId | VARCHAR(36) | 작성자(Member 참조, FK 아님 — 모듈 경계상 논리적 참조만) |
| title | VARCHAR(200) | 공고 제목 |
| companyName, ceoName, industry, address, contact, websiteUrl | VARCHAR | 회사 정보 |
| companyDescription | TEXT | 회사 소개 |
| isBusinessRegistered | BOOLEAN | 사업자등록 여부 |
| isResumeRequired, isCoverLetterRequired | BOOLEAN | 이력서/자기소개서 필수 여부 |
| roles, genres | 자식 테이블 `job_posting_roles`/`job_posting_genres` | 모집 역할/장르 (복수) |
| workScope | VARCHAR(500) | 작업 범위 |
| deadline | DATE | 마감일 (NULL이면 상시모집) |
| recruitCount | INT | 모집 인원 |
| hiringProcess | TEXT | 채용 절차 |
| education, experience, age, gender | VARCHAR | 자유 텍스트 요건 (laiteu와 동일하게 자유 텍스트 유지 — 정형화는 이번 스코프 아님) |
| employmentType | ENUM(JobEmploymentType) | 고용 형태 |
| workLocationType | ENUM(`JobWorkLocationType`) | OFFICE/REMOTE/HYBRID |
| workScheduleType | ENUM(JobWorkScheduleType) | 근무 형태 (FLEXIBLE 포함) |
| coreTimeStart, coreTimeEnd | TIME NULL | FLEXIBLE일 때만 사용 |
| hasOvertimePay, hasSocialInsurance, hasContract | BOOLEAN | 복지 플래그 |
| paymentType, paymentUnit | ENUM | 급여 체계(연봉제 등), 단위 |
| minAmount, maxAmount | BIGINT NULL | 급여 범위 |
| isNegotiable | BOOLEAN | 협의 가능 여부 |
| mgAmount | BIGINT NULL | MG(미니멈개런티) 금액 |
| rsRatio | DECIMAL(5,2) NULL | RS(러닝개런티) 비율(%) |
| hasBuyout | BOOLEAN | 매절 여부 |
| benefitDescription | TEXT | 복지 설명 |
| benefitKeywords | JSON | 복지 키워드 리스트 (표시 전용) |
| thumbnailImage | VARCHAR(500) NULL | 썸네일 URL (이번 스코프는 URL 저장만 — Presigned 업로드 연동은 범위 밖, §7) |
| referenceImages | JSON | 참고 이미지 URL 리스트 |
| bookmarkCount, viewCount | BIGINT DEFAULT 0 | 비정규화 카운트 |
| boostedUntil | DATETIME(UTC `Instant`) NULL | 끌어올리기 만료 시각. `now < boostedUntil`이면 상단고정 노출 대상. 재적용은 `now >= boostedUntil`일 때만 허용(적용 기간=쿨다운이 동일 48시간이라 별도 쿨다운 컬럼 불필요) — §2.1.1 |
| status | ENUM(JobPostingStatus) | DRAFT/PENDING/PUBLISHED/CLOSED/DELETED |
| deletedAt | DATETIME NULL | 소프트 삭제(휴지통) 시각 |
| version | BIGINT | 낙관적 락 |
| createdAt, updatedAt | DATETIME | 감사 컬럼 |

**상태 전이**: `DRAFT → PENDING`(제출) `→ PUBLISHED`(관리자 승인) / `→ PENDING`(반려 후 재제출) `→ CLOSED`(마감/작성자 종료) `→ DELETED`(휴지통, soft delete). laiteu와 동일하게 PENDING 승인 절차 유지.

#### 2.1.1 끌어올리기(boost) 적용 규칙 (2026-07-31 추가)

기획서 "구인글 상세 페이지-R05" 기준. `PATCH /api/recruit/job-postings/{id}/boost`(작성자 전용):
- 적용 시 `boostedUntil = now + 48h` 갱신, 홈/검색 목록에서 상단고정 정렬(구현은 `boostedUntil` 내림차순 → 일반 정렬 순으로 2단 정렬).
- 재적용 시도 시 `now < boostedUntil`이면 거부(`RecruitErrorCode`에 `BOOST_COOLDOWN` 신규) — 안내 모달 문구는 기획서 그대로("현재 끌어올리기가 적용 중이에요. 48시간 후 재적용할 수 있어요.") 프론트 책임, 서버는 409 계열 에러코드만 반환.
- 결제 게이팅(구매한 끌어올리기 개수 확인)은 Polar 모듈 완성 전까지 생략 — TODO 주석으로 명시하고 지금은 작성자 권한 검증만 수행.

### 2.2 TeamPosting (테이블: `team_postings`)

JobPosting과 유사하되 승인 절차 없음(생성 시 즉시 `PUBLISHED`). **끌어올리기는 JobPosting과 동일하게 지원**(2026-07-31 정정 — laiteu에는 없었으나 기획서 REQ-015·"팀원 모집글 상세 페이지-R03"에 명시된 요구사항).

| 필드 | 타입 | 설명 |
|---|---|---|
| id, authorMemberId, title | 공통 | |
| isBusinessRegistered, isResumeRequired, isCoverLetterRequired | BOOLEAN | |
| authorName, contact, authorDescription | VARCHAR/TEXT | |
| recruitPurposes | JSON | 모집 목적 리스트 (표시 전용) |
| workLocationType | ENUM(`TeamWorkLocationType`) | OFFLINE/ONLINE/HYBRID — **JobPosting과 이름은 같지만 별개 enum** (laiteu 기술부채 §9.1 재발 방지) |
| activityRegion | VARCHAR NULL | workLocationType=ONLINE이면 비활성(NULL 강제, 도메인 불변식) |
| roles, genres | 자식 테이블 | |
| hasParticipationFee, hasProfitSharing | BOOLEAN | 참여비용/수익배분 여부 |
| extraCost | VARCHAR(500) NULL | |
| deadline | DATE NULL | |
| recruitCount | INT | |
| selectionProcess | TEXT | |
| activityDuration | ENUM(TeamActivityDuration) | |
| weeklyActivityTime | ENUM(TeamWeeklyActivityTime) | |
| projectDescription | TEXT | |
| thumbnailImage | VARCHAR(500) NULL | |
| referenceImages | JSON | |
| bookmarkCount, viewCount | BIGINT DEFAULT 0 | |
| boostedUntil | DATETIME(UTC `Instant`) NULL | JobPosting과 동일 규칙(§2.1.1) |
| status | ENUM(TeamPostingStatus) | DRAFT/PUBLISHED/CLOSED/DELETED (PENDING 없음) |
| deletedAt, version, createdAt, updatedAt | 공통 | |

### 2.3 JobSeekingPost (테이블: `job_seeking_posts`)

laiteu에 대응 엔티티 없음 — Figma(4979:871)에서만 확인된 신규 엔티티. 창작자가 "나를 채용하세요" 형태로 올리는 구직글.

| 필드 | 타입 | 설명 |
|---|---|---|
| id, authorMemberId | 공통 | 작성자(창작자) |
| title | VARCHAR(200) | |
| roles, genres | 자식 테이블 | 희망 역할/장르 |
| drawingStyle | VARCHAR(200) | 작화 스타일 |
| preferredFeedbackStyle | ENUM(FeedbackStyle) | 선호 피드백 방식 |
| workStyle | ENUM(WorkStyle) | 작업 스타일 |
| desiredRate | VARCHAR(200) | 희망 단가 (laiteu 대응 없음 — Figma상 자유 텍스트로 확인, 정형화는 후속) |
| portfolioDescription | TEXT | |
| referenceImages | JSON | |
| status | ENUM(JobSeekingPostStatus) | DRAFT/PUBLISHED/CLOSED/DELETED |
| deletedAt, version, createdAt, updatedAt | 공통 | |

### 2.4 Application (테이블: `job_applications`, `team_applications`)

laiteu와 동일하게 지원 대상별로 테이블을 분리한다(구인글 지원과 팀원모집글 지원은 요구 필드가 동일해도 도메인상 별개 — laiteu 구조를 그대로 따름).

**공통 필드**: `id`, `applicantMemberId`, `serialExperience`(ENUM), `assistantExperience`(BOOLEAN), `resumeUrl`(VARCHAR NULL), `appliedAt`(DATETIME).

- `job_applications.job_posting_id` — **laiteu의 `artworkPublicId` 오명명을 정명**. `JobPosting.id` FK(논리적 참조).
- `team_applications.team_posting_id` — laiteu와 동일하게 정상 명명 유지.

**유니크 제약**: `(job_posting_id, applicant_member_id)`, `(team_posting_id, applicant_member_id)` — 중복 지원 방지(laiteu의 `existsByArtworkPublicIdAndApplicantId` 체크를 DB 제약으로 승격, check-then-act 동시성 취약점 회피).

**지원 상태**: laiteu에는 상태 필드가 없었음(접수만 기록). 이번 설계에서도 동일하게 상태 필드 없이 접수만 기록하고, "지원자 관리(채용 단계)"는 §2.5의 별도 상태로 관리한다(laiteu에 없던 신규 기능 — Figma 지원자 관리 UI 근거).

### 2.5 지원자 관리 상태 (신규, laiteu 대응 없음)

Figma 지원자 관리 UI에서 "채용 단계"별 처리가 확인되므로 `ApplicationReviewStatus` 컬럼을 Application 테이블에 추가: `RECEIVED`(접수) / `REVIEWING`(검토중) / `ACCEPTED`(합격) / `REJECTED`(불합격). 기본값 `RECEIVED`. 일괄 처리(bulk update)는 작성자만 가능(§2.6 소유권 검증).

### 2.6 소유권 검증 (laiteu 취약점 재발 방지)

`TeamApplicationService.getApplicationsByTeamPosting`에 해당하는 recruit의 메서드는 **주석 처리 없이 처음부터** `teamPosting.getAuthorMemberId().equals(currentMemberId)` 검증을 포함한다. JobPosting 쪽(정상 구현이었던 laiteu 로직)과 동일한 검증을 두 도메인 모두에 대칭적으로 적용 — "Job은 검증하고 Team은 임시로 뺀다" 같은 비대칭을 두지 않는다.

### 2.7 관심 작가 (신규, 2026-07-31 추가, 기업 전용)

기획서 "구인구직-R05"(작가 관리 탭), "마이페이지_작가-R01"(♡ 저장 버튼) 기준. laiteu 대응 기능 없음(신규).

**테이블 2개**:
- `company_liked_artists` (companyMemberId, artistMemberId, likedAt) — 좋아요 저장/해제. PK는 `(companyMemberId, artistMemberId)` 복합키, 해제는 행 삭제.
- `company_recently_viewed_artists` (companyMemberId, artistMemberId, viewedAt) — 기업 계정이 작가 마이페이지(`MY-P01`)를 조회할 때마다 upsert(동일 쌍 재조회 시 `viewedAt` 갱신). 목록은 `viewedAt` 내림차순.

**API**: `POST/DELETE /api/recruit/liked-artists/{artistMemberId}`(토글), `GET /api/recruit/liked-artists`(검색·정렬), `GET /api/recruit/recently-viewed-artists`(정렬만, 검색 없음 — 기획서 구인구직-R05 기준 "저장된 글 N개"/"검색 결과 N개" 라벨은 좋아요 탭 전용).

**검색·정렬** (2026-08-01 구현 완료, 설계 정정): 애초 계획은 좋아요한 작가 검색을 search 모듈의 작가 검색
인덱스에 위임하는 것이었으나, 구현 단계에서 두 가지 문제가 확인됐다 — (1) search 모듈 Phase 1 색인은
`Artwork`만 대상이라 **작가 검색 인덱스 자체가 존재하지 않음**, (2) 이 시점에 이미 `search → recruit`
의존(포트 연동, 이슈 #36)이 성립해 있어 `recruit → search`를 추가하면 Spring Modulith 순환 의존이 된다.
따라서 search 대신 기존에 허용된 `recruit → member` 경로를 사용해 `MemberService.findIdsByKeyword`
(신규, 후보 memberId 집합을 호출자가 한정 + 탈퇴 회원 제외 + 컨트롤러 미노출)로 좋아요한 작가 중 키워드
매칭 대상을 필터링한다. recruit은 별도 검색 인덱스를 두지 않고 DB LIKE 기반으로 조회하며, Elasticsearch
색인 이관은 별도 스코프로 남겨둔다.

**모듈 경계** (2026-08-01 구현 완료): 최근 본 작가 자동 기록은 이벤트 발행 방식으로 연동했다.
`member.MemberServiceImpl.findProfileByHandle`가 작가 마이페이지 조회 시 `ArtistProfileViewedEvent`를
발행(본인 조회 제외)하고, recruit의 `@ApplicationModuleListener`(`ArtistProfileViewListener`)가 이를
구독해 `LikedArtistService.recordArtistView`를 호출한다. 비로그인 조회(`viewerMemberId == null`)는
리스너에서 무시한다.

---

## 3. Enum 정의 (요약)

모든 enum은 순수 enum(설명 필드 금지) + 한글 인라인 주석 (`feedback_code_standards` 원칙 준수).

```java
public enum JobPostingStatus { DRAFT, PENDING, PUBLISHED, CLOSED, DELETED }
public enum TeamPostingStatus { DRAFT, PUBLISHED, CLOSED, DELETED }
public enum JobSeekingPostStatus { DRAFT, PUBLISHED, CLOSED, DELETED }

public enum JobWorkLocationType { OFFICE, REMOTE, HYBRID }
public enum TeamWorkLocationType { OFFLINE, ONLINE, HYBRID }  // Job과 이름 유사하나 별개 enum

public enum ApplicationReviewStatus { RECEIVED, REVIEWING, ACCEPTED, REJECTED }
```

그 외 `JobEmploymentType`, `JobWorkScheduleType`, `JobPaymentType`, `JobPaymentUnit`, `SerialExperience`, `TeamActivityDuration`, `TeamWeeklyActivityTime`, `FeedbackStyle`, `WorkStyle`은 laiteu/Figma 값 그대로 구현 단계에서 확정(값 목록이 필드 나열 수준이라 이 문서에서는 생략, 구현 시 laiteu enum 값을 1차 참고).

---

## 4. API 설계

artwork/community와 동일하게 커서 페이지네이션(`CursorPage<T>`), OFFSET 금지.

### 4.1 구인글 (JobPosting)

| 메서드 | 경로 | 인증 | 설명 |
|---|---|---|---|
| POST | `/api/recruit/job-postings` | 필요 | 작성 (DRAFT 또는 즉시 PENDING 제출) |
| PUT | `/api/recruit/job-postings/{id}` | 필요(작성자) | 수정 |
| PATCH | `/api/recruit/job-postings/{id}/submit` | 필요(작성자) | DRAFT → PENDING 제출/재제출 |
| PATCH | `/api/recruit/job-postings/{id}/status` | 필요(작성자) | CLOSED 등 상태 변경 |
| DELETE | `/api/recruit/job-postings/{id}` | 필요(작성자) | 휴지통 이동(soft delete) |
| GET | `/api/recruit/job-postings/trash` | 필요(작성자) | 휴지통 목록 |
| PATCH | `/api/recruit/job-postings/{id}/restore` | 필요(작성자) | 복구 |
| GET | `/api/recruit/job-postings/me` | 필요 | 내 목록 |
| GET | `/api/recruit/job-postings/{id}` | 불필요 | 상세(PUBLISHED만 공개 노출) |
| GET | `/api/recruit/job-postings` | 불필요 | 목록(커서) |
| GET | `/api/recruit/job-postings/{id}/applications` | 필요(작성자) | 지원자 목록 — 소유권 검증 |
| PATCH | `/api/recruit/job-postings/{id}/applications/{applicationId}/review-status` | 필요(작성자) | 지원자 상태 변경 |
| POST | `/api/recruit/job-postings/{id}/applications` | 필요 | 지원 |
| PATCH | `/api/recruit/job-postings/{id}/boost` | 필요(작성자) | 끌어올리기 적용(§2.1.1) |
| GET/PATCH | `/api/recruit/admin/job-postings` | 필요(관리자) | PENDING 목록 조회, 승인/반려 — 관리자 권한 체계는 이번 스코프에서 최소 구현(§7 참고) |

### 4.2 팀원모집글 (TeamPosting) / 구직글 (JobSeekingPost)

JobPosting과 대칭 구조, `/api/recruit/team-postings`, `/api/recruit/job-seeking-posts` 하위에 동일 패턴(단 승인 관련 엔드포인트 없음). `PATCH /api/recruit/team-postings/{id}/boost`는 JobPosting과 동일 규칙.

### 4.3 관심 작가 (신규, §2.7)

| 메서드 | 경로 | 인증 | 설명 |
|---|---|---|---|
| POST | `/api/recruit/liked-artists/{artistMemberId}` | 필요(기업) | 좋아요 저장 |
| DELETE | `/api/recruit/liked-artists/{artistMemberId}` | 필요(기업) | 좋아요 해제 |
| GET | `/api/recruit/liked-artists` | 필요(기업) | 좋아요한 작가 목록(검색·정렬) |
| GET | `/api/recruit/recently-viewed-artists` | 필요(기업) | 최근 본 작가 목록(정렬만) |

---

## 5. 응답 포맷

`ApiResponse<T>`, `CursorPage<T>` — 기존 모듈과 동일. 목록 카드 응답은 community 피드용 `CommunityJobPostingCardInfo`/`CommunityTeamRecruitCardInfo`를 그대로 재사용(§6.1).

---

## 6. 모듈 경계

### 6.1 recruit → community (RecruitFeedPort 이관)

기존 `community.internal.application.NoopRecruitFeedPort`와 `com.atcrew.community.RecruitFeedPort` 인터페이스를 **폐기**하고, artwork↔community와 동일하게 recruit이 공개 서비스를 직접 제공한다.

```java
// com.atcrew.recruit.RecruitService (신규 공개 인터페이스)
public interface RecruitService {
    CursorPage<CommunityJobPostingCardInfo> getJobPostingFeed(String cursor, int size);
    CursorPage<CommunityTeamRecruitCardInfo> getTeamRecruitFeed(String cursor, int size);
    // ... 이하 recruit 자체 API용 메서드
}
```

`CommunityJobPostingCardInfo`/`CommunityTeamRecruitCardInfo` record는 `com.atcrew.community` → `com.atcrew.recruit` 공개 패키지로 이관(소유권이 recruit으로 넘어가는 것이 자연스러움 — community는 소비자일 뿐). community 모듈의 `CommunityController`는 `RecruitService`를 직접 주입받아 호출하도록 수정.

### 6.2 recruit → member

```java
MemberInfo author = memberService.getMember(authorMemberId); // 작성자 표시용
```
신규 메서드 불필요 — 기존 `MemberService` 재사용.

### 6.3 recruit → 기업 프로필 모듈 (미구현, 병행 개발 중)

기업 계정/프로필 모듈이 완성되면 "기업 계정만 구인글/팀원모집글 작성 가능" 검증이 필요하다. 현재는 스텁: 로그인한 모든 Member가 작성 가능(§7). 기업 프로필 모듈이 먼저 병합되면 recruit에 다음 인터페이스를 추가해 연동한다(artwork의 성인인증 게이팅과 동일 패턴 — 완성된 쪽이 인터페이스를 소유):

```java
// com.atcrew.company 쪽에서 제공 예정 (아직 없음)
boolean isCompanyAccount(String memberId);
```

---

## 7. 게이팅 스텁 (사용자 결정: 이번 스코프에서 보류)

- **기업 인증 게이팅**: 없음. 로그인한 모든 Member가 JobPosting/TeamPosting 작성 가능. 기업 프로필 모듈 완료 후 §6.3 인터페이스로 연동.
- **성인 인증 게이팅**: recruit 콘텐츠 자체는 성인물 게이팅 대상이 아니므로(Figma 기준 구인/구직 콘텐츠에 연령 제한 없음) 해당 없음.
- **관리자 권한**: `JobPosting` PENDING 승인/반려 엔드포인트는 있으나, 별도 관리자 역할(Role) 체계가 member 모듈에 없으므로 이번 스코프에서는 `@PreAuthorize` 없이 인증만 요구하는 최소 구현으로 두고 TODO 주석으로 명시. 관리자 역할 체계는 로드맵 범위 밖(별도 결정 필요) — 실제 운영 반영 전 필수 후속 작업.
- **이미지 업로드**: `thumbnailImage`/`referenceImages`는 URL 문자열 저장만 지원(직접 업로드 API 없음).
  **2026-08-03 정정**: 애초 "artwork 모듈의 Presigned URL + Worker 파이프라인 재사용"으로 적었으나, artwork의
  Worker/webhook/재시도 구현은 `internal` 캡슐화라 재사용 불가하고 도메인 로직과도 강결합돼 있음을 확인.
  대신 그 인프라를 범용 `media` 모듈로 추출해 artwork·recruit이 함께 소비하는 구조로 재설계함
  (`docs/design/media-module-design.md` §10에 recruit 적용 계획 — 신규 자식 테이블 3개, `imageProcessingStatus`
  필드, `MediaAssetProcessedEvent` 리스너). 여전히 후속 스코프.

---

## 8. 인덱스 설계

```sql
-- job_postings
CREATE INDEX idx_job_postings_feed ON job_postings (status, created_at DESC);       -- 공개 목록(커서)
CREATE INDEX idx_job_postings_author ON job_postings (author_member_id, status);     -- 내 목록
CREATE INDEX idx_job_postings_admin_pending ON job_postings (status, created_at)     -- 관리자 PENDING 목록
    WHERE status = 'PENDING';  -- MariaDB는 partial index 미지원 → 일반 복합 인덱스로 대체, status 선두 컬럼으로 선택도 확보

-- team_postings / job_seeking_posts: job_postings와 동일 패턴

-- job_applications / team_applications
CREATE UNIQUE INDEX uk_job_applications_posting_applicant ON job_applications (job_posting_id, applicant_member_id);
CREATE INDEX idx_job_applications_posting ON job_applications (job_posting_id, applied_at DESC);  -- 지원자 목록(작성자용)
-- team_applications 동일 패턴(team_posting_id 기준)
```

laiteu의 커뮤니티 쿼리 성능 이슈(p95 7.3s, `count()+find()` 이중 쿼리)를 반복하지 않도록 처음부터 커서 페이지네이션 전용으로 설계(`community-module-design.md` §9-7 교훈 적용).

---

## 9. 범위 밖 (Phase 2 이후)

| 항목 | 사유 |
|---|---|
| ~~부스트(48시간 끌어올리기)~~ | **2026-07-31 스코프 편입 — §2.1.1·§4.1·§4.2 참고.** 기획서 대조 결과 P0 요구사항으로 확인되어 제외 결정 철회 |
| ~~관심 작가(좋아요/최근 본)~~ | **2026-07-31 스코프 편입 — §2.7·§4.3 참고.** 기획서 REQ-017에 P0로 명시 확인되어 제외 결정 철회 |
| 인재 관리 중 테스트 지시서 일괄 요청 | 기획서에서도 확인되지 않는 항목 — 범위 밖 유지 |
| 끌어올리기 결제 게이팅(구매 개수 차감) | Polar 모듈(로드맵 5번) 완성 후 연동. 지금은 무료로 적용 가능(§2.1.1) |
| 신고/차단 | laiteu에는 있으나 기획서도 "초기 버전에는 신고 접수 UI 제외"로 명시(정책 홈-R15) — 상태 관리·자동 전환은 로드맵 8번(관리자/모더레이션) 스코프 |
| 기업 인증 게이팅, 관리자 권한 체계 | §7 스텁 상태 유지, 로드맵 1번(인증)·8번(관리자) 완료 후 연동 |
| 검색 고도화(태그 인기순 등) | 로드맵 4번 검색 모듈 스코프 |

---

## 10. laiteu 대비 차이점 요약

| # | 항목 | laiteu | 앳크루 |
|---|---|---|---|
| 1 | Application 필드명 | `JobApplication.artworkPublicId`(오명명, 실제로는 jobPostingId) | `job_applications.job_posting_id`로 정명 |
| 2 | TeamPosting 지원자 조회 소유권 검증 | 주석 처리로 비활성(취약점) | 처음부터 활성화, Job과 대칭 |
| 3 | 중복 지원 방지 | 애플리케이션 레벨 `existsBy...` 체크(check-then-act) | DB unique 제약으로 승격 |
| 4 | WorkLocationType | Job/Team 동일 이름, 다른 값(혼동 위험) | `JobWorkLocationType`/`TeamWorkLocationType` 분리 |
| 5 | 지원자 채용 단계 관리 | 상태 필드 없음(접수만 기록) | `ApplicationReviewStatus` 신규 추가(Figma 근거) |
| 6 | 부스트 | 미구현(문서 근거로만 존재, 실제 코드 없음) | 이번 스코프 제외 |
