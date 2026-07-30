# MongoDB → MariaDB(RDB) 전면 전환 설계·실행 계획

> 작성일: 2026-07-30
> 상태: 설계안 (구현 전)
> 범위: 앳크루 백엔드 전체 영속성 계층(4개 도메인 모듈 member·auth·artwork·community + common, 애그리게잇 7종 + 보조 컬렉션 2종)을 MongoDB에서 MariaDB로 전환. 스키마 설계, ID 전략, 동시성 재설계, Spring Modulith 이벤트 레지스트리 교체, 테스트 인프라 전환, 로컬→prod 전환 순서까지 포함. 애플리케이션의 API 계약(요청/응답 DTO)은 변경하지 않는 것을 원칙으로 한다.
>
> **rev.2 (2026-07-30, 동일자 개정)**: (1) 실사용자 데이터가 아직 없음을 사용자가 확인 — ID 전략에서 Mongo ObjectId 보존 요구가 사라지고(§3.1), 데이터 마이그레이션(ETL)·점검 창 컷오버 계획 전체가 불필요해짐(§5 전면 축소). (2) 로컬 MariaDB로 우선 전환하고 prod 연결은 이후로 미루는 순서로 확정(§5). (3) [docs/roadmap.md](../roadmap.md), [global-country-plan-design.md](global-country-plan-design.md) 반영 — `Member.countryCode` 필드를 스키마에 추가(§3.2/§4)하고, 로드맵의 신규 모듈(인증 시스템/recruit 등) 착수 순서와 이번 전환의 선후 관계를 §9에서 결정.

---

## 0. 설계 요약 (TL;DR)

| 항목 | 결정 |
|------|------|
| DB | MariaDB 11.4 LTS, InnoDB, `utf8mb4` 고정 |
| ID 전략 | **String ID 유지** — 컬럼은 `VARCHAR(36)`, **전량 애플리케이션 생성 UUIDv7로 신규 발급**(실사용자 데이터 없음 확인 완료 — Mongo ObjectId 보존·FK 재매핑 문제 자체가 소멸, §3.1). 공개 인터페이스·JWT 클레임·리포지토리 시그니처 전부 무변경 |
| ORM | Spring Data JPA (Hibernate). enum은 전부 `@Enumerated(EnumType.STRING)` |
| 임베디드 문서 | 검색 대상(careers, activityFields, images, tags 등)은 자식 테이블로 정규화. 표시 전용 나열(videoLinks, material의 내부 리스트 등)은 MariaDB JSON 컬럼 |
| 동적 검색 | JPA `Specification` 채택 (QueryDSL은 동적 쿼리 3개 이상으로 늘어나는 시점에 재검토) |
| 커서 페이지네이션 | 기존 keyset 로직을 SQL `WHERE (a < ? OR (a = ? AND b < ?))` 형태로 그대로 이식 — 로직 변경 없음 |
| 원자적 연산 | `findAndModify` → `@Modifying UPDATE ... WHERE` + 영향 행 수 판별, `findAndRemove` → 조건부 `DELETE` + 영향 행 수 판별, `upsert $inc` → `INSERT ... ON DUPLICATE KEY UPDATE` |
| 낙관적 락 | `Member`, `Artwork`에 `@Version` 도입 (isNew 판별 문제도 함께 해결). 나머지는 미도입 |
| Partial unique 인덱스 | 불필요 — MariaDB UNIQUE는 NULL 중복을 허용하므로 일반 복합 UNIQUE로 충분. **RDB가 오히려 단순** |
| TTL 인덱스 | `@Scheduled` 정리 배치로 대체 + 조회 시점 만료 조건을 쿼리에 명시 (정확성은 쿼리 조건이, 용량은 배치가 담당) |
| 트랜잭션 | InnoDB 네이티브 — standalone/레플리카셋 분기(`MongoConfig`) 삭제, 로컬·프로덕션 트랜잭션 동작 통일. **이번 전환의 실질적 이득** |
| 이벤트 레지스트리 | `spring-modulith-starter-mongodb` → `spring-modulith-starter-jdbc`. `event_publication` 스키마는 Flyway로 직접 관리(자동 생성 비활성), **UUID 컬럼 매핑 검증 필수**(과거 `CodecConfigurationException` 유사 사고 재발 방지) |
| 스키마 관리 | Flyway (`flyway-core` + `flyway-mysql`), `ddl-auto: validate` 전 환경 고정 |
| 라이트(Laiteu) 호환 제약 | **해소** — 조사 결과 라이트도 RDB를 쓰지 않음(§1.1). 이번 설계는 그린필드, "라이트 마이그레이션"은 Mongo→RDB ETL 문제로 재정의 |
| 데이터 마이그레이션(ETL) | **불필요** — 실사용자 데이터 없음 확인 완료. 점검 창·듀얼 라이트·롤백 시한 전부 대상 없음(§5) |
| 전환 순서 | **로컬 MariaDB 우선** — docker-compose로 로컬 전환·전 모듈 검증을 먼저 끝내고, prod 데이터소스 연결은 준비되는 시점에 별도로 진행(§5) |
| 전환 단위 | 코드 전환은 모듈별 순차 PR(community → member → auth → artwork) |
| 신규 필드 동기화 | `Member.countryCode`([global-country-plan-design.md](global-country-plan-design.md), 구현 착수 중)를 스키마에 포함(§3.2/§4) |
| 로드맵과의 순서 | **MariaDB 전환을 먼저 완료 후 신규 모듈(인증/recruit 등) 착수** — [docs/roadmap.md](../roadmap.md)의 열린 질문에 대한 결정, 근거는 §9 |

---

## 1. 현재 상태 진단 (as-is)

코드 직접 조사 결과 기준 (2026-07-30):

| 항목 | 현황 | 전환 영향 |
|------|------|-----------|
| 의존성 | `spring-boot-starter-data-mongodb`, `spring-modulith-starter-mongodb`, `testcontainers-mongodb` (`build.gradle`) | 전부 교체 대상 |
| 도메인 모델 | 7개 `@Document` 애그리게잇 + 스키마리스 컬렉션 2종(`login_attempts`, `event_publication`), 전부 `@Id private String id` (ObjectId hex) | 엔티티 재작성 + ID 전략 결정 필요 (§3.1) |
| 임베디드 문서 | `Member.careers`(값 객체 리스트), `Member`의 enum 리스트 3종, `Artwork.images`/`materials`, `TermsAgreement` 등 | 정규화/JSON 분류 필요 (§3.2) |
| Mongo 전용 연산 | `findAndModify`(recordLogin), `findAndRemove`(RefreshToken 소비), `updateMulti`(Banner sortOrder 시프트), `upsert $inc`(LoginAttemptLimiter) | 전부 RDB 원자 연산으로 재설계 필요 (§3.3) |
| Mongo 전용 인덱스 | partialFilterExpression unique(`MemberIndexInitializer`), TTL 인덱스 2건(RefreshToken.expiresAt, login_attempts.firstFailedAt), sparse unique(handle) | §3.5 — partial은 오히려 단순해지고, TTL만 대체 설계 필요 |
| 인덱스 관리 | `@Indexed` + `*IndexInitializer` 3종(member/artwork/community) + `auto-index-creation: true`(로컬) / 수동(prod) | 전부 삭제, Flyway DDL로 일원화 |
| 동적 검색 | `MemberServiceImpl.searchProfiles()` — `MongoTemplate` Criteria 동적 필터 + 복합 커서 keyset 페이지네이션 | §3.6 |
| 트랜잭션 | standalone Mongo는 멀티 도큐먼트 트랜잭션 미지원 → 로컬은 트랜잭션 없음, prod만 레플리카셋 가정 + `MongoTransactionManager` 조건부 빈(`MongoConfig`) | §3.7 — 제약 자체가 소멸 |
| 이벤트 레지스트리 | Modulith 이벤트를 Mongo에 저장. UUID 인코딩 문제로 `uuidRepresentationCustomizer` 빈 필요했던 전례 | §3.8 |
| 테스트 | 테스트 클래스 14개, `SharedContainersConfig`의 `MongoDBContainer` `@ServiceConnection` 공유 | §3.11 |
| 로컬 인프라 | `docker-compose.yml`: `mongo:7` 단일 서비스 | MariaDB로 교체 |

### 1.1 "라이트 호환" 제약의 재정의 — 사실 확인 결과

CLAUDE.md에는 "라이트 → 앳크루 무중단 마이그레이션을 위해 데이터 모델 하위 호환성 유지"라는 제약이 있다. 그러나 레거시 저장소(`laiteu-be`)를 직접 조사한 결과:

