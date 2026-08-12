package com.atcrew.portfolio;

import com.atcrew.artwork.AgeRating;
import com.atcrew.artwork.ArtworkField;
import com.atcrew.artwork.Visibility;

import java.time.Instant;

/**
 * 포트폴리오에 담긴 작품 1건의 카드 표시 정보 (docs/design/portfolio-module-design.md §4).
 *
 * <p>최신 반영형(LIVE)·작가 페이지는 원본 작품에서 조회 시점에 채운다.
 */
public record PortfolioArtworkCardInfo(
        String artworkId,          // 원본 작품 ID
        String title,              // 작품 제목
        String thumbKey,           // 카드 썸네일 R2 키 — 사용자 지정 썸네일 우선, 없으면 대표 이미지 썸네일
        String thumbAdultKey,      // 성인 블러 썸네일 R2 키 — 사용자 지정 썸네일을 쓰는 경우 null
        AgeRating ageRating,       // 연령 등급
        ArtworkField artworkField, // 작품 분야
        Visibility visibility,     // 원본 작품의 공개 범위
        Instant createdAt          // 원본 작품 등록 시각 — 포트폴리오 내 정렬(업로드순) 기준
) {
}
