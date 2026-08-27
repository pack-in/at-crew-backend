package com.atcrew.member;

/**
 * 희망 담당 업무 — 복수 선택 + 직접입력 (기획서 마이페이지_작가-R24 "희망 담당 업무(22+직접입력)").
 *
 * <p>recruit 모듈의 구직글에도 담당 업무가 있지만 값 집합이 서로 다르다(구직글은 artwork.ArtworkRole을
 * 쓰며 작화·식자가 없고 ETC가 있다). 모듈 간 직접 의존 금지 원칙에 따라 member가 자체 정의하며,
 * 두 값 집합의 통일은 별도 과제다.
 */
public enum DesiredRole {
    TOTAL_ARTWORK,         // 총작화
    ARTWORK,               // 작화
    ADAPTATION_STORYBOARD, // 각색콘티
    STORYBOARD,            // 콘티
    DIRECTION,             // 연출
    LINEART,               // 선화
    SKETCH,                // 스케치
    COLORING,              // 채색
    BASE_COLOR,            // 밑색
    ONE_TONE_SHADING,      // 1도명암
    POST_PROCESSING,       // 후보정
    PANEL_DECORATION,      // 원고꾸미기
    LETTERING,             // 식자
    FULL_COLOR,            // 풀채색
    THREE_D_MODELING,      // 3D모델링
    MATERIAL_MAKING,       // 소재제작
    MATERIAL_PLACEMENT,    // 소재배치
    BACKGROUND,            // 배경
    WEBNOVEL_COVER,        // 웹소설표지
    CHARACTER_DESIGN,      // 캐릭터디자인
    CHARACTER_SHEET,       // 캐릭터시트
    TYPOGRAPHY,            // 타이포
    BROADCAST_THUMBNAIL    // 방송썸네일
}
