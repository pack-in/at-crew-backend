# 사용자 아이디(handle) 운영 변경 절차

운영자가 회원의 사용자 아이디(handle)를 **강제로 바꿔야 할 때**(사칭·부적절한 아이디 신고, 회원 요청 중
정책상 허용되는 건 등)의 절차와, handle 기능(V52·V53)이 포함된 배포의 **배포 절차**를 적는다.
설계 배경은 `docs/design/member-handle-design.md` §1.6.

관리자 API는 없다(범위 밖). 변경은 DB 직접 SQL로 수행하고, §4의 재색인 호출을 반드시 함께 실행한다.

> **원장 행(`member_handles`)은 절대 삭제·수정하지 않는다.** 원장은 "이 handle은 이 회원 소유"라는
> 영구 기록이고, 옛 주소 리다이렉트와 재할당 금지가 모두 이 행에 기대고 있다. 행을 지우거나
> `member_id`를 바꾸면 (1) 옛 링크가 404로 바뀌고 (2) 다른 사람이 그 handle을 가져가 사칭할 수 있게 된다.
> 변경은 항상 **새 행 INSERT + `members.handle` UPDATE**로만 한다. 옛 행은 그대로 둔다.

> **탈퇴 회원의 handle을 풀어 달라는 요청은 받지 않는다.** 한 번 쓰인 handle은 탈퇴 후에도 그 회원 소유로
> 영구히 남는 정책이다(설계 §0 결정 5, 사칭 방지). 본인이 재가입해도 예전 handle은 쓸 수 없다
> (`DUPLICATE_HANDLE`). 요청이 오면 정책 사항이라 불가하다고 답하고 이 문서의 SQL로 풀지 않는다.

## 0. 접속

접속은 SSM이다(SSH 불가). 접속 방법과 `deploy/ssm-run.sh` 사용법은 `docs/operations/incident-runbook.md` §0,
인스턴스 ID는 저장소 Secret `APP_INSTANCE_ID`를 참조한다(여기에 값을 적지 않는다).

```bash
aws ssm start-session --target <인스턴스 ID>      # session-manager-plugin 필요
cd ~/at-crew-backend/deploy
PW=$(sed -n 's/^MARIADB_ROOT_PASSWORD=//p' .env | tail -1)
docker exec -it -e MYSQL_PWD="$PW" deploy-mariadb-1 mariadb -u root atcrew
```

## 1. 대상 확인

회원 한 명이 정확히 특정되는지, 활성 회원인지 확인한다. 탈퇴 회원(`is_active = 0`)은 변경하지 않는다.

```sql
SELECT id, handle, is_active, handle_changed_at
  FROM members
 WHERE id = :memberId;          -- 또는 login_email = :email

-- 이 회원이 쓴 적 있는 handle 전체(현재 + 옛 handle)
SELECT handle, created_at FROM member_handles WHERE member_id = :memberId ORDER BY created_at;
```

`members.handle`이 위 원장 목록에 있어야 한다(활성 회원의 불변식). 없으면 변경하지 말고 먼저 원인을 확인한다.

## 2. 새 handle이 원장에 없는지 확인

대소문자는 구분하지 않는다(`member_handles.handle`은 대소문자 무관 collation이라 `Foo`와 `foo`가 같은 행).
**0행이어야 한다.** 한 행이라도 나오면 이미 누군가(탈퇴 회원 포함)의 소유라 쓸 수 없다. 예외는 그 행의
`member_id`가 **대상 회원 본인**인 경우(본인 옛 handle 복귀·대소문자만 변경)뿐이며, 이때는 §3 대신 §3.1을 쓴다.

```sql
SELECT handle, member_id FROM member_handles WHERE handle = :newHandle;
```

새 handle은 앱 규칙을 지킨다 — 영문·숫자·밑줄(`_`) 4~14자, 연속 밑줄(`__`) 금지, 예약어 금지
(`HandleValidator`, 설계 §1.2). SQL은 형식을 검사하지 않으므로 직접 확인한다. 예약어를 심으면 그 이름의 FE
최상위 라우트와 충돌한다.

## 3. 한 트랜잭션으로 변경

