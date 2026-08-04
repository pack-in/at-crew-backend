package com.atcrew.recruit.internal.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** 팀원모집글 이미지 (docs/design/media-module-design.md §10.1). */
@Entity
@Table(name = "team_posting_images")
public class TeamPostingImage extends RecruitPostingImage {

    protected TeamPostingImage() {
    }

    public static TeamPostingImage pending(String postingId, RecruitImageRole role, int ordinal, String originalKey) {
        TeamPostingImage image = new TeamPostingImage();
        image.init(postingId, role, ordinal, originalKey);
        return image;
    }
}
