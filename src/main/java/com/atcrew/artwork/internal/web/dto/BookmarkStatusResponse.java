package com.atcrew.artwork.internal.web.dto;

// 현재 회원의 작품 저장 여부 — 상세 화면의 저장 버튼 상태용이다.
public record BookmarkStatusResponse(boolean bookmarked) {}