원장 INSERT와 `members.handle` UPDATE를 **같은 트랜잭션**에서 한다. 하나만 반영되면 "활성 회원의 handle은
반드시 원장에 존재한다"는 불변식이 깨진다. `handle_changed_at`은 **건드리지 않는다** — 운영자 변경은 사용자
30일 변경 제한에 산정하지 않는다(회원은 변경 직후에도 스스로 바꿀 수 있다).

`:operator`에는 실행한 사람을 `ops:<담당자>` 형식으로 넣는다(`moderation-block.md` §2와 같은 규칙).

```sql
START TRANSACTION;

-- 대상 행을 잠그고 현재 값을 다시 확인한다
SELECT id, handle FROM members WHERE id = :memberId AND is_active = 1 FOR UPDATE;

INSERT INTO member_handles (handle, member_id, created_at)
VALUES (:newHandle, :memberId, UTC_TIMESTAMP(6));

UPDATE members
   SET handle = :newHandle,
       version = version + 1,                 -- 앱의 낙관적 락(@Version)과 충돌하지 않도록 올린다
       updated_at = UTC_TIMESTAMP(6),
       last_modified_by = :operator
 WHERE id = :memberId
   AND is_active = 1;

-- 영향 행이 1인지, 의도한 상태인지 확인한 뒤에만 커밋한다. 이상하면 ROLLBACK.
SELECT m.id, m.handle, h.member_id
  FROM members m JOIN member_handles h ON h.handle = m.handle
 WHERE m.id = :memberId;

COMMIT;
```

INSERT가 PK 중복으로 실패하면(§2를 건너뛴 경우) 트랜잭션을 `ROLLBACK`하고 §2부터 다시 한다. `members.handle`의
UNIQUE(`uk_members_handle`)가 이중 방어로 중복 UPDATE도 막는다.

### 3.1 본인 옛 handle 복귀·대소문자만 변경

새 handle이 원장에 **이미 대상 회원 소유로** 있으면(예: `foo` → `bar`로 바꿨다가 `foo`로 복귀, 또는 `foo` → `Foo`)
원장 행을 새로 넣지 않고 `members.handle`만 바꾼다. 앱의 `PATCH /api/members/me/handle`과 같은 처리다(설계 §1.3 6단계).
원장 PK는 대소문자 무관이라 `Foo`를 INSERT하면 `foo` 행과 충돌한다 — §3의 INSERT를 그대로 쓰면 실패한다.

```sql
START TRANSACTION;

SELECT id, handle FROM members WHERE id = :memberId AND is_active = 1 FOR UPDATE;

-- 원장 행을 잠그고 소유자를 확인한다. 결과로 분기한다:
--   1행이고 member_id = :memberId  → 아래 UPDATE만 실행한다(INSERT 생략)
--   1행이고 member_id ≠ :memberId  → 다른 회원(탈퇴 포함) 소유다. 즉시 ROLLBACK하고 중단한다
--   0행                            → 이 절이 아니다. ROLLBACK하고 §3으로 간다
SELECT handle, member_id FROM member_handles WHERE handle = :newHandle FOR UPDATE;

UPDATE members
   SET handle = :newHandle,               -- 표시할 대소문자 그대로 넣는다(원장 행의 대소문자는 바꾸지 않는다)
       version = version + 1,
       updated_at = UTC_TIMESTAMP(6),
       last_modified_by = :operator
 WHERE id = :memberId
   AND is_active = 1
   AND EXISTS (SELECT 1 FROM member_handles h WHERE h.handle = :newHandle AND h.member_id = :memberId);

-- 영향 행이 1인지 확인한다. 0이면 위 분기 판단이 틀린 것이니 ROLLBACK.
SELECT m.id, m.handle, h.handle AS ledger_handle, h.member_id
  FROM members m JOIN member_handles h ON h.handle = m.handle
 WHERE m.id = :memberId;

COMMIT;
```

`handle_changed_at`은 §3과 같이 건드리지 않는다. 이후 §4의 검증과 재색인을 똑같이 실행한다. 대소문자만 바꾼 경우
옛 값(`foo`)과 새 값(`Foo`)은 같은 원장 행이라 §4 검증 쿼리에 행이 늘지 않는 것이 정상이다.

## 4. 검증과 재색인

