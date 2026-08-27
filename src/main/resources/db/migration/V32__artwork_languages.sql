-- 작품별 게시물 작성·노출 언어(작품 설정 6단계, 업로드-R30)를 추가한다.
-- 스타터는 주 사용 언어 1개, 프로는 다중 선택이라 컬럼이 아니라 연결 테이블이어야 한다(REQ-020).
--
-- 기존 작품은 행을 만들지 않는다. 언어를 고른 적이 없는 작품에 임의 값을 채우면 그 작품이 특정
-- 언어 세그먼트에만 보이게 되므로, 언어 정보가 없는 작품은 조회 필터에서 "항상 노출"로 폴백한다.

CREATE TABLE artwork_languages (
    artwork_id VARCHAR(36) CHARACTER SET latin1 COLLATE latin1_bin NOT NULL,
    value      VARCHAR(10) NOT NULL,
    PRIMARY KEY (artwork_id, value),
    -- 언어로 먼저 좁힌 뒤 작품 ID로 조인하는 커뮤니티 피드 필터용 역방향 인덱스
    KEY idx_al_value (value, artwork_id)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
