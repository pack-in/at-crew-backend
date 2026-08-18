package com.atcrew.recruit;

import com.atcrew.artwork.ArtworkRole;
import com.atcrew.artwork.Genre;

import java.time.Instant;
import java.util.List;

/**
 * 구직글 상세 응답 (docs/design/recruit-module-design.md §2.3).
 */
public record JobSeekingPostInfo(
        String id,                             // 구직글 ID
        String authorMemberId,                 // 작성자(창작자) Member ID
        String authorName,                     // 작성자 표시명 (member 모듈 조회 실패 시 null)
        String title,                          // 구직글 제목
        List<ArtworkRole> roles,               // 희망 역할
        List<Genre> genres,                    // 희망 장르
        String drawingStyle,                   // 작화 스타일
        FeedbackStyle preferredFeedbackStyle,  // 선호 피드백 방식
        WorkStyle workStyle,                   // 작업 스타일
        String desiredRate,                    // 희망 단가 (자유 텍스트)
        String portfolioDescription,           // 포트폴리오 소개
        List<String> referenceImages,          // 참고 이미지 URL 목록 (표시 전용)
        JobSeekingPostStatus status,           // 상태
        Instant deletedAt,                     // 휴지통 이동 시각 (null이면 휴지통 아님)
        Instant createdAt,                     // 생성 시각
        Instant updatedAt                      // 수정 시각
) {
}