```sql
-- 현재 handle과 원장이 일치하는지
SELECT m.handle AS current_handle, h.handle AS ledger_handle, h.member_id
  FROM members m JOIN member_handles h ON h.member_id = m.id
 WHERE m.id = :memberId
 ORDER BY h.created_at;
-- 옛 handle 행이 그대로 남아 있고, current_handle과 같은 값의 행이 새로 생겼어야 한다.
```

앱 동작 확인(서버 안에서): 새 handle과 옛 handle 모두 200이고, 옛 handle 응답의 `handle`이 새 값이어야 한다.

```bash
curl -4 -s http://127.0.0.1:8080/api/members/<새 handle>   | head -c 300
curl -4 -s http://127.0.0.1:8080/api/members/<옛 handle>   | head -c 300   # data.handle == 새 handle
```

**검색 재색인은 필수다.** DB 직접 UPDATE는 `MemberHandleChangedEvent`를 발행하지 않으므로 ES의 `authorHandle`이
옛 값으로 남아 검색 결과 카드 링크가 옛 주소로 나간다. 전체 재색인 API를 서버 안에서 호출한다
(`/internal/search/`는 nginx가 외부 접근을 막아 둔다 — `docs/operations/moderation-block.md` §3).

```bash
cd ~/at-crew-backend/deploy
SECRET=$(sed -n 's/^SEARCH_INTERNAL_SECRET=//p' .env | tail -1)
curl -4 -s -o /dev/null -w '%{http_code}\n' -X POST http://127.0.0.1:8080/internal/search/reindex \
  -H "X-Internal-Secret: $SECRET"        # 204가 나오면 성공
unset SECRET
```

재색인은 작가 한 명이 아니라 작품 인덱스 전체를 다시 만든다. 여러 건을 바꿀 때는 모두 바꾼 뒤 1회만 호출한다.

## 5. 배포 절차 (V52·V53 포함 배포)

handle 기능은 Flyway 마이그레이션 두 개를 포함한다. 둘 다 **기존 데이터를 일괄 변경**하므로 아래 순서를 지킨다.
자동 배포(`.github/workflows/deploy.yml`)는 main 병합 시 바로 돌아 앱 기동 시점에 Flyway를 적용하므로,
**1·2단계는 main 병합 전에 끝낸다.**

| 마이그레이션 | 내용 |
|---|---|
| V52 | `member_handles` 원장 생성, 활성·탈퇴 회원 handle 백필, **규칙 위반 handle 일괄 변경**(`{이름부 ≤8자}_{숫자 5자리}`), `members.handle_changed_at` 추가 |
| V53 | `artworks.public_code`·`portfolios.public_code` 추가·백필, UNIQUE 생성 |

1. **배포 직전 DB 스냅샷(백업) 확보.** V52는 회원 handle을 되돌릴 수 없게 바꾼다(옛 값은 원장에 남지만 `members`는
   덮어쓴다). 수동 백업을 한 번 돌리고 결과를 확인한다.
   ```bash
   sudo systemctl start atcrew-backup.service
   journalctl -u atcrew-backup.service -n 20      # 업로드 성공 로그 확인
   ```
   백업 파일 복원 방법은 `docs/operations/incident-runbook.md`의 DB 복원 절차를 따른다.
2. 적용될 마이그레이션이 V52·V53뿐인지, 이미 배포된 마이그레이션 파일이 수정되지 않았는지 확인한다
   (CI `guard-applied-migrations`가 막지만 병합 전에 한 번 더 본다). 병렬 브랜치와 버전 번호가 겹치지 않는지도
   원격 `origin/dev`·`origin/main` 기준으로 재확인한다.
3. main 병합 → 자동 배포 → 헬스체크 통과 확인. Flyway가 V52·V53을 적용하다 실패하면 앱이 기동하지 못한다.
4. **배포 직후 `POST /internal/search/reindex` 전체 재색인 호출 — 필수.**
   - ES 문서에 `publicCode` 매핑이 새로 생긴다. 재색인 전까지 기존 문서에는 `publicCode`가 없어 검색 결과 카드가
     `/{handle}/artwork/{code}` 링크를 만들 수 없다.
   - V52의 일괄 변경 handle은 이벤트를 내지 않으므로 ES의 `authorHandle`이 옛 값으로 남아 있다.
   호출 방법은 §4의 `curl` 블록과 같다(204 확인).
