package com.atcrew.portfolio;

import com.atcrew.artwork.AgeRating;
import com.atcrew.artwork.ArtworkField;
import com.atcrew.artwork.ArtworkImageInfo;
import com.atcrew.artwork.ArtworkRole;
import com.atcrew.artwork.MaterialInfo;

import java.time.Instant;
import java.util.List;

/**
 * 고정형(SNAPSHOT) 포트폴리오 전용 작품 스냅샷 상세 (마이페이지_작가-R39·R42).
 *
 * <p>원본 작품 상세와 별개인 독립 자원이다 — 값은 전부 포트폴리오 생성 시점에 얼린 것이고, 원본의 수정·
 * 피드 공개 OFF·삭제에 영향받지 않는다. 열람 전용이라 원본 작품 ID·원본 이동 링크·북마크 관련 필드는
 * 담지 않는다(내부 연결값 {@code sourceArtworkId}를 제3자에게 노출하지 않기 위함).
 */
public record PortfolioSnapshotDetailInfo(
        String snapshotId,               // 스냅샷 공개 식별자 — 상세 URL의 식별자와 동일
        String title,                    // 생성 시점의 작품 제목
        List<ArtworkImageInfo> images,   // 생성 시점의 이미지 목록 (R2 키는 생성 당시 자산 버전)
        int representativeImageIndex,    // 대표 이미지 인덱스 — images 기준
        List<MaterialInfo> materials,    // 생성 시점의 첨부 자료
        List<String> tags,               // 태그
        List<String> tools,              // 사용 툴
        List<ArtworkRole> roles,         // 담당 역할
        List<String> genres,             // 장르
        List<String> videoLinks,         // 영상 링크
        String description,              // 작품 설명
        AgeRating ageRating,             // 연령 등급
        ArtworkField artworkField,       // 작품 분야
        Instant sourceCreatedAt,         // 원본 작품의 등록 시각 — 생성 시점에 복사한 값
        String ownerName                 // 작성자 이름 — 포트폴리오 생성 시점에 얼린 값
) {
}
