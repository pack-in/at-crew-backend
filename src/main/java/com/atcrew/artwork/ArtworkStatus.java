package com.atcrew.artwork;

public enum ArtworkStatus {
    /** 이미지 처리 대기·진행 중. */
    PROCESSING,
    READY,
    /**
     * 이미지가 한 장도 성공하지 못해 공개할 수 없는 상태. 재시도 대상이 아니므로 사용자가 다시 올려야 한다
     * — 이 상태가 없으면 전량 실패한 작품이 PROCESSING에 영구 고착된다(부분 실패는 여전히 READY다).
     */
    FAILED,
    DELETED
}