5. 스모크: 일반 회원 handle 변경 → 옛 주소 해석 → 검색 반영, 작품·포트폴리오 새 주소 조회
   (`plans/261015-member-handle/PLAN-AGENT.md` PA-15).
6. 일괄 변경된 회원 수를 확인해 둔다(고객 문의 대응용). V52 이전에는 회원당 원장 행이 1개였으므로 2개 이상인 회원이
   일괄 변경 대상이다(배포 후 사용자가 직접 바꾼 회원도 섞이므로 배포 직후에 센다).
   ```sql
   SELECT COUNT(*) FROM (SELECT member_id FROM member_handles GROUP BY member_id HAVING COUNT(*) > 1) t;
   ```

FE가 새 주소 체계와 308 리다이렉트를 반영하기 전에 BE가 먼저 나가도 옛 API는 그대로 동작한다
(`docs/design/member-handle-frontend-integration.md`). 가입 `handle`은 FE 가입 화면 배포 후 필수가 됐다(PA-14, 2026-10-21).

V51은 병렬 브랜치(`feat/og-image`, `V51__og_image_keys.sql`)가 쓰는 번호다. Flyway `outOfOrder`가 꺼져 있으므로
(기본값, `application.yml`에 설정 없음) **V51이 V52·V53보다 먼저 운영에 적용돼야 한다.** V52·V53이 먼저 나가면 이후
V51은 "현재 버전보다 낮은 미적용 마이그레이션"이 돼 검증에서 기동이 거부된다. 그 순서가 안 되면 og-image 쪽 번호를
V54 이상으로 옮긴다.

## 6. 마이그레이션 중간 실패 시 복구

증상: 배포 후 앱이 기동하지 못하고 로그에 `Migration V52__member_handle_ledger.sql failed`(또는 V53)가 남는다. 배포
워크플로는 마이그레이션이 있는 배포라 자동 롤백하지 않고 P1만 울린다(`incident-runbook.md` "배포 실패·롤백").

**MariaDB DDL은 자동 커밋된다.** Flyway가 스크립트를 트랜잭션으로 감싸도 `CREATE TABLE`·`ALTER TABLE`·`DROP TABLE`은
실행 직전에 그때까지의 작업을 커밋하므로, 실패 지점 이전의 DDL과 그 앞 DML은 남고 **마지막 DDL 이후의 DML만**
롤백된다. 그래서 "반쯤 적용된" 스키마가 남는다. 실패한 마이그레이션은 `flyway_schema_history`에 `success = 0`
행으로 남고, 이 행이 있는 한 Flyway 검증이 기동을 막는다.

### 6.1 무엇이 남았는지 확인

```sql
-- 실패 행 확인
SELECT installed_rank, version, description, success, installed_on
  FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 5;

-- V52가 남겼을 수 있는 객체
SELECT table_name FROM information_schema.tables
 WHERE table_schema = DATABASE() AND table_name IN ('member_handles', 'v52_handle_renames');
SELECT column_name FROM information_schema.columns
 WHERE table_schema = DATABASE() AND table_name = 'members' AND column_name = 'handle_changed_at';

-- V53이 남겼을 수 있는 객체
SELECT table_name, column_name, is_nullable FROM information_schema.columns
 WHERE table_schema = DATABASE() AND table_name IN ('artworks', 'portfolios') AND column_name = 'public_code';
SELECT table_name, index_name FROM information_schema.statistics
 WHERE table_schema = DATABASE() AND index_name IN ('uk_artworks_author_code', 'uk_pf_owner_code');
```

