package com.atcrew.recruit.internal.application;

import com.atcrew.recruit.JobSeekingPostInfo;
import com.atcrew.recruit.internal.domain.JobSeekingPost;

class JobSeekingPostMapper {

    private JobSeekingPostMapper() {
    }

    // images가 null이면(자식 행이 없는 과거 데이터) 기존 컬럼으로 폴백한다(설계 §10.4).
    static JobSeekingPostInfo toInfo(JobSeekingPost post, String authorName, PostingImages images) {
        PostingImages resolved = images != null ? images
                : PostingImages.legacy(null, post.getReferenceImages());
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
                resolved.referenceImages(),
                post.getStatus(),
                post.getDeletedAt(),
                post.getCreatedAt(),
                post.getUpdatedAt()
        );
    }
}
