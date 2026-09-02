#!/usr/bin/env bash
# 부하 테스트용 더미 데이터 시딩 — 대상 호스트에서 실행한다.
# 설계: docs/operations/baseline/README.md
#
# 왜 필요한가: 2026-09-02 기준선 측정은 실데이터 85행 상태에서 나왔다. 모든 조회가 사실상
# 빈 테이블 스캔이라 그때의 한계 처리량(약 950 RPS)은 **상한**일 뿐 서비스 능력이 아니다.
# 실사용 규모의 행 수를 넣고 다시 재야 비교 가능한 수치가 된다.
#
# 실행(서버에 파일을 남기지 않는다):
#   ssh -i ~/.ssh/<키페어>.pem ec2-user@<호스트> 'bash -s' < scripts/baseline/seed-loadtest-data.sh
#   ssh ... 'bash -s' < scripts/baseline/seed-loadtest-data.sh -- --clean     # 정리
#
# 주의: 여기 쓰는 enum 문자열은 반드시 src/main/java의 enum 상수와 정확히 일치해야 한다.
# DB 컬럼이 varchar라 틀린 값도 INSERT는 통과하고, 나중에 조회할 때 500(No enum constant)으로만
# 드러난다. 실제로 첫 시도에서 AgeRating.R(정답 R18), ImageLayoutType.GRID(정답 VERTICAL_SCROLL),
# RecruitImageProcessingStatus.DONE(정답 READY), Language.ko(정답 KO) 네 개가 이 방식으로 깨졌다.
#
# 안전장치
# - **프로덕션에서 실행하지 않는다.** staging 전용이다.
# - 모든 행의 PK에 `loadtest-` 접두사를 붙인다. 정리는 그 접두사만 지우면 되고, 기존 데이터를
#   건드릴 수 없다. 실행 전 원본 덤프를 따로 떠 두는 것을 권한다.
# - `--clean`은 접두사가 붙은 행만 지운다. TRUNCATE를 쓰지 않는다.

set -euo pipefail

MODE="seed"
[ "${1:-}" = "--clean" ] && MODE="clean"
[ "${2:-}" = "--clean" ] && MODE="clean"

MEMBERS="${MEMBERS:-1000}"
ARTWORKS="${ARTWORKS:-10000}"
JOB_POSTINGS="${JOB_POSTINGS:-1000}"
ENV_FILE="${ENV_FILE:-$HOME/at-crew-backend/deploy/.env}"

PW="$(sed -n 's/^MARIADB_ROOT_PASSWORD=//p' "$ENV_FILE" | tail -1 | tr -d '"')"
C="$(docker ps --filter 'label=com.docker.compose.service=mariadb' --format '{{.Names}}' | head -1)"
[ -n "$C" ] || { echo "mariadb 컨테이너를 찾지 못했다" >&2; exit 1; }
sql() { docker exec -i -e MYSQL_PWD="$PW" "$C" mariadb -uroot -N -B atcrew; }

counts() {
  sql <<'Q'
SELECT CONCAT('members=', (SELECT COUNT(*) FROM members),
              ' artworks=', (SELECT COUNT(*) FROM artworks),
              ' artwork_images=', (SELECT COUNT(*) FROM artwork_images),
              ' job_postings=', (SELECT COUNT(*) FROM job_postings));
Q
}

echo "[seed] 실행 전: $(counts)"

if [ "$MODE" = "clean" ]; then
  # 접두사가 붙은 행만 지운다. 자식 행(이미지)을 먼저 지운다.
  sql <<'Q'
DELETE FROM artwork_images WHERE artwork_id LIKE 'loadtest-a-%';
DELETE FROM artworks      WHERE id         LIKE 'loadtest-a-%';
DELETE FROM job_postings  WHERE id         LIKE 'loadtest-j-%';
DELETE FROM members       WHERE id         LIKE 'loadtest-m-%';
Q
  echo "[seed] 정리 완료: $(counts)"
  exit 0
fi

START=$(date +%s)
# 재귀 CTE 기본 상한이 낮은 환경이 있어 세션 단위로 올린다.
sql <<Q
SET SESSION max_recursive_iterations = 1000000;
SET SESSION sql_mode = '';

INSERT INTO members
  (id, login_email, auth_provider, handle, name, email_verified, timezone, country_code,
   primary_language, employment_status, experience_rank, is_active, created_at, updated_at)
