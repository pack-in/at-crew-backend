package com.atcrew.artwork;

import com.atcrew.member.Language;

import java.util.List;

/**
 * 작품 업로드 명령 (업로드-R09).
 *
 * <p>공개 상태를 직접 받지 않는다 — 사용자는 "노출 위치"(작품 피드 공개 여부 × 담을 포트폴리오)만 고르고
 * 서버가 그 조합으로 공개 상태를 계산한다.
 */
public record UploadArtworkCommand(
        List<String> imageKeys,          // R2 원본 이미지 키 목록
        int representativeImageIndex,    // 대표 이미지 인덱스 — imageKeys 기준
        String thumbnailKey,             // 사용자 지정 썸네일 R2 키
        ImageLayoutType imageLayoutType, // 이미지 배치 방식
        String title,                    // 작품 제목
        String description,              // 작품 설명
        ArtworkField artworkField,       // 작품 분야
        CreativeType creativeType,       // 창작 유형
        List<ArtworkRole> roles,         // 담당 역할
        List<Genre> genres,              // 장르 (정본 enum)
        List<String> tags,               // 태그
        AgeRating ageRating,             // 연령 등급
        List<Language> languages,        // 게시물 작성·노출 언어 (업로드-R30) — 스타터 1개, 프로 다중
        boolean publishToFeed,           // 앳크루 작품 피드에 공개할지 — 서버가 이 값으로 공개 상태를 계산한다
        List<String> portfolioIds,       // 담을 라이브 포트폴리오 ID 목록 — null/빈 목록이면 편입 없음
        List<String> tools,              // 사용 툴
        WorkDuration workDuration,       // 작업 기간
        Integer cutCount,                // 컷 수
        List<String> videoLinks,         // 영상 링크
        List<MaterialData> materials     // 소재 정보
) {
}
