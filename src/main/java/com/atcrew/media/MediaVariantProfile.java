package com.atcrew.media;

/**
 * Worker가 만들 변형본 집합 — 이미지의 역할(본문인가 카드 썸네일인가)로 정한다.
 *
 * <p>본문 이미지에는 카드 썸네일이 필요 없다. 예전에는 모든 이미지에 원본·썸네일·블러 썸네일을 다 만들어 쓰이지
 * 않는 파일이 쌓였고, 상세 본문이 3:4로 잘린 썸네일을 띄우는 결함의 빌미가 됐다. 카드 썸네일은 사용자가 3:4로
 * 잘라 올린 이미지 한 장에서만 만든다.
 *
 * <p>Worker의 {@code VARIANT_SETS}와 값이 일치해야 한다.
 */
public enum MediaVariantProfile {
    /** 본문 이미지 — {@code original/}만 만든다. 화질은 {@link MediaQualityTier}를 따른다. */
    ORIGINAL,
    /** 카드 썸네일 — {@code thumb/}만 만든다. */
    THUMBNAIL,
    /**
     * 카드 썸네일과 성인물 블러본 — {@code thumb/}와 {@code thumb-adult/}를 만든다(홈-R06). 연령 등급은 업로드 뒤에도
     * 바뀔 수 있어 등급과 무관하게 항상 만든다.
     */
    THUMBNAIL_WITH_ADULT_BLUR
}