- `spring-boot-starter-data-jpa` 의존성은 선언되어 있으나 `DataSourceAutoConfiguration`을 명시적으로 제외하고 있고,
- `@Entity` 클래스가 **한 건도 없으며**,
- docker-compose에 RDB 컨테이너가 없다.

즉 **라이트도 실질적으로 MongoDB(Atlas 추정)만 사용 중이고 RDB는 사용하지 않는다.** 따라서:

1. 이번 MariaDB 스키마는 맞춰야 할 기존 RDB 스키마가 없는 **그린필드 설계**다. 라이트의 테이블 구조에 끌려갈 이유가 없다.
2. 기존 제약 "라이트 마이그레이션 호환성"은 스키마 호환 문제가 아니라 **"라이트 Mongo 데이터 → 앳크루 MariaDB로의 ETL" 문제로 재정의**된다. 이는 앳크루 자체의 Mongo→MariaDB 이관(§5)과 동일한 변환 파이프라인을 공유할 수 있으므로 별도 제약이 아니라 §5의 입력 소스가 하나 더 늘어나는 것에 가깝다. (라이트 데이터의 필드 매핑 자체는 별도 논의 필요 — §8)

**결론**: 스키마 설계의 자유도는 확보되어 있다. 어려운 부분은 스키마가 아니라 (1) Mongo 전용 원자 연산의 재설계, (2) 전환 순서와 컷오버, (3) 테스트 인프라 전면 교체다.

---

## 2. 핵심 원칙

1. **API 계약 동결** — 이번 전환은 영속성 계층 교체이지 API 개편이 아니다. 모듈 공개 인터페이스(`MemberInfo` 등 Info record, Command record), 컨트롤러 DTO, JWT 클레임 구조는 변경하지 않는다. ID가 `String`인 것도 계약의 일부다(§3.1).
2. **모듈 경계를 전환 단위로** — Spring Modulith로 모듈 간 직접 의존이 금지되어 있어 영속성 계층이 모듈별로 격리되어 있다. 이 강점을 활용해 모듈별 순차 PR로 리뷰 가능한 크기를 유지한다. 단, **프로덕션에서 두 DB를 병행 운영하는 기간은 만들지 않는다**(§6) — 이벤트 레지스트리와 트랜잭션 경계가 DB 두 개로 쪼개지는 상태가 가장 위험하다.
3. **스키마는 Flyway가 유일한 진실** — `@Indexed`·IndexInitializer·`auto-index-creation` 같은 "코드가 스키마를 만드는" 패턴을 전부 버리고, 버전 관리된 SQL 마이그레이션만 스키마를 정의한다. `ddl-auto: validate`로 엔티티-스키마 불일치를 기동 시점에 잡는다.
4. **Mongo의 원자성은 SQL의 원자성으로 1:1 치환** — `findAndModify`류를 "SELECT 후 UPDATE" 2단계로 풀어쓰면 Mongo에는 없던 레이스가 생긴다. 반드시 단일 문장(조건부 UPDATE/DELETE, `ON DUPLICATE KEY UPDATE`) + 영향 행 수 판별로 치환한다.
5. **시간은 UTC로 저장** — [global-timezone-strategy.md](global-timezone-strategy.md)의 원칙을 그대로 계승한다. MariaDB `DATETIME`은 시간대 정보가 없으므로 JDBC 커넥션과 Hibernate 양쪽에서 UTC를 명시 고정한다(§3.10).

---

## 3. 결정 사항

### 3.1 ID 전략 — String ID 유지, `VARCHAR(36)` + UUIDv7

**결정**: 도메인·공개 인터페이스의 `String id`를 유지한다. 컬럼 타입은 `VARCHAR(36)`(latin1 계열 collation 지정, 아래 참고). **실사용자 데이터가 없음을 확인했으므로**(사용자 확인, 2026-07-30) 기존 Mongo ObjectId를 보존해야 한다는 제약 자체가 없다 — 로컬 개발 데이터는 스키마 전환과 함께 새로 만들면 되고, 전 레코드가 애플리케이션에서 생성한 **UUIDv7**(시간순 정렬 가능) 문자열을 PK로 받는다. DB auto-increment는 사용하지 않는다.

**근거**:
- **변경 파급 최소화**: 현재 모든 공개 API(Info record·DTO)가 `String id`를 노출하고, JWT의 subject·`memberId` 클레임, 4개 모듈의 리포지토리 시그니처(`MongoRepository<T, String>`)가 전부 String이다. `Long` auto-increment로 바꾸면 이 전부가 연쇄 수정 대상이 된다. String을 유지하면 모듈 간 참조(memberId, artworkId, authorId, folderId)의 타입이 그대로라 리포지토리 시그니처 변경만으로 끝난다 — 실데이터가 없어 ETL의 FK 재매핑 문제는 애초에 발생하지 않지만, 설령 나중에 실데이터가 생겨도 String 유지 결정 자체는 API 계약 안정성 때문에 유효하다.
- **UUIDv7인 이유**: 무작위 UUIDv4를 PK로 쓰면 InnoDB 클러스터드 인덱스에 무작위 삽입이 일어나 페이지 분할·버퍼 풀 오염이 생긴다. UUIDv7은 상위 비트가 타임스탬프라 삽입이 근사 단조 증가하여 auto-increment에 준하는 삽입 지역성을 가진다. 현 서비스 규모(실사용자 없음)에서는 v4로도 문제가 없지만, 생성 코드는 어차피 한 곳(공통 유틸)이므로 처음부터 v7로 간다.
- **`VARCHAR(36)` vs `BINARY(16)`**: BINARY(16)이 공간·인덱스 효율은 좋지만(레코드당 20바이트 절약) `AttributeConverter` 전면 도입, 콘솔에서의 가독성 저하라는 비용이 붙는다. 수백만 행 이하 규모에서 이 공간 차이는 의미가 없으므로 VARCHAR(36)로 확정하고, BINARY(16) 전환은 규모가 커졌을 때의 선택지로 남긴다(§8). ID 컬럼에는 `CHARACTER SET latin1 COLLATE latin1_bin`을 지정해(UUID는 ASCII뿐) utf8mb4 대비 인덱스 키를 줄이고 대소문자 구분 비교를 보장한다.
- **`Long` 전환 대안 기각 사유**: 인덱스 성능(8바이트 PK)은 가장 좋지만, ID enumeration(연번 추측) 노출, JWT 클레임 타입 변경, 4개 모듈 공개 계약 전면 수정 — 실데이터가 없어 마이그레이션 리스크는 사라졌어도 이 코드 변경 범위는 그대로 남는다. 이번 프로젝트의 목표는 "안전한 저장소 교체"이지 ID 체계 개편이 아니므로 여전히 String을 유지한다.

**JPA 주의점 (assigned ID의 isNew 문제)**: 애플리케이션이 ID를 직접 할당하면 Spring Data JPA의 `save()`가 신규/기존을 구분하지 못해 `merge`(불필요한 SELECT 선행)로 동작한다. `@Version` 필드가 있으면 `version == null`로 신규 판별이 되므로 §3.4의 낙관적 락 도입이 이 문제를 함께 해결한다. `@Version`을 두지 않는 엔티티는 `Persistable<String>` 구현(`@Transient boolean isNew`)으로 처리한다.

**트레이드오프**: Long PK 대비 인덱스가 크고(36바이트 키), FK 조인 비용이 다소 높다. 현 규모에서 무시 가능하다고 판단하며, 병목이 실측되면 BINARY(16) 마이그레이션은 컬럼 타입 변경 + Converter 추가로 국소적으로 가능하다.

### 3.2 임베디드 문서 → 정규화/JSON 분류

**결정 기준**: **WHERE 절에 등장할 가능성이 있으면 자식 테이블, 통째로 읽고 통째로 쓰는 표시 전용 데이터면 JSON 컬럼.**

#### member 모듈

