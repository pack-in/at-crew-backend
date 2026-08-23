# community 모듈 설계

> 작성일: 2026-07-15
> 상태: 설계안 (구현 전)
> 범위: **커뮤니티 피드만** — 작품·구인글·팀원모집글 탭 노출, 배너, 작가 찾아보기, 성인물 게이팅, 필터
> 범위 밖(추후 별도 설계): 구인글/팀원모집글/구직글 CRUD, 지원(Application) 플로우, 지원자 관리, 끌어올리기(부스트), 신고/차단 — `recruit` 모듈로 분리 예정
> 피그마 근거: UI개편_커뮤니티(4856:14126) 중 `구인글 및 팀원모집글 카드`(5222:15649)·`성인물 로직`(5359:78798)·`작가 찾아보기 화면`(6107:24822), UI개편_구인구직(5154:41764)

---

## 0. 설계 요약 (TL;DR)

| 항목 | 결정 |
|------|------|
| 모듈 경계 | 신규 `community` 모듈 — 자체 데이터는 배너뿐, 나머지는 다른 모듈 조회 결과의 **투영(projection)** |
| artwork 탭 | `ArtworkService.getCommunityArtworks(...)` 그대로 재사용. `CommunityController`를 artwork 모듈 밖으로 이전 |
| 구인글/팀원모집글 탭 | **설계만 완료, 구현 보류** — `recruit` 모듈이 아직 없어 실제 데이터 소스 없음. `RecruitFeedPort` 인터페이스만 정의해두고 recruit 모듈 완성 후 구현체 연결 |
| 작가 찾아보기 탭 | `MemberService`에 검색 메서드 신규 추가 필요 (`searchProfiles(...)`) — 현재 `findByHandle`/`findById`만 존재 |
| 배너 | community 모듈이 직접 소유하는 유일한 엔티티. `memberId`로 member 모듈 참조 (직접 컬렉션 접근 금지) |
| 성인물 게이팅 | `MemberService`에 성인 인증 상태 필드 필요 (미구현 — artwork 모듈 문서에도 동일하게 기록된 기존 갭) |
| 탭 간 관계 | **단일 통합 피드가 아님** — 포트폴리오/작가 프로필/구인글/팀원모집글 4개 탭은 각각 독립 리스트. 카드 UI 규격(294×448)만 공유 |
| DB | MongoDB (기존 스택 통일), community 모듈은 `banners` 컬렉션만 신규 생성 |

---

## 1. 모듈 분리 결정 근거

### 1.1 왜 community를 별도 모듈로 분리하는가

현재 `/api/community/artworks`는 artwork 모듈 내부(`artwork.internal.web.CommunityController`)에 있다. 하지만 피그마 UI개편_커뮤니티 페이지는 작품 피드가 아니라 **포트폴리오 / 작가 프로필 / 구인글 / 팀원모집글 4개 탭 + 배너**로 구성된 진입 화면이다. artwork 모듈이 job/team/member 데이터까지 조회하게 되면 "artwork가 다른 도메인 모듈에 의존"하는 역방향 의존이 생겨 모듈 경계가 무너진다.

따라서 community는 여러 도메인의 조회 결과를 **조합만 하는 파사드(facade) 모듈**로 분리한다. community는 각 도메인 모듈의 공개 인터페이스(`ArtworkService`, `MemberService`, 추후 `RecruitService`)만 의존하고, 자체 도메인 엔티티는 배너 하나뿐이다.

### 1.2 탭은 "통합 피드"가 아니라 "독립 리스트 모음"

피그마 원문: *"커뮤니티 탭 구조: 포트폴리오 / 작가 프로필 / 구인글 / 팀원 모집글"* — 4개 탭은 전환식이며, 작품·구인글·팀원모집글이 하나의 정렬된 목록에 섞여 나오지 않는다. 카드 크기(294×420~448px)만 공유할 뿐 각 탭은 독립된 목록 API를 호출한다.

이 구조 덕분에 community 모듈은 "여러 소스를 병합 정렬하는 복잡한 aggregation"이 아니라, **탭별로 다른 모듈에 위임하는 라우터**로 단순하게 설계할 수 있다.

### 1.3 recruit 모듈이 없는 상태에서 구인글/팀원모집글 탭을 어떻게 다루는가

