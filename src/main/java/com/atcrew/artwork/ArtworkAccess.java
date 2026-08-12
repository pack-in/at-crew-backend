package com.atcrew.artwork;

public enum ArtworkAccess {
    ALLOWED,    // 열람 허용
    NOT_FOUND,  // 존재를 노출하지 않음(이미지 처리 중인 타인 작품)
    DELETED,    // 삭제된 작품
    PRIVATE,    // 완전 비공개 작품(피드 비공개 + 어느 라이브 포트폴리오에도 미포함)
    BLOCKED     // 운영 정책·법적 조치로 외부 노출이 중단된 작품
}
