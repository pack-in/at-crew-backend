package com.atcrew.artwork;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = """
        작품 상태 — 이미지 처리 진행 상황과 휴지통 여부를 나타낸다.
        - PROCESSING: 업로드 직후 이미지 변환 중. 공개 상태 변경·북마크 대상이 되지 않고, 타인에게는 존재가 노출되지 않는다.
        - READY: 모든 이미지 처리가 끝나 하나 이상 성공한 상태. 공개 범위에 따라 타인에게 노출된다. \
        이미지를 교체하면 다시 PROCESSING으로 돌아간다.
        - DELETED: 휴지통. 공개 범위가 PRIVATE로 강제되며 내 작품 목록에서 빠지고 휴지통 목록에 나온다. \
        복구하면 READY로 돌아간다.""",
        example = "READY")
public enum ArtworkStatus {
    PROCESSING, READY, DELETED
}
