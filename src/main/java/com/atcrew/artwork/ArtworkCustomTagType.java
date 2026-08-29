package com.atcrew.artwork;

/**
 * 직접입력 태그가 붙는 항목 (기획서 업로드-R13 "직접입력 칩 공통 규칙").
 *
 * <p>직접입력 값은 공통 코드로 등록하지 않고 작품별 개별 레코드로 저장한다(정책 데이터구조-R04,
 * {@code com.atcrew.member.CustomTagType}과 동일한 이유). 소재 대상은 이미 JSON 컬럼이라
 * 여기 포함하지 않는다({@code MaterialData.customTargets} 참고).
 */
public enum ArtworkCustomTagType {
    ROLE,  // 담당 업무
    GENRE  // 장르
}
