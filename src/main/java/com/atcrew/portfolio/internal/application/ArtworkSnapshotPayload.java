package com.atcrew.portfolio.internal.application;

import com.atcrew.artwork.ArtworkImageInfo;
import com.atcrew.artwork.ArtworkRole;
import com.atcrew.artwork.MaterialInfo;

import java.util.List;

/**
 * 고정형 스냅샷의 상세 본문 페이로드 (docs/design/portfolio-module-design.md §2.3).
 *
 * <p>하이브리드 저장의 JSON 쪽 — 카드·커버 렌더에 쓰는 필드는 {@code portfolio_item_snapshots} 컬럼에
 * 두고, 상세 화면에서만 필요한 본문을 이 구조로 직렬화해 {@code payload_json} 1컬럼에 담는다.
 * 직렬화 전용 내부 타입이라 모듈 밖으로 노출하지 않는다.
 */
record ArtworkSnapshotPayload(
        List<ArtworkImageInfo> images,   // 작품 이미지 목록 — R2 키는 원본을 그대로 참조한다(§5.6)
        List<MaterialInfo> materials,    // 첨부 자료
        List<String> tags,               // 태그
        List<String> tools,              // 사용 툴
        List<ArtworkRole> roles,         // 담당 역할
        List<String> genres,             // 장르
        List<String> videoLinks,         // 영상 링크
        String description,              // 작품 설명
        int representativeImageIndex     // 대표 이미지 인덱스 — images 기준
) {
}
