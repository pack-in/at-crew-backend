-- 작품 열람 집계 재설계 + "이번 주 가장 핫한 작품" — 홈-R03·홈-R14 (plans/260922-hot-artworks).
--
-- V34의 "dedup 없는 단순 증가" 정책을 폐기한다. 이제 작품 상세 GET은 조회수를 올리지 않고, 브라우저가
-- 호출하는 POST /api/artworks/{id}/views만 집계한다. 동일 열람자(회원 ID 또는 익명 UUID)의 24시간 이내
-- 반복 열람은 최초 1회만 센다(V29 member_profile_views와 같은 판정). 바꾼 이유는 둘이다.
--   1. 홈-R14가 핫 작품 순위에 "최근 7일 기간 조회수(24시간 dedup)"를 요구하고, 누적 view_count 사용을 금지한다
--   2. FE 상세 페이지가 SSR에서 토큰 없이 GET을 호출해 열람자를 식별할 수 없었고, 편집 화면·포트폴리오의
--      같은 GET 호출까지 조회수에 섞였다 — dedup도 본인 제외도 사실상 작동하지 않았다
--
-- 회원이 로그인 전에 남긴 익명 기록과 로그인 후 기록은 합치지 않는다 — viewer_type이 키에 포함된다.
-- 외래 키는 두지 않는다(V29와 동일). 이벤트 INSERT가 artworks 행에 S 락을 잡은 뒤 같은 트랜잭션의
-- view_count +1이 X 락을 요구하면, 같은 작품을 동시에 여는 두 요청이 서로를 기다리며 교착된다.

-- 24시간 dedup 판정용 — (작품, 열람자)당 한 행만 두고 마지막으로 "집계된" 시각을 덮어쓴다.
-- 24시간 안의 반복 열람은 이 시각을 갱신하지 않는다(갱신하면 매일 들르는 사람이 영영 다시 집계되지 않는다).
CREATE TABLE artwork_view_dedup (
    artwork_id      VARCHAR(36) CHARACTER SET latin1 COLLATE latin1_bin NOT NULL,
    viewer_type     VARCHAR(20) CHARACTER SET latin1 COLLATE latin1_bin NOT NULL,
    viewer_key      VARCHAR(36) CHARACTER SET latin1 COLLATE latin1_bin NOT NULL,
    last_counted_at DATETIME(6) NOT NULL,
    PRIMARY KEY (artwork_id, viewer_type, viewer_key),
    -- 탈퇴 회원 행 삭제(열람자 기준)와 익명 1년 경과 행 삭제(시각 기준)가 PK 순서로는 전체 스캔이 된다.
    -- 전체 스캔 DELETE는 REPEATABLE READ에서 훑은 행 전부에 락을 걸어 그동안 열람 기록이 멈춘다.
    KEY idx_avd_viewer (viewer_type, viewer_key),
    KEY idx_avd_retention (viewer_type, last_counted_at)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- 유효 열람(FIRST·REVISIT) 원천 기록 — 기간 조회수 계산의 유일한 근거다. 24시간 안의 반복 열람은 남기지 않는다.
-- referrer·기기·국가 같은 마케팅 속성은 두지 않는다(PostHog 몫).
-- viewer_key는 탈퇴 회원 비식별화 때 NULL이 된다 — 행은 남겨 기간 조회수·누적 조회수가 바뀌지 않게 한다.
CREATE TABLE artwork_view_events (
    id          VARCHAR(36) CHARACTER SET latin1 COLLATE latin1_bin NOT NULL,
    artwork_id  VARCHAR(36) CHARACTER SET latin1 COLLATE latin1_bin NOT NULL,
    viewer_type VARCHAR(20) CHARACTER SET latin1 COLLATE latin1_bin NOT NULL,
    viewer_key  VARCHAR(36) CHARACTER SET latin1 COLLATE latin1_bin NULL,
    view_kind   VARCHAR(20) CHARACTER SET latin1 COLLATE latin1_bin NOT NULL,
    viewed_at   DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    -- 매시간 배치의 "최근 168시간 GROUP BY artwork_id"와 일별 보관 배치의 "1년 경과분" 범위 조회용.
    KEY idx_avev_viewed_at (viewed_at, artwork_id),
    -- 탈퇴 회원 비식별화(viewer_type = 'MEMBER' AND viewer_key = ?)용 — 없으면 탈퇴마다 전체 스캔 UPDATE가 된다.
    KEY idx_avev_viewer (viewer_type, viewer_key)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- 핫 작품 순위용 기간 조회수 — 매시 정각 배치가 통째로 비우고 최근 168시간 이벤트로 다시 채운다.
-- 기간 조회수가 0인 작품은 행이 없다. 공개·언어·성인 필터는 요청 시 artworks 쪽에서 건다(뷰어마다 다르다).
CREATE TABLE artwork_hot_scores (
    artwork_id   VARCHAR(36) CHARACTER SET latin1 COLLATE latin1_bin NOT NULL,
    window_views BIGINT NOT NULL,
    computed_at  DATETIME(6) NOT NULL,
    PRIMARY KEY (artwork_id)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- 1년이 지난 익명 열람 이벤트의 일별 합계 — 홈-R14 "익명 식별자 기록은 1년 후 집계 통계만 보관".
-- 원본 이벤트는 합산과 같은 트랜잭션에서 지운다. 날짜는 UTC 기준(viewed_at이 UTC로 저장된다).
CREATE TABLE artwork_view_daily_stats (
    artwork_id  VARCHAR(36) CHARACTER SET latin1 COLLATE latin1_bin NOT NULL,
    stat_date   DATE NOT NULL,
    viewer_type VARCHAR(20) CHARACTER SET latin1 COLLATE latin1_bin NOT NULL,
    view_kind   VARCHAR(20) CHARACTER SET latin1 COLLATE latin1_bin NOT NULL,
    view_count  BIGINT NOT NULL,
    PRIMARY KEY (artwork_id, stat_date, viewer_type, view_kind)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- 기존 조회수는 dedup 없이 쌓였고 작가 본인의 편집 화면 열람까지 섞여 있어 신뢰할 수 없다 — 0부터 다시 센다.
-- 원천 이력이 없으므로 V34의 북마크 수처럼 복원할 방법도 없다.
UPDATE artworks SET view_count = 0;
