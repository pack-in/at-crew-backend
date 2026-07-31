package com.atcrew.recruit.internal.web.dto;

import com.atcrew.recruit.FeedbackStyle;
import com.atcrew.recruit.WorkStyle;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateJobSeekingPostRequest(
        @NotBlank @Size(max = 200) String title,
        @Size(max = 20) List<@NotBlank @Size(max = 100) String> roles,
        @Size(max = 20) List<@NotBlank @Size(max = 100) String> genres,
        @Size(max = 200) String drawingStyle,
        FeedbackStyle preferredFeedbackStyle,
        WorkStyle workStyle,
        @Size(max = 200) String desiredRate,
        @Size(max = 5000) String portfolioDescription,
        @Size(max = 20) List<@NotBlank @Size(max = 500) String> referenceImages,
        boolean publish
) {
}
