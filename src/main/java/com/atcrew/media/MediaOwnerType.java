package com.atcrew.media;

/**
 * 이미지 소유자 종류.
 *
 * <p>{@code ARTWORK_THUMBNAIL}은 작품의 사용자 지정 썸네일이다(ownerId는 작품 ID). 본문 이미지({@code ARTWORK})와
 * owner를 나눠 두어, 본문 목록을 읽는 곳(대표 이미지 인덱스·처리 상태·스냅샷)에 썸네일이 섞이지 않게 한다.
 */
public enum MediaOwnerType { ARTWORK, ARTWORK_THUMBNAIL, JOB_POSTING, TEAM_POSTING, JOB_SEEKING_POST }
