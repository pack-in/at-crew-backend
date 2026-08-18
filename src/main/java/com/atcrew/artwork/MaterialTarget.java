package com.atcrew.artwork;

/**
 * 소재 대상 정본 목록 (Notion 태그 정본 — 피그마 "UI개편_검색 필터 패널" 주석 기준).
 * 작품에 첨부하는 소재({@code Material}) 하나가 어떤 대상을 다루는지 나타낸다.
 */
public enum MaterialTarget {
    WEAPON,               // 무기
    BACKGROUND,           // 배경
    ACCESSORY,            // 장신구
    PANEL_DECORATION,     // 컷꾸미기
    EFFECT,               // 효과
    TYPESETTING,          // 식자
    CHARACTER             // 인물
}
