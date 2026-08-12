package com.atcrew.artwork;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = """
        작품의 창작 유형.
        - ORIGINAL: 1차 창작.
        - SECONDARY: 2차 창작.
        - FAN_ART: 팬아트.
        - OC: 오리지널 캐릭터.
        - COMMISSION: 커미션 작업물.""",
        example = "ORIGINAL")
public enum CreativeType {
    ORIGINAL,   // 1차 창작
    SECONDARY,  // 2차 창작
    FAN_ART,    // 팬아트
    OC,         // OC (오리지널 캐릭터)
    COMMISSION  // 커미션
}
