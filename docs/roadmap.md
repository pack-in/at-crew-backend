# 모듈 개발 로드맵

> 작성일: 2026-07-30
> 상태: 우선순위 확정, 착수 전
> Figma UI 화면 목록(`docs/design/figma.md`)과 구현 완료 모듈(`auth`/`member`/`artwork`/`community`)을 대조해
> 미구현 영역을 정리하고 우선순위를 확정한 문서.

---

## 확정된 착수 순서

| 순서 | 항목 | 상태 |
|---|---|---|
| 0 | MongoDB → MariaDB 마이그레이션 | 설계 완료, 구현 전 — `docs/design/mariadb-migration-design.md` |
| 1 | 본인/기업 인증(verification) 시스템 | 착수 전 |
| 2 | recruit 모듈 | 착수 전 |
| 3 | 기업 계정/프로필 모듈 | 착수 전 |
| 4 | 검색 모듈 | 착수 전 |
| 5 | 결제/구독(요금제) — 설정 | 착수 전 |
| 6 | 설정 나머지(로그아웃/비밀번호 변경 등) | 착수 전 |

사용자 결정 이력:
1. 인증 시스템을 먼저 제대로 만들고(원래 4번 항목을 1순위로), 그다음 원래 1순위였던 recruit부터 순서대로 진행.
2. **MariaDB 마이그레이션을 그보다 먼저 끝내고 나서 착수** — 이후 신규 모듈(인증/recruit/기업 프로필/검색/결제)은 전부 MariaDB(JPA) 기준으로 설계·구현한다. MongoDB로 먼저 만들고 나중에 이관하는 경로는 채택하지 않음.

**Why:** 4개 기존 모듈(auth/member/artwork/community)에 신규 모듈까지 얹은 뒤 마이그레이션하면 이관 대상이
더 커지고, 신규 모듈을 Mongo 기준으로 설계했다가 다시 JPA로 다시 짜는 이중 작업이 발생함. 지금 마이그레이션을
끝내두면 이후 모든 신규 모듈은 처음부터 MariaDB/JPA로 한 번에 설계 가능.

---

## 0. MongoDB → MariaDB 마이그레이션

설계는 이미 완료된 상태(`docs/design/mariadb-migration-design.md`, 상태: 설계안 → 구현 착수).
ID 전략(String 유지, VARCHAR(36), 신규 레코드는 UUIDv7), 스키마 정규화, 원자 연산 재설계, Flyway,
Spring Modulith JDBC 이벤트 레지스트리 교체, ETL·컷오버 계획까지 포함. API 계약(요청/응답 DTO)은
변경하지 않는 것이 원칙.

**이 로드맵과의 관계:** 1~6번 항목(신규 모듈)은 전부 이 마이그레이션 완료 이후 착수한다. 마이그레이션
자체는 기존 4개 모듈(auth/member/artwork/community)의 영속성 계층 전환이 스코프이며 신규 기능 추가는 아님.

---

## 1. 본인/기업 인증(verification) 시스템

**Why 최우선으로 승격했는가:** artwork(성인물 게이팅) → community(성인물 게이팅) → recruit(업로드 게이팅), 기업 프로필(구인글 업로드 게이팅) 순으로 **같은 블로커에 세 번째** 부딪힘. 지금까지는 매번 "인증 필드 없음 → 클라이언트 필터로 임시 처리"로 미뤄왔는데(artwork/community 모듈 회고 참고), 이번엔 제대로 설계하기로 결정.

**필요한 것:**
- `Member`에 인증 상태 필드 추가 (성인 인증 여부, 창작자 본인 인증 여부, 기업 인증 여부 — 별개 개념으로 분리 필요할 수 있음)
- 인증 플로우 자체(신분증/사업자등록증 진위 확인 등 외부 서비스 연동 여부는 별도 설계 필요)
- 기존에 미뤄뒀던 게이팅 로직 소급 적용 여부 결정: artwork/community의 성인물 게이팅을 이 시점에 함께 구현할지, 아니면 필드만 추가하고 게이팅은 각 모듈 후속 작업으로 남길지

**Figma 근거:**
- `UI개편_작품/팀원모집글/구인글/구직글 업로드` (node 4979:871) — "팀원 모집글 업로드 로직_본인 인증 전/후" 섹션
- `UI개편_마이페이지_기업` (node 5154:41398) — "구인글 업로드 모달_기업 인증 전", "기업 인증 후_요금제 안내 로직" 섹션
- `UI개편_커뮤니티` (node 4856:14126) — 성인 콘텐츠 접근 제한

---

## 2. recruit 모듈

