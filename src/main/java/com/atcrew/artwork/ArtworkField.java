package com.atcrew.artwork;

public enum ArtworkField {
    ILLUSTRATION, WEBTOON,
    // TODO: 작품 분야 값 집합이 피그마 화면마다 다르다 — 기획 확정 필요.
    //  - 홈 UI개편_홈(구 커뮤니티) 6107:24822: 전체/웹툰/일러스트/애니메이션/출판만화 (웹소설·기타 없음)
    //  - 검색 UI개편_검색 5752:29315: 일러스트/웹툰/애니메이션/웹소설/기타 (출판만화 없음)
    //  기획서(2026-07-28) 홈-R01·업로드-R06은 출판만화 쪽이며, 홈 프레임이 검색 프레임보다 최신이다.
    //  웹소설로 확정되면 이 enum과 member/company의 ActivityField.PRINT_COMIC을 함께 교체하고
    //  데이터 마이그레이션(artworks·portfolio_item_snapshots·ES 재색인)이 필요하다.
    PRINT_COMIC, ANIMATION, ETC
}
