package com.atcrew.recruit.internal.application;

import com.atcrew.recruit.JobSeekingPostInfo;
import com.atcrew.recruit.internal.domain.JobSeekingPost;

class JobSeekingPostMapper {

    private JobSeekingPostMapper() {
    }

    static JobSeekingPostInfo toInfo(JobSeekingPost post, String authorName) {
        return new JobSeekingPostInfo(
                post.getId(),
                post.getAuthorMemberId(),
                authorName,
                post.getTitle(),
                post.getRoles(),
                post.getGenres(),
                post.getDrawingStyle(),
                post.getPreferredFeedbackStyle(),
                post.getWorkStyle(),
                post.getDesiredRate(),
                post.getPortfolioDescription(),
                post.getReferenceImages(),
                post.getStatus(),
                post.getDeletedAt(),
                post.getCreatedAt(),
                post.getUpdatedAt()
        );
    }
}
