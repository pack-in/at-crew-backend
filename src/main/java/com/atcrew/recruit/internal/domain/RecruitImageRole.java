package com.atcrew.recruit.internal.domain;

/**
 * 게시글 이미지의 용도 (docs/design/media-module-design.md §10.1).
 * 응답의 {@code thumbnailImage} 단일 필드는 THUMBNAIL 행에서, {@code referenceImages} 리스트는
 * REFERENCE 행에서 ordinal 순서대로 조립한다.
 */
public enum RecruitImageRole {
    THUMBNAIL,
    REFERENCE
}
