package com.atcrew.recruit.internal.web.dto;

import com.atcrew.recruit.FeedbackStyle;
import com.atcrew.recruit.WorkStyle;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateJobSeekingPostRequest(
        @NotBlank @Size(max = 200) String title,                                        // 구직글 제목
        @Size(max = 20) List<@NotBlank @Size(max = 100) String> roles,                  // 희망 역할
        @Size(max = 20) List<@NotBlank @Size(max = 100) String> genres,                 // 희망 장르
        @Size(max = 200) String drawingStyle,                                           // 작화 스타일
        FeedbackStyle preferredFeedbackStyle,                                           // 선호 피드백 방식
        WorkStyle workStyle,                                                            // 작업 스타일
        @Size(max = 200) String desiredRate,                                            // 희망 단가 (자유 텍스트)
        @Size(max = 5000) String portfolioDescription,                                  // 포트폴리오 소개
        @Size(max = 20) List<@NotBlank @Size(max = 500) String> referenceImages,        // 참고 이미지 URL 목록
        boolean publish                                                                 // true면 저장 직후 PUBLISHED로 게시
) {
}
