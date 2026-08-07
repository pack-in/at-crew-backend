-- 태그 정본 목록 정규화 (artwork) — docs/design/search-module-design.md §1.4/§9-2
--
-- Artwork.genres(Set<String>) → Set<Genre>, Material.targets(List<String>) → List<MaterialTarget>로
-- 타입을 고정하면서, 자유 텍스트로 쌓인 기존 값이 @Enumerated(STRING) 역매핑을 깨뜨리지 않도록 정리한다.
-- 정본 목록은 전부 영문 상수 이름이라 기존 한글 자유 텍스트 중 살아남는 값은 없다(실사용자 데이터 없음).

-- ------------------------------------------------------------
-- artwork_genres — 정본(Genre) 밖의 값 제거 후 artwork_roles와 동일한 폭으로 정렬
-- ------------------------------------------------------------
DELETE FROM artwork_genres
WHERE value NOT IN (
    'BL', 'HL', 'GL', 'FANTASY', 'ROMANCE_FANTASY', 'ACTION', 'MARTIAL_ARTS', 'GORE', 'HORROR',
    'NOIR', 'CRIME', 'THRILLER', 'MYSTERY', 'SUPERPOWER', 'SF', 'COMEDY', 'HEALING',
    'SLICE_OF_LIFE', 'DRAMA', 'SCHOOL', 'GAME', 'ORIENTAL_SETTING', 'WESTERN_SETTING',
    'PERIOD_HISTORY', 'MEDIEVAL', 'MODERN', 'EROTIC', 'CREATURE', 'YOUTH'
);

ALTER TABLE artwork_genres MODIFY COLUMN value VARCHAR(30) NOT NULL;

-- ------------------------------------------------------------
-- artwork_materials.targets — JSON 배열 안의 자유 텍스트는 개별 필터링이 불가능해 통째로 비운다.
-- (JSON 컬럼이라 컬럼 타입 변경은 필요 없다 — 값 형태만 enum 이름 배열로 바뀐다.)
-- ------------------------------------------------------------
UPDATE artwork_materials
SET targets = JSON_ARRAY()
WHERE targets IS NOT NULL AND JSON_LENGTH(targets) > 0;
