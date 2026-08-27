package com.atcrew.portfolio;

public enum ReflectionType {
    LIVE,     // 최신 반영형 — 원본 작품을 참조해 수정·삭제·공개범위 변경이 그대로 반영된다
    SNAPSHOT  // 고정형 — 생성 시점 표시 필드를 복사해 원본 변경에 영향받지 않는다
}