| Mongo 필드 | RDB 설계 | 분류 근거 |
|------|------|------|
| `careers` (List\<CareerEntry\>) | 자식 테이블 `member_careers` — id VARCHAR(36) PK(기존 UUID 유지), member_id FK, work_title, role, start_date DATE, end_date DATE NULL, ongoing, description. `@OneToMany(cascade = ALL, orphanRemoval = true)` | 개별 항목 추가/삭제 API가 존재(addCareer/deleteCareer, careerId 단위 조작). 최대 50개 제한은 도메인 로직 유지. `periodDisplay()` 등 계산 로직은 값 객체/엔티티 메서드로 그대로 이식 |
| `activityFields` (List\<enum\>) | 자식 테이블 `member_activity_fields` — (member_id, activity_field) 복합 PK, `@ElementCollection` | **검색 필터로 쓰임**(작가 찾아보기의 활동 분야 필터) — equality 검색이 인덱스를 타야 하므로 정규화 필수. 역방향 인덱스 `idx_maf_field` (activity_field, member_id) 추가 |
| `activeRegions`, `teamExperiences` (List\<enum\>) | 자식 테이블 `member_active_regions`, `member_team_experiences` — 동일 패턴 | 현재는 검색에 안 쓰이지만 성격상(지역 필터) 쓰일 가능성이 높고, enum 리스트 3종의 매핑 패턴을 하나로 통일하는 것이 유지보수에 유리. JSON으로 갈 실익 없음 |
| `termsAgreement` (record) | `members` 테이블 내 `@Embeddable` 컬럼 5개 — terms_privacy_policy, terms_service_terms, terms_third_party, terms_marketing, terms_agreed_at | 1:1 값 객체, 리스트 아님 — 별도 테이블 불필요. record → `@Embeddable` 클래스 전환(JPA는 record 임베더블 미지원 주의) |
| `experienceRank` (int, ordinal 캐시) | **컬럼 유지** — experience_rank TINYINT | Mongo에서 enum name 문자열 정렬이 안 되어 만든 캐시인데, RDB에서도 `@Enumerated(STRING)` 컬럼은 사전순 정렬이라 동일 문제가 있다. ordinal 캐시 컬럼을 유지하는 것이 정합성 로직(도메인에서 experienceLevel 변경 시 함께 갱신) 포함 무변경으로 이식 가능 |
| `countryCode` (String, 신규) | `members.country_code` **VARCHAR(2) NOT NULL** | [global-country-plan-design.md](global-country-plan-design.md)에서 병행 설계·구현 착수 중인 필드(§2). `timezone`과 동일하게 카탈로그 검증(`Locale.getISOCountries()`) 값만 저장하는 단일 값이라 리스트 정규화 대상이 아니다. 이 문서 작성 시점 기준 아직 Mongo `Member`에 반영되지 않았을 수 있으므로, 실제 전환 착수 전 두 작업의 병합 순서를 §9에서 확정 |

#### artwork 모듈

| Mongo 필드 | RDB 설계 | 분류 근거 |
|------|------|------|
| `images` (List\<ArtworkImage\>, 순서 있음) | 자식 테이블 `artwork_images` — artwork_id FK, **ordinal INT**, original_key, thumb_key, thumb_adult_key, original_avif_key, processing_status. UNIQUE(artwork_id, ordinal). `@OrderColumn(name = "ordinal")` | 순서가 의미를 가짐(representativeImageIndex가 ordinal 참조), 이미지별 processing_status 갱신이 있음. 최대 20개 제한은 도메인 로직 유지 |
| `materials` (List\<Material\>, 내부에 List\<String\> 3개) | 자식 테이블 `artwork_materials`(artwork_id FK, ordinal, name) + 내부 3개 리스트(targets, attachment_keys, links)는 **JSON 컬럼** | 손자 테이블 3개를 만들면 material 하나에 테이블 4개가 붙는데, 이 리스트들은 검색·개별 수정이 전혀 없는 순수 표시 데이터다. material 단위 name은 컬럼으로 빼고(향후 조회 가능성), 내부 리스트는 JSON — 정규화 원리주의보다 실용을 택한다. MariaDB JSON은 LONGTEXT + `JSON_VALID` CHECK 제약이므로 저장·조회 모두 단순하다 |
| `roles`, `genres`, `tags`, `tools` (List\<String\>/enum) | 자식 테이블 4개 — `artwork_roles`, `artwork_genres`, `artwork_tags`, `artwork_tools`, 각 (artwork_id, value) 복합 PK + 역방향 인덱스 | 태그·장르·역할·툴은 탐색/필터 기능이 로드맵상 유력하다(커뮤니티 피드·검색). JSON에 넣으면 나중에 검색이 필요해진 시점에 다시 테이블을 파야 하므로 처음부터 정규화 |
| `videoLinks` (List\<String\>) | `artworks.video_links` **JSON 컬럼** | 순수 나열, 검색 가능성 없음 |
| `OrphanedImageKey.keys` (List\<String\>) | `orphaned_image_keys.keys` **JSON 컬럼** | 정리 작업 큐 성격 — 통째로 읽고 통째로 지움. 개별 키 검색 불필요 |

**트레이드오프**: `@ElementCollection`·`@OneToMany` 정규화는 애그리게잇 로드 시 컬렉션 페치 쿼리가 늘어난다(Member 상세 조회 시 4~5개 쿼리). `@BatchSize` 또는 필요 지점의 fetch join으로 제어하되, 이는 구현 단계 튜닝 항목이다. JSON 컬럼은 해당 데이터에 대한 DB 레벨 제약·검색을 포기하는 것 — 위 분류표의 항목들은 그 포기가 실질 비용이 없는 것들만 골랐다.

### 3.3 동시성 재설계 — Mongo 원자 연산의 1:1 치환

Mongo 전용 원자 연산 4곳이 이번 전환의 정밀 타격 지점이다. 각각 단일 SQL 문장으로 치환해 원자성을 유지한다.

#### 3.3.1 `recordLogin()` — `findAndModify` → 조건부 UPDATE

**결정**:
```java
@Modifying(clearAutomatically = true)
@Query("UPDATE Member m SET m.lastLoginAt = :now, m.updatedAt = :now WHERE m.id = :id AND m.active = true")
int recordLogin(String id, Instant now);
```
반환된 영향 행 수가 0이면 "미존재 또는 비활성"으로 판별 — 기존 `findAndModify`의 "활성 회원만 원자적 갱신" 시맨틱과 동일하다.

**근거**: SELECT 후 엔티티 수정으로 풀면 탈퇴 처리와의 레이스에서 탈퇴 회원의 lastLoginAt이 갱신될 수 있다. 조건부 UPDATE 단일 문장은 InnoDB 행 락으로 이 레이스가 원천 차단된다.

**트레이드오프**: 이 UPDATE는 `@Version`(§3.4)을 증가시키지 않는 bulk 연산이다. lastLoginAt은 비즈니스 불변식과 무관한 필드이므로 허용한다.

#### 3.3.2 RefreshToken 소비 — `findAndRemove` → SELECT 후 조건부 DELETE + 영향 행 수 판별

**결정**: 2단계이되 원자성 판별은 DELETE가 담당한다.
```java
Optional<RefreshToken> token = repository.findByTokenValue(tokenValue);
// ... 검증 후
int deleted = repository.deleteByIdReturningCount(token.get().getId()); // DELETE WHERE id = ?
if (deleted == 0) { /* 동시 요청이 먼저 소비 → 재사용 시도로 간주, 거부 */ }
```
동시 요청 두 개가 같은 토큰으로 들어와도 DELETE의 영향 행 수 1을 가져가는 쪽은 하나뿐이므로, Mongo `findAndRemove`의 "하나만 토큰을 가져간다" 보장이 유지된다.

**근거**: MariaDB는 `DELETE ... RETURNING`을 10.0.5부터 지원하므로(MySQL과 다른 지점) 단일 문장 치환도 가능하다. 다만 JPA에서 DELETE의 RETURNING 결과를 엔티티로 받으려면 네이티브 쿼리 + 수동 매핑이 필요해 코드가 지저분해진다. `SELECT FOR UPDATE` 후 DELETE 방식도 가능하지만 락 대기가 생긴다. 위 방식은 락 없이(lock-free) 영향 행 수만으로 승자를 결정하므로 가장 단순하고, 세 방식 모두 시맨틱이 동일하다.

**트레이드오프**: SELECT와 DELETE 사이에 다른 요청이 먼저 지울 수 있으나, 그 경우 deleted == 0으로 정확히 감지되어 거부되므로 보안 시맨틱(토큰 단일 소비)은 훼손되지 않는다. 없음에 가깝다.

#### 3.3.3 Banner sortOrder 벌크 시프트 — `updateMulti` → JPQL bulk UPDATE

**결정**:
```java
@Modifying(clearAutomatically = true, flushAutomatically = true)
@Query("UPDATE Banner b SET b.sortOrder = b.sortOrder + :delta, b.updatedAt = :now " +
       "WHERE b.status = 'ACTIVE' AND b.sortOrder BETWEEN :from AND :to AND b.id <> :selfId")
int shiftRange(int delta, int from, int to, String selfId, Instant now);
```
생성 시 "지정 순번 이후 전부 +1", 이동 시 "구간 내 방향에 따라 ±1"(`shiftForMove`) 로직을 그대로 이식.