WITH RECURSIVE seq AS (SELECT 1 AS n UNION ALL SELECT n+1 FROM seq WHERE n < $MEMBERS)
SELECT CONCAT('loadtest-m-', LPAD(n,6,'0')),
       CONCAT('loadtest', n, '@example.invalid'), 'EMAIL',
       CONCAT('loadtest_', LPAD(n,6,'0')),
       CONCAT('부하테스트작가', n),
       1, 'Asia/Seoul', 'KR', 'KO',
       ELT(1+(n%4), 'PREPARING','AVAILABLE','NEGOTIABLE','CLOSED'),
       n%10, 1,
       NOW(6) - INTERVAL n MINUTE, NOW(6) - INTERVAL n MINUTE
FROM seq;

INSERT INTO artworks
  (id, author_id, title, description, representative_image_index, thumbnail_key, image_layout_type,
   artwork_field, creative_type, cut_count, age_rating, view_count, bookmark_count,
   visibility, portfolio_included, status, created_at, updated_at)
WITH RECURSIVE seq AS (SELECT 1 AS n UNION ALL SELECT n+1 FROM seq WHERE n < $ARTWORKS)
SELECT CONCAT('loadtest-a-', LPAD(n,6,'0')),
       CONCAT('loadtest-m-', LPAD(1+(n%$MEMBERS),6,'0')),
       CONCAT('부하테스트 작품 ', n, ' 일러스트 캐릭터 디자인 배경'),
       REPEAT(CONCAT('작품 설명 문단 ', n, '. '), 20),
       0, CONCAT('loadtest/thumb/', n, '.webp'),
       ELT(1+(n%2), 'VERTICAL_SCROLL','HORIZONTAL_SWIPE'),
       ELT(1+(n%2), 'ILLUSTRATION','PRINT_COMIC'),
       ELT(1+(n%5), 'ORIGINAL','SECONDARY','FAN_ART','OC','COMMISSION'),
       1+(n%12),
       -- 10%만 성인 등급으로 둔다. 전부 ALL이면 성인 필터 분기가 아예 안 타고,
       -- 전부 R이면 비로그인 피드가 비어서 둘 다 측정이 무의미해진다.
       IF(n%10=0, 'R18', 'ALL'),
       n%5000, n%300, 'PUBLIC', 1, 'READY',
       NOW(6) - INTERVAL n MINUTE, NOW(6) - INTERVAL n MINUTE
FROM seq;

INSERT INTO artwork_images
  (artwork_id, ordinal, original_key, thumb_key, processing_status)
WITH RECURSIVE seq AS (SELECT 1 AS n UNION ALL SELECT n+1 FROM seq WHERE n < $ARTWORKS)
SELECT CONCAT('loadtest-a-', LPAD(n,6,'0')), 0,
       CONCAT('loadtest/orig/', n, '.png'), CONCAT('loadtest/thumb/', n, '.webp'), 'DONE'
FROM seq;

INSERT INTO job_postings
  (id, author_member_id, title, company_name, industry, contact, work_scope, deadline,
   recruit_count, employment_type, status, image_processing_status, created_at, updated_at)
WITH RECURSIVE seq AS (SELECT 1 AS n UNION ALL SELECT n+1 FROM seq WHERE n < $JOB_POSTINGS)
SELECT CONCAT('loadtest-j-', LPAD(n,6,'0')),
       CONCAT('loadtest-m-', LPAD(1+(n%$MEMBERS),6,'0')),
       CONCAT('부하테스트 구인글 ', n, ' 일러스트레이터 모집'),
       CONCAT('부하테스트컴퍼니', n), '게임', 'loadtest@example.invalid',
       CONCAT('캐릭터 일러스트 ', 1+(n%10), '컷'),
       CURDATE() + INTERVAL (n%60) DAY,
       1+(n%5), 'FULL_TIME', 'PUBLISHED', 'READY',
       NOW(6) - INTERVAL n MINUTE, NOW(6) - INTERVAL n MINUTE
FROM seq;
Q

echo "[seed] 실행 후: $(counts)"
echo "[seed] 소요: $(( $(date +%s) - START ))초"
echo "[seed] DB 크기: $(sql <<<"SELECT CONCAT(ROUND(SUM(DATA_LENGTH+INDEX_LENGTH)/1024/1024,1),' MB') FROM information_schema.TABLES WHERE TABLE_SCHEMA='atcrew'")"
echo
echo "다음 단계: Elasticsearch 재색인이 필요하다(검색 엔드포인트 측정용)."
echo "  curl -sS -X POST localhost:8080/internal/search/reindex -H \"X-Internal-Secret: \$(sed -n 's/^SEARCH_INTERNAL_SECRET=//p' $ENV_FILE | tail -1)\""
