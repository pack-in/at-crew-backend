package com.atcrew.artwork;

import java.time.Instant;
import java.util.List;

public record ArtworkSummaryInfo(
        String id,                 // 작품 ID
        String authorId,           // 작성자 회원 ID
        String authorName,         // 작성자 이름
        String authorHandle,       // 작성자 handle
        String title,              // 작품 제목
        String thumbKey,           // 카드 썸네일 R2 키
        String thumbAdultKey,      // 성인 블러 썸네일 R2 키
        ArtworkField artworkField, // 작품 분야
        List<String> tags,         // 태그
        AgeRating ageRating,       // 연령 등급
        Visibility visibility,     // 작품 피드 공개 여부
        // 운영 정책·법적 조치에 따른 외부 노출 중단 여부(마이페이지_작가-R39) — 작품 관리 화면에서
        // 차단 안내 배지를 노출하고, 포트폴리오 작품 선택 대상에서는 제외한다(마이페이지_작가-R38)
        boolean blocked,
        ArtworkStatus status,      // 작품 상태
        Instant createdAt          // 등록 시각
) {
}
