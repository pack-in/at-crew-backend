package com.atcrew.artwork;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = """
        작품에서 맡은 참여 역할 (복수 선택). 응답에서는 중복이 제거되고 아래 선언 순서로 정렬된다.
        TOTAL_ARTWORK: 총작화, ADAPTATION_STORYBOARD: 각색콘티, STORYBOARD: 콘티, DIRECTION: 연출, \
        LINEART: 선화, SKETCH: 스케치, COLORING: 채색, BASE_COLOR: 밑색, TONE_WORK: 1도명암, \
        POST_PROCESSING: 후보정, FULL_COLOR: 풀채색, PANEL_DECORATION: 컷꾸미기, THREE_D_MODELING: 3D모델링, \
        MATERIAL_MAKING: 소재제작, MATERIAL_PLACEMENT: 소재배치, BACKGROUND: 배경, WEBNOVEL_COVER: 웹소설표지, \
        CHARACTER_DESIGN: 캐릭터디자인, CHARACTER_SHEET: 캐릭터시트, TYPOGRAPHY: 타이포, \
        BROADCAST_THUMBNAIL: 방송썸네일, ETC: 직접입력""",
        example = "TOTAL_ARTWORK")
public enum ArtworkRole {
    TOTAL_ARTWORK,        // 총작화
    ADAPTATION_STORYBOARD,// 각색콘티
    STORYBOARD,           // 콘티
    DIRECTION,            // 연출
    LINEART,              // 선화
    SKETCH,               // 스케치
    COLORING,             // 채색
    BASE_COLOR,           // 밑색
    TONE_WORK,            // 1도명암
    POST_PROCESSING,      // 후보정
    FULL_COLOR,           // 풀채색
    PANEL_DECORATION,     // 컷꾸미기
    THREE_D_MODELING,     // 3D모델링
    MATERIAL_MAKING,      // 소재제작
    MATERIAL_PLACEMENT,   // 소재배치
    BACKGROUND,           // 배경
    WEBNOVEL_COVER,       // 웹소설표지
    CHARACTER_DESIGN,     // 캐릭터디자인
    CHARACTER_SHEET,      // 캐릭터시트
    TYPOGRAPHY,           // 타이포
    BROADCAST_THUMBNAIL,  // 방송썸네일
    ETC                   // 직접입력 (기타)
}
