package com.atcrew.recruit.internal.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** 구인글 이미지 (docs/design/media-module-design.md §10.1). */
@Entity
@Table(name = "job_posting_images")
public class JobPostingImage extends RecruitPostingImage {

    protected JobPostingImage() {
    }

    public static JobPostingImage pending(String postingId, RecruitImageRole role, int ordinal, String originalKey) {
        JobPostingImage image = new JobPostingImage();
        image.init(postingId, role, ordinal, originalKey);
        return image;
    }
}
