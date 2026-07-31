package com.atcrew.recruit;

import java.util.List;

/**
 * 구직글 수정 커맨드 (docs/design/recruit-module-design.md §2.3, §4.2).
 * null인 필드는 기존 값을 유지하는 부분 업데이트다.
 */
public record UpdateJobSeekingPostCommand(
        String title,                          // 구직글 제목
        List<String> roles,                    // 희망 역할
        List<String> genres,                   // 희망 장르
        String drawingStyle,                   // 작화 스타일
        FeedbackStyle preferredFeedbackStyle,  // 선호 피드백 방식
        WorkStyle workStyle,                   // 작업 스타일
        String desiredRate,                    // 희망 단가 (자유 텍스트)
        String portfolioDescription,           // 포트폴리오 소개
        List<String> referenceImages           // 참고 이미지 URL 목록 (표시 전용)
) {
}
