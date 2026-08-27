package com.atcrew.member;

/**
 * 직접입력 태그가 붙는 항목 (기획서 업로드-R13 "직접입력 칩 공통 규칙").
 *
 * <p>직접입력 값은 공통 코드로 등록하지 않고 프로필별 개별 레코드로 저장한다(정책 데이터구조-R04).
 * 항목마다 테이블을 따로 두는 대신 한 테이블에 유형을 함께 저장한다 — 저장 규칙(10자·중복 불가·
 * 공백 제거)이 전 항목 공통이라 분리해서 얻는 것이 없다.
 */
public enum CustomTagType {
    DRAWING_STYLE, // 작화 스타일
    DESIRED_ROLE,  // 희망 담당 업무
    DESIRED_GENRE  // 희망 장르
}
