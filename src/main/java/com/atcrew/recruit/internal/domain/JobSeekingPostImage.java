package com.atcrew.recruit.internal.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** 구직글 이미지 (docs/design/media-module-design.md §10.1). 구직글은 썸네일 없이 REFERENCE만 쓴다. */
@Entity
@Table(name = "job_seeking_post_images")
public class JobSeekingPostImage extends RecruitPostingImage {

    protected JobSeekingPostImage() {
    }

    public static JobSeekingPostImage pending(String postingId, RecruitImageRole role, int ordinal,
            String originalKey) {
        JobSeekingPostImage image = new JobSeekingPostImage();
        image.init(postingId, role, ordinal, originalKey);
        return image;
    }
}
