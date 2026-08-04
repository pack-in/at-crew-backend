package com.atcrew.recruit.internal.domain;

/**
 * 게시글의 이미지 처리 상태 (docs/design/media-module-design.md §10.2).
 *
 * <p>발행 상태({@code JobPostingStatus} DRAFT/PENDING/PUBLISHED/CLOSED/DELETED)와는 <b>독립된 축</b>이다.
 * artwork는 이미지가 곧 콘텐츠라 두 상태를 합쳤지만, recruit은 텍스트 게시글에 이미지가 부속이라
 * 발행 흐름과 이미지 처리 흐름을 분리해야 상태 전이 로직이 단순하게 유지된다.
 */
public enum RecruitImageProcessingStatus {
    PENDING,
    READY
}
