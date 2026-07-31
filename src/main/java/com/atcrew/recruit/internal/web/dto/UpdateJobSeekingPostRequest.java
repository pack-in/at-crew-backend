package com.atcrew.recruit.internal.web.dto;

import com.atcrew.recruit.FeedbackStyle;
import com.atcrew.recruit.WorkStyle;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

// 부분 업데이트 — null인 필드는 기존 값을 유지한다.
public record UpdateJobSeekingPostRequest(
        @Size(max = 200) String title,
        @Size(max = 20) List<@NotBlank @Size(max = 100) String> roles,
        @Size(max = 20) List<@NotBlank @Size(max = 100) String> genres,
        @Size(max = 200) String drawingStyle,
        FeedbackStyle preferredFeedbackStyle,
        WorkStyle workStyle,
        @Size(max = 200) String desiredRate,
        @Size(max = 5000) String portfolioDescription,
        @Size(max = 20) List<@NotBlank @Size(max = 500) String> referenceImages
) {
}