이번 스코프는 "피드만"이지만, 구인글/팀원모집글 탭이 보여줄 실제 데이터(JobPosting, TeamRecruitPost)는 아직 at-crew-backend에 존재하지 않는다. 두 가지 선택지가 있었다:

- (A) recruit 모듈까지 함께 설계·구현
- (B) community가 의존할 **포트 인터페이스와 응답 모델만 확정**하고, recruit 모듈이 생기면 그 구현체를 주입

사용자가 스코프를 "피드만"으로 한정했으므로 **(B)**를 택한다. `RecruitFeedPort`(§7.3)를 community 모듈에 정의하고, recruit 모듈이 아직 없는 동안 해당 탭 엔드포인트는 `501 Not Implemented` 또는 빈 목록을 반환하는 스텁으로 둔다.

---

## 2. 도메인 모델

### 2.1 Banner (컬렉션: `banners`) — community 모듈이 직접 소유하는 유일한 엔티티

```java
@Document(collection = "banners")
public class Banner {

    @Id
    private String id;

    private String memberId;      // 배너 등록 대상 작가/기업 (member 모듈 참조, 직접 조인 금지)
    private String imageUrl;      // R2 이미지 key 또는 URL
    private String linkUrl;       // 클릭 시 이동 경로
    private int sortOrder;        // 낮을수록 먼저 노출
    private BannerStatus status;  // ACTIVE / INACTIVE / DELETED

    @CreatedDate
    private Instant createdAt;
    @LastModifiedDate
    private Instant updatedAt;
}
```

라이트(laiteu) `Banner` 엔티티와 필드 구조가 거의 동일하다 (§9.2 참고). `folioId` → `memberId`로 명칭만 앳크루 컨벤션에 맞춤.

```java
public enum BannerStatus { ACTIVE, INACTIVE, DELETED }
```

**정렬 규칙**: `sortOrder` 오름차순. 생성 시 미지정하면 마지막 순번 자동 부여, 특정 순번 지정 시 이후 배너들 순번 +1 밀림 (라이트 동일 정책 유지).

### 2.2 CommunityArtworkCard — artwork 모듈 투영 (신규 엔티티 아님)

기존 `ArtworkSummaryInfo`(artwork 모듈 공개 레코드)를 그대로 사용한다. 별도 DTO를 만들지 않고 community 컨트롤러가 `ArtworkService.getCommunityArtworks(...)` 응답을 그대로 반환한다.

### 2.3 CommunityJobPostingCardInfo / CommunityTeamRecruitCardInfo — 설계만, 미구현

`RecruitFeedPort`가 반환할 카드 응답 모델. 피그마 `구인글 및 팀원모집글 카드`(5222:15649) 노출 규칙을 그대로 반영한다.

```java
public record CommunityJobPostingCardInfo(
        String id,
        String publicId,
        String thumbnailUrl,
        String title,           // "구인글 제목 · 기업명" 한 줄 결합 표시는 클라이언트 책임
        String companyName,
        String authorName,
        LocalDate deadline,     // null이면 상시모집
        boolean closed,         // true면 카드에 "마감" 텍스트로 대체 표시
        AgeRating ageRating
) {}

public record CommunityTeamRecruitCardInfo(
        String id,
        String publicId,
        String thumbnailUrl,
        String title,
        String authorName,
        LocalDate deadline,
        boolean closed,
        AgeRating ageRating
) {}
```

두 레코드가 필드상 거의 동일하지만 통합하지 않는다 — §1.3에서 정한 대로 recruit 모듈이 구인글/팀원모집글을 별도 엔티티로 설계할 예정이며(§9 laiteu 차이점 참고), 카드 응답도 그 경계를 따른다.

---

## 3. Enum 정의

```
BannerStatus        : ACTIVE / INACTIVE / DELETED

CommunityTab        : PORTFOLIO(포트폴리오) / AUTHOR(작가 프로필) /
                       JOB_POSTING(구인글) / TEAM_RECRUIT(팀원모집글)
                       — API 파라미터가 아니라 프론트 라우팅용 개념. 서버는 탭별 API가 분리되어 있어 별도 enum 파라미터 불필요.

AdultGateResult      : VERIFIED(인증완료, 열람가능) /
                       VERIFICATION_REQUIRED(성인이지만 미인증 → 본인인증 유도) /
                       BLOCKED(19세 미만 → 하드 차단)
```

