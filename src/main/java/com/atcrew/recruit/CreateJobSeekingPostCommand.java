package com.atcrew.recruit;

import com.atcrew.artwork.ArtworkRole;
import com.atcrew.artwork.Genre;

import java.util.List;

/**
 * 구직글 작성 커맨드 (docs/design/recruit-module-design.md §2.3, §4.2).
 * 승인 절차가 없으므로 {@code publish=true}면 저장 즉시 PUBLISHED로 게시된다.
 */
public record CreateJobSeekingPostCommand(
        String title,                          // 구직글 제목
        List<ArtworkRole> roles,               // 희망 역할
        List<Genre> genres,                    // 희망 장르
        String drawingStyle,                   // 작화 스타일
        FeedbackStyle preferredFeedbackStyle,  // 선호 피드백 방식
        WorkStyle workStyle,                   // 작업 스타일
        String desiredRate,                    // 희망 단가 (자유 텍스트)
        String portfolioDescription,           // 포트폴리오 소개
        List<String> referenceImages,          // 참고 이미지 URL 목록 (표시 전용)
        boolean publish                        // true면 저장 직후 PUBLISHED로 게시, false면 DRAFT 저장
) {
}