**근거**: JPQL bulk UPDATE는 영속성 컨텍스트를 우회하므로, 같은 트랜잭션에서 이미 로드한 Banner 엔티티의 sortOrder가 stale해지는 함정이 있다. `flushAutomatically = true`(bulk 전에 미반영 변경 flush) + `clearAutomatically = true`(bulk 후 1차 캐시 클리어)를 반드시 지정하고, **시프트 후에 자기 자신의 sortOrder를 갱신·저장하는 순서**로 서비스 로직을 재배치한다(클리어로 인해 시프트 이전에 로드한 엔티티를 재사용하지 않도록).

**트레이드오프**: bulk UPDATE는 `@Version`·auditing(`@LastModifiedDate`)을 우회하므로 updatedAt을 쿼리에 명시했다. Banner에 `@Version`을 두지 않는 이유이기도 하다(§3.4).

#### 3.3.4 LoginAttemptLimiter — `upsert $inc` → `INSERT ... ON DUPLICATE KEY UPDATE`

브리핑에 없던 항목이나 조사에서 확인됨 (`auth/internal/application/LoginAttemptLimiter.java`) — 스키마리스 컬렉션 `login_attempts`에 `_id = "email:x" / "ip:y"` 키로 `$inc` upsert + TTL 인덱스(10분 윈도우).

**결정**: 테이블 `login_attempts`(attempt_key VARCHAR(350) PK, fail_count INT, first_failed_at DATETIME(6))로 전환하고, 증가 연산은 MariaDB 네이티브 원자 upsert로 치환한다.
```sql
INSERT INTO login_attempts (attempt_key, fail_count, first_failed_at)
VALUES (?, 1, ?)
ON DUPLICATE KEY UPDATE fail_count = fail_count + 1;
```
TTL 대체는 이중화한다: (1) **정확성** — `checkBlocked`의 조회 쿼리에 `AND first_failed_at > NOW() - INTERVAL 600 SECOND` 조건을 명시해 만료된 윈도우를 즉시 무시, (2) **용량** — `@Scheduled` 정리 잡이 만료 행을 주기 삭제(§3.5.2). 이렇게 하면 Mongo TTL의 "삭제 지연(최대 60초+)" 동안 차단이 과도하게 유지되던 기존 동작보다 오히려 정확해진다.

**근거**: `ON DUPLICATE KEY UPDATE`는 Mongo upsert `$inc`와 동일한 단일 문장 원자성을 제공한다. JPA 파생 쿼리로는 표현이 안 되므로 이 한 곳은 네이티브 쿼리(또는 `JdbcTemplate`)를 쓴다.

**트레이드오프**: 윈도우 시작(first_failed_at)이 upsert 시 갱신되지 않는 고정 윈도우 시맨틱은 기존과 동일하게 유지된다. 없음.

### 3.4 낙관적 락 (`@Version`) 도입 범위

**결정**: `Member`, `Artwork` 두 애그리게잇에만 `@Version private Long version;`을 도입한다. RefreshToken·BookmarkEntry·BookmarkFolder·Banner·OrphanedImageKey는 미도입.

**근거**:
- Member(프로필 수정 vs 탈퇴 vs 커리어 조작)와 Artwork(수정 vs Worker 콜백의 processingStatus 갱신 vs 휴지통 이동)는 **여러 경로가 같은 애그리게잇을 동시에 수정**할 수 있는 유이한 지점이다. Mongo에서는 마지막 쓰기가 조용히 이기는(lost update) 구조였는데, RDB 전환을 기회로 충돌을 감지해 도메인 예외(409)로 승격한다.
- 부수 효과로 assigned String ID의 isNew 판별 문제(§3.1)가 이 두 엔티티에서 자동 해결된다.
- RefreshToken(생성·삭제만 있음), BookmarkEntry(불변 스냅샷), Banner(bulk UPDATE가 version을 우회하므로 오히려 도입하면 안 됨 — §3.3.3)는 동시 수정 시나리오가 없거나 version과 상충한다.

**트레이드오프**: `ObjectOptimisticLockingFailureException`을 도메인 예외(예: `CONCURRENT_MODIFICATION`)로 변환하는 핸들링이 GlobalExceptionHandler에 추가되어야 하고, 해당 충돌 시 클라이언트 재시도 UX가 필요하다. 충돌 빈도가 낮은 초기에는 사실상 비용 없음.

### 3.5 Mongo 전용 인덱스의 대체

#### 3.5.1 (loginEmail, authProvider) partial unique — 일반 UNIQUE로 충분 (RDB가 더 단순)

**결정**: `UNIQUE KEY uk_members_login_email_provider (login_email, auth_provider)` 일반 복합 UNIQUE 인덱스 하나로 끝낸다. partialFilterExpression에 해당하는 장치는 **불필요**하다.

**근거**: 현재 Mongo에서는 sparse의 한계("두 필드 모두 null일 때만 제외") 때문에 "loginEmail이 BSON string 타입인 문서만 인덱싱"하는 partialFilterExpression으로 우회해야 했다(`MemberIndexInitializer`). MariaDB(MySQL 계열)의 UNIQUE 인덱스는 SQL 표준대로 **NULL끼리는 서로 다른 값으로 취급해 NULL 중복을 무제한 허용**한다. 탈퇴 시 `loginEmail = null`로 클리어하는 기존 도메인 로직(`deactivate()`)이 그대로 "탈퇴 회원은 unique 제약에서 자동 제외"를 달성한다 — Mongo에서 initializer 클래스와 긴 주석으로 설명해야 했던 문제가 RDB에서는 기본 동작이다. **이 항목은 전환으로 오히려 단순해지는 대표 사례다.**

`handle`의 sparse unique도 동일: `UNIQUE KEY uk_members_handle (handle)` — 탈퇴 시 null 클리어로 자동 제외. `deleted_login_email`은 일반(비유니크) 인덱스.

**트레이드오프**: 없음.

#### 3.5.2 TTL 인덱스 → `@Scheduled` 정리 배치 + 조회 시점 만료 조건

**결정**: RDB에는 TTL이 없으므로 두 컬렉션(refresh_tokens, login_attempts)에 대해:
1. **조회/검증 쿼리에 만료 조건 명시** — RefreshToken 검증은 이미 `expiresAt` 비교 로직이 있으므로 유지, login_attempts는 §3.3.4처럼 윈도우 조건을 쿼리에 추가. **정확성은 TTL이 아니라 쿼리 조건이 담당**하게 하여 배치 지연이 보안에 영향을 주지 않도록 한다.
2. **용량 관리는 `@Scheduled(fixedDelay = ...)` 정리 잡** — `DELETE FROM refresh_tokens WHERE expires_at < ?`, `DELETE FROM login_attempts WHERE first_failed_at < ?`를 1시간 주기로 실행. 기존 스케줄러(ImageRetryScheduler 등)와 같은 패턴, fixedDelay라 시간대 무관.

**근거**: "로그인/재발급 시점마다 정리" 방식은 요청 경로에 부가 쓰기를 얹고 트래픽이 없으면 정리도 안 된다. 별도 배치가 요청 경로와 분리되어 더 예측 가능하다. Mongo TTL도 어차피 최대 60초+ 지연이 있었으므로 시맨틱 저하가 아니다.

**트레이드오프**: 다중 인스턴스 배포 시 정리 잡이 중복 실행될 수 있으나 DELETE는 멱등이라 무해하다. 없음에 가깝다.

#### 3.5.3 검색용 복합 인덱스 — 그대로 이식

`{active, employmentStatus, updatedAt desc}` / `{active, employmentStatus, experienceRank desc, updatedAt desc}` 두 인덱스는 다음과 같이 이식한다 (MariaDB 10.8+의 DESC 인덱스 지원 활용):

```sql
KEY idx_members_search_latest     (is_active, employment_status, updated_at DESC),
KEY idx_members_search_experience (is_active, employment_status, experience_rank DESC, updated_at DESC)
```

단, activityFields 필터가 걸리는 검색은 `member_activity_fields` 조인이 섞이므로 실행 계획 확인 후 커버링 전략을 구현 단계에 조정한다.

### 3.6 동적 검색·커서 페이지네이션 — JPA Specification + keyset 그대로 이식

**결정**: `MongoTemplate` Criteria 동적 쿼리는 **Spring Data JPA `Specification`**(`JpaSpecificationExecutor`)으로 전환한다. QueryDSL은 채택하지 않는다.

**근거**: 현재 코드베이스의 동적 쿼리는 `searchProfiles()` 단 한 곳이다(필터 2종 + 정렬 2종 + 커서). 이 규모에 QueryDSL을 도입하면 annotation processor(Q클래스 생성)라는 빌드 복잡도가 추가되는데, 원조 QueryDSL의 유지보수 정체와 Spring Boot 4 호환성 검증 부담을 고려하면 표준 스택(Specification)이 팀 규모(소규모)와 리스크 프로파일에 맞다. 동적 쿼리가 3개 이상으로 늘어나는 시점(recruit 모듈의 구인 검색 등이 유력)에 QueryDSL 재검토를 명시적 트리거로 남긴다(§8).

