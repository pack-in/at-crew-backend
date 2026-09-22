package com.atcrew.media;

/**
 * 소유자가 원하는 이미지 한 장 — {@link MediaService#syncAssets}에 넘기는 입력이다.
 *
 * @param key      업로드된 raw 원본 key
 * @param slotRole 소유자가 정하는 슬롯 이름(recruit의 {@code THUMBNAIL}/{@code REFERENCE}). media는 값을 해석하지
 *                 않고 보관했다가 그대로 돌려준다. 슬롯 구분이 없는 소유자(artwork)는 null을 넣는다.
 */
public record MediaAssetSpec(String key, String slotRole) {
    public static MediaAssetSpec of(String key) { return new MediaAssetSpec(key, null); }
}
