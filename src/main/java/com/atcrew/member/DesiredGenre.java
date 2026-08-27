package com.atcrew.member;

/**
 * 희망 장르 — 복수 선택 + 직접입력 (기획서 마이페이지_작가-R24 "희망 장르(29+직접입력)").
 *
 * <p>값 집합과 상수 이름은 artwork 모듈의 {@code Genre}와 동일하다(기획서 "이 값 구성은
 * 업로드-R20·R27의 장르와 동일"). 모듈 간 직접 의존 금지 원칙에 따라 별도 정의하되,
 * 이름을 맞춰 두면 구직글 복사 시 값 매핑이 그대로 성립한다.
 */
public enum DesiredGenre {
    BL, HL, GL,
    FANTASY,          // 판타지
    ROMANCE_FANTASY,  // 로맨스판타지
    ACTION,           // 액션
    MARTIAL_ARTS,     // 무협
    GORE,             // 고어
    HORROR,           // 공포
    NOIR,             // 느와르
    CRIME,            // 범죄
    THRILLER,         // 스릴러
    MYSTERY,          // 추리
    SUPERPOWER,       // 초능력
    SF,               // S/F
    COMEDY,           // 개그
    HEALING,          // 힐링
    SLICE_OF_LIFE,    // 일상
    DRAMA,            // 드라마
    SCHOOL,           // 학원
    GAME,             // 게임
    ORIENTAL_SETTING, // 동양배경
    WESTERN_SETTING,  // 서양배경
    PERIOD_HISTORY,   // 시대/역사
    MEDIEVAL,         // 중세
    MODERN,           // 현대
    EROTIC,           // 에로
    CREATURE,         // 크리처
    YOUTH             // 청춘
}