**Why 다음 순서인가:** community 모듈 구현 시점에 이미 laiteu 필드 조사가 끝나 있어(`docs/design/community-module-design.md` §9, [[project_community_module]] 메모) 설계 착수 난이도가 낮음. community의 `RecruitFeedPort`/`NoopRecruitFeedPort` 스텁을 실제 구현으로 대체하는 자연스러운 다음 단계.

**스코프:**
- 구인글(JobPosting)/팀원모집글(TeamPosting)/구직글(JobSeekingPost) CRUD
- 지원(Application) 플로우, 지원자 관리
- 마감된 공고 정책
- "작가 관리" 검색 필터링

**laiteu 대비 참고사항 (이미 조사 완료, [[project_community_module]] 메모에 상세):**
- `JobPosting`: 회사정보/급여체계(연봉제·MG+RS)/이력서 필수여부/부스트(48시간)/PENDING 승인 절차
- `TeamPosting`: 승인 절차 없음(즉시 게시), 참여비용/수익배분, 부스트 없음
- `JobSeekingPost`: laiteu에 대응 없는 신규 엔티티(Figma에서만 확인) — 작화스타일/선호피드백방식/작업스타일/희망단가
- 기술부채 주의: laiteu `JobApplication.artworkPublicId`가 실제로는 jobPostingPublicId를 가리키는 오명명 필드, TeamPosting 지원자 조회에 소유권 검증 누락 이력 있음 — 앳크루에서는 처음부터 바르게 설계할 것
- `WorkLocationType` enum이 JobPosting/TeamPosting에서 이름 같고 값 다르게 중복 정의돼 있었음 → 앳크루에서는 `JobWorkLocationType`/`TeamWorkLocationType`으로 이름 분리 필요
- laiteu는 커뮤니티 쿼리 성능 이슈(p95 7.3s) 이력 있음 — artwork 모듈처럼 처음부터 커서 페이지네이션+복합 인덱스로 설계

**Figma 근거:** `UI개편_구인구직(창작자/기업 둘 다)` (node 5154:41764), `UI개편_구인글세부페이지` (5154:41397), `UI개편_팀원모집글세부페이지` (5154:41765)

---

## 3. 기업 계정/프로필 모듈

**Why:** recruit과 강하게 결합 — 구인글을 게시하는 주체(기업 계정)가 없으면 recruit이 반쪽짜리가 됨. recruit과 동시 진행 또는 직후 권장.

**스코프:**
- 기업 마이페이지 (Company Info Section, Career Section — 작가용과 달리 "담당 업무" 필드 없음, "삭제하기" 버튼 없음)
- 접근 권한: 비로그인/타 사용자도 열람 가능, 본인 기업 계정으로 볼 때만 수정/업로드/관리 액션 노출
- 구인글 업로드 카드 진입점

**Figma 근거:** `UI개편_마이페이지_기업` (5154:41398), `UI개편_마이페이지_기업_수정페이지` (5244:25813)

---

## 4. 검색 모듈

**Why:** 스코프가 작고 다른 모듈과 결합도가 낮아 병행 처리하기 좋음.

**스코프:** 태그 기반 검색, 검색창, 결과 없음 상태.

**Figma 근거:** `UI개편_검색` (5154:41768)

---

## 5. 결제/구독(요금제) — 설정

**Why 뒤로 미뤘는가:** 스코프가 예상보다 큼 — PG 연동, 정기결제, 환불 정책까지 포함된 실제 결제 시스템. "기업 인증 후 요금제 안내 로직"으로 설계돼 있어 3번(기업 프로필) 완료가 선행조건. PG사 선정 등 외부 의존성도 있어 별도 설계 세션 필요.

**스코프:**
- 요금제 Tab, 결제 내역 조회, 결제 내역 없는 경우
- 플랜 변경 로직
- 환불 모달(단건 상품 / 정기 결제 취소)
- 정기 결제 실패 이메일 안내

**Figma 근거:** `UI개편_설정` (5154:41400) 내 "요금제 및 결제 Tab bar 화면" 섹션(6230:47908)

---

## 6. 설정 나머지 (로그아웃/비밀번호 변경 등)

**스코프:** 로그아웃 모달, 비밀번호 변경, 인증/동의 노출 정책. member 모듈의 작은 확장 정도 — 아무 때나 끼워 넣기 좋음.

**Figma 근거:** `UI개편_설정` (5154:41400)

---

## 이미 구현 완료 (참고)

| 모듈 | 상태 | 설계 문서 |
|---|---|---|
| auth/member | 완료 (이메일 자체인증 + Google 로그인) | `docs/design/auth-email-custom-redesign.md` |
| artwork | 완료 (작품 CRUD, 북마크, 휴지통, 이미지 업로드) | `docs/design/artwork-module-design.md`, `-summary.md` |
| community | 완료 (배너, 포트폴리오/작가 찾아보기 피드) | `docs/design/community-module-design.md` |
