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
 *
 * <p>{@code payload_json}은 write-once라 필드가 늘어나면 구버전 스키마로 저장된 행이 그대로 남는다.
 * 그래서 모든 컴포넌트를 nullable로 둔다 — 기본형(int)을 쓰면 필드가 없는 구버전 JSON을 역직렬화할 때
 * Jackson이 {@code FAIL_ON_NULL_FOR_PRIMITIVES}로 실패해 옛 스냅샷 상세가 통째로 열리지 않는다.
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
        Integer representativeImageIndex // 대표 이미지 인덱스 — images 기준. 구버전 payload는 null일 수 있다
) {
}
