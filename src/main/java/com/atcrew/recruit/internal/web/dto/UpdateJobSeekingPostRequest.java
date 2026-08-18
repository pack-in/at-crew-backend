package com.atcrew.recruit.internal.web.dto;

import com.atcrew.artwork.ArtworkRole;
import com.atcrew.artwork.Genre;
import com.atcrew.recruit.FeedbackStyle;
import com.atcrew.recruit.WorkStyle;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

// 부분 업데이트 — null인 필드는 기존 값을 유지한다.
public record UpdateJobSeekingPostRequest(
        @Size(max = 200) String title,                                                  // 구직글 제목
        @Size(max = 20) List<@NotNull ArtworkRole> roles,                               // 희망 역할
        @Size(max = 20) List<@NotNull Genre> genres,                                    // 희망 장르
        @Size(max = 200) String drawingStyle,                                           // 작화 스타일
        FeedbackStyle preferredFeedbackStyle,                                           // 선호 피드백 방식
        WorkStyle workStyle,                                                            // 작업 스타일
        @Size(max = 200) String desiredRate,                                            // 희망 단가 (자유 텍스트)
        @Size(max = 5000) String portfolioDescription,                                  // 포트폴리오 소개
        @Size(max = 20) List<@NotBlank @Size(max = 500) String> referenceImages         // 참고 이미지 URL 목록
) {
}