---

## 4. 피그마 비즈니스 규칙

### 4.1 커뮤니티 작품/구인글/팀원모집글 카드 공통 규칙

| 규칙 | 내용 |
|------|------|
| 카드 크기 | 294×420~448px, 작품 카드 규칙과 동일 |
| 구인글/팀원모집글 카드 표시 정보 | 썸네일, "구인글 제목 · 기업명"(한 줄 결합, bold, 말줄임), 작성자 이름, `D-NN` 또는 `마감` |
| 작품 카드와의 차이 | "#태그 대신 구인글 / 팀원 모집글 게시글 유형을 노출" — 태그 목록 자리에 게시글 유형 표시 |
| 마감 표시 | `D-NN` → 마감 시 `마감` 텍스트로 교체, 색상 dark-gray(#919196) |
| Hover | 반투명 dim(rgba(26,26,26,0.5)) 오버레이 |
| 탭 전환 시 필터 초기화 | 구인글/팀원모집글 탭 선택 시 "작품 분야" 필터 기본값이 "전체"로 리셋 |

### 4.2 성인물 게이팅 (5359:78798)

3단계 사용자 상태에 따라 분기한다.

| 사용자 상태 | 동작 |
|---|---|
| 성인 인증 완료 | 정상 열람 |
| 성인이지만 미인증 | 모달: "해당 콘텐츠는 성인만 열람할 수 있어요. 확인을 누르면 본인 인증 페이지로 이동해요." → 확인 시 본인인증 페이지로 리다이렉트 |
| 19세 미만 | 모달: "19세 이하 사용자의 경우 성인물 열람이 불가해요." → 확인해도 접근 불가 (하드 블록) |

목록 자체(카드)는 그대로 렌더링되고, 상세 진입 시점에 모달로 차단하는 방식으로 추정된다 (배경 블러 + 모달 오버레이 구조).

**서버 구현 시 필요 조건**: `MemberService`에 연령/성인인증 상태를 판별할 수 있는 필드가 있어야 한다. 현재 미구현 — artwork 모듈 설계 문서(§10.5)에도 동일하게 "성인물 인증 연동: member 모듈에 `adultVerificationStatus` 필드 추가 + PASS/KCB 외부 API 연동 선행 필요"로 기록된 **공유 블로커**다. community 모듈의 게이팅 로직도 이 필드가 추가된 뒤에만 구현 가능하다.

### 4.3 작가 찾아보기 (6107:24826)

- 상단 탭이 `포트폴리오 | 작가 프로필 | 구인글 | 팀원 모집글`로 구성되어 있어, "작가 프로필" 자체가 커뮤니티 화면의 한 탭이다.
- 카드 필드: 사용자 이름, 상태 배지(`신규 작업 가능`/`협의 가능`), 작품 분야, 경력, 담당 업무 태그, 장르 태그, 근무 형태, 업로드된 작품 개수, 이메일/전화번호
- 필터: 전체/웹툰/일러스트/애니메이션/출판만화
- 정렬: 최신 업데이트순 / 조회순 / **경력순**
- 피그마 원문: *"구직글의 경우 신규 작업 가능/협의 가능, 즉 구인 가능한 상태의 창작자/개인 계정만 게시된다. (...) 커뮤니티에 노출되는 카드의 경우 checkbox 가 없다."* → community 탭에서는 `employmentStatus`가 `신규 작업 가능` 또는 `협의 가능`인 회원만 노출.
- **노출 조건 추가(2026-08-23)**: 구인 가능 상태여도 노출 대상 항목이 비어 있으면 목록에서 제외한다(기획서 마이페이지_작가-R08). 정본이 정한 7개 항목 중 구현 가능한 4개(사용자 이름·활동 분야·활동 경력·연락처)만 적용했고, 희망 담당 업무·희망 장르·희망 채용 형태는 "구직 정보" 탭(마이페이지_작가-R24) 미구현이라 도메인에 필드가 없다.
- **필터 카디널리티**: 칩 필터는 단일 선택이다("전체" 칩 포함, 기획서 홈-R01·홈-R13). 프로필의 활동 분야는 복수 선택이지만 단일값 필터와 충돌하지 않는다 — `member_activity_fields`에 해당 값이 있으면 매칭된다.

이 탭은 `MemberProfileInfo`(§7.2) 필드로 대부분 커버되지만, **검색/필터/정렬 메서드가 `MemberService`에 없다** — 신규 추가 필요.

---

## 5. API 설계

### 5.1 배너

| 메서드 | 경로 | 인증 | 설명 |
|--------|------|------|------|
| GET | `/api/community/banners` | 불필요 | 활성 배너 목록 (`sortOrder` ASC) |
| POST | `/api/community/banners` | 관리자 | 배너 등록 |
| PATCH | `/api/community/banners/{bannerId}` | 관리자 | 배너 수정/순서 변경 |
| DELETE | `/api/community/banners/{bannerId}` | 관리자 | 배너 삭제 (soft delete) |

```
GET /api/community/banners
응답 200: [{ "id", "memberId", "imageUrl", "linkUrl", "sortOrder" }, ...]
```

### 5.2 포트폴리오 탭 (기존 artwork 커뮤니티 피드 이전)

| 메서드 | 경로 | 인증 | 설명 |
|--------|------|------|------|
| GET | `/api/community/artworks` | 불필요 | 기존 `artwork.internal.web.CommunityController`와 동일 — 위치만 이전 |

내부 구현은 변경 없음. `community` 모듈의 컨트롤러가 `ArtworkService.getCommunityArtworks(artworkField, ageRating, cursor, size)`를 그대로 호출한다.

### 5.3 작가 프로필 탭

| 메서드 | 경로 | 인증 | 설명 |
|--------|------|------|------|
| GET | `/api/community/authors` | 불필요 | 구인 가능 상태(`신규 작업 가능`/`협의 가능`) 창작자 목록 |

```
GET /api/community/authors
  ?activityField=WEBTOON        선택
  ?sort=recentlyUpdated|viewCount|experience   기본 recentlyUpdated
  ?cursor=...
  ?size=20
```

**의존성**: `MemberService.searchProfiles(SearchProfilesCommand)` 신규 메서드 필요 — `employmentStatus IN (신규 작업 가능, 협의 가능)` 조건, 노출 조건(§4.3), `activityField` 필터, 3종 정렬을 지원해야 한다. `조회수`(viewCount)는 현재 `MemberProfileInfo`에 없는 필드이므로 member 모듈에 함께 추가 필요.

### 5.4 구인글 / 팀원모집글 탭 — 설계만, 구현 보류

| 메서드 | 경로 | 인증 | 설명 | 상태 |
|--------|------|------|------|------|
| GET | `/api/community/job-postings` | 불필요 | 구인글 카드 목록 | recruit 모듈 완성 후 구현 |
| GET | `/api/community/team-recruits` | 불필요 | 팀원모집글 카드 목록 | recruit 모듈 완성 후 구현 |

```
GET /api/community/job-postings
  ?cursor=...
  &size=20

recruit 모듈 미구현 상태에서는 빈 CursorPage({"items":[], "nextCursor":null, "hasNext":false}) 반환.
```

---

## 6. 응답 포맷 통일

모든 목록 API는 artwork 모듈과 동일하게 커서 페이지네이션(`CursorPage<T>`)을 사용한다 (OFFSET 방식 금지). 커서 형식·`hasNext` 판정 로직은 artwork-module-summary.md §5와 동일한 규칙을 따른다.

---

## 7. 모듈 경계

### 7.1 community → artwork

```java
// 기존 ArtworkService 인터페이스 재사용, 신규 메서드 없음
CursorPage<ArtworkSummaryInfo> feed =
    artworkService.getCommunityArtworks(artworkField, ageRating, cursor, size);
```

### 7.2 community → member

```java
// 신규 메서드 필요 (MemberService에 추가)
CursorPage<MemberProfileInfo> authors =
    memberService.searchProfiles(new SearchProfilesCommand(
        List.of(EmploymentStatus.NEW_AVAILABLE, EmploymentStatus.NEGOTIABLE),
        activityField, sort, cursor, size
    ));
```

`MemberProfileInfo`에 `profileViewCount` 필드 추가가 함께 필요하다 (정렬 기준 "조회순" 지원용).

### 7.3 community → recruit (미구현, 인터페이스만 정의)

```java
// recruit 모듈이 아직 없으므로 community 모듈 내부에 임시로 인터페이스만 선언.
// recruit 모듈 구현 후 해당 모듈이 이 인터페이스의 구현체를 제공(또는 인터페이스 자체를 recruit로 이관).
public interface RecruitFeedPort {
    CursorPage<CommunityJobPostingCardInfo> getJobPostingFeed(String cursor, int size);
    CursorPage<CommunityTeamRecruitCardInfo> getTeamRecruitFeed(String cursor, int size);
}

// 스텁 구현 (recruit 모듈 완성 전까지)
@Component
class NoopRecruitFeedPort implements RecruitFeedPort {
    public CursorPage<CommunityJobPostingCardInfo> getJobPostingFeed(String cursor, int size) {
        return CursorPage.empty();
    }
    public CursorPage<CommunityTeamRecruitCardInfo> getTeamRecruitFeed(String cursor, int size) {
        return CursorPage.empty();
    }
}
```

### 7.4 성인물 게이팅 의존성 (미구현 공유 블로커)

```java
// MemberService에 추가 필요 (member 모듈)
AdultVerificationStatus getAdultVerificationStatus(String memberId);
// NOT_VERIFIED / VERIFIED / UNDER_19
```

이 메서드가 생기기 전까지 community의 성인물 게이팅(§4.2)은 클라이언트가 `ageRating` 필터만으로 임시 처리하고, 서버 사이드 하드 블록은 스킵한다 (artwork 모듈의 기존 방식과 동일한 임시 상태).

---

## 8. 인덱스 설계

```javascript
// banners 컬렉션
{ status: 1, sortOrder: 1 }   // 활성 배너 목록 조회

// artworks, member(profile) 관련 인덱스는 각 소유 모듈 문서 참고 — community는 신규 인덱스 불필요
```

---

## 9. 라이트(laiteu) 대비 차이점

laiteu-be 코드 조사 결과와 비교했을 때 확인된 주요 차이점.

| # | 항목 | laiteu | 앳크루 신규 설계 | 비고 |
|---|------|--------|-----------------|------|
| 1 | 커뮤니티 범위 | `artworks` 컬렉션만 조회하는 순수 작품 피드. 구인글(`/v*/postings`)·팀원모집글(`/v*/team/postings`)은 완전히 분리된 도메인으로, 커뮤니티 API에 전혀 노출되지 않음 | 피그마 기준 커뮤니티 화면에 포트폴리오/작가 프로필/구인글/팀원모집글 4개 탭이 통합됨 (UI 레벨 통합, API는 탭별 분리 유지) | **가장 큰 구조 변경.** laiteu에서 별개였던 화면이 앳크루에서는 하나의 진입점으로 묶임 |
| 2 | 작가 찾아보기 | laiteu 조사에서 커뮤니티 도메인 내 창작자 검색/브라우징 기능 미발견 | 신규 탭. `employmentStatus`가 구인 가능 상태인 창작자만 노출, 경력순 정렬 등 laiteu에 없던 기능 | member 모듈에 검색 API 신규 추가 필요 |
| 3 | 배너 | 독립 도메인(`/v*/banners`), `folioId` 기준, `order` 필드로 정렬, ACTIVE/INACTIVE/DELETED 상태 | 구조 거의 동일하게 재사용(`memberId`, `sortOrder`, `BannerStatus`) — 이 부분은 laiteu 설계를 그대로 이식 | 리라이트 폭이 가장 작은 영역 |
| 4 | 성인물 게이팅 | `ages` enum(ALL/ADULT/R_18/R_18G) 필터 쿼리 파라미터만 확인됨. 실제 열람 차단 모달·본인인증 유도 로직은 laiteu 코드 조사에서 확인되지 않음 (블러 처리는 클라이언트 단으로 추정) | 피그마에 인증완료/미인증(유도)/19세미만(하드블록) 3단계 게이팅 로직이 명시적으로 정의됨 | 앳크루가 laiteu보다 **서버 사이드 강제력이 강한** 정책으로 설계됨. 단, member 모듈의 성인인증 필드가 아직 없어 즉시 구현은 불가 (§4.2, §7.4) |
| 5 | 필터/정렬 체계 | role 다중선택(최대 3개), genre/role/materialTarget/age 조합 필터, 인기 역할(popular roles) API, 정렬 4종(publishDate/publishDateAsc/viewCount/favoritesCount) | 현재 artwork 모듈은 `artworkField` 단일값 + `ageRating` 단일값만 지원 — laiteu 대비 **필터가 단순화**되어 있음. 인기 태그 API도 미구현 | 커뮤니티 피드 자체의 필터 고도화는 이번 스코프 밖이지만, laiteu 수준으로 복원하려면 artwork 모듈에 추가 작업 필요 |
| 6 | 카드 노출 정보 | (해당 없음 — 구인글이 커뮤니티에 없으므로 카드 규격도 없음) | 구인글/팀원모집글 카드는 "#태그 대신 게시글 유형" 노출 등 작품 카드와 다른 표시 규칙을 가짐 | recruit 모듈 설계 시 카드 응답 스펙(§2.3)을 그대로 따라야 함 |
| 7 | 쿼리 성능 | `count()+find()` 이중 쿼리로 p95 7.3s까지 저하된 이력 존재 (`laiteu-be/docs/performance/community-query-analysis.md`). 전용 인덱스 3개 + MongoDB aggregation으로 해결 | artwork 모듈이 처음부터 커서 페이지네이션 + 복합 인덱스(`idx_artwork_community_feed` 등)를 적용해 동일 문제를 구조적으로 회피 | 라이트가 겪은 실패를 앳크루는 설계 단계에서 이미 반영함 — 회귀 위험 낮음 |
| 8 | 신고/차단 | `Report` 도메인 존재 (`reporterId`+`artworkId` unique, PENDING/COMPLETED/REJECTED 상태, 관리자 처리 플로우 포함) | 피그마 커뮤니티 화면 조사 범위에서 신고 관련 UI 미확인. 앳크루에 신고 기능 자체가 아직 없음 | 커뮤니티 스코프에 포함할지는 별도 확인 필요 — 이번 설계에는 포함하지 않음 |
| 9 | 댓글/좋아요 | laiteu 코드베이스에 댓글/좋아요 기능 자체가 없음 (북마크만 존재) | 이번 피그마 조사 범위에서도 댓글/좋아요 UI 미확인 | 라이트-앳크루 양쪽 다 없음 → 이번 설계 범위에 포함하지 않음 |

### 9.1 마이그레이션 관점 메모

- 배너(§9-3)는 필드 매핑이 단순(`folioId`→`memberId`)하므로 데이터 마이그레이션 난이도가 낮다.
- 구인글/팀원모집글은 laiteu에 이미 풍부한 데이터 모델(급여 체계, 이력서 정책 등)이 존재하므로, recruit 모듈 설계 시 laiteu의 `JobPosting`/`TeamPosting` 필드를 참고 우선순위로 삼을 것 (라이트 호환 데이터 모델 유지 원칙, `CLAUDE.md` 마이그레이션 제약 참고).
- laiteu의 `WorkLocationType` enum이 Job(OFFICE/REMOTE/HYBRID)과 Team(OFFLINE/ONLINE/HYBRID)에서 **동일한 이름, 다른 값**으로 중복 정의되어 있음 — recruit 모듈 설계 시 이름을 분리(`JobWorkLocationType`/`TeamWorkLocationType`)해야 함.

---

## 10. 범위 밖 — recruit 모듈에서 별도 설계할 항목

| 항목 | laiteu 참고 근거 |
|------|-----------------|
| 구인글(JobPosting) CRUD, 임시저장, 승인(PENDING) 플로우 | `domain/job/*` |
| 팀원모집글(TeamRecruitPost) CRUD | `domain/team/*` — 승인 절차 없이 즉시 게시되는 점이 구인글과 다름 |
| 구직글(JobSeekingPost) — 피그마에서 신규 확인된 엔티티, laiteu에는 대응 없음 | 업로드 플로우(4979:871) 참고 |
| 지원(Application) 플로우, 이력서 필수/선택 분기 | `domain/application/*`, `domain/team/*Application*` |
| 지원자 관리(채용 단계, 일괄 처리) | 구인글/팀원모집글 상세페이지 지원자 관리 UI |
| 끌어올리기(부스트) — 구인글 전용, 팀원모집글에는 없음 | 피그마 구인글세부페이지 |
| 인재 관리(기업 전용) — 관심 작가, 최근 본 작가, 테스트 지시서 일괄 요청 | 피그마 구인구직 리스트(5154:41764) |
| 신고/차단 | `domain/report/*` |