**커서(keyset) 이식 — 로직 무변경**: 기존 복합 커서(`experienceRank_updatedAtEpochMilli`)의 비교 로직은 SQL 표준 형태로 그대로 옮겨진다:

```sql
-- 경력순 (experience_rank DESC, updated_at DESC) 다음 페이지
WHERE is_active = true AND employment_status = ?
  AND (experience_rank < ?
       OR (experience_rank = ? AND updated_at < ?))
ORDER BY experience_rank DESC, updated_at DESC
LIMIT ?;

-- 최신순은 updated_at 단일 커서: AND updated_at < ?
```

커서 인코딩(정렬 키 직렬화) 형식도 API 계약이므로 유지한다 — 클라이언트가 들고 있는 커서가 컷오버를 넘어 유효할 필요는 없지만(피드 커서는 휘발성), 형식을 유지하면 프론트 수정이 0이 된다. Specification은 위 OR 조건을 `cb.or(cb.lessThan(...), cb.and(...))`로 직접 표현 가능하다.

**트레이드오프**: Specification은 QueryDSL 대비 타입 안전성이 낮고(속성명 문자열) 복잡한 쿼리에서 가독성이 떨어진다. 현재 1곳 규모에서는 수용하고, 위 재검토 트리거로 방어한다.

### 3.7 트랜잭션 경계 — 제약의 소멸, 이번 전환의 실질 이득

**결정**: `MongoConfig` 삭제(uuidRepresentationCustomizer·조건부 MongoTransactionManager 모두 불필요). `application.yml`의 `MongoDbTransactionAutoConfiguration` exclude 제거. 서비스 계층의 `@Transactional`은 JPA `JpaTransactionManager`(자동 구성)로 일원화되고, **로컬 docker-compose 환경과 프로덕션의 트랜잭션 동작이 처음으로 동일해진다.**

**근거**: 지금까지는 standalone Mongo가 멀티 도큐먼트 트랜잭션을 지원하지 않아 "로컬에서는 트랜잭션 없이 돌고 prod에서만 레플리카셋 + MongoTransactionManager"라는 환경 비대칭이 있었다 — 로컬·테스트에서 재현되지 않는 부분 커밋 버그의 온상이자, `MongoConfig`의 조건부 빈이라는 부자연스러운 구조의 원인이었다. InnoDB는 어디서나 네이티브 ACID이므로 이 클래스의 문제가 통째로 사라진다. 여러 애그리게잇을 함께 쓰는 유스케이스(탈퇴 시 Member 갱신 + 이벤트 발행, Banner 시프트 + 저장, 폴더 삭제 + 엔트리 정리)가 전 환경에서 원자적이 된다.

**트레이드오프**: 없음 — 순수 개선.

### 3.8 Spring Modulith 이벤트 레지스트리 — JDBC 전환

**결정**: `spring-modulith-starter-mongodb` → `spring-modulith-starter-jdbc` 교체. `event_publication` 테이블 스키마는 **Modulith 자동 생성(`spring.modulith.events.jdbc.schema-initialization.enabled`)을 끄고 Flyway 마이그레이션으로 직접 관리**한다 — Modulith 배포 산출물(jar) 안의 MariaDB용 스키마 SQL을 그대로 복사해 `V2__modulith_event_publication.sql`로 커밋한다. `republish-outstanding-events-on-restart: true`(prod)는 유지.

**근거**: 프로덕션 스키마의 진실이 두 곳(Flyway + Modulith 자동 생성)으로 갈라지면 `ddl-auto: validate`류의 검증이 불가능해진다. 버전 업그레이드로 Modulith가 스키마를 바꿀 때도 Flyway 마이그레이션으로 명시적으로 반영하는 것이 안전하다.

**⚠️ UUID 컬럼 타입 검증 (과거 사고 재발 방지)**: Mongo에서 Modulith 이벤트 발행 ID(UUID) 인코딩 문제로 `CodecConfigurationException`이 발생해 `uuidRepresentationCustomizer` 빈으로 해결한 전례가 있다. JDBC 레지스트리에서도 동일 계열 리스크가 있다 — `event_publication.id`의 컬럼 타입(MariaDB 스키마 기준 VARCHAR(36) vs 드라이버/버전에 따른 UUID 타입 처리)과 Modulith 버전이 기대하는 매핑이 일치하는지 **전환 Phase에서 이벤트 발행→소비→완료 마킹 전 사이클을 Testcontainers 통합 테스트로 명시 검증**한다. 특히 (1) 이벤트 발행 후 `event_publication`에 행이 실제 INSERT되는지, (2) 재기동 시 `republish-outstanding-events-on-restart`가 미완료 이벤트를 재발행하는지, (3) MariaDB `UUID` 네이티브 타입(10.7+)을 쓰지 않고 Modulith 공식 스키마의 타입을 그대로 쓰는지를 체크리스트로 남긴다.

**트레이드오프**: 이벤트 레지스트리가 비즈니스 데이터와 같은 DB에 있게 되어(기존 Mongo도 동일 구조였음) 트랜잭션 통합은 오히려 좋아진다. 없음.

### 3.9 스키마 마이그레이션 — Flyway

**결정**:
- `flyway-core` + `flyway-mysql`(MariaDB는 mysql 모듈이 담당) 도입, `src/main/resources/db/migration/V1__baseline_schema.sql`(전 테이블·인덱스), `V2__modulith_event_publication.sql` 구성.
- `spring.jpa.hibernate.ddl-auto: validate`를 **로컬·테스트·프로덕션 전 환경에 고정**한다. `update`는 어느 환경에서도 쓰지 않는다.
- 테스트도 Flyway로 스키마를 만든다(Testcontainers 기동 시 마이그레이션 실행) — 테스트가 곧 마이그레이션 스크립트의 회귀 검증이 된다.

**근거**: `update`는 컬럼 rename을 add로 오인하는 등 스키마 드리프트의 고전적 원인이고, "로컬은 update·prod는 validate"로 나누면 로컬에서만 굴러가는 엔티티 변경이 prod 배포에서 터진다. 전 환경 validate + Flyway 단일 경로면 엔티티-스키마 불일치가 가장 이른 시점(로컬 기동)에 드러난다.

**트레이드오프**: 엔티티 필드를 하나 추가할 때마다 마이그레이션 SQL 작성이 강제되어 로컬 개발 리듬이 약간 느려진다. 이는 의도된 마찰이며(스키마 변경을 리뷰 가능한 산출물로 만듦), 정 필요하면 로컬 한정 `flyway.clean` + 재마이그레이션으로 보완한다.

### 3.10 시간·문자셋 정책

**결정**:
- 모든 `Instant` 필드는 `DATETIME(6)`(마이크로초)으로 저장. JDBC URL에 `connectionTimeZone=UTC`(MariaDB Connector/J 3.x 기준 파라미터) 및 `spring.jpa.properties.hibernate.jdbc.time_zone: UTC`를 명시해 세션 시간대 의존을 차단한다. `LocalDate`(careers)는 `DATE`.
- DB·전 테이블 `CHARACTER SET utf8mb4` (이모지 포함 사용자 입력 대응), collation `utf8mb4_unicode_ci` 계열. ID 컬럼만 §3.1의 latin1_bin.

**근거**: [global-timezone-strategy.md](global-timezone-strategy.md)의 "저장·연산은 UTC" 원칙을 계승한다. MariaDB `DATETIME`은 시간대 무정보 타입이라 커넥션 시간대가 암묵 개입하는데, 이를 양쪽(드라이버+Hibernate)에서 명시 고정하지 않으면 서버 TZ에 따라 저장값이 달라지는 재현 불가 버그가 생긴다. `TIMESTAMP` 타입은 2038년 문제와 자동 갱신 시맨틱 때문에 배제.

**트레이드오프**: 없음.

### 3.11 의존성·설정·테스트 인프라 변경 목록

**build.gradle** — 제거: `spring-boot-starter-data-mongodb`, `spring-modulith-starter-mongodb`, `testcontainers-mongodb`. 추가: `spring-boot-starter-data-jpa`, `org.mariadb.jdbc:mariadb-java-client`(runtimeOnly), `org.flywaydb:flyway-core` + `org.flywaydb:flyway-mysql`, `spring-modulith-starter-jdbc`, `org.testcontainers:testcontainers-mariadb`(test).