V52에서 `members.handle`을 덮어쓰는 UPDATE는 작업용 테이블 `v52_handle_renames`를 지우는 `DROP TABLE` 바로 앞의 DML이다.
**`v52_handle_renames`가 남아 있으면** 그 UPDATE는 실행 전이거나 롤백된 것이라 `members.handle`은 배포 전 그대로다
(설계된 실패 지점인 "재추첨 후에도 원장 PK 충돌"이 이 경우다). `v52_handle_renames`가 없는데 V52가 실패로 남아 있으면
`members.handle`이 이미 바뀌었을 수 있으므로 정리하지 말고 6.3 백업 복원으로 간다.
드물게 `DROP TABLE` 직전의 암시적 커밋 뒤 DROP 자체가 실패하면(메타데이터 락 대기 초과 등) UPDATE는 커밋되고 작업용 테이블도
남는다. 그래서 `v52_handle_renames`가 남아 있어도 정리 전에 그 테이블의 새 handle 값이 `members.handle`에 이미 들어갔는지
대조하고, 들어갔으면 6.3으로 간다.

### 6.2 정리 후 재기동

남은 객체를 지운다. 존재하는 것만 지우면 되도록 `IF EXISTS`를 쓴다. V52를 정리하면 V53은 실행되지 않았으므로
V53 정리는 필요 없다.

```sql
-- V52 실패: 원장·작업용 테이블·컬럼을 지운다(원장은 이 마이그레이션이 만든 것이라 운영 데이터가 아니다)
DROP TABLE IF EXISTS v52_handle_renames;
DROP TABLE IF EXISTS member_handles;
ALTER TABLE members DROP COLUMN IF EXISTS handle_changed_at;
DELETE FROM flyway_schema_history WHERE version = '52' AND success = 0;

-- V53 실패(V52는 success = 1): 코드 컬럼과 제약만 지운다. 코드는 이 마이그레이션이 만든 값이라 잃는 데이터가 없다
ALTER TABLE artworks   DROP INDEX IF EXISTS uk_artworks_author_code;
ALTER TABLE artworks   DROP COLUMN IF EXISTS public_code;
ALTER TABLE portfolios DROP INDEX IF EXISTS uk_pf_owner_code;
ALTER TABLE portfolios DROP COLUMN IF EXISTS public_code;
DELETE FROM flyway_schema_history WHERE version = '53' AND success = 0;
```

실패 행 `DELETE`는 `flyway repair`(실패 행 제거)와 같은 효과다. 서버에는 Flyway CLI가 없으므로 SQL로 한다. 실패 행만
지우고 `success = 1` 행은 절대 건드리지 않는다.

정리 후 선택지는 둘이다.

- **원인을 고쳐 재배포**: 실패 원인(로그의 SQL 오류)을 고친 커밋을 main에 올려 다시 배포한다. 같은 버전 번호를
  그대로 써도 된다 — 운영에 성공 기록이 없으므로 "적용된 마이그레이션 불변" 규칙에 걸리지 않는다. 다만 V53만 실패해
  V52가 성공 기록으로 남은 경우 **V52 파일은 고치지 않는다.**
- **직전 이미지로 롤백**: `incident-runbook.md` "배포 실패·롤백"의 수동 롤백 절차로 이전 SHA 이미지를 띄운다. 정리를
  끝낸 뒤라 구버전 앱의 Flyway 검증·`ddl-auto: validate`가 통과한다. V52가 성공으로 남아 있으면(V53만 실패) 구버전은
  모르는 성공 마이그레이션(future)을 무시하고 기동하며, 추가된 컬럼·테이블은 구버전 엔티티 검증에 영향이 없다.

재기동 후 `curl -4 -s http://127.0.0.1:8081/actuator/health/liveness`로 확인하고, V52·V53이 결국 적용됐다면 §5의
4단계(전체 재색인)부터 다시 진행한다.

### 6.3 백업 복원으로 갈 때

다음 중 하나면 정리 대신 §5 1단계에서 확보한 백업으로 복원한다(`incident-runbook.md` "DB 복원").

- 6.1에서 `members.handle`이 이미 바뀌었을 수 있다고 판단됨(`v52_handle_renames` 없음 + V52 실패 기록).
- 남은 객체가 위 목록과 다르거나, 무엇이 적용됐는지 확신할 수 없음.
- 정리 SQL이 실패함.

복원은 **백업 시점 이후의 쓰기를 잃는다.** 앱이 기동하지 못한 동안에는 쓰기가 없었지만, 백업과 배포 사이에 구버전 앱이
받은 쓰기(가입·작품 업로드 등)는 사라진다. 그래서 백업은 main 병합 직전에 뜨고, 6.1 판단이 서면 복원보다 정리를
우선한다.
