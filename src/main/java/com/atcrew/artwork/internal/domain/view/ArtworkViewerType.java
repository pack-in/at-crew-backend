package com.atcrew.artwork.internal.domain.view;

/**
 * 작품 열람자 식별 방식(홈-R14). 같은 사람이라도 로그인 전후 기록은 서로 다른 열람자로 본다.
 */
public enum ArtworkViewerType {
    MEMBER,    // 로그인 회원 — viewer_key는 회원 ID
    ANONYMOUS  // 비로그인 — viewer_key는 FE가 1st-party 쿠키로 발급한 익명 UUID(X-Anonymous-Id 헤더)
}
