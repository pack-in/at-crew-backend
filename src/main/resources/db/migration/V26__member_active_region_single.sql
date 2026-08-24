-- 작가 활동 지역을 복수(연결 테이블) → 단일(컬럼)로 전환하고 값 집합을 도 단위 10종으로 교체한다.
-- 근거: 피그마 UI개편_마이페이지_작가_수정페이지 4971:25431("활동 지역"에는 복수 선택 라벨이 없다),
--       기획서 마이페이지_작가-R23("단일 칩: … 활동 지역(10)").
--
-- 값 집합 변경: 기존 SEOUL·GYEONGGI·DAEJEON·DAEGU·GWANGJU·BUSAN·OTHER (광역시 중심 7종)
--            → SEOUL·GYEONGGI·GANGWON·CHUNGBUK·CHUNGNAM·JEONBUK·JEONNAM·GYEONGBUK·GYEONGNAM·JEJU (도 단위 10종)
--
-- 이관 정책: 정본에 그대로 남는 SEOUL·GYEONGGI만 옮긴다. 광역시(대전·대구·광주·부산)와 OTHER는
--          정본에 1:1 대응값이 없다. 이를 포함 도(예: 부산→경상남도)로 자동 치환하면 사용자가 직접
--          고른 활동 지역을 사실과 다르게 바꿔 쓰는 셈이므로, 미입력(NULL)으로 비우고 사용자가
--          다시 고르게 한다.
-- 복수로 저장돼 있던 회원은 enum 선언 순서상 가장 앞선 값 하나만 남는다 — 단일 선택으로 바뀌는 이상
-- 손실은 불가피하며, 결정적(deterministic) 규칙을 택한다.

ALTER TABLE members ADD COLUMN active_region VARCHAR(30) NULL AFTER experience_rank;

UPDATE members m
SET m.active_region = (
    SELECT r.value
    FROM member_active_regions r
    WHERE r.member_id = m.id
      AND r.value IN ('SEOUL', 'GYEONGGI')
    ORDER BY FIELD(r.value, 'SEOUL', 'GYEONGGI')
    LIMIT 1
);

DROP TABLE member_active_regions;