**application.yml**: `spring.mongodb.*`·`spring.data.mongodb.*`·`MongoDbTransactionAutoConfiguration` exclude 삭제 → `spring.datasource.url: jdbc:mariadb://localhost:3306/atcrew?connectionTimeZone=UTC` + username/password(환경변수), `spring.jpa.hibernate.ddl-auto: validate`, `spring.jpa.open-in-view: false`(OSIV는 처음부터 끔 — 커넥션 점유·지연 로딩 사고 예방), hibernate.jdbc.time_zone. **application-prod.yml**: datasource 환경변수화, `auto-index-creation` 항목 소멸. **테스트 application.yml**: 동일 방향.

**docker-compose.yml**: `mongo:7` 서비스 → `mariadb:11.4`(포트 3306, `MARIADB_DATABASE: atcrew`, 볼륨 `mariadb_data`, `command: --character-set-server=utf8mb4`).

**테스트 인프라**:
- `SharedContainersConfig`: `MongoDBContainer` → `MariaDBContainer`(`mariadb:11.4`) + `@ServiceConnection` — 구조는 동일, 이미지와 타입만 교체. ApplicationContext 생명주기 바인딩 유지.
- 리포지토리 슬라이스 검증에는 `@DataJpaTest` + `@AutoConfigureTestDatabase(replace = NONE)` + Testcontainers 조합을 신규 활용 가능(임베디드 H2는 MariaDB 방언 차이 때문에 사용하지 않는다).
- 재작성 범위 추정: 테스트 클래스 14개 중 — REST Docs 문서 테스트(mock 기반)와 순수 도메인 테스트(`MemberTest`)는 **무변경**, Testcontainers 통합 테스트(`MemberModuleTests`, `SecurityIntegrationTest`, `ModularStructureTests`의 컨텍스트 로딩, 각 모듈 통합 테스트)는 컨테이너 교체 + Mongo 전용 준비 코드(인덱스 대기, 컬렉션 클린업)의 SQL 치환이 필요. 서비스 단위 테스트 중 `MongoTemplate`을 mock하던 곳(searchProfiles, Banner 시프트, LoginAttemptLimiter)은 리포지토리 인터페이스 변경에 따라 재작성. 체감 재작성 비중은 통합 테스트 위주로 전체의 약 1/3 수준으로 추정하며, Phase 5에서 실측 조정한다.

**삭제되는 코드**: `MongoConfig`, `MemberIndexInitializer`, `ArtworkIndexInitializer`, `CommunityIndexInitializer`, `RefreshTokenCustomRepositoryImpl`(파생/네이티브 쿼리로 대체), `@EnableMongoAuditing`류 → `@EnableJpaAuditing` 교체.

**트레이드오프**: 없음 — 기계적 교체 목록.

---

## 4. 목표 스키마 정의 (요약)

테이블·컬럼은 snake_case, boolean은 `is_` 접두 없이 TINYINT(1)로 두되 컬럼명은 가독성 우선(`active` → `is_active`). 모든 테이블에 `created_at`/`updated_at DATETIME(6)`(auditing 대상만).

| 테이블 | 주요 컬럼 | 인덱스 |
|------|------|------|
| `members` | id PK, login_email, auth_provider, handle, name, creator_role, password_hash, email_verified, timezone, **country_code**, employment_status, experience_level, experience_rank, total_slot_count, available_slot_count, contact, sns, tools, terms_* 5컬럼, is_active, deleted_at, last_login_at, deleted_login_email, version, created_at, updated_at | `uk_members_login_email_provider`(login_email, auth_provider), `uk_members_handle`(handle), `idx_members_deleted_email`(deleted_login_email), `idx_members_search_latest`, `idx_members_search_experience` (§3.5.3) |
| `member_careers` | id PK, member_id FK, work_title, role, start_date, end_date, ongoing, description | `idx_mc_member`(member_id) |
| `member_activity_fields` | (member_id, activity_field) PK | `idx_maf_field`(activity_field, member_id) |
| `member_active_regions` / `member_team_experiences` | (member_id, value) PK | — |
| `refresh_tokens` | id PK, member_id, token_value, expires_at, created_at | `uk_rt_token`(token_value), `idx_rt_member`(member_id), `idx_rt_expires`(expires_at) |
| `login_attempts` | attempt_key PK, fail_count, first_failed_at | `idx_la_first_failed`(first_failed_at) |
| `artworks` | id PK, author_id, title, description, thumbnail_key, image_layout_type, artwork_field, creative_type, work_duration, cut_count, age_rating, visibility, visibility_before_delete, status, video_links JSON, deleted_at, version, created_at, updated_at | `idx_aw_author`(author_id), `idx_aw_retry`(status, updated_at) — Worker 재시도 스케줄러 쿼리(`status='PROCESSING' AND updated_at < ?`)용 |
| `artwork_images` | id PK(대리키 BIGINT AUTO_INCREMENT — 외부 노출 없는 순수 내부 자식 행), artwork_id FK, ordinal, original_key, thumb_key, thumb_adult_key, original_avif_key, processing_status | `uk_ai_order`(artwork_id, ordinal) |
| `artwork_materials` | id PK(동일 대리키), artwork_id FK, ordinal, name, targets JSON, attachment_keys JSON, links JSON | `uk_am_order`(artwork_id, ordinal) |
| `artwork_roles` / `artwork_genres` / `artwork_tags` / `artwork_tools` | (artwork_id, value) PK | `idx_*_value`(value, artwork_id) |
| `orphaned_image_keys` | id PK, keys JSON, marked_at | `idx_oik_marked`(marked_at) |
| `bookmark_folders` | id PK, member_id, name, sort_order, created_at | `uk_bf_member_name`(member_id, name), `idx_bf_member_sort`(member_id, sort_order) |
| `bookmark_entries` | id PK, member_id, artwork_id, folder_id NULL, artwork_visibility_at_save, saved_at | `idx_be_cursor`(member_id, folder_id, saved_at DESC, id), `uk_be_member_artwork`(member_id, artwork_id)*, `idx_be_folder`(folder_id) |
| `banners` | id PK, member_id, image_url, link_url, sort_order, status, created_at, updated_at | `idx_bn_status_sort`(status, sort_order) |
| `event_publication` | Modulith 공식 MariaDB 스키마 그대로 (§3.8) | 공식 스키마의 인덱스 그대로 |

\* `uk_be_member_artwork`는 현재 Mongo에 없는 제약 — 중복 북마크 방지가 애플리케이션 로직에만 있다면 DB 제약으로 승격할 기회다. 기존 데이터에 중복이 있는지 ETL에서 검증 후 적용 여부 확정(§8).

**FK 정책**: 같은 모듈 내 부모-자식(members↔member_careers, artworks↔artwork_images 등)은 FK + `ON DELETE CASCADE` 없이 JPA cascade로 관리(삭제가 soft delete 중심이라 DB cascade 실익 없음). **모듈 경계를 넘는 참조(bookmark_entries.artwork_id → artworks, banners.member_id → members 등)에는 FK 제약을 걸지 않는다** — Modulith 원칙상 모듈 간 결합은 인터페이스·이벤트로만 하며, DB FK는 모듈 독립 배포·테이블 분리(향후 서비스 분리)의 발목을 잡는다. 정합성은 기존대로 이벤트 드리븐으로 유지.

---

## 5. 로컬 우선 전환, 데이터 마이그레이션 불필요

**확인된 전제(2026-07-30, 사용자 확인)**: 프로덕션에 실사용자 데이터가 없다. 따라서 이 문서의 이전 판(rev.1)에 있던 ETL·검증 게이트·점검 창 컷오버·롤백 시한 계획 전체가 **대상 자체가 없다.** 로컬 개발 데이터(Mongo 기준 시드/테스트 데이터)는 폐기하고 MariaDB 스키마로 새로 시작하면 된다.

**결정 — 실행 순서**:
1. **로컬 우선**: `docker-compose.yml`의 `mongo` 서비스를 `mariadb:11.4`로 교체하고, 로컬 환경에서 Flyway 마이그레이션(§3.9)으로 스키마를 만든 뒤 §6의 Phase대로 모듈별 전환 코드를 작성·검증한다. 이 단계에서는 `application.yml`(로컬)만 MariaDB를 가리키면 되고, `application-prod.yml`은 아직 건드릴 필요가 없다.
2. **prod 연결은 별도 단계로 분리**: 로컬에서 전 모듈 전환과 회귀 테스트(§6 전 Phase)가 완전히 끝난 뒤, `application-prod.yml`에 실제 MariaDB 호스팅(§8 미결정) 접속 정보를 채워 넣고 배포한다. 이 시점에 프로덕션 Mongo에 옮겨야 할 실사용자 데이터가 없으므로 **배포 자체가 곧 컷오버**다 — 별도 점검 창, 다운타임 공지, 롤백 시한이 필요 없다.
3. **다만 라이트(Laiteu) 이관은 별개 문제로 남는다**: §1.1에서 정리했듯 "라이트→앳크루 데이터 이관"이 실제로 필요한 시점(라이트 서비스 종료 시)이 오면, 그때는 라이트 쪽에 실사용자 데이터가 있으므로 별도의 ETL 설계가 필요하다. 그 설계는 (a) 이관 시점의 앳크루 스키마가 이미 MariaDB로 확정되어 있고 (b) 이관 대상이 앳크루 자체가 아니라 라이트라는 점에서 이번 섹션보다 단순한 단방향 배치가 될 가능성이 높지만, 지금 미리 설계하지는 않는다(라이트 종료 일정이 아직 없음).

**트레이드오프**: 없음 — 실데이터가 없다는 전제가 깨지면(예: 전환 도중 프로덕션에 데이터가 생기면) 이 섹션 전체를 rev.1의 ETL/점검 창 계획으로 되돌려야 한다. 그 경우를 대비해 rev.1의 계획은 git 히스토리에 남아있다.

---

## 6. 단계별 실행 계획 (Phase 분해)

**대원칙**: 코드 전환은 모듈별 순차 PR(리뷰 가능 크기)로 **로컬/CI 환경에서** 진행하고, **프로덕션 배포는 전 모듈 완료 후 일괄**(feature 브랜치에 PR 스택 적층, §5). 모듈별로 프로덕션에 순차 배포하면 "member는 MariaDB, artwork는 Mongo"인 기간이 생기는데, 이 상태는 이벤트 레지스트리를 어느 한쪽에 두어야 해서 반대쪽 모듈의 이벤트 발행 원자성이 깨지고, 트랜잭션 경계도 DB별로 갈라진다 — 순차 배포의 리스크 분산 효과보다 이원 운영의 구조적 위험이 크다. 실사용자 데이터가 없으므로(§5) 이 배포는 다운타임이나 컷오버 절차 없이 단순 배포로 끝난다.

| Phase | 내용 | 되돌리기 |
|------|------|------|
| **P1. 인프라 준비** ✅ 완료 | build.gradle 의존성 추가(Mongo 의존성은 아직 유지 — 공존), Flyway V1 스키마, docker-compose에 mariadb 서비스 추가(mongo와 병행), `SharedContainersConfig` 및 개별 `@Container` 테스트 클래스 3곳에 MariaDBContainer 추가, datasource/JPA 설정. 전체 테스트 스위트 그린 확인 완료. **V2(modulith event_publication 스키마)는 P5로 연기** — spring-modulith-starter-jdbc를 아직 추가하지 않아 공식 스키마를 jar에서 추출할 수 없었음(§8 O.Q. 추가). P1 진행 중 발견한 이슈: 테스트 전용 `application.yml`에 `MongoDbTransactionAutoConfiguration` exclude가 없어 JPA `transactionManager` 빈과 이름 충돌 — main과 동일하게 exclude 추가로 해결(기존에도 존재했던 로컬/테스트 설정 드리프트) | **쉬움** — 순수 추가, 기존 경로 무영향 |
| **P2. community 파일럿 전환** ✅ 완료 | Banner 엔티티·리포지토리·bulk 시프트(§3.3.3)를 JPA로 전환. **가장 작고(애그리게잇 1개), 다른 모듈이 참조하지 않으며, 관리자 전용 기능이라 사고 반경이 최소** — 여기서 JPA 매핑·Flyway·테스트 패턴을 확립해 이후 모듈의 템플릿으로 삼는다. member부터 시작하지 않는 이유: member는 auth·artwork·community가 전부 참조하는 중심 모듈이라 패턴 미검증 상태에서 손대면 실패 비용이 가장 크다 | **쉬움** — 모듈 하나, PR 단위 revert 가능 |
| **P3. member 전환** ✅ 완료 | Member + 자식 테이블 4종, searchProfiles Specification + keyset(§3.6), recordLogin(§3.3.1), `@Version`. 가장 큰 단일 작업 — P2에서 검증된 패턴 적용 | **보통** — PR revert 가능하나 후속 P4가 의존 |
| **P4. auth·artwork 전환** | RefreshToken 소비(§3.3.2), LoginAttemptLimiter(§3.3.4), TTL 정리 배치(§3.5.2) / Artwork + 자식 테이블, 북마크 커서 쿼리, Worker 재시도 쿼리(`@Query` JPQL 치환). 두 모듈은 상호 독립이라 병렬 PR 가능 | **보통** |
| **P5. 이벤트 레지스트리·정리** | modulith-starter-jdbc 교체 + UUID 검증 테스트(§3.8), `MongoConfig`·IndexInitializer 3종 삭제, **Mongo 의존성·docker-compose mongo 서비스 최종 제거**, 전체 테스트 스위트 회귀(기존 `/test` 커맨드 기준 전체 녹색) — 이 시점에는 아직 로컬/CI에서만 검증된 상태 | **어려움 시작** — 여기서부터 Mongo 경로가 소멸. P5 머지 전이 마지막 무비용 회귀 지점 |
| **P6. prod 연결** | `application-prod.yml`에 실 MariaDB 접속 정보 채움(§8 호스팅 형태 확정 후), 배포. 실사용자 데이터가 없으므로 ETL·점검 창·롤백 시한 불필요(§5) — 배포가 곧 컷오버 | **쉬움** — 데이터 이관이 없어 순수 배포 리스크만 존재 |

각 Phase는 독립 PR(들)로 리뷰하고, P2~P4 기간 중 main에서 진행되는 기능 개발과의 충돌을 줄이기 위해 **전환 기간 중 신규 기능은 전환 브랜치 기준으로 작업**하는 팀 규칙이 필요하다(§8).

---

## 7. 리스크

1. **단일 PR 전환은 금지 수준의 위험** — 4개 모듈, 애그리게잇 7종 + 보조 2종, 영속성 계층 전체 + 테스트 인프라가 한 번에 바뀐다. 단일 PR이면 리뷰 불가능한 diff가 되고 결함이 어느 모듈에서 왔는지 격리가 안 된다. §6의 Phase 분해가 이 리스크의 직접 대응이다.
2. **강점 — 모듈 경계가 이미 전환 단위다**: Modulith 규율 덕에 리포지토리·도메인이 모듈별 `internal`에 격리되어 있고 모듈 간 참조는 String ID뿐이다. ID 전략을 String으로 유지(§3.1)하는 순간, 한 모듈의 영속성 교체가 다른 모듈 코드에 전혀 전파되지 않는다 — 이 구조가 아니었다면 모듈별 순차 PR 자체가 불가능했다.
3. **Mongo 원자 연산의 침묵 열화**: `findAndModify`류를 무심코 SELECT+저장 2단계로 옮기면 컴파일도 테스트도 통과하지만 동시성 시맨틱이 조용히 깨진다. §3.3의 4개 지점을 전환 체크리스트로 못박고, 각 지점에 동시성 시나리오 테스트(스레드 2개 경합)를 요구한다.
4. **bulk 쿼리와 영속성 컨텍스트의 상호작용**(§3.3.3, §3.3.1): Mongo에는 1차 캐시 개념이 없어 존재하지 않던 버그 클래스다. `@Modifying` 사용처 전부에 clear/flush 속성 명시를 리뷰 기준으로 삼는다.
5. **이벤트 레지스트리 전환 결함**(§3.8): 이벤트 유실은 탈퇴 전파(soft delete 이벤트 드리븐 읽기 모델) 같은 모듈 간 정합성을 조용히 깨뜨린다. UUID 매핑 검증 + 재발행 동작 테스트를 P5 게이트로 지정.
6. **시간대 회귀**(§3.10): DATETIME 무시간대 특성상 커넥션 TZ 설정 누락 시 저장값이 9시간 밀리는 사고가 전형적이다. 통합 테스트에 "저장한 Instant == 조회한 Instant" 왕복 검증을 포함한다.
7. **전환 기간의 이중 작업**: P2~P5 동안 main의 기능 개발이 Mongo 기준으로 진행되면 전환 브랜치와 충돌한다. 전환 기간을 짧게(집중 스프린트) 가져가고 기능 개발 기준 브랜치를 합의하는 것이 유일한 완화책이다.

---

## 9. 로드맵(신규 모듈)과의 순서 — [docs/roadmap.md](../roadmap.md) 열린 질문에 대한 결정

`docs/roadmap.md`(§"병행 고려 사항")는 "MariaDB 마이그레이션을 먼저 끝내고 신규 모듈을 MariaDB 기준으로 새로 설계할지, 아니면 신규 모듈(인증 시스템 → recruit → 기업 프로필 → 검색 → 결제/구독)을 우선 MongoDB로 만들고 나중에 함께 이관할지" 결정이 필요하다고 명시해뒀다.

**결정**: **MariaDB 전환(§6 P1~P6)을 먼저 완료한 뒤 로드맵의 신규 모듈에 착수한다.**

**근거**:
- 실사용자 데이터가 없다는 것이 확인된 지금이 이 전환의 비용이 가장 낮은 시점이다(§5) — 애그리게잇이 7종에 머물러 있는 지금 전환하는 것과, 인증 시스템·recruit·기업 프로필·검색·구독까지 5개 모듈이 더 늘어난(로드맵 1~5순위) 뒤 전환하는 것은 정규화해야 할 테이블 수, 재작성할 리포지토리 수, 회귀 검증 범위가 전부 몇 배로 벌어진다.
- 신규 모듈을 MongoDB 기준으로 먼저 만들면, 그 모듈들도 결국 이번 문서의 정규화 원칙(§2.2)·동시성 재설계(§3.3)를 처음부터 다시 거쳐야 한다 — "일단 Mongo로 만들고 나중에 같이 옮기자"는 사실상 같은 일을 두 번 하는 것이다.
- 반대로 신규 모듈을 먼저 만들면 얻는 이득(로드맵 우선순위 준수, 사용자 대면 기능이 더 빨리 나옴)은 실사용자가 아직 없는 현재로서는 실질적 이득이 작다 — 정식 오픈 전에 저장소를 확정하는 것이 제품 일정보다 우선한다.
- `Member.countryCode`(§3.2, 구현 착수 중)처럼 이미 병행 진행 중인 작업은 예외로 둔다 — 이 필드는 스코프가 작고(String 컬럼 하나) 이미 착수됐으므로 MariaDB 전환과 병합해 진행하되, **인증 시스템 이하 로드맵 1~6순위 항목은 MariaDB 전환(P6) 완료 후 착수한다.**

**트레이드오프**: 신규 기능(특히 로드맵 1순위 인증 시스템)의 사용자 대면 출시가 MariaDB 전환 기간만큼 늦춰진다. §6 Phase 규모(4개 모듈, 집중 스프린트 가정)를 고려하면 지연은 제한적이라고 판단하지만, 실제 소요 기간은 P1~P4 착수 후 재추정이 필요하다.

---

## 10. 미결정 사항 (Open Questions)

1. **MariaDB 호스팅 형태** — managed(RDS/SkySQL 등) vs self-hosted. 백업·PITR(point-in-time recovery) 전략과 `application-prod.yml` 접속 설정이 여기 걸린다(§5의 prod 연결 단계에서 확정 필요).
2. **라이트(laiteu) Mongo 데이터의 필드 매핑** — §1.1/§5-3에서 재정의된 별도 이관 문제. 라이트 서비스 종료 일정이 잡히면 별도 문서로 설계.
3. ~~**UUIDv7 생성 방식**~~ — **해결(P2)**: 라이브러리 추가 없이 직접 구현(`com.atcrew.common.id.UuidV7Generator`, RFC 9562, ms 타임스탬프+SecureRandom). 전 모듈 공용.
4. **`uk_be_member_artwork`(중복 북마크 방지) DB 제약 승격 여부** — 실사용자 데이터가 없으므로 기존 데이터 중복 스캔은 불필요, 로컬 전환 시 바로 제약으로 적용 가능할 것으로 보이나 §4에서 최종 확정.
5. **QueryDSL 재검토 트리거** — 동적 쿼리 3개 이상(recruit 모듈 검색 예상) 시점에 Specification 유지 vs QueryDSL 도입 재평가 (§3.6).
6. **전환 기간 중 기능 개발 기준 브랜치 합의** — §7-7. 팀 운영 결정 사항(단, §9 결정으로 신규 모듈 착수 자체가 미뤄지므로 실질적 충돌 범위는 줄어든다).
7. **`Member.countryCode` 병합 시점** — [global-country-plan-design.md](global-country-plan-design.md) 구현이 이 문서의 P3(member 전환)보다 먼저 끝나면 Mongo `Member`에 필드가 먼저 생기고, 그 반대면 MariaDB 스키마(§3.2/§4)에 바로 반영된다. 어느 쪽이 먼저 끝나든 필드 자체(String, 카탈로그 검증)는 동일하므로 순서에 따른 재작업은 없다.
8. **V2(`event_publication`) 마이그레이션 SQL** — P1에서 작성하지 않음. spring-modulith-starter-jdbc를 P5에서 추가한 직후, 해당 jar에 포함된 MariaDB 공식 스키마 SQL을 그대로 복사해 `V2__modulith_event_publication.sql`로 커밋할 것(§3.8의 UUID 컬럼 타입 검증 경고와 동일한 이유로 추측 작성 금지).

## 11. P2에서 발견한 Spring Boot 4 / Modulith 관련 함정 (다음 Phase 참고용)

- **Flyway 자동설정이 별도 아티팩트로 분리됨**: Boot 3까지는 `flyway-core`를 클래스패스에 두기만 하면 `spring-boot-autoconfigure`가 `FlywayAutoConfiguration`을 제공했으나, **Boot 4.0.6부터는 `org.springframework.boot:spring-boot-flyway`를 별도로 추가해야 한다**(Hibernate/Mongo도 각각 `spring-boot-hibernate`/`spring-boot-mongodb`로 분리된 것과 같은 패턴). 이 의존성이 없으면 Flyway가 아무 로그·에러 없이 조용히 스킵되고, Hibernate `ddl-auto: validate`가 "missing table" 에러로만 뒤늦게 드러난다 — 원인 파악이 까다로우니 다음 Phase에서 새 Boot 4 전용 모듈을 추가할 때는 이 분리 패턴을 먼저 의심할 것.
- **Spring Modulith는 `common`의 하위 패키지마다 `package-info.java` + `@NamedInterface`가 필요**: `com.atcrew.common.id`(UuidV7Generator)처럼 새 공용 유틸 패키지를 추가하면, 기존 `common.response`/`common.security` 등과 동일하게 `@org.springframework.modulith.NamedInterface("id")` package-info를 함께 추가해야 `ModularStructureTests`가 통과한다.

## 12. P3에서 발견한 함정 (다음 Phase 참고용)

- **단방향 `@OneToMany` + `@JoinColumn`은 자식 테이블의 FK 컬럼이 `NOT NULL`이면 깨진다**: Hibernate는 단방향 컬렉션의 신규 원소를 "FK 없이 INSERT → UPDATE로 FK 채움" 2단계로 처리하는데, 1단계 INSERT 시점엔 FK 컬럼이 비어 있어 `NOT NULL` 제약과 즉시 충돌한다(`Field 'member_id' doesn't have a default value`). `member_careers`처럼 자식이 부모를 필요로 하는 FK가 `NOT NULL`인 스키마에서는 **양방향 매핑(자식이 `@ManyToOne` 역참조 + 부모가 `mappedBy`)으로 가야 단일 INSERT로 끝난다** — CareerEntry에 `Member` 필드를 추가해 해결.
- **Mongo는 항상 문서 전체를 즉시 로드하지만 JPA `@OneToMany`/`@ElementCollection`은 기본이 지연 로딩이다**: 트랜잭션 없이 호출되는 단순 조회 메서드(`findByHandle`, `findById` 등)에서 세션 종료 후 컬렉션에 접근하면 `LazyInitializationException`이 난다. Mongo의 "문서 하나 = 항상 전체 필드 로드" 시맨틱을 그대로 유지하려면, 읽기 메서드마다 `@Transactional(readOnly=true)`을 새로 붙이는 대신 **해당 컬렉션들을 `fetch = FetchType.EAGER`로 선언**하는 편이 기존 서비스 계층 변경을 최소화한다(Member의 careers/activityFields/activeRegions/teamExperiences 전부 EAGER로 전환).
- **어설션 컬럼 타입은 실제 JPA 매핑 타입과 반드시 맞춰야 한다**: `experienceRank`가 Java `int`인데 V1에 `TINYINT`로 잘못 선언해 Hibernate `ddl-auto: validate`가 "wrong column type" 에러로 즉시 잡아냈다 — Flyway 마이그레이션은 이미 적용된 버전을 고치지 않고 새 버전(V2)으로 수정.
- **assigned-ID + `saveAndFlush` 없이는 unique 제약 위반이 재시도 로직 안에서 안 잡힌다**: `Persistable`로 신규 엔티티는 `persist()`를 타지만, Hibernate는 실제 INSERT를 트랜잭션 커밋 시점까지 지연시킨다. `register()`의 handle 충돌 재시도 `try/catch(DuplicateKeyException)`처럼 **같은 메서드 안에서 동기적으로 제약 위반을 잡아야 하는 곳은 `save()` 대신 `saveAndFlush()`를 써야** 즉시 flush되어 예외가 그 자리에서 터진다.
